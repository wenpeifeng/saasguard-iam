#!/usr/bin/env bash

# SaaSGuard IAM 一键启动：基础设施 -> 后端 -> 前端。
# 用法：./script/sh/start-all.sh [--backend-only|--web-only] [--no-infra] [--skip-build] [--skip-install] [--check-only] [--no-auto-install]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

MODE="all"
START_INFRA=true
SKIP_BUILD=false
SKIP_INSTALL=false
CHECK_ONLY=false
export AUTO_INSTALL=true

usage() {
    cat <<'EOF'
SaaSGuard IAM 一键启动

用法：
  ./script/sh/start-all.sh [选项]

选项：
  --backend-only  仅启动 MySQL、Redis 和后端
  --web-only      仅启动前端
  --no-infra      不启动 MySQL、Redis（使用已配置的外部基础设施）
  --skip-build    跳过 Maven 后端构建（需要已有 JAR）
  --skip-install  跳过 pnpm install（需要已有 node_modules）
  --check-only    检查并自动补齐环境，但不启动项目组件
  --no-auto-install
                  只检查依赖，缺少工具时停止，不执行自动安装
  -h, --help      显示帮助
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --backend-only) MODE="backend" ;;
        --web-only) MODE="web"; START_INFRA=false ;;
        --no-infra) START_INFRA=false ;;
        --skip-build) SKIP_BUILD=true ;;
        --skip-install) SKIP_INSTALL=true ;;
        --check-only) CHECK_ONLY=true ;;
        --no-auto-install) export AUTO_INSTALL=false ;;
        -h|--help) usage; exit 0 ;;
        *) usage; die "未知选项：$1" ;;
    esac
    shift
done

cd "${PROJECT_ROOT}"

preflight_environment() {
    info "执行启动前置环境检查"
    ensure_curl

    if [[ "${MODE}" != "web" ]]; then
        configure_java_21
        if [[ "${SKIP_BUILD}" == false ]]; then
            require_maven_39
        elif ! find auth-backend/auth-bootstrap/target -maxdepth 1 -type f \
            -name 'saasguard-iam-server-*.jar' ! -name '*.original' -print -quit 2>/dev/null | grep -q .; then
            die "--skip-build 需要已有后端 JAR，请去掉该选项后重试。"
        fi

        remove_stale_pid "${BACKEND_PID_FILE}" "saasguard-iam-server"
        if ! managed_process_running "${BACKEND_PID_FILE}" "saasguard-iam-server" && port_is_open 8082; then
            die "端口 8082 已被其他进程占用。请释放端口后重试；脚本不会终止非本项目进程。"
        fi
    fi

    if [[ "${MODE}" != "backend" ]]; then
        require_node_20
        if [[ "${SKIP_INSTALL}" == false ]]; then
            require_pnpm_9
        elif [[ ! -f auth-web/node_modules/vite/bin/vite.js ]]; then
            die "--skip-install 需要已有前端 node_modules，请去掉该选项后重试。"
        fi

        remove_stale_pid "${FRONTEND_PID_FILE}" "vite/bin/vite.js"
        if ! managed_process_running "${FRONTEND_PID_FILE}" "vite/bin/vite.js" && port_is_open 5174; then
            die "端口 5174 已被其他进程占用。请释放端口后重试；脚本不会终止非本项目进程。"
        fi
    fi

    if [[ "${MODE}" != "web" && "${START_INFRA}" == true ]]; then
        ensure_docker_ready
    else
        info "当前选项不启动基础设施，跳过 Docker 检查"
    fi

    success "前置环境检查通过"
    if [[ "${MODE}" != "web" ]]; then
        local backend_summary
        backend_summary="Java $(java_major_version "${JAVA_HOME}/bin/java")"
        [[ "${SKIP_BUILD}" == true ]] || backend_summary="${backend_summary} / Maven $(mvn -version | awk 'NR == 1 { print $3 }')"
        info "后端环境：${backend_summary}"
    fi
    if [[ "${MODE}" != "backend" ]]; then
        local frontend_summary
        frontend_summary="Node $(node --version)"
        [[ "${SKIP_INSTALL}" == true ]] || frontend_summary="${frontend_summary} / pnpm $(pnpm --version)"
        info "前端环境：${frontend_summary}"
    fi
    if [[ "${MODE}" != "web" && "${START_INFRA}" == true ]]; then
        info "容器环境：Docker $(docker_cmd version --format '{{.Server.Version}}') / Compose $(docker_cmd compose version --short)"
    fi
}

