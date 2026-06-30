# EBZ MDM-Cockpit (Formularverwaltung P1.0). Multi-Stage: Vite-Build mit Node+pnpm → nginx.
# Der Build nutzt die EINGECHECKTE generierte Schicht (src/gen); `pnpm gen` regeneriert sie
# lokal aus dem laufenden /q/openapi (zur Build-Zeit im Container ist der Service nicht erreichbar).
#
# Build-Context = Repo-Root (NICHT ./apps/mdm), damit die geteilten Pakete (Vite-Source-Aliase
# @crm-ui → ../../packages/crm-ui/src etc.) verfügbar sind. Das In-Container-Layout SPIEGELT die
# Repo-Struktur (/apps/mdm + /packages/…), sodass DERSELBE relative Alias in Dev UND Docker stimmt.
# Root-.dockerignore hält den Context schlank. Siehe docker-compose.yml / infra/compose (build.context).
FROM node:22-alpine AS build
RUN corepack enable
# Geteilte Pakete unter /packages spiegeln → Alias ../../packages/crm-ui/src zeigt auf /packages/crm-ui/src.
COPY packages/crm-ui /packages/crm-ui
COPY packages/ui-base /packages/ui-base
WORKDIR /apps/mdm
COPY apps/mdm/package.json apps/mdm/pnpm-lock.yaml apps/mdm/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY apps/mdm/ ./
RUN pnpm build

FROM nginx:1.27-alpine
COPY apps/mdm/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /apps/mdm/dist /usr/share/nginx/html
EXPOSE 80
