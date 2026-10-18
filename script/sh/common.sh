#!/usr/bin/env bash
# shellcheck disable=SC2034  # 公共变量由 source 本文件的生命周期脚本使用。

# SaaSGuard IAM 本地运维脚本公共函数。该文件只被 start/stop/status 脚本 source。

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_DIR="${PROJECT_ROOT}/.run"
LOG_DIR="${PROJECT_ROOT}/logs"
BACKEND_PID_FILE="${RUN_DIR}/backend.pid"
FRONTEND_PID_FILE="${RUN_DIR}/frontend.pid"
BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"
LOCAL_TOOLS_DIR="${RUN_DIR}/tools"
APT_UPDATED=false
DOCKER_USE_SUDO=false

mkdir -p "${RUN_DIR}" "${LOG_DIR}" "${LOCAL_TOOLS_DIR}"

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

auto_install_enabled() {
    [[ "${AUTO_INSTALL:-true}" == true ]]
}

prepend_path() {
    local directory="$1"
    [[ -d "${directory}" ]] || return 0
    case ":${PATH}:" in
        *":${directory}:"*) ;;
        *) export PATH="${directory}:${PATH}" ;;
    esac
    hash -r
}

# 复用上一次一键启动下载的项目本地工具，避免每次重复安装。
prepend_path "${LOCAL_TOOLS_DIR}/pnpm/bin"
prepend_path "${LOCAL_TOOLS_DIR}/pnpm"
prepend_path "${LOCAL_TOOLS_DIR}/node/bin"
prepend_path "${LOCAL_TOOLS_DIR}/maven/bin"

run_privileged() {
    if [[ "${EUID}" -eq 0 ]]; then
        "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        die "自动安装需要管理员权限，但当前系统没有 sudo。请使用管理员账号安装后重试。"
    fi
}

ensure_homebrew() {
    if command -v brew >/dev/null 2>&1; then
        return
    fi
    auto_install_enabled || die "缺少 Homebrew，且已禁用自动安装。"
    [[ -x /usr/bin/curl ]] || die "安装 Homebrew 需要系统 curl。"
    info "未检测到 Homebrew，正在安装（系统可能要求确认管理员权限）"
    /bin/bash -c "$(/usr/bin/curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    prepend_path "/opt/homebrew/bin"
    prepend_path "/usr/local/bin"
    require_command brew "Homebrew 自动安装未完成，请查看上方安装日志。"
}

linux_package_manager() {
    local manager
    for manager in apt-get dnf yum pacman zypper; do
        command -v "${manager}" >/dev/null 2>&1 && { printf '%s\n' "${manager}"; return; }
    done
    return 1
}

install_linux_packages() {
    local manager
    manager="$(linux_package_manager)" \
        || die "无法识别 Linux 包管理器，请先安装：$*"

    case "${manager}" in
        apt-get)
            if [[ "${APT_UPDATED}" == false ]]; then
                run_privileged apt-get update
                APT_UPDATED=true
            fi
            run_privileged env DEBIAN_FRONTEND=noninteractive apt-get install -y "$@"
            ;;
        dnf|yum)
            run_privileged "${manager}" install -y "$@"
            ;;
        pacman)
            run_privileged pacman -Sy --noconfirm --needed "$@"
            ;;
        zypper)
            run_privileged zypper --non-interactive install "$@"
            ;;
    esac
}

refresh_windows_path() {
    command -v powershell.exe >/dev/null 2>&1 || return 0
    command -v cygpath >/dev/null 2>&1 || return 0
    local windows_path converted_path
    windows_path="$(powershell.exe -NoProfile -NonInteractive -Command \
        '[Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User")' \
        2>/dev/null | tr -d '\r')"
    if [[ -n "${windows_path}" ]]; then
        converted_path="$(cygpath --path "${windows_path}")"
        export PATH="${converted_path}:${PATH}"
    fi
    hash -r
}

install_windows_package() {
    local package_id="$1"
    require_command winget.exe "请使用 Windows 10 1809+ / Windows 11，并在 Microsoft Store 安装 App Installer。"
    winget.exe install --exact --id "${package_id}" \
        --accept-source-agreements --accept-package-agreements --silent \
        || die "Windows 软件包 ${package_id} 自动安装失败，请查看上方 winget 日志。"
    refresh_windows_path
}

