#!/usr/bin/env bash
# Importiert die SCORM-1.2-Seed-Kurse aus testdata/scorm/ per OpenOLAT-REST-API und veröffentlicht sie.
# Beweist den Content-Pfad reproduzierbar (Ersatz für den noch fehlenden Lemon-Export) und liefert die
# repoEntryKeys, die das Backend (WbtKurs.openolatKey) später referenziert.
#
# Verifizierter Endpunkt (OpenOLAT 20.1, openapi.json):
#   PUT  /restapi/repo/entries            multipart: file,filename,resourcename,displayname  → importiert
#   POST /restapi/repo/entries/{key}/status  newStatus=published                            → freigeben
# WICHTIG: curl -F erzwingt POST → für den Import IMMER -X PUT mitgeben (sonst 405).
#
# Voraussetzung: openolat-Container läuft (Profil controlling), Seeds via lms-fetch-testdata.sh geladen.
# Nutzung:  bash showcase/openolat/lms-import-seed.sh
#           OLAT_BASE=http://localhost:8089/restapi OLAT_CRED=administrator:openolat bash ...
set -euo pipefail

# pwd -W → Windows-Form (c:/…), sonst öffnet die native curl.exe das @file-Argument nicht (/c/… → curl:26).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && { pwd -W 2>/dev/null || pwd; })"
SCORM="$ROOT/testdata/scorm"
BASE="${OLAT_BASE:-http://localhost:8089/restapi}"
CRED="${OLAT_CRED:-administrator:openolat}"

# Idempotenz: existierenden Eintrag per displayname finden (vermeidet Dubletten bei erneutem Lauf).
existing_key() {
  local displayname="$1"
  curl -fsS -u "$CRED" -H 'Accept: application/json' "$BASE/repo/entries" \
    | python -c "import sys,json;dn=sys.argv[1];print(next((str(e['key']) for e in json.load(sys.stdin) if e['displayname']==dn),''))" "$displayname"
}

# Menschliche Meldungen → stderr (live sichtbar), NUR der Key → stdout (sauber abgreifbar).
import_course() {
  local folder="$1" displayname="$2"
  local zip="$SCORM/$folder.zip"
  if [ ! -f "$zip" ]; then
    echo "  ✗ $displayname: $zip fehlt — erst 'bash showcase/lms-fetch-testdata.sh' ausführen" >&2; return 1
  fi
  local key; key="$(existing_key "$displayname")"
  if [ -n "$key" ]; then
    echo "  = $displayname: bereits importiert (key $key) — übersprungen" >&2
    echo "$key"; return 0
  fi
  local resp; resp="$(curl -fsS -u "$CRED" -X PUT -H 'Accept: application/json' \
    -F "filename=$folder.zip" \
    -F "file=@${zip};type=application/zip" \
    -F "resourcename=$displayname" \
    -F "displayname=$displayname" \
    "$BASE/repo/entries")"
  local rtype
  key="$(echo "$resp" | python -c "import sys,json;d=json.load(sys.stdin);print(d['key'])")"
  rtype="$(echo "$resp" | python -c "import sys,json;print(json.load(sys.stdin)['olatResourceTypeName'])")"
  # Freigeben, damit eingeschriebene Lernende den Kurs starten können.
  curl -fsS -o /dev/null -u "$CRED" -X POST -H 'Accept: application/json' \
    --data-urlencode "newStatus=published" "$BASE/repo/entries/$key/status"
  echo "  ✓ $displayname: importiert ($rtype) + published (key $key)" >&2
  echo "$key"
}

echo "OpenOLAT-Import gegen $BASE"
echo "→ Seed-Kurse importieren:"
# Reihenfolge stabil halten → stabile Key-Zuordnung beim ersten Lauf.
K_H5P="$(import_course h5p-cp-scorm        'H5P Showcase – Course Presentation' | tail -n1)"
K_MIN="$(import_course  minimal-smoke        'Minimal Smoke (SCORM 1.2)'  | tail -n1)"
K_LGB="$(import_course  learn-git-branching   'Learn Git Branching (Seed)' | tail -n1)"

echo
echo "repoEntryKeys (→ WbtKurs.openolatKey):"
echo "  h5p-cp-scorm        = $K_H5P"
echo "  minimal-smoke       = $K_MIN"
echo "  learn-git-branching = $K_LGB"
