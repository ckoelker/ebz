# Monorepo-Struktur — Reorganisation (Decision Record)

**Status:** in Umsetzung (2026-06-30). **Entscheidungen (Nutzer):** voller Reorg (Phase 0–4),
`showcase/`-Inhalt **nach Repo-Root heben**, **Source-Alias bleibt** (kein pnpm-Workspace/Nx jetzt —
bewusste Wahl wegen Storybook-docgen + keine node_modules-Duplikate; revisit erst bei Team/Schmerz).
Kontext: kein Showcase mehr, großer ERP-Stack, Produktiv >1 Jahr — saubere Struktur lohnt, aber gegated.

## Zielbaum (Repo-Root)
```
ebz/
├── apps/                  mdm/  portal/  storefront/  design-system/{crm-kernmaske,kunden-kernmaske}/
├── packages/              crm-ui/  customer-ui/  ui-base/        (Vite-Source-Alias-Libs, kein Deploy)
├── services/              integration/ (Quarkus)  vendure/  openolat/
├── data/                  postgres/  dlt/  dbt/  lightdash/
├── infra/                 compose/ (split per Concern + include)  observability/ (otel/jaeger/phoenix)
│                          auth/ (keycloak)  seeds/ (mandanten-seed, lms-fetch-testdata.sh)
├── tests/                 e2e/
├── tools/                 check-seam.mjs  stack.sh (ex showcase-aufbau.sh)
├── docs/                  architecture/ (DESIGN-SYSTEM, ARCHITEKTUR-GENERIERUNG, STRUKTUR)
│                          planung/ (alle *-planung/)  prozessdoku/  bpmn/  wireframes/ (ex crm-wireframe)
├── .tmp/   (gitignored)   transiente Artefakte (test-results/, Render-Output)
└── docker-compose.yml     nur noch `include:` der infra/compose/*-Dateien
```

## Kopplungs-Inventar (was beim Verschieben nachgezogen werden MUSS)
**Runtime/Build (hart):**
- Vite-Aliase `../crm-ui/src` → `../../packages/crm-ui/src` (je App: vite.config/nuxt.config + tsconfig `paths` + Tailwind `@source`) und Docker `context:` + `COPY <pkg>`-Pfade.
- `tools/check-seam.mjs` → `roots` + Specifier-Pfade + Hinweis-String (`showcase/DESIGN-SYSTEM.md`).
- `prozessdoku/{build.sh,layout.mjs,generate.py}` schreiben nach `../docs/bpmn/` → Pfad nach Move anpassen.
- `showcase-aufbau.sh` (→ `tools/stack.sh`): Schritt-Pfade (`prozessdoku/build.sh`, Service-Verzeichnisse, Seeds), BPMN-Ausgabe-Meldungen.
- `docker-compose.yml`: alle `build.context`/`dockerfile`/`volumes`-Mounts; `postgres/initdb`-Mount; `otel-collector-config.yaml`-Mount.
- `e2e/` Pfade auf App-URLs/Artefakte.

**Doku/Kommentare (weich, Korrektheit):** dutzende Markdown-Querverweise (`../shop-planung/` …) + Code-Kommentare. Per globalem Replace nachziehen; nicht blockierend.

## Phasen (jede mit Gate + eigenem Commit)
- **0 — Docs ✅ ERLEDIGT (2026-06-30):** 13 `*-planung/` (Root+showcase) → `docs/planung/`; `DESIGN-SYSTEM.md` + `ARCHITEKTUR-GENERIERUNG.md` → `docs/architecture/`; `check-seam.mjs`-Hinweisstring nachgezogen. `prozessdoku/` + `showcase/docs/bpmn/` BLEIBEN vorerst (Script-Kopplung → Phase 4). Gate `node showcase/tools/check-seam.mjs` grün.
- **1 — Tempdata:** `test-results/` → `.gitignore` (tmp/ + testdata/ sind bereits ignored). Gate: `git status`.
- **2 — Compose:** `docker-compose.yml` → `infra/compose/*` via `include`; `otel-collector-config.yaml` → `infra/observability/`. Gate: `docker compose config`.
- **3 — Data:** `postgres/dlt/dbt/lightdash` → `data/`; Mount-Pfade nachziehen. Gate: Stack `up`+seed.
- **4 — Code + Root-Lift:** `apps/`, `packages/`, `services/`, `tests/`, `tools/` heben; alle Aliase/Contexts/Scripts/Seeds/prozessdoku/Naht-Roots nachziehen; `showcase/` löst sich auf. Gate: je App `pnpm build` + `naht` + Docker-Build + voller Stack.

Reihenfolge bewusst: billig+sicher zuerst, der teure Root-Lift (Phase 4) zuletzt als eigenes gegated Vorhaben.

## Resume-Punkt (für Wiedereinstieg nach Compaction)
**Stand:** Phase 0 fertig + committet. **Als Nächstes: Phase 1 (Tempdata)** → `test-results/` in `.gitignore`.
Arbeitsweise je Phase: `git mv` → Referenzen nachziehen (Inventar oben) → Gate → eigener Commit+Push.
Nutzer-Vorgaben: einzelne schlichte Kommandos, md-Edits sind erlaubt, selbständig committen+pushen.
