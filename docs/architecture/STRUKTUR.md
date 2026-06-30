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
**Stand:** Phase 0+1+2 fertig. Reorg **abgeschlossen** — `showcase/` aufgelöst, Voll-Stack-Gate (`tools/stack.sh`) grün (UP/SEED/Java/SPA/Storybook/Vendure/E2E/BPMN/Lightdash).
**Beim Root-Lift aufgetretene & gefixte Fallstricke (für die Zukunft):**
- Default-**Projektname** wechselte mit dem Verzeichnis (`showcase`→`ebz`); jetzt deterministisch via `name: ebz` in docker-compose.yml + `stack.sh PROJEKT`-Default.
- Verschobene **pnpm-`node_modules`** sind unbrauchbar (Symlinks/State auf alten Pfad) → pro App/Paket `pnpm install` (CI=true) bzw. bei „Already up to date"-Falle `rm -rf node_modules` erzwingen. Docker-Builds sind unbetroffen (frischer In-Image-Install).
- Verschobene Python-**venvs**: `python.exe` läuft weiter, aber console-script-Exes (`dbt.exe`) brechen → in stack.sh `dbt` via `python -m dbt.cli.main` (move-fest).
- `generate.py` Span-Log-Pfad war repo-relativ (`integration`→`services/integration`).

**Offener, bewusst vertagter Folgeschritt:** Compose `include:`-Split (infra/compose/*) — siehe Phase-2-Eintrag (Risiko/Wert; separat gegatet, wenn gewünscht).
Nutzer-Vorgaben: einzelne schlichte Kommandos, md-Edits sind erlaubt, selbständig committen+pushen.
