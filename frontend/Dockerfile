# syntax=docker/dockerfile:1
# Web client: static build served by unprivileged nginx, which also proxies /api (Java) and
# /realtime (NestJS websocket) so the browser talks to one origin. Build context: ./frontend
#   docker build -t crm-frontend ./frontend

FROM node:24-alpine AS build
WORKDIR /src
RUN npm install -g pnpm@12.21.0
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginxinc/nginx-unprivileged:1.29-alpine
# Upstreams are filled in at start from the environment (nginx image's envsubst on templates)
ENV API_UPSTREAM=http://backend:8080 \
    REALTIME_UPSTREAM=http://realtime:3001
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
