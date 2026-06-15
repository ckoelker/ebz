#!/usr/bin/env bash
# Lädt frei verfügbare SCORM-1.2-Seed-Kurse nach testdata/scorm/ (gitignored).
# Ersatz für den (noch nicht verfügbaren) Lemon-Export — reproduzierbarer Seed aus der Quelle,
# analog zum Repeatable-Seed-Prinzip (vgl. showcase/smoke-portal-rechnungen.sh).
# python wird NUR als JSON-Parser genutzt. Nutzung:  bash showcase/lms-fetch-testdata.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/testdata/scorm"
mkdir -p "$DEST"

verify_scorm12() {
  local dir="$1" name="$2" manifest
  manifest="$(find "$dir" -maxdepth 2 -iname imsmanifest.xml | head -n1)"
  if [ -z "$manifest" ]; then echo "  ✗ $name: imsmanifest.xml FEHLT (kein gültiges SCORM-Paket)"; return 1; fi
  if grep -qiE 'schemaversion[^>]*1\.2|adlcp_rootv1p2|adlcp_v1p2' "$manifest"; then
    echo "  ✓ $name: SCORM 1.2 bestätigt"
  else
    echo "  ⚠ $name: SCORM-Version NICHT eindeutig 1.2 — manuell prüfen ($manifest)"
  fi
}

fetch_zip() {
  local url="$1" folder="$2" name="$3"
  local zip="$DEST/$folder.zip"
  echo "→ $name"
  curl -fsSL -o "$zip" "$url"
  rm -rf "$DEST/$folder"; mkdir -p "$DEST/$folder"
  unzip -q -o "$zip" -d "$DEST/$folder"
  verify_scorm12 "$DEST/$folder" "$name"
}

# 1) Umfänglicher Golf-Beispielkurs (SCORM 1.2, mehrseitig, mit Quiz/Completion)
#    Quelle: github.com/jbroadway/scorm (samples). Inhalt = Rustici Golf Examples (Wikipedia/Wikihow).
fetch_zip \
  "https://raw.githubusercontent.com/jbroadway/scorm/master/samples/SCORM%201.2%20Completes%20On%20Passing%20Quiz.zip" \
  "golf-scorm12" "Golf Examples (Completes On Passing Quiz)"

# 2) Minimal-Smoke (SCORM 1.2 RuntimeMinimumCalls = kanonisches Minimalpaket) — schnelle Tests
fetch_zip \
  "https://raw.githubusercontent.com/jbroadway/scorm/master/samples/AllGolfExamples/RuntimeMinimumCalls_SCORM12.zip" \
  "minimal-smoke" "Runtime Minimum Calls (SCORM 1.2)"

# 3) Echter interaktiver Kurs: Learn Git Branching als SCORM 1.2 (jeweils latest Release-Asset).
#    Quelle: github.com/andre-wojtowicz/learn-git-branching-scorm (Underlying learnGitBranching = MIT).
LGB_URL="$(curl -fsSL https://api.github.com/repos/andre-wojtowicz/learn-git-branching-scorm/releases/latest \
  | python -c "import sys,json;print(next(a['browser_download_url'] for a in json.load(sys.stdin)['assets'] if a['name'].endswith('.zip')))")"
fetch_zip "$LGB_URL" "learn-git-branching" "Learn Git Branching"

echo
echo "Fertig. Seed-Kurse unter: $DEST (gitignored)"
ls -1 "$DEST"
