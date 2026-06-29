# EBZ MDM-Cockpit (Formularverwaltung P1.0). Multi-Stage: Vite-Build mit Node+pnpm → nginx.
# Der Build nutzt die EINGECHECKTE generierte Schicht (src/gen); `pnpm gen` regeneriert sie
# lokal aus dem laufenden /q/openapi (zur Build-Zeit im Container ist der Service nicht erreichbar).
#
# Build-Context = showcase/ (NICHT ./mdm), damit das geteilte crm-ui (Vite-Source-Alias
# @crm-ui → ../crm-ui/src) als Geschwister im Build verfügbar ist. showcase/.dockerignore hält
# den Context schlank (node_modules/dist/… ausgeschlossen). Siehe docker-compose.yml (build.context).
FROM node:22-alpine AS build
RUN corepack enable
# Geteilte CRM-Primitive als Geschwister von /build ablegen → Alias ../crm-ui/src zeigt auf /crm-ui/src.
COPY crm-ui /crm-ui
# Neutrale UI-Infrastruktur → Alias ../ui-base/src zeigt auf /ui-base/src.
COPY ui-base /ui-base
WORKDIR /build
COPY mdm/package.json mdm/pnpm-lock.yaml mdm/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY mdm/ ./
RUN pnpm build

FROM nginx:1.27-alpine
COPY mdm/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
