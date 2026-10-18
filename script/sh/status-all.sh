#!/usr/bin/env bash

# SaaSGuard IAM 运行状态检查。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

process_status() {
    local name="$1"
    local pid_file="$2"
    local marker="$3"
    local url="$4"
    local port="$5"
    local pid="-"
    local state="未运行"

    if managed_process_running "${pid_file}" "${marker}"; then
        pid="$(pid_value "${pid_file}")"
        if curl --silent --fail --max-time 2 "${url}" >/dev/null 2>&1; then
            state="运行正常"
        else
            state="进程存在，服务未就绪"
        fi
    elif port_is_open "${port}"; then
        state="端口被非本脚本进程占用"
    fi
    printf '%-12s %-24s %s\n' "${name}" "${state}" "PID ${pid}"
}

container_status() {
    local name="$1"
    local container="$2"
    local state="未创建"
    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
        state="$(docker inspect --format '{{.State.Status}}{{if .State.Health}} / {{.State.Health.Status}}{{end}}' "${container}" 2>/dev/null || echo '未创建')"
    fi
    printf '%-12s %-24s %s\n' "${name}" "${state}" "${container}"
}

printf 'SaaSGuard IAM 运行状态\n\n'
printf '%-12s %-24s %s\n' "组件" "状态" "标识"
printf '%-12s %-24s %s\n' "------------" "------------------------" "--------------------"
process_status "后端" "${BACKEND_PID_FILE}" "saasguard-iam-server" "http://localhost:8082/api/actuator/health" "8082"
process_status "前端" "${FRONTEND_PID_FILE}" "vite/bin/vite.js" "http://localhost:5174" "5174"
container_status "MySQL" "auth-mysql"
container_status "Redis" "auth-redis"

printf '\n日志：\n'
printf '  %s\n  %s\n' "${BACKEND_LOG}" "${FRONTEND_LOG}"
