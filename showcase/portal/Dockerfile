# EBZ Außenportal — Vue-3-SPA. Multi-Stage: Vite-Build mit Node+pnpm → nginx.
# Nutzt die EINGECHECKTE generierte Schicht (src/gen); `pnpm gen` regeneriert sie lokal aus dem
# laufenden /q/openapi (zur Build-Zeit im Container ist der Service nicht erreichbar).
#
# Build-Context = showcase/ (NICHT ./portal), damit das geteilte crm-ui (Vite-Source-Alias
# @crm-ui → ../crm-ui/src) als Geschwister im Build verfügbar ist. showcase/.dockerignore hält
# den Context schlank. Siehe docker-compose.yml (build.context).
FROM node:22-alpine AS build
RUN corepack enable
# Geteilte CRM-Primitive als Geschwister von /build ablegen → Alias ../crm-ui/src zeigt auf /crm-ui/src.
COPY crm-ui /crm-ui
WORKDIR /build
COPY portal/package.json portal/pnpm-lock.yaml portal/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY portal/ ./
RUN pnpm build

FROM nginx:1.27-alpine
COPY portal/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
