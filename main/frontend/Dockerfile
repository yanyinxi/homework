# ============================================================
# 多阶段构建 — 阶段 1：Node.js 构建 Vue 应用
# ============================================================
FROM node:20-alpine AS builder

WORKDIR /app

# 先复制 package.json，利用 Docker 层缓存加速依赖安装
COPY package.json ./

# 安装依赖（使用 npm ci 保证可复现性；生产 build 不需要 devDeps 外的东西）
RUN npm install

# 复制源码并执行生产构建
COPY . .
RUN npm run build

# ============================================================
# 多阶段构建 — 阶段 2：nginx:alpine 提供静态文件服务
# ============================================================
FROM nginx:alpine AS runner

# 删除默认 nginx 配置
RUN rm /etc/nginx/conf.d/default.conf

# 复制自定义 nginx 配置（含 /api 反代到 backend:8080）
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 从 builder 阶段复制 Vite 构建输出
COPY --from=builder /app/dist /usr/share/nginx/html

# 暴露 80 端口
EXPOSE 80

# nginx 前台运行
CMD ["nginx", "-g", "daemon off;"]
