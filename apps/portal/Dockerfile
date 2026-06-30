# EBZ Außenportal — Vue-3-SPA. Multi-Stage: Vite-Build mit Node+pnpm → nginx.
# Nutzt die EINGECHECKTE generierte Schicht (src/gen); `pnpm gen` regeneriert sie lokal aus dem
# laufenden /q/openapi (zur Build-Zeit im Container ist der Service nicht erreichbar).
#
# Build-Context = Repo-Root (NICHT ./apps/portal), damit die geteilten Pakete (Aliase
# @crm-ui/@customer-ui/@ui-base → ../../packages/…/src) verfügbar sind. Das In-Container-Layout
# SPIEGELT die Repo-Struktur (/apps/portal + /packages/…), sodass DERSELBE relative Alias in
# Dev UND Docker stimmt. Root-.dockerignore hält den Context schlank.
FROM node:22-alpine AS build
RUN corepack enable
# Geteilte Pakete unter /packages spiegeln → Alias ../../packages/crm-ui/src zeigt auf /packages/crm-ui/src.
COPY packages/crm-ui /packages/crm-ui
COPY packages/customer-ui /packages/customer-ui
COPY packages/ui-base /packages/ui-base
WORKDIR /apps/portal
COPY apps/portal/package.json apps/portal/pnpm-lock.yaml apps/portal/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY apps/portal/ ./
RUN pnpm build

FROM nginx:1.27-alpine
COPY apps/portal/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /apps/portal/dist /usr/share/nginx/html
EXPOSE 80
