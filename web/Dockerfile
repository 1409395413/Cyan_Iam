# =============================================================================
#  前端：Node 构建 → Nginx 托管静态产物
#  构建发生在镜像内，所以"克隆仓库 → docker compose up --build"即可，
#  不需要先在宿主机 npm run build。
# =============================================================================

# ---------- stage 1: build ----------
FROM node:22-alpine AS build
WORKDIR /app

# 只先拷依赖清单，充分利用 Docker 层缓存
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- stage 2: serve ----------
FROM nginx:1.27-alpine

# 静态产物来自 build 阶段，而不是宿主机（否则必须先本地构建才能打包）
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 上传素材的只读挂载点（docker-compose 把 upload-data 卷挂到这里）
RUN mkdir -p /var/www/media \
 && touch /var/run/nginx.pid \
 && chown -R nginx:nginx /var/run/nginx.pid /usr/share/nginx/html /var/cache/nginx /var/www/media

USER nginx
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
