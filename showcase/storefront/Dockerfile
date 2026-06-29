# EBZ Akademie — Nuxt-SSR-Storefront (Produktkatalog P2). Node-Server-Output.
#
# Build-Context = showcase/ (NICHT ./storefront), damit das geteilte crm-ui (Alias @crm-ui →
# ../crm-ui/src) als Geschwister im Build verfügbar ist. showcase/.dockerignore hält den Context schlank.
# Siehe docker-compose.yml (build.context).
FROM node:22-bookworm-slim AS build
WORKDIR /app
RUN corepack enable
# Geteilte CRM-Primitive als Geschwister von /app ablegen → Alias ../crm-ui/src zeigt auf /crm-ui/src.
COPY crm-ui /crm-ui
# Gebrandete Kunden-Primitive → Alias ../customer-ui/src zeigt auf /customer-ui/src.
COPY customer-ui /customer-ui
# Neutrale UI-Infrastruktur → Alias ../ui-base/src zeigt auf /ui-base/src.
COPY ui-base /ui-base
COPY storefront/package.json storefront/pnpm-workspace.yaml ./
RUN pnpm install
COPY storefront/ ./
RUN pnpm build

FROM node:22-bookworm-slim AS run
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOST=0.0.0.0
COPY --from=build /app/.output ./.output
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