ensure_curl() {
    command -v curl >/dev/null 2>&1 && return
    auto_install_enabled || die "缺少 curl，且已禁用自动安装。"
    info "未检测到 curl，正在自动安装"
    case "$(uname -s 2>/dev/null || true)" in
        Darwin)
            ensure_homebrew
            brew install curl
            prepend_path "$(brew --prefix curl)/bin"
            ;;
        Linux)
            install_linux_packages curl ca-certificates
            ;;
        MINGW*|MSYS*|CYGWIN*)
            install_windows_package cURL.cURL
            ;;
        *)
            die "当前系统不支持自动安装 curl，请手工安装后重试。"
            ;;
    esac
    require_command curl "自动安装未完成，请查看上方日志。"
}

ensure_archive_tools() {
    command -v tar >/dev/null 2>&1 && return
    auto_install_enabled || die "缺少 tar，且已禁用自动安装。"
    info "未检测到 tar，正在自动安装"
    case "$(uname -s 2>/dev/null || true)" in
        Darwin)
            ensure_homebrew
            brew install gnu-tar
            prepend_path "$(brew --prefix gnu-tar)/libexec/gnubin"
            ;;
        Linux)
            install_linux_packages tar gzip
            ;;
        *)
            die "当前系统不支持自动安装 tar；Windows 请使用包含 tar 的 Git for Windows。"
            ;;
    esac
    require_command tar "自动安装未完成，请查看上方日志。"
}

download_archive() {
    local url="$1"
    local output="$2"
    local fallback_url="${3:-}"
    if curl --fail --location --retry 3 --connect-timeout 15 --output "${output}" "${url}"; then
        return
    fi
    [[ -n "${fallback_url}" ]] || die "下载失败：${url}"
    warn "主下载源不可用，切换备用源"
    curl --fail --location --retry 3 --connect-timeout 15 --output "${output}" "${fallback_url}" \
        || die "下载失败：${url}；备用源：${fallback_url}"
}

archive_platform() {
    case "$(uname -s 2>/dev/null || true)" in
        Darwin) printf 'mac' ;;
        Linux) printf 'linux' ;;
        *) return 1 ;;
    esac
}

archive_arch() {
    case "$(uname -m 2>/dev/null || true)" in
        x86_64|amd64) printf 'x64' ;;
        arm64|aarch64) printf 'aarch64' ;;
        *) return 1 ;;
    esac
}

install_local_jdk_21() {
    ensure_curl
    ensure_archive_tools
    local platform arch temp_dir java_home
    platform="$(archive_platform)" || die "当前系统不支持本地安装 JDK 21。"
    arch="$(archive_arch)" || die "当前 CPU 架构不支持本地安装 JDK 21。"
    temp_dir="$(mktemp -d "${RUN_DIR}/jdk-install.XXXXXX")"
    info "正在下载并安装项目本地 JDK 21"
    download_archive \
        "https://api.adoptium.net/v3/binary/latest/21/ga/${platform}/${arch}/jdk/hotspot/normal/eclipse" \
        "${temp_dir}/jdk.tar.gz"
    mkdir -p "${temp_dir}/extracted"
    tar -xzf "${temp_dir}/jdk.tar.gz" -C "${temp_dir}/extracted"
    java_home="$(find "${temp_dir}/extracted" -type f -path '*/bin/java' -print -quit | sed 's#/bin/java$##')"
    [[ -n "${java_home}" ]] || die "JDK 21 压缩包中未找到 Java 运行时。"
    rm -rf "${LOCAL_TOOLS_DIR}/jdk-21"
    mv "${java_home}" "${LOCAL_TOOLS_DIR}/jdk-21"
    rm -rf "${temp_dir}"
}

install_local_maven() {
    ensure_curl
    ensure_archive_tools
    local version="3.9.11"
    local temp_dir=""
    temp_dir="$(mktemp -d "${RUN_DIR}/maven-install.XXXXXX")"
    info "正在下载并安装项目本地 Maven ${version}"
    download_archive \
        "https://repo.huaweicloud.com/apache/maven/maven-3/${version}/binaries/apache-maven-${version}-bin.tar.gz" \
        "${temp_dir}/maven.tar.gz" \
        "https://archive.apache.org/dist/maven/maven-3/${version}/binaries/apache-maven-${version}-bin.tar.gz"
    tar -xzf "${temp_dir}/maven.tar.gz" -C "${temp_dir}"
    rm -rf "${LOCAL_TOOLS_DIR}/maven"
    mv "${temp_dir}/apache-maven-${version}" "${LOCAL_TOOLS_DIR}/maven"
    rm -rf "${temp_dir}"
    prepend_path "${LOCAL_TOOLS_DIR}/maven/bin"
}

