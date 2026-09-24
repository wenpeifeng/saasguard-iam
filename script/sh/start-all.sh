#!/bin/bash
# sf-auth 统一认证中心一键启动脚本
# 流程：环境检查 -> 基础设施(容器已在跑则跳过) -> 后端(编译+后台启动) -> 前端(dev server)
# 注意：后端必须在仓库根目录启动（Flyway 迁移脚本在 script/sql/migration）。
# 用法：./script/sh/start-all.sh  [--backend-only | --web-only]

set -e
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${PROJECT_ROOT}"
mkdir -p script/logs

# 环境自包含：脚本内直接定位 JDK21 与 Maven（不依赖用户 shell 的 PATH 配置）
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="${JAVA_HOME}/bin:${HOME}/tools/maven/bin:/opt/homebrew/bin:${PATH}"

log()  { echo -e "\033[36m[START]\033[0m $1"; }
warn() { echo -e "\033[33m[START][警告]\033[0m $1"; }
die()  { echo -e "\033[31m[START][错误]\033[0m $1"; exit 1; }

MODE="all"
[ "$1" == "--backend-only" ] && MODE="backend"
[ "$1" == "--web-only" ] && MODE="web"

# ---------- 1. 环境 & 基础设施 ----------
if [ "${MODE}" != "web" ]; then
    command -v docker >/dev/null 2>&1 || die "未安装 Docker"
    docker info >/dev/null 2>&1 || die "Docker daemon 未运行，请先打开 Docker Desktop"
    log "Docker 正常 (v$(docker --version 2>/dev/null | sed 's/[^0-9.]*//g' || echo '?'))"

    # 基础设施三态处理（与 erp/mes 的 env-init.sh 逻辑对齐）：
    #   运行中 -> 跳过；已停止 -> docker start；不存在 -> docker compose 创建
    if docker ps --format '{{.Names}}' | grep -q "^auth-"; then
        log "auth-mysql / auth-redis 已在运行，跳过"
    elif docker ps -a --format '{{.Names}}' | grep -q "^auth-"; then
        log "启动已停止的 auth-mysql / auth-redis..."
        docker start auth-mysql auth-redis || die "基础设施启动失败"
    else
        log "首次创建基础设施 (MySQL 13306 / Redis 16379)..."
        docker compose up -d || die "基础设施启动失败"
    fi

    log "等待 MySQL 就绪..."
    for i in $(seq 1 30); do
        docker exec auth-mysql mysqladmin ping -uroot -pauth123456 --silent >/dev/null 2>&1 && break
        [ "$i" -eq 30 ] && die "MySQL 60 秒内未就绪: docker logs auth-mysql"
        sleep 2
    done
    log "环境初始化完成 ✓"
fi

# ---------- 2. 后端 ----------
if [ "${MODE}" != "web" ]; then
    log "编译后端 (跳过测试，首次较慢)..."
    (mvn -q -f auth-backend/pom.xml install -DskipTests) || die "后端编译失败"

    if lsof -i :8082 -sTCP:LISTEN >/dev/null 2>&1; then
        log "端口 8082 已被占用，先停止旧进程..."
        kill $(lsof -t -i :8082 -sTCP:LISTEN) 2>/dev/null || true
        sleep 2
    fi

    log "启动后端 (8082)..."
    JAR=$(ls auth-backend/auth-bootstrap/target/auth-bootstrap-*.jar | head -1)
    # 必须在仓库根目录启动（Flyway 脚本在 script/sql/migration）
    nohup java -jar "${JAR}" \
        --spring.profiles.active=dev \
        > "${PROJECT_ROOT}/script/logs/backend.log" 2>&1 &
    echo $! > "${PROJECT_ROOT}/script/logs/backend.pid"

    log "等待后端就绪..."
    for i in $(seq 1 30); do
        if curl -sf http://localhost:8082/api/actuator/health >/dev/null 2>&1; then
            log "后端就绪: http://localhost:8082/api  (日志: script/logs/backend.log)"
            break
        fi
        [ "$i" -eq 30 ] && { tail -30 "${PROJECT_ROOT}/script/logs/backend.log"; die "后端 60 秒内未就绪，以上是日志尾部"; }
        sleep 2
    done
fi

# ---------- 3. 前端 ----------
if [ "${MODE}" != "backend" ]; then
    log "安装前端依赖（首次较慢）..."
    (cd auth-web && (pnpm install --silent 2>/dev/null || npm install --silent)) || die "前端依赖安装失败"

    if lsof -i :5174 -sTCP:LISTEN >/dev/null 2>&1; then
        log "端口 5174 已被占用，先停止旧进程..."
        kill $(lsof -t -i :5174 -sTCP:LISTEN) 2>/dev/null || true
        sleep 1
    fi

    log "启动前端 dev server (5174)..."
    nohup bash -c 'cd auth-web && (pnpm dev 2>/dev/null || npm run dev)' \
        > "${PROJECT_ROOT}/script/logs/frontend.log" 2>&1 &

    for i in $(seq 1 20); do
        if curl -sf http://localhost:5174 >/dev/null 2>&1; then
            log "前端就绪: http://localhost:5174  (日志: script/logs/frontend.log)"
            break
        fi
        [ "$i" -eq 20 ] && warn "前端 20 秒内未响应，可查看 script/logs/frontend.log"
        sleep 1
    done
fi

log "全部启动完成 ✓  前端: http://localhost:5174   后端: http://localhost:8082/api"
