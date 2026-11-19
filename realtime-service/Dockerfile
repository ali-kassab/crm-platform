# syntax=docker/dockerfile:1
# Realtime hint relay (R11, ADR-0015). Build context: ./realtime-service
#   docker build -t crm-realtime ./realtime-service

FROM node:24-alpine AS build
WORKDIR /src
RUN npm install -g pnpm@12.21.0
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY tsconfig.json ./
COPY src src
COPY test test
RUN pnpm build && pnpm prune --prod

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=3001
COPY --from=build /src/package.json ./
COPY --from=build /src/node_modules node_modules
COPY --from=build /src/dist/src dist/src
USER node
EXPOSE 3001
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1:3001/health | grep -q UP || exit 1
CMD ["node", "dist/src/main.js"]
