# EBZ Außenportal — Vue-3-SPA. Multi-Stage: Vite-Build mit Node+pnpm → nginx.
# Nutzt die EINGECHECKTE generierte Schicht (src/gen); `pnpm gen` regeneriert sie lokal aus dem
# laufenden /q/openapi (zur Build-Zeit im Container ist der Service nicht erreichbar).
FROM node:22-alpine AS build
WORKDIR /build
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