install_local_node() {
    ensure_curl
    ensure_archive_tools
    local platform arch version temp_dir archive_root
    platform="$(archive_platform)" || die "当前系统不支持本地安装 Node.js。"
    arch="$(archive_arch)" || die "当前 CPU 架构不支持本地安装 Node.js。"
    [[ "${arch}" == aarch64 ]] && arch="arm64"
    [[ "${platform}" == mac ]] && platform="darwin"
    version="$(curl --fail --silent --show-error https://nodejs.org/dist/index.tab \
        | awk '$1 ~ /^v24\./ && !found { version=$1; found=1 } END { print version }')"
    [[ -n "${version}" ]] || die "无法解析 Node.js 24 的可用版本。"
    temp_dir="$(mktemp -d "${RUN_DIR}/node-install.XXXXXX")"
    archive_root="node-${version}-${platform}-${arch}"
    info "正在下载并安装项目本地 Node.js ${version}"
    download_archive \
        "https://nodejs.org/dist/${version}/${archive_root}.tar.gz" \
        "${temp_dir}/node.tar.gz"
    tar -xzf "${temp_dir}/node.tar.gz" -C "${temp_dir}"
    rm -rf "${LOCAL_TOOLS_DIR}/node"
    mv "${temp_dir}/${archive_root}" "${LOCAL_TOOLS_DIR}/node"
    rm -rf "${temp_dir}"
    prepend_path "${LOCAL_TOOLS_DIR}/node/bin"
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
    local version
    version="$(mvn -version 2>/dev/null | awk 'NR == 1 { print $3 }' || true)"
    if ! command -v mvn >/dev/null 2>&1 || ! version_at_least "${version}" 3 9; then
        auto_install_enabled \
            || die "需要 Maven 3.9 或更高版本，当前版本为 ${version:-未安装}，且已禁用自动安装。"
        install_local_maven
        version="$(mvn -version 2>/dev/null | awk 'NR == 1 { print $3 }' || true)"
    fi
    version_at_least "${version}" 3 9 \
        || die "Maven 自动安装后仍未达到 3.9，当前版本为 ${version:-未知}。"
}

require_pnpm_9() {
    local version
    version="$(pnpm --version 2>/dev/null || true)"
    if ! command -v pnpm >/dev/null 2>&1 || ! version_at_least "${version}" 9 0; then
        auto_install_enabled \
            || die "需要 pnpm 9 或更高版本，当前版本为 ${version:-未安装}，且已禁用自动安装。"
        require_command npm "自动安装 pnpm 需要 npm。"
        info "正在安装项目本地 pnpm 9"
        npm install --global --prefix "${LOCAL_TOOLS_DIR}/pnpm" pnpm@9
        prepend_path "${LOCAL_TOOLS_DIR}/pnpm/bin"
        prepend_path "${LOCAL_TOOLS_DIR}/pnpm"
        version="$(pnpm --version 2>/dev/null || true)"
    fi
    version_at_least "${version}" 9 0 \
        || die "pnpm 自动安装后仍未达到 9，当前版本为 ${version:-未知}。"
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

install_docker() {
    auto_install_enabled || { docker_install_help >&2; die "未安装 Docker，且已禁用自动安装。"; }
    info "未检测到 Docker，正在自动安装"
    case "$(uname -s 2>/dev/null || true)" in
        Darwin)
            ensure_homebrew
            brew install --cask docker
            prepend_path "/Applications/Docker.app/Contents/Resources/bin"
            ;;
        Linux)
            if [[ -n "${WSL_DISTRO_NAME:-}" ]]; then
                require_command powershell.exe "WSL2 自动安装 Docker Desktop 需要调用 Windows PowerShell。"
                powershell.exe -NoProfile -Command \
                    'winget install --exact --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements --silent' \
                    || die "Docker Desktop 自动安装失败，请查看上方 winget 日志。"
                start_windows_docker_desktop
                local i
                for ((i = 1; i <= 60; i++)); do
                    [[ -x /mnt/wsl/docker-desktop/cli-tools/usr/bin/docker ]] && break
                    sleep 2
                done
                [[ -x /mnt/wsl/docker-desktop/cli-tools/usr/bin/docker ]] \
                    || die "Docker Desktop 已安装，但尚未向当前 WSL 发行版开放。请在 Docker Desktop 中启用 WSL Integration 后重试。"
                prepend_path "/mnt/wsl/docker-desktop/cli-tools/usr/bin"
            else
                ensure_curl
                local temp_dir
                temp_dir="$(mktemp -d "${RUN_DIR}/docker-install.XXXXXX")"
                download_archive "https://get.docker.com" "${temp_dir}/get-docker.sh"
                run_privileged sh "${temp_dir}/get-docker.sh"
                rm -rf "${temp_dir}"
                if [[ "${EUID}" -ne 0 && -n "${USER:-}" ]] && getent group docker >/dev/null 2>&1; then
                    run_privileged usermod -aG docker "${USER}"
                    warn "已将 ${USER} 加入 docker 组；本次启动将暂用 sudo，重新登录后可直接使用 docker。"
                fi
            fi
            ;;
        MINGW*|MSYS*|CYGWIN*)
            install_windows_package Docker.DockerDesktop
            prepend_path "/c/Program Files/Docker/Docker/resources/bin"
            ;;
        *)
            docker_install_help >&2
            die "当前系统不支持自动安装 Docker。"
            ;;
    esac
    hash -r
}

