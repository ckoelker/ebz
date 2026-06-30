# EBZ Akademie — Nuxt-SSR-Storefront (Produktkatalog P2). Node-Server-Output.
#
# Build-Context = Repo-Root (NICHT ./apps/storefront), damit die geteilten Pakete (Aliase
# @crm-ui/@customer-ui/@ui-base → ../../packages/…/src) verfügbar sind. Das In-Container-Layout
# SPIEGELT die Repo-Struktur (/apps/storefront + /packages/…), sodass DERSELBE relative Alias in
# Dev UND Docker stimmt. Root-.dockerignore hält den Context schlank.
FROM node:22-bookworm-slim AS build
RUN corepack enable
# Geteilte Pakete unter /packages spiegeln → Alias ../../packages/crm-ui/src zeigt auf /packages/crm-ui/src.
COPY packages/crm-ui /packages/crm-ui
COPY packages/customer-ui /packages/customer-ui
COPY packages/ui-base /packages/ui-base
WORKDIR /apps/storefront
COPY apps/storefront/package.json apps/storefront/pnpm-workspace.yaml ./
RUN pnpm install
COPY apps/storefront/ ./
RUN pnpm build

FROM node:22-bookworm-slim AS run
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOST=0.0.0.0
COPY --from=build /apps/storefront/.output ./.output
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
