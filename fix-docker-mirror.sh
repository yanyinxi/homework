#!/bin/bash
set -e
echo "=== Docker 镜像源修复 ==="
OS="$(uname -s)"
echo "检测系统: $OS"

if ! docker info &>/dev/null; then
    echo "错误: Docker 未运行，请先启动 Docker Desktop"
    exit 1
fi

echo "[配置] 创建 Docker 镜像加速器..."

if [ "$OS" = "Darwin" ]; then
    DOCKER_DIR="$HOME/.docker"
    DOCKER_CONF="$DOCKER_DIR/daemon.json"
    mkdir -p "$DOCKER_DIR"
    
    if [ -f "$DOCKER_CONF" ]; then
        if grep -q "daocloud\|tencent\|baidu" "$DOCKER_CONF" 2>/dev/null; then
            echo "      已配置镜像加速器，跳过"
        else
            EXISTING=$(cat "$DOCKER_CONF")
            cat > "$DOCKER_CONF" << EOF
$EXISTING
{"registry-mirrors":["https://docker.m.daocloud.io","https://mirror.ccs.tencentyun.com","https://mirror.baidubce.com"]}
EOF
        fi
    else
        cat > "$DOCKER_CONF" << 'EOF'
{"registry-mirrors":["https://docker.m.daocloud.io","https://mirror.ccs.tencentyun.com","https://mirror.baidubce.com"]}
EOF
        echo "      已创建 $DOCKER_CONF"
    fi
    
    echo ""
    echo "=========================================="
    echo "  macOS: Docker Desktop → Settings → Apply & Restart"
    echo "=========================================="
    
elif [ "$OS" = "Linux" ]; then
    DOCKER_CONF="/etc/docker/daemon.json"
    sudo mkdir -p /etc/docker
    
    if [ -f "$DOCKER_CONF" ]; then
        if grep -q "daocloud\|tencent\|baidu" "$DOCKER_CONF" 2>/dev/null; then
            echo "      已配置镜像加速器，跳过"
        else
            sudo tee "$DOCKER_CONF" > /dev/null << 'EOF'
{"registry-mirrors":["https://docker.m.daocloud.io","https://mirror.ccs.tencentyun.com","https://mirror.baidubce.com"]}
EOF
        fi
    else
        sudo tee "$DOCKER_CONF" > /dev/null << 'EOF'
{"registry-mirrors":["https://docker.m.daocloud.io","https://mirror.ccs.tencentyun.com","https://mirror.baidubce.com"]}
EOF
        echo "      已创建 $DOCKER_CONF"
    fi
    
    sudo systemctl restart docker 2>/dev/null || sudo service docker restart 2>/dev/null || echo "      请手动重启 Docker"
fi

echo ""
echo "=== 修复完成 ==="
echo "重启 Docker 后执行: docker compose up --build"