install_docker_compose() {
    auto_install_enabled \
        || die "Docker Compose v2 不可用，且已禁用自动安装。"
    info "未检测到 Docker Compose v2，正在自动安装"
    case "$(uname -s 2>/dev/null || true)" in
        Darwin)
            ensure_homebrew
            brew install docker-compose
            local plugin_source plugin_target
            plugin_source="$(brew --prefix)/lib/docker/cli-plugins/docker-compose"
            plugin_target="${HOME}/.docker/cli-plugins/docker-compose"
            mkdir -p "$(dirname "${plugin_target}")"
            if [[ ! -e "${plugin_target}" && ! -L "${plugin_target}" ]]; then
                ln -s "${plugin_source}" "${plugin_target}"
            fi
            ;;
        Linux)
            case "$(linux_package_manager)" in
                apt-get|dnf|yum) install_linux_packages docker-compose-plugin ;;
                pacman|zypper) install_linux_packages docker-compose ;;
                *) die "无法识别 Linux 包管理器，不能自动安装 Docker Compose v2。" ;;
            esac
            ;;
        MINGW*|MSYS*|CYGWIN*)
            install_windows_package Docker.DockerDesktop
            ;;
        *)
            die "当前系统不支持自动安装 Docker Compose v2。"
            ;;
    esac
}

