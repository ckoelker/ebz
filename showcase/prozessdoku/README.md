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
- `uebersicht.bpmn` — Phasen-Ablauf (Grobsicht).
- `sub-<phase>.bpmn` — je Phase die Schritte; jeder Task trägt „— <Akteur> · <System>".

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

## Bekannte Grenze: Swimlanes
`bpmn-auto-layout` layoutet (noch) **keine** Pools/Lanes — daher steht „wer/wo" aktuell **im Task-Label**.
Eine Lane-Injektion ist in `generate.py` vorbereitet (`LANES_AKTIV`, `_mit_lanes`), wartet aber auf ein
lane-fähiges Layout (geplant: eigener Swimlane-Layouter, Spalte = Reihenfolge, Zeile = Akteur).
