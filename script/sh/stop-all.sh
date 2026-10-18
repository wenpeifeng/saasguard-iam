#!/usr/bin/env bash

# SaaSGuard IAM 一键停止。默认保留 MySQL/Redis；--with-infra 会停止容器但保留数据卷。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

STOP_INFRA=false

usage() {
    cat <<'EOF'
SaaSGuard IAM 一键停止

用法：
  ./script/sh/stop-all.sh [--with-infra]

选项：
  --with-infra  同时停止 MySQL、Redis 容器，但保留数据库数据卷
  --with-db     --with-infra 的兼容别名
  -h, --help    显示帮助
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --with-infra|--with-db) STOP_INFRA=true ;;
        -h|--help) usage; exit 0 ;;
        *) usage; die "未知选项：$1" ;;
    esac
    shift
done

cd "${PROJECT_ROOT}"
stop_managed_process "前端" "${FRONTEND_PID_FILE}" "vite/bin/vite.js"
stop_managed_process "后端" "${BACKEND_PID_FILE}" "saasguard-iam-server"

if [[ "${STOP_INFRA}" == true ]]; then
    require_command docker "请安装 Docker。"
    if ! detect_docker_access \
        && [[ "$(uname -s 2>/dev/null || true)" == Linux ]] \
        && command -v sudo >/dev/null 2>&1; then
        sudo -v
        detect_docker_access || true
    fi
    if detect_docker_access; then
        for container in auth-mysql auth-redis; do
            if docker_cmd inspect "${container}" >/dev/null 2>&1; then
                docker_cmd stop "${container}" >/dev/null
            fi
        done
        success "MySQL 与 Redis 已停止，数据卷已保留"
    else
        warn "Docker 服务未运行，跳过基础设施停止"
    fi
else
    info "MySQL 与 Redis 保持运行；如需一并停止，请执行：./script/sh/stop-all.sh --with-infra"
fi

if port_is_open 5174; then
    warn "端口 5174 仍被非本脚本管理的进程占用"
fi
if port_is_open 8082; then
    warn "端口 8082 仍被非本脚本管理的进程占用"
fi
success "停止完成"
