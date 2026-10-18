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

version_at_least() {
    local current="${1#v}"
    local required_major="$2"
    local required_minor="${3:-0}"
    local current_major="${current%%.*}"
    local remainder="${current#*.}"
    local current_minor="${remainder%%.*}"

    [[ "${current_major}" =~ ^[0-9]+$ && "${current_minor}" =~ ^[0-9]+$ ]] || return 1
    (( current_major > required_major )) \
        || (( current_major == required_major && current_minor >= required_minor ))
}

require_maven_39() {
    require_command mvn "请安装 Maven 3.9 或更高版本。"
    local version
    version="$(mvn -version 2>/dev/null | awk 'NR == 1 { print $3 }')"
    version_at_least "${version}" 3 9 \
        || die "需要 Maven 3.9 或更高版本，当前版本为 ${version:-未知}。"
}

require_pnpm_9() {
    require_command pnpm "请执行 'corepack enable'，或通过 npm install -g pnpm 安装 pnpm 9+。"
    local version
    version="$(pnpm --version 2>/dev/null)"
    version_at_least "${version}" 9 0 \
        || die "需要 pnpm 9 或更高版本，当前版本为 ${version:-未知}。"
}

docker_install_help() {
    case "$(uname -s 2>/dev/null || true)" in
        Darwin)
            printf '%s\n' \
                "  macOS：brew install --cask docker" \
                "  或访问：https://docs.docker.com/desktop/setup/install/mac-install/"
            ;;
        Linux)
            if [[ -n "${WSL_DISTRO_NAME:-}" ]]; then
                printf '%s\n' \
                    "  WSL2：请在 Windows 安装 Docker Desktop 并启用 WSL Integration" \
                    "  文档：https://docs.docker.com/desktop/features/wsl/"
            else
                printf '%s\n' \
                    "  Linux：请安装 Docker Engine 与 Compose 插件" \
                    "  文档：https://docs.docker.com/engine/install/"
            fi
            ;;
        MINGW*|MSYS*|CYGWIN*)
            printf '%s\n' \
                "  Windows：winget install -e --id Docker.DockerDesktop" \
                "  或访问：https://docs.docker.com/desktop/setup/install/windows-install/"
            ;;
        *)
            printf '%s\n' "  安装文档：https://docs.docker.com/get-docker/"
            ;;
    esac
}

start_windows_docker_desktop() {
    require_command powershell.exe "请手工启动 Docker Desktop 后重试。"
    powershell.exe -NoProfile -NonInteractive -Command \
        '$dockerDesktop = Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"; if (!(Test-Path $dockerDesktop)) { exit 1 }; Start-Process $dockerDesktop' \
        >/dev/null 2>&1 \
        || die "无法自动启动 Docker Desktop，请确认已安装后手工启动。"
}

start_docker_engine() {
    local platform
    platform="$(uname -s 2>/dev/null || true)"

    case "${platform}" in
        Darwin)
            require_command open "无法自动打开 Docker Desktop，请手工启动后重试。"
            open -gja Docker >/dev/null 2>&1 \
                || die "未找到 Docker Desktop。请先安装：brew install --cask docker"
            ;;
        Linux)
            if [[ -n "${WSL_DISTRO_NAME:-}" ]] && command -v powershell.exe >/dev/null 2>&1; then
                start_windows_docker_desktop
            elif command -v systemctl >/dev/null 2>&1; then
                if systemctl --user start docker-desktop >/dev/null 2>&1; then
                    :
                elif [[ "${EUID}" -eq 0 ]] && systemctl start docker >/dev/null 2>&1; then
                    :
                elif command -v sudo >/dev/null 2>&1 && sudo -n systemctl start docker >/dev/null 2>&1; then
                    :
                else
                    die "Docker 已安装但服务未运行，自动启动需要管理员权限。请执行：sudo systemctl start docker"
                fi
            else
                die "Docker 已安装但服务未运行，当前环境无法自动启动，请手工启动 Docker 后重试。"
            fi
            ;;
        MINGW*|MSYS*|CYGWIN*)
            start_windows_docker_desktop
            ;;
        *)
            die "Docker 已安装但服务未运行，当前系统无法自动识别启动方式，请手工启动后重试。"
            ;;
    esac
}

ensure_docker_ready() {
    if ! command -v docker >/dev/null 2>&1; then
        warn "未检测到 Docker，无法启动 MySQL 与 Redis。"
        docker_install_help >&2
        exit 1
    fi

    if ! docker info >/dev/null 2>&1; then
        info "Docker 已安装但服务未运行，正在尝试自动启动"
        start_docker_engine
        local i
        for ((i = 1; i <= 60; i++)); do
            docker info >/dev/null 2>&1 && break
            [[ "${i}" -eq 60 ]] \
                && die "Docker 120 秒内未就绪，请检查 Docker Desktop/Engine 状态后重试。"
            sleep 2
        done
    fi

    docker compose version >/dev/null 2>&1 \
        || die "Docker Compose v2 不可用。请安装 Compose 插件：https://docs.docker.com/compose/install/"
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
