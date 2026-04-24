#!/usr/bin/env bash
# start-local.sh — 一键本地启动（自动检测并安装缺失依赖）
# 支持 macOS (Homebrew) 和 Ubuntu/Debian (apt)
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"

# ── 颜色 ──────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log()  { echo -e "${BLUE}[INFO]${RESET}  $*"; }
ok()   { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn() { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
err()  { echo -e "${RED}[ERROR]${RESET} $*"; exit 1; }
step() { echo -e "\n${BOLD}${CYAN}══ $* ══${RESET}"; }

# ── OS 检测 ───────────────────────────────────────────────────────────────────
detect_os() {
    if [[ "$OSTYPE" == "darwin"* ]]; then echo "macos"
    elif [[ -f /etc/debian_version ]]; then echo "debian"
    else echo "unknown"
    fi
}
OS=$(detect_os)
log "操作系统: $OS"

# ── 端口冲突检测与释放 ─────────────────────────────────────────────────────────
free_port() {
    local port=$1 desc=$2
    local pids; pids=$(lsof -ti:"$port" 2>/dev/null || true)
    if [[ -n "$pids" ]]; then
        warn "端口 $port ($desc) 被占用（PID: $pids），正在释放..."
        echo "$pids" | xargs kill -9 2>/dev/null || true
        sleep 1
        ok "端口 $port 已释放"
    fi
}

# ── 工具安装函数 ───────────────────────────────────────────────────────────────
ensure_brew() {
    if ! command -v brew &>/dev/null; then
        warn "未检测到 Homebrew，正在安装..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        ok "Homebrew 安装完成"
    fi
}

install_java() {
    step "检查 Java 17+"
    if command -v java &>/dev/null; then
        local ver; ver=$(java -version 2>&1 | grep -oE '"[0-9]+' | head -1 | tr -d '"')
        if [[ "$ver" -ge 17 ]]; then
            ok "Java 已满足要求: $(java -version 2>&1 | head -1)"
            return
        fi
    fi
    warn "未检测到 Java 17+，正在安装..."
    case $OS in
        macos)
            ensure_brew
            brew install --quiet openjdk@17
            export PATH="/opt/homebrew/opt/openjdk@17/bin:/usr/local/opt/openjdk@17/bin:$PATH"
            ;;
        debian)
            sudo apt-get update -qq && sudo apt-get install -y -qq openjdk-17-jdk
            ;;
        *) err "不支持的系统，请手动安装 Java 17+" ;;
    esac
    ok "Java 安装完成: $(java -version 2>&1 | head -1)"
}

install_maven() {
    step "检查 Maven 3.9+"
    if command -v mvn &>/dev/null; then
        local major minor
        major=$(mvn -v 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 | cut -d. -f1)
        minor=$(mvn -v 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 | cut -d. -f2)
        if [[ "$major" -gt 3 ]] || [[ "$major" -eq 3 && "$minor" -ge 9 ]]; then
            ok "Maven 已满足要求: $(mvn -v | head -1)"
            return
        fi
    fi
    warn "未检测到 Maven 3.9+，正在安装..."
    case $OS in
        macos) ensure_brew; brew install --quiet maven ;;
        debian) sudo apt-get install -y -qq maven ;;
        *) err "不支持的系统，请手动安装 Maven" ;;
    esac
    ok "Maven 安装完成: $(mvn -v | head -1)"
}

install_node() {
    step "检查 Node.js 20+"
    if command -v node &>/dev/null; then
        local ver; ver=$(node -v | sed 's/v//' | cut -d. -f1)
        if [[ "$ver" -ge 20 ]]; then
            ok "Node.js 已满足要求: $(node -v)"
            return
        fi
    fi
    warn "未检测到 Node.js 20+，正在安装..."
    case $OS in
        macos)
            ensure_brew
            brew install --quiet node@20
            export PATH="/opt/homebrew/opt/node@20/bin:/usr/local/opt/node@20/bin:$PATH"
            ;;
        debian)
            curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash - &>/dev/null
            sudo apt-get install -y -qq nodejs
            ;;
        *) err "不支持的系统，请手动安装 Node.js 20+" ;;
    esac
    ok "Node.js 安装完成: $(node -v)"
}

