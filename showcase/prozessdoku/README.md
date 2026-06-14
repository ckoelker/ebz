# Prozessdoku (Living Documentation) — BPMN aus OpenTelemetry-Spans

Erzeugt **automatisch BPMN-Diagramme** des Prozesses „Anmeldung Berufsschule" aus den im E2E-Test
erfassten **OpenTelemetry-Business-Spans** — dieselbe Instrumentierung, die in Prod an **Jaeger** geht.
So bleibt die Doku ohne Handarbeit in Sync mit dem Code. Plan: [`../../prozessdoku-planung/`](../../prozessdoku-planung/README.md).

## Pipeline
```
E2E-Test (rest-assured)                Prod
  └─ Prozessspur.schritt(...) ── OTel-Spans ──┬─ OTLP ─→ Jaeger (UI :16686)
                                              └─ SpanLogExporter ─→ integration/target/prozess-log/spans.jsonl
                                                                        └─ generate.py (PM4py) ─→ out/*.bpmn
                                                                              └─ layout.mjs (bpmn-auto-layout) ─→ ../docs/bpmn/*.bpmn
```

- **Case-Id** = `prozess.fall` (W3C-Baggage, im Test der Szenario-Name); **Subprozesse** je `prozess.phase`;
  **Akteur/System** je Schritt (`prozess.akteur`/`prozess.system`).
- Mehrere Test-Szenarien (Fälle) → der Inductive Miner erzeugt **Gateways/Verzweigungen**.

## Ausgabe (`../docs/bpmn/`)
- `uebersicht.bpmn` — Phasen-Ablauf (Grobsicht, mit Gateways aus den Varianten).
- `sub-<phase>.bpmn` — je Phase die Schritte als **Swimlanes** (Lane = Akteur/„Person"; System am Task-Label).

Öffnen in **Camunda Modeler**, **draw.io** oder jedem BPMN-Viewer.

## Lokal erzeugen
```bash
# 1) Event-Log aus den E2E-Tests (schreibt integration/target/prozess-log/spans.jsonl)
mvn -f ../integration/pom.xml test -Dtest='*E2ETest'

# 2) Python-Setup (einmalig) + BPMN entdecken
py -3.13 -m venv .venv && ./.venv/Scripts/python -m pip install -r requirements.txt
./.venv/Scripts/python generate.py            # → out/*.bpmn (deterministische IDs)

# 3) Auto-Layout + Ablage nach ../docs/bpmn
pnpm install && node layout.mjs
```
(Verifiziert mit PM4py 2.7.22.4 auf Python 3.13.3.)

## Live-Traces (Prod-Sicht)
```bash
docker compose --profile controlling up -d jaeger integration
# nach einem Durchlauf: Jaeger UI → http://localhost:16686 (Service „ebz-integration")
```

## Swimlanes
`bpmn-auto-layout` kann **keine** Pools/Lanes. Die Subprozesse layoutet daher ein **eigener
Swimlane-Layouter** in `generate.py` (`_swimlane`, `LANES_AKTIV=True`): Spalte = Reihenfolge
(Longest-Path), Zeile = Akteur-Lane, mit eigenem DI (Pool/Lane-Boxen + orthogonale Kanten). `layout.mjs`
erkennt diese fertig layouteten Dateien am Marker `swimlane-laidout` und übernimmt sie unverändert; nur
die `uebersicht.bpmn` (ohne Lanes) geht durch bpmn-auto-layout. Auf `LANES_AKTIV=False` umschaltbar
(dann „Person · System" nur im Task-Label, Layout komplett über bpmn-auto-layout).