start_windows_docker_desktop() {
    require_command powershell.exe "请手工启动 Docker Desktop 后重试。"
    # shellcheck disable=SC2016  # 变量由 PowerShell 展开，不应由 Bash 展开。
    if ! powershell.exe -NoProfile -NonInteractive -Command \
        '$dockerDesktop = Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"; if (!(Test-Path $dockerDesktop)) { exit 1 }' \
        >/dev/null 2>&1; then
        auto_install_enabled \
            || die "未安装 Docker Desktop，且已禁用自动安装。"
        install_windows_package Docker.DockerDesktop
    fi
    # shellcheck disable=SC2016  # 变量由 PowerShell 展开，不应由 Bash 展开。
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
            if [[ ! -d /Applications/Docker.app ]]; then
                auto_install_enabled \
                    || die "未安装 Docker Desktop，且已禁用自动安装。"
                ensure_homebrew
                brew install --cask docker
                prepend_path "/Applications/Docker.app/Contents/Resources/bin"
            fi
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
                elif command -v sudo >/dev/null 2>&1 && sudo systemctl start docker >/dev/null 2>&1; then
                    :
                else
                    auto_install_enabled \
                        || die "Docker Engine 未完整安装或无法启动，且已禁用自动安装。"
                    warn "仅检测到 Docker CLI，正在补装 Docker Engine"
                    install_docker
                    run_privileged systemctl start docker
                fi
            elif command -v service >/dev/null 2>&1; then
                run_privileged service docker start
            elif command -v rc-service >/dev/null 2>&1; then
                run_privileged rc-service docker start
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

detect_docker_access() {
    if command docker info >/dev/null 2>&1; then
        DOCKER_USE_SUDO=false
        return 0
    fi
    if [[ "$(uname -s 2>/dev/null || true)" == Linux ]] \
        && command -v sudo >/dev/null 2>&1 \
        && sudo -n docker info >/dev/null 2>&1; then
        DOCKER_USE_SUDO=true
        return 0
    fi
    return 1
}

docker_cmd() {
    if [[ "${DOCKER_USE_SUDO}" == true ]]; then
        sudo docker "$@"
    else
        command docker "$@"
    fi
}

ensure_docker_ready() {
    if ! command -v docker >/dev/null 2>&1; then
        install_docker
        command -v docker >/dev/null 2>&1 \
            || die "Docker 已执行安装，但当前终端尚未发现 docker 命令。请重新打开终端后重试。"
    fi

    if ! detect_docker_access; then
        info "Docker 已安装但服务未运行，正在尝试自动启动"
        start_docker_engine
        local i
        for ((i = 1; i <= 60; i++)); do
            detect_docker_access && break
            [[ "${i}" -eq 60 ]] \
                && die "Docker 120 秒内未就绪，请检查 Docker Desktop/Engine 状态后重试。"
            sleep 2
        done
    fi

    if ! docker_cmd compose version >/dev/null 2>&1; then
        install_docker_compose
        docker_cmd compose version >/dev/null 2>&1 \
            || die "Docker Compose v2 自动安装后仍不可用：https://docs.docker.com/compose/install/"
    fi
}

java_major_version() {
    "$1" -version 2>&1 | awk -F '"' '/version/ { split($2, version, "."); print version[1]; exit }'
}

configure_java_21() {
    local candidate=""
    local java_bin=""
    local resolved_java_home=""
    local candidates=(
        "${JAVA_HOME:-}"
        "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
        "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
        "/usr/lib/jvm/java-21-openjdk"
        "/usr/lib/jvm/java-21-openjdk-amd64"
        "/usr/lib/jvm/java-21-openjdk-arm64"
        "${LOCAL_TOOLS_DIR}/jdk-21"
    )

    if command -v java >/dev/null 2>&1 && [[ "$(java_major_version "$(command -v java)")" == "21" ]]; then
        java_bin="$(command -v java)"
        resolved_java_home="$("${java_bin}" -XshowSettings:properties -version 2>&1 | awk -F '= ' '/^[[:space:]]*java.home =/ { print $2; exit }')"
        export JAVA_HOME="${resolved_java_home}"
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

    auto_install_enabled || die "需要 JDK 21，当前未安装或版本不符，且已禁用自动安装。"
    case "$(uname -s 2>/dev/null || true)" in
        MINGW*|MSYS*|CYGWIN*)
            install_windows_package Microsoft.OpenJDK.21
            ;;
        *)
            install_local_jdk_21
            ;;
    esac
    [[ -x "${LOCAL_TOOLS_DIR}/jdk-21/bin/java" ]] || {
        java_bin="$(command -v java 2>/dev/null || true)"
        [[ -n "${java_bin}" && "$(java_major_version "${java_bin}")" == 21 ]] \
            || die "JDK 21 自动安装后仍无法使用，请查看上方日志。"
        resolved_java_home="$("${java_bin}" -XshowSettings:properties -version 2>&1 | awk -F '= ' '/^[[:space:]]*java.home =/ { print $2; exit }')"
        export JAVA_HOME="${resolved_java_home}"
        export PATH="${JAVA_HOME}/bin:${PATH}"
        return
    }
    export JAVA_HOME="${LOCAL_TOOLS_DIR}/jdk-21"
    prepend_path "${JAVA_HOME}/bin"
}

require_node_20() {
    local major
    major="$(node --version 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/' || true)"
    if ! command -v node >/dev/null 2>&1 || [[ ! "${major}" =~ ^[0-9]+$ ]] || [[ "${major}" -lt 20 ]]; then
        auto_install_enabled \
            || die "需要 Node.js 20 或更高版本，当前版本为 $(node --version 2>/dev/null || printf '未安装')，且已禁用自动安装。"
        case "$(uname -s 2>/dev/null || true)" in
            MINGW*|MSYS*|CYGWIN*) install_windows_package OpenJS.NodeJS.LTS ;;
            *) install_local_node ;;
        esac
        major="$(node --version 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/' || true)"
    fi
    [[ "${major}" =~ ^[0-9]+$ && "${major}" -ge 20 ]] \
        || die "Node.js 自动安装后仍未达到 20，当前版本为 $(node --version 2>/dev/null || printf '未知')。"
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
