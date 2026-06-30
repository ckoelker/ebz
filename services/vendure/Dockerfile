# Vendure showcase backend — one image, used by both the `server` and `worker`
# services (the worker just overrides the start command). Paketmanager: pnpm.
FROM node:22-bookworm-slim

WORKDIR /usr/src/app

# pnpm via Corepack aktivieren (Version kommt aus package.json "packageManager").
RUN corepack enable

# Dependencies zuerst (bessere Layer-Caches). pnpm-workspace.yaml liefert die
# Settings (nodeLinker/trustPolicy/allowBuilds), damit native Module (bcrypt/sharp)
# im Image gebaut werden. Dev-Deps werden für den tsc-Build gebraucht.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

# Sourcen kopieren und Server + Worker bauen (tsc -> dist/).
COPY . .
RUN pnpm run build
# Dashboard (React) ins Image bauen (vite -> dist/dashboard); wird bei SERVE_DASHBOARD=true unter
# /dashboard ausgeliefert. Schema-Introspektion läuft statisch aus der Config (kein laufender Server nötig).
RUN pnpm run build:dashboard

EXPOSE 3000

# API-Server als Default; der Worker-Service überschreibt das mit `pnpm run start:worker`.
CMD ["pnpm", "run", "start:server"]
