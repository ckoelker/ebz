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
- **1 — Tempdata ✅ ERLEDIGT (2026-06-30):** `test-results/` (+ `**/test-results/`) in `.gitignore`; tmp/ + testdata/ waren bereits ignored. Gate `git status --ignored`: `!! test-results/`. Commit + Push.
- **2 — Atomarer Root-Lift (Compose-Split + data/ GEFALTET, Nutzer-Entscheid 2026-06-30):** `showcase/` löst sich auf; `apps/ packages/ services/ data/ infra/ tests/ tools/ docs/` an Repo-Root. In EINEM Schritt: Dirs heben (`git mv`) + `docker-compose.yml` per `include:` auf `infra/compose/*` splitten + `postgres/dlt/dbt/lightdash` → `data/` + `otel-collector-config.yaml` → `infra/observability/`. Begründung: Phasen 2+3 hätten dieselben Compose-Pfade angefasst, die der Root-Lift ohnehin umschreibt → jede Pfadzeile genau EINMAL anfassen. Alle Aliase/Contexts/Scripts/Seeds/prozessdoku/Naht-Roots nachziehen. Gate: je App `pnpm build` + `naht` + Docker-Build + voller Stack (`docker compose config` als Vorab-Gate).

Reihenfolge bewusst: billig+sicher zuerst (0,1), dann der Root-Lift als EIN gegateter atomarer Schritt.

## Resume-Punkt (für Wiedereinstieg nach Compaction)
**Stand:** Phase 0+1 fertig + committet. **Als Nächstes: Phase 2 (atomarer Root-Lift, 2+3 gefaltet)** — siehe Phase-2-Eintrag oben. Großer Schritt: erst vollständige Ziel-Mapping-Tabelle (jeder showcase/-Eintrag → Zielpfad) erstellen, dann `git mv` in Blöcken, dann alle Kopplungen (Inventar oben) nachziehen, dann Gates.
Arbeitsweise je Phase: `git mv` → Referenzen nachziehen (Inventar oben) → Gate → eigener Commit+Push.
Nutzer-Vorgaben: einzelne schlichte Kommandos, md-Edits sind erlaubt, selbständig committen+pushen.
