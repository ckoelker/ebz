#!/usr/bin/env bash
# Lädt frei verfügbare SCORM-1.2-Seed-Kurse nach testdata/scorm/ (gitignored).
# Ersatz für den (noch nicht verfügbaren) Lemon-Export — reproduzierbarer Seed aus der Quelle,
# analog zum Repeatable-Seed-Prinzip (vgl. services/integration/smoke-portal-rechnungen.sh).
# python wird als JSON-Parser UND (mangels zip-Binary) zum Packen genutzt.
# Nutzung:  bash infra/seeds/lms-fetch-testdata.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEST="$ROOT/testdata/scorm"
TEMPLATE="$ROOT/services/openolat/h5p-scorm"   # committete SCORM-Hülle (index.html + imsmanifest.xml)
mkdir -p "$DEST"

# Reichhaltige H5P-Beispiel-„Course Presentation" (viele Content-Typen: MultiChoice, Blanks,
# DragText, DragQuestion, MarkTheWords, Summary, SingleChoiceSet, Dialogcards, TrueFalse, Table,
# InteractiveVideo …). Quelle: offizielles h5p/h5p-integration-test-suite (GitHub, raw).
H5P_EXAMPLE_URL="${H5P_EXAMPLE_URL:-https://raw.githubusercontent.com/h5p/h5p-integration-test-suite/master/test-content/h5p-org-examples/course-presentation-08042017.h5p}"
# h5p-standalone: rendert H5P rein clientseitig (HTML5, kein H5P-Server, kein Flash). 1.x/3.x-Linie.
H5P_STANDALONE="${H5P_STANDALONE:-3.8.0}"

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

# Baut ein SCORM-1.2-Paket, das eine H5P-„Course Presentation" rein clientseitig (HTML5) abspielt.
# Verpackt = entpacktes H5P (h5p.json + content/ + libraries) + h5p-standalone-Player (assets/) +
# committete SCORM-Hülle (index.html + imsmanifest.xml). Ersetzt den früheren Flash-Golf-Kurs, der
# in modernen Browsern nur „Couldn't load plugin." zeigte. Import-/Render-Pfad bleibt unverändert
# (OpenOLAT: FileResource.SCORMCP), nur der Inhalt ist jetzt zeitgemäßes H5P.
build_h5p_scorm() {
  local folder="$1" name="$2"
  local work="$DEST/$folder"
  echo "→ $name (H5P→SCORM)"
  rm -rf "$work"; mkdir -p "$work/assets" "$work/h5p"
  # 1) Beispiel-H5P holen + entpacken (das .h5p-Format bündelt h5p.json, content/ und alle libraries).
  curl -fsSL -o "$DEST/$folder.h5p" "$H5P_EXAMPLE_URL"
  unzip -q -o "$DEST/$folder.h5p" -d "$work/h5p"
  # 2) h5p-standalone-Dist (npm-Tarball) holen + Player nach assets/ legen.
  curl -fsSL -o "$DEST/h5p-standalone.tgz" \
    "https://registry.npmjs.org/h5p-standalone/-/h5p-standalone-${H5P_STANDALONE}.tgz"
  rm -rf "$DEST/package"; tar xzf "$DEST/h5p-standalone.tgz" -C "$DEST"
  cp -r "$DEST/package/dist/." "$work/assets/"; rm -rf "$DEST/package" "$DEST/h5p-standalone.tgz"
  # 3) SCORM-Hülle (Loader + Manifest) aus dem committeten Template.
  cp "$TEMPLATE/index.html" "$TEMPLATE/imsmanifest.xml" "$work/"
  # 4) Zippen ohne zip-Binary (Python). imsmanifest.xml liegt dadurch top-level im Zip.
  rm -f "$DEST/$folder.zip"
  python -c "import shutil,sys; shutil.make_archive(sys.argv[1],'zip',sys.argv[2])" "$work" "$work"
  verify_scorm12 "$work" "$name"
}

# 1) Reichhaltiger H5P-Showcase-Kurs (Course Presentation) als SCORM 1.2 verpackt (HTML5, kein Flash).
build_h5p_scorm "h5p-cp-scorm" "H5P Showcase – Course Presentation"

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