start_infrastructure() {
    info "启动 MySQL 8 与 Redis 7（重复执行是安全的）"
    if docker_cmd inspect auth-mysql >/dev/null 2>&1; then
        info "复用已存在的 auth-mysql 容器"
        docker_cmd start auth-mysql >/dev/null
    else
        docker_cmd compose up -d mysql
    fi
    if docker_cmd inspect auth-redis >/dev/null 2>&1; then
        info "复用已存在的 auth-redis 容器"
        docker_cmd start auth-redis >/dev/null
    else
        docker_cmd compose up -d redis
    fi

    info "等待 MySQL 健康检查通过"
    local i health
    for ((i = 1; i <= 40; i++)); do
        health="$(docker_cmd inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' auth-mysql 2>/dev/null || true)"
        [[ "${health}" == "healthy" ]] && break
        [[ "${health}" == "unhealthy" ]] && die "MySQL 健康检查失败，请执行：docker logs auth-mysql"
        [[ "${i}" -eq 40 ]] && die "MySQL 80 秒内未就绪，请执行：docker logs auth-mysql"
        sleep 2
    done

    for ((i = 1; i <= 20; i++)); do
        health="$(docker_cmd inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' auth-redis 2>/dev/null || true)"
        [[ "${health}" == "healthy" ]] && break
        [[ "${health}" == "unhealthy" ]] && die "Redis 健康检查失败，请执行：docker logs auth-redis"
        [[ "${i}" -eq 20 ]] && die "Redis 40 秒内未就绪，请执行：docker logs auth-redis"
        sleep 2
    done
    success "MySQL 与 Redis 已就绪"
}

start_backend() {
    remove_stale_pid "${BACKEND_PID_FILE}" "saasguard-iam-server"

    if managed_process_running "${BACKEND_PID_FILE}" "saasguard-iam-server"; then
        info "检测到本项目后端正在运行，先停止旧进程再重新启动"
        stop_managed_process "后端" "${BACKEND_PID_FILE}" "saasguard-iam-server"
    fi
    port_is_open 8082 && die "端口 8082 已被其他进程占用。请释放端口后重试；脚本不会终止非本项目进程。"

    if [[ "${SKIP_BUILD}" == false ]]; then
        info "使用 JDK $(java_major_version "${JAVA_HOME}/bin/java") 构建后端"
        mvn -q -f auth-backend/pom.xml -DskipTests package
    fi

    local jar=""
    if [[ -d auth-backend/auth-bootstrap/target ]]; then
        jar="$(find auth-backend/auth-bootstrap/target -maxdepth 1 -type f -name 'saasguard-iam-server-*.jar' ! -name '*.original' | sort | head -n 1)"
    fi
    [[ -n "${jar}" ]] || die "未找到后端 JAR。请去掉 --skip-build 后重试。"

    info "启动后端：http://localhost:8082/api"
    nohup "${JAVA_HOME}/bin/java" -Dfile.encoding=UTF-8 -jar "${jar}" \
        --spring.profiles.active=dev >"${BACKEND_LOG}" 2>&1 &
    echo "$!" > "${BACKEND_PID_FILE}"

    if ! wait_for_url "http://localhost:8082/api/actuator/health" 60 2; then
        warn "后端启动失败，最近日志如下："
        tail -n 60 "${BACKEND_LOG}" >&2 || true
        stop_managed_process "后端" "${BACKEND_PID_FILE}" "saasguard-iam-server"
        die "后端 120 秒内未就绪。完整日志：${BACKEND_LOG}"
    fi
    success "后端已就绪：http://localhost:8082/api"
}

