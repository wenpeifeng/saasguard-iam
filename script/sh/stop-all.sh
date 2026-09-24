#!/bin/bash
# sf-auth 统一认证中心一键停止脚本
# 停止：后端 java 进程、前端 dev server；数据库容器默认保留（--with-db 时一并停止）

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
log() { echo -e "\033[36m[STOP]\033[0m $1"; }

# 停后端（优先用 pid 文件，兜底按端口杀）
if [ -f "${PROJECT_ROOT}/script/logs/backend.pid" ]; then
    PID=$(cat "${PROJECT_ROOT}/script/logs/backend.pid")
    if kill -0 "${PID}" 2>/dev/null; then kill "${PID}" && log "后端已停止 (pid ${PID})"; fi
    rm -f "${PROJECT_ROOT}/script/logs/backend.pid"
fi
lsof -t -i :8082 -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null

# 停前端
lsof -t -i :5174 -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null && log "前端已停止"

# 可选：连数据库一起停
if [ "$1" == "--with-db" ]; then
    docker stop auth-mysql auth-redis >/dev/null 2>&1 && log "MySQL/Redis 容器已停止（数据保留）"
else
    log "数据库容器保持运行（如需停止: $0 --with-db）"
fi

log "完成 ✓"
