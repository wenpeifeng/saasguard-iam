#!/usr/bin/env bash

# SaaSGuard IAM 本地运维脚本公共函数。该文件只被 start/stop/status 脚本 source。

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_DIR="${PROJECT_ROOT}/.run"
LOG_DIR="${PROJECT_ROOT}/logs"
BACKEND_PID_FILE="${RUN_DIR}/backend.pid"
FRONTEND_PID_FILE="${RUN_DIR}/frontend.pid"
BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"

mkdir -p "${RUN_DIR}" "${LOG_DIR}"

if [[ -t 1 ]]; then
    COLOR_CYAN=$'\033[36m'
    COLOR_GREEN=$'\033[32m'
    COLOR_YELLOW=$'\033[33m'
    COLOR_RED=$'\033[31m'
    COLOR_RESET=$'\033[0m'
else
    COLOR_CYAN=""
    COLOR_GREEN=""
    COLOR_YELLOW=""
    COLOR_RED=""
    COLOR_RESET=""
fi

info()    { printf '%s[SAASGUARD]%s %s\n' "${COLOR_CYAN}" "${COLOR_RESET}" "$*"; }
success() { printf '%s[SAASGUARD] ✓%s %s\n' "${COLOR_GREEN}" "${COLOR_RESET}" "$*"; }
warn()    { printf '%s[SAASGUARD] !%s %s\n' "${COLOR_YELLOW}" "${COLOR_RESET}" "$*" >&2; }
die()     { printf '%s[SAASGUARD] ✗%s %s\n' "${COLOR_RED}" "${COLOR_RESET}" "$*" >&2; exit 1; }

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "缺少命令 '$1'。$2"
}

java_major_version() {
    "$1" -version 2>&1 | awk -F '"' '/version/ { split($2, version, "."); print version[1]; exit }'
}

configure_java_21() {
    local candidate=""
    local java_bin=""
    local candidates=(
        "${JAVA_HOME:-}"
        "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
        "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
        "/usr/lib/jvm/java-21-openjdk"
        "/usr/lib/jvm/java-21-openjdk-amd64"
        "/usr/lib/jvm/java-21-openjdk-arm64"
    )

    if command -v java >/dev/null 2>&1 && [[ "$(java_major_version "$(command -v java)")" == "21" ]]; then
        java_bin="$(command -v java)"
        export JAVA_HOME="$("${java_bin}" -XshowSettings:properties -version 2>&1 | awk -F '= ' '/^[[:space:]]*java.home =/ { print $2; exit }')"
        [[ -n "${JAVA_HOME}" ]] || die "无法从当前 Java 运行时解析 JAVA_HOME。"
        export PATH="${JAVA_HOME}/bin:${PATH}"
        return
    fi

    for candidate in "${candidates[@]}"; do
        [[ -n "${candidate}" && -x "${candidate}/bin/java" ]] || continue
        if [[ "$(java_major_version "${candidate}/bin/java")" == "21" ]]; then
            export JAVA_HOME="${candidate}"
            export PATH="${JAVA_HOME}/bin:${PATH}"
            return
        fi
    done

    die "需要 JDK 21。请安装后设置 JAVA_HOME，例如 macOS 执行：brew install openjdk@21"
}

require_node_20() {
    require_command node "请安装 Node.js 20 或更高版本。"
    local major
    major="$(node --version | sed -E 's/^v([0-9]+).*/\1/')"
    [[ "${major}" =~ ^[0-9]+$ && "${major}" -ge 20 ]] \
        || die "需要 Node.js 20 或更高版本，当前版本为 $(node --version)。"
}

pid_value() {
    local pid_file="$1"
    [[ -f "${pid_file}" ]] && tr -dc '0-9' < "${pid_file}"
}

managed_process_running() {
    local pid_file="$1"
    local marker="$2"
    local pid
    pid="$(pid_value "${pid_file}")"
    [[ -n "${pid}" ]] || return 1
    kill -0 "${pid}" 2>/dev/null || return 1
    ps -p "${pid}" -o command= 2>/dev/null | grep -Fq -- "${marker}"
}

remove_stale_pid() {
    local pid_file="$1"
    local marker="$2"
    if [[ -f "${pid_file}" ]] && ! managed_process_running "${pid_file}" "${marker}"; then
        rm -f "${pid_file}"
    fi
}

port_is_open() {
    local port="$1"
    if command -v lsof >/dev/null 2>&1; then
        lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
    elif command -v nc >/dev/null 2>&1; then
        nc -z 127.0.0.1 "${port}" >/dev/null 2>&1
    else
        (echo >/dev/tcp/127.0.0.1/"${port}") >/dev/null 2>&1
    fi
}

wait_for_url() {
    local url="$1"
    local attempts="$2"
    local interval="$3"
    local i
    for ((i = 1; i <= attempts; i++)); do
        curl --silent --fail --max-time 2 "${url}" >/dev/null 2>&1 && return 0
        sleep "${interval}"
    done
    return 1
}

stop_managed_process() {
    local name="$1"
    local pid_file="$2"
    local marker="$3"
    local pid

    if ! managed_process_running "${pid_file}" "${marker}"; then
        rm -f "${pid_file}"
        info "${name}未由本项目脚本启动，无需停止"
        return
    fi

    pid="$(pid_value "${pid_file}")"
    kill "${pid}" 2>/dev/null || true
    for _ in {1..20}; do
        kill -0 "${pid}" 2>/dev/null || break
        sleep 0.5
    done
    if kill -0 "${pid}" 2>/dev/null; then
        warn "${name}未在 10 秒内退出，执行强制停止"
        kill -9 "${pid}" 2>/dev/null || true
    fi
    rm -f "${pid_file}"
    success "${name}已停止"
}
