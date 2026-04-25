#!/usr/bin/env bash
# start-docker.sh — Docker 一键启动（自动处理端口冲突 + 镜像加速）
# 要求：Docker + Docker Compose 已安装并运行
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

echo -e "\n${BOLD}${CYAN}╔══════════════════════════════════════════╗"
echo -e "║    视频素材查询服务 — Docker 一键启动    ║"
echo -e "╚══════════════════════════════════════════╝${RESET}"

# ── 检查 Docker 是否运行 ──────────────────────────────────────────────────────
step "检查 Docker"
if ! docker info &>/dev/null; then
    err "Docker 未运行，请先启动 Docker Desktop 后重试"
fi
ok "Docker 正在运行: $(docker version --format '{{.Server.Version}}' 2>/dev/null)"

# ── 释放端口冲突 ──────────────────────────────────────────────────────────────
step "检查端口占用"
free_port() {
    local port=$1 desc=$2
    local pids; pids=$(lsof -ti:"$port" 2>/dev/null || true)
    if [[ -n "$pids" ]]; then
        warn "端口 $port ($desc) 被占用（PID: $pids），正在释放..."
        echo "$pids" | xargs kill -9 2>/dev/null || true
        sleep 1
        ok "端口 $port 已释放"
    else
        ok "端口 $port ($desc) 空闲"
    fi
}
free_port 80   "前端 Nginx"
free_port 8080 "后端 Spring Boot"
free_port 5432 "PostgreSQL"

# 端口释放后重新验证 Docker 仍在运行（kill 进程可能触发 Docker Desktop 短暂重启）
log "验证 Docker daemon 仍可用..."
retry=0
until docker info &>/dev/null; do
    ((retry++))
    if [[ $retry -ge 15 ]]; then
        err "Docker daemon 不可用，请打开 Docker Desktop 等待其完全启动后，再运行 bash start-docker.sh"
    fi
    warn "  Docker daemon 暂时不可用，等待中（${retry}/15）..."
    sleep 2
done
ok "Docker daemon 可用"

# ── 配置 Docker 镜像加速（支持 macOS + Linux） ───────────────────────────────
step "配置 Docker 镜像加速"
OS="$(uname -s)"

# 国内镜像源列表
MIRRORS='["https://docker.m.daocloud.io","https://mirror.ccs.tencentyun.com","https://mirror.baidubce.com"]'

if [[ "$OS" == "Darwin" ]]; then
    # macOS: Docker Desktop 配置路径
    DOCKER_CONF="$HOME/.docker/daemon.json"
    if [[ -f "$DOCKER_CONF" ]] && grep -q "daocloud\|tencent\|baidu\|aliyun" "$DOCKER_CONF" 2>/dev/null; then
        ok "镜像加速器已配置，跳过"
    else
        warn "正在配置镜像加速器..."
        mkdir -p "$HOME/.docker"
        if [[ -f "$DOCKER_CONF" ]] && [[ -s "$DOCKER_CONF" ]]; then
            python3 -c "
import json
with open('$DOCKER_CONF') as f: cfg = json.load(f)
cfg.setdefault('registry-mirrors', [])
for m in $MIRRORS:
    if m not in cfg['registry-mirrors']: cfg['registry-mirrors'].append(m)
with open('$DOCKER_CONF', 'w') as f: json.dump(cfg, f, indent=2)
" 2>/dev/null || true
        else
            echo "{\"registry-mirrors\": $MIRRORS}" > "$DOCKER_CONF"
        fi
        ok "镜像加速器已写入 $DOCKER_CONF"
        warn "提示：若拉取镜像仍超时，请在 Docker Desktop → Settings → Apply & Restart 后重试"
    fi

elif [[ "$OS" == "Linux" ]]; then
    # Linux: Docker daemon 配置路径
    DOCKER_CONF="/etc/docker/daemon.json"
    if [[ -f "$DOCKER_CONF" ]] && grep -q "daocloud\|tencent\|baidu\|aliyun" "$DOCKER_CONF" 2>/dev/null; then
        ok "镜像加速器已配置，跳过"
    else
        warn "正在配置镜像加速器（需要 sudo 权限）..."
        sudo mkdir -p /etc/docker
        if [[ -f "$DOCKER_CONF" ]] && [[ -s "$DOCKER_CONF" ]]; then
            echo "{\"registry-mirrors\": $MIRRORS}" | sudo tee "$DOCKER_CONF" > /dev/null
        else
            echo "{\"registry-mirrors\": $MIRRORS}" | sudo tee "$DOCKER_CONF" > /dev/null
        fi
        ok "镜像加速器已写入 $DOCKER_CONF"
        warn "正在重启 Docker 服务..."
        sudo systemctl restart docker 2>/dev/null || sudo service docker restart 2>/dev/null || {
            warn "无法自动重启 Docker，请手动执行: sudo systemctl restart docker"
        }
        ok "Docker 服务已重启"
    fi
else
    warn "未知系统: $OS，跳过镜像加速配置"
fi

# ── 停止并清理旧容器（避免容器名冲突） ───────────────────────────────────────
step "清理旧容器"
if docker compose -f "$ROOT/docker-compose.yml" ps -q 2>/dev/null | grep -q .; then
    log "检测到旧容器，正在停止..."
    docker compose -f "$ROOT/docker-compose.yml" down 2>/dev/null || true
    ok "旧容器已清理"
else
    ok "无旧容器，跳过清理"
fi

# ── 启动所有服务（带网络失败自动回退） ───────────────────────────────────────
step "启动所有服务（docker compose up --build）"
log "实时日志如下，首次启动需拉取镜像约 3-5 分钟..."
echo ""

set +e
docker compose -f "$ROOT/docker-compose.yml" up --build
COMPOSE_EXIT=$?
set -e

if [[ $COMPOSE_EXIT -ne 0 ]]; then
    echo ""
    warn "启动异常（退出码 $COMPOSE_EXIT），尝试自动修复..."
    # 清理残留容器
    docker compose -f "$ROOT/docker-compose.yml" down 2>/dev/null || true

    if docker image inspect homework-backend:latest &>/dev/null 2>&1 && \
       docker image inspect homework-frontend:latest &>/dev/null 2>&1; then
        warn "检测到本地缓存镜像（可能因网络/代理无法拉取最新），直接使用缓存启动..."
        docker compose -f "$ROOT/docker-compose.yml" up
    else
        warn "本地无完整缓存，尝试跳过远端拉取、用本地基础镜像构建（--pull=false）..."
        if docker compose -f "$ROOT/docker-compose.yml" build --pull=false; then
            docker compose -f "$ROOT/docker-compose.yml" up
        else
            err "自动修复失败。常见原因：\n  ① 代理未开启（当前检测到 7890 端口不可达）\n  ② 网络不可达\n  ③ 磁盘空间不足\n请解决后重试: bash start-docker.sh"
        fi
    fi
fi

# ── 正常退出（Ctrl+C）到这里 ──────────────────────────────────────────────────
echo -e "\n${YELLOW}服务已停止。${RESET}"
echo -e "再次启动: ${CYAN}bash start-docker.sh${RESET}"
