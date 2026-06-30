#!/usr/bin/env bash
# Prozessdoku-Pipeline (Living Documentation):
#   E2E-Tests → OTel-Span-Log → PM4py (BPMN) → bpmn-auto-layout → docs/bpmn/
#
# Aufruf:
#   bash build.sh            # generiert/aktualisiert die BPMN
#   bash build.sh --check    # CI: schlägt fehl, wenn die committeten BPMN vom Code abweichen
set -euo pipefail
cd "$(dirname "$0")"

echo "==> 1/3 E2E-Tests (erzeugen integration/target/prozess-log/spans.jsonl)"
mvn -q -f ../../services/integration/pom.xml test -Dtest='*E2ETest'

echo "==> 2/3 generate.py (PM4py Inductive Miner → out/*.bpmn)"
if [ ! -d .venv ]; then
  py -3.13 -m venv .venv
  ./.venv/Scripts/python -m pip install -q -r requirements.txt
fi
./.venv/Scripts/python generate.py

echo "==> 3/3 layout.mjs (bpmn-auto-layout → ../bpmn/)"
[ -d node_modules ] || pnpm install
node layout.mjs

if [ "${1:-}" = "--check" ]; then
  echo "==> CI-Verify: git diff der generierten BPMN"
  git diff --exit-code -- ../bpmn/
fi
echo "OK: Prozessdoku aktualisiert (docs/bpmn/)."