frontend_dependency_fingerprint() {
    node -e '
        const crypto = require("node:crypto");
        const fs = require("node:fs");
        const hash = crypto.createHash("sha256");
        for (const file of process.argv.slice(1)) {
            hash.update(file);
            hash.update(fs.readFileSync(file));
        }
        process.stdout.write(hash.digest("hex"));
    ' \
        auth-web/package.json \
        auth-web/pnpm-lock.yaml \
        auth-web/pnpm-workspace.yaml
}

ensure_frontend_dependencies() {
    local fingerprint
    local fingerprint_file="auth-web/node_modules/.saasguard-dependencies.sha256"
    fingerprint="$(frontend_dependency_fingerprint)"

    if [[ -f auth-web/node_modules/vite/bin/vite.js \
        && -f "${fingerprint_file}" \
        && "$(<"${fingerprint_file}")" == "${fingerprint}" ]]; then
        info "前端依赖未变化，直接复用现有 node_modules"
        return
    fi

    info "首次安装或依赖文件已变化，安装前端依赖"
    (cd auth-web && pnpm install --frozen-lockfile)
    printf '%s\n' "${fingerprint}" > "${fingerprint_file}"
}

start_frontend() {
    remove_stale_pid "${FRONTEND_PID_FILE}" "vite/bin/vite.js"

    if managed_process_running "${FRONTEND_PID_FILE}" "vite/bin/vite.js"; then
        info "检测到本项目前端正在运行，先停止旧进程再重新启动"
        stop_managed_process "前端" "${FRONTEND_PID_FILE}" "vite/bin/vite.js"
    fi
    port_is_open 5174 && die "端口 5174 已被其他进程占用。请释放端口后重试；脚本不会终止非本项目进程。"

    if [[ "${SKIP_INSTALL}" == false ]]; then
        ensure_frontend_dependencies
    fi
    [[ -f auth-web/node_modules/vite/bin/vite.js ]] \
        || die "未找到 Vite。请去掉 --skip-install 后重试。"

    info "启动前端：http://localhost:5174"
    (
        cd auth-web
        nohup node node_modules/vite/bin/vite.js --host 0.0.0.0 --port 5174 --strictPort \
            >"${FRONTEND_LOG}" 2>&1 &
        echo "$!" > "${FRONTEND_PID_FILE}"
    )

    if ! wait_for_url "http://localhost:5174" 40 1; then
        warn "前端启动失败，最近日志如下："
        tail -n 60 "${FRONTEND_LOG}" >&2 || true
        stop_managed_process "前端" "${FRONTEND_PID_FILE}" "vite/bin/vite.js"
        die "前端 40 秒内未就绪。完整日志：${FRONTEND_LOG}"
    fi
    success "前端已就绪：http://localhost:5174"
}

preflight_environment
if [[ "${CHECK_ONLY}" == true ]]; then
    success "仅检查模式完成，未启动项目容器或应用进程"
    exit 0
fi

if [[ "${MODE}" != "web" && "${START_INFRA}" == true ]]; then
    start_infrastructure
fi
if [[ "${MODE}" != "web" ]]; then
    start_backend
fi
if [[ "${MODE}" != "backend" ]]; then
    start_frontend
fi

printf '\n'
success "启动完成"
printf '  管理控制台  http://localhost:5174\n'
printf '  后端 API    http://localhost:8082/api\n'
printf '  Swagger     http://localhost:8082/api/swagger-ui.html\n'
printf '  状态检查    ./script/sh/status-all.sh\n'
printf '  停止服务    ./script/sh/stop-all.sh\n'
printf '  查看日志    tail -f logs/backend.log logs/frontend.log\n'