setup_postgres() {
    step "检查 PostgreSQL"

    if ! command -v psql &>/dev/null; then
        warn "未检测到 PostgreSQL，正在安装..."
        case $OS in
            macos)
                ensure_brew
                brew install --quiet postgresql@15
                export PATH="/opt/homebrew/opt/postgresql@15/bin:/usr/local/opt/postgresql@15/bin:$PATH"
                ;;
            debian)
                sudo apt-get install -y -qq postgresql-15
                ;;
            *) err "不支持的系统，请手动安装 PostgreSQL 15" ;;
        esac
        ok "PostgreSQL 安装完成"
    else
        ok "PostgreSQL 已安装: $(psql --version)"
    fi

    # 启动服务
    log "检查 PostgreSQL 服务状态..."
    if ! pg_isready -q 2>/dev/null; then
        warn "PostgreSQL 服务未运行，正在启动..."
        case $OS in
            macos) brew services start postgresql@15 2>/dev/null || true ;;
            debian) sudo systemctl start postgresql 2>/dev/null || sudo service postgresql start || true ;;
        esac
        sleep 2
    fi

    # 等待就绪（最多 15 秒）
    local retries=15
    while ! pg_isready -q 2>/dev/null && [[ $retries -gt 0 ]]; do
        sleep 1; ((retries--))
    done
    [[ $retries -eq 0 ]] && err "PostgreSQL 启动超时"
    ok "PostgreSQL 已就绪"

    # 读取 application.yml 中的数据库配置
    local yml="$ROOT/main/backend/src/main/resources/application.yml"
    local db_name="asset_db"
    local db_user="asset"
    local db_pass="asset123"
    if [[ -f "$yml" ]]; then
        db_name=$(grep -E '^\s+url:' "$yml" | head -1 | grep -oE '/[^/?]+$' | tr -d '/')
        db_user=$(grep -E '^\s+username:' "$yml" | head -1 | awk '{print $2}')
        db_pass=$(grep -E '^\s+password:' "$yml" | head -1 | awk '{print $2}')
        db_name=${db_name:-asset_db}
        db_user=${db_user:-asset}
        db_pass=${db_pass:-asset123}
    fi

    # macOS Homebrew PG 的管理员是当前 OS 用户
    local pg_admin; pg_admin=$(whoami)

    log "配置数据库用户 '$db_user' 和数据库 '$db_name'..."

    # 创建用户（忽略已存在的错误）
    psql -U "$pg_admin" postgres -c \
        "DO \$\$ BEGIN
           CREATE ROLE $db_user LOGIN PASSWORD '$db_pass';
         EXCEPTION WHEN duplicate_object THEN NULL;
         END \$\$;" 2>/dev/null || true

    # 创建数据库（忽略已存在的错误）
    psql -U "$pg_admin" postgres -c \
        "SELECT 1 FROM pg_database WHERE datname='$db_name'" 2>/dev/null \
        | grep -q 1 || \
        psql -U "$pg_admin" postgres -c \
        "CREATE DATABASE $db_name OWNER $db_user;" 2>/dev/null || true

    # 授权
    psql -U "$pg_admin" postgres -c \
        "GRANT ALL PRIVILEGES ON DATABASE $db_name TO $db_user;" 2>/dev/null || true

    ok "数据库 '$db_name' 就绪（Flyway 将在后端启动时自动建表迁移）"
}

# ── 主流程 ────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}╔══════════════════════════════════════════╗"
echo -e "║     视频素材查询服务 — 本地一键启动      ║"
echo -e "╚══════════════════════════════════════════╝${RESET}"

install_java
install_maven
install_node
setup_postgres

# ── 释放端口冲突（保证 100% 启动成功） ────────────────────────────────────────
step "检查端口占用"
free_port 8080 "后端"
free_port 5173 "前端"

# ── 启动后端（实时日志打印到终端） ────────────────────────────────────────────
step "启动后端 (Spring Boot)"
log "后端日志实时输出如下，等待 /actuator/health 就绪..."
(
    cd "$ROOT/main/backend"
    mvn spring-boot:run -Dspring-boot.run.arguments="--ingest=all" 2>&1 | while IFS= read -r line; do
        echo -e "${CYAN}[BACKEND]${RESET} $line"
    done
) &
BACKEND_PID=$!

# 等待后端就绪（最多 120 秒）
waited=0
until curl -sf http://localhost:8080/actuator/health &>/dev/null; do
    sleep 2; ((waited+=2))
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        err "后端进程意外退出，请检查上方日志"
    fi
    if [[ $waited -ge 120 ]]; then
        err "后端启动超时（120s），请检查上方日志"
    fi
done
ok "后端已就绪 → http://localhost:8080"

# ── 启动前端（实时日志打印到终端） ────────────────────────────────────────────
step "启动前端 (Vite + Vue 3)"
(
    cd "$ROOT/main/frontend"
    npm install 2>&1 | while IFS= read -r line; do
        echo -e "${YELLOW}[NPM]${RESET}     $line"
    done
    npm run dev 2>&1 | while IFS= read -r line; do
        echo -e "${GREEN}[FRONTEND]${RESET} $line"
    done
) &
FRONTEND_PID=$!

# 等待前端就绪（最多 30 秒）
waited=0
until curl -sf http://localhost:5173 &>/dev/null; do
    sleep 1; ((waited++))
    [[ $waited -ge 30 ]] && break
done

# ── 就绪摘要 ──────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${GREEN}╔══════════════════════════════════════════╗"
echo -e "║              所有服务已启动！            ║"
echo -e "╠══════════════════════════════════════════╣"
echo -e "║  前端管理后台  http://localhost:5173      ║"
echo -e "║  Swagger UI   http://localhost:8080/     ║"
echo -e "║               swagger-ui.html            ║"
echo -e "║  健康检查     http://localhost:8080/     ║"
echo -e "║               actuator/health            ║"
echo -e "╠══════════════════════════════════════════╣"
echo -e "║  按 Ctrl+C 停止所有服务                  ║"
echo -e "╚══════════════════════════════════════════╝${RESET}\n"

trap 'echo -e "\n${YELLOW}正在停止所有服务...${RESET}"; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; ok "已停止"; exit 0' INT TERM
wait
