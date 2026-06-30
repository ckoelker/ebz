#!/usr/bin/env bash
#
# lms-nachweis-seed.sh — M6 Nachweis-Seam (PoC-Kriterium K6).
# ====================================================================================================
# Stellt reproduzierbar einen Weiterbildungsnachweis her: ein Lernender (customer) schliesst einen WBT ab,
# OpenOLAT haelt die Completion (trackbarer Nachweis-Kurs, System-of-Record), und das Backend projiziert
# sie als kanonischen LernleistungsFakt mit den anrechenbaren Soll-Stunden (NICHT SCORM session_time).
#
# Treibt rein die offiziellen Backend-Endpunkte (Staff-OIDC, Rolle katalog-pflege):
#   POST /lms/kurse                                   (WbtKurs anlegen, Soll-Stunden, openolatKey=Nugget)
#   POST /lms/einschreibungen                         (customer einschreiben → Dispatcher provisioniert OpenOLAT)
#   POST /lms/nachweise/kurs/{wbtId}/sicherstellen    (trackbarer Nachweis-Kurs, idempotent)
#   POST /lms/nachweise/einschreibung/{id}/abschluss-melden   (Completion in OpenOLAT festhalten)
#   POST /lms/nachweise/einschreibung/{id}/synchronisieren    (Completion -> LernleistungsFakt)
#
# Idempotent (find-or-create je Schritt). Voraussetzung: lms-import-seed (Nugget 884736), Keycloak/Backend up.
# Hook fuer tools/stack.sh (Schritt `lms-nachweis`, nach `lms-share`).
set -uo pipefail

# Issuer-Host, dem das Backend vertraut (Netzwerk-Alias, NICHT localhost:8088 — sonst 401 wegen iss-Mismatch).
KC="${KC:-http://keycloak.localhost:8080}"
BACKEND="${BACKEND:-http://localhost:8090}"
WBT_CODE="${WBT_CODE:-WBT-NACHWEIS-DEMO}"
OL="${OL:-http://localhost:8089}"           # OpenOLAT-Basis (für Repo-Key-Auflösung)
NUGGET_NAME="${NUGGET_NAME:-H5P Showcase}"  # Anzeigename des geteilten Nuggets (Inhaltsreferenz des WBT)
NUGGET_KEY="${NUGGET_KEY:-}"                # LEER = dynamisch über NUGGET_NAME auflösen (Key ist NICHT
                                            # deterministisch über Builds — wie lms-share). Gesetzt = Vorrang.
OLA_USER="${OPENOLAT_ADMIN_USER:-administrator}"
OLA_PASS="${OPENOLAT_ADMIN_PASSWORD:-openolat}"
SOLL="${SOLL:-2.50}"                   # anrechenbare Soll-Stunden (rechtliche Zaehlung)
LERNENDER="${LERNENDER:-customer}"     # Demo-Lernender (Realm ebz-customers)

rot=$'\e[31m'; gruen=$'\e[32m'; aus=$'\e[0m'
ok(){ echo "  ${gruen}✓${aus} $*"; }
warn(){ echo "  ${rot}!${aus} $*" >&2; }
fail(){ echo "${rot}✗ $*${aus}" >&2; exit 1; }

# python-Auswertung eines JSON von stdin; das Ergebnis steht in `d` (Liste oder Objekt).
jget(){ python -c "import sys,json
try: d=json.load(sys.stdin)
except Exception: print(''); sys.exit()
$1"; }

staff_token(){
  curl -s -X POST "$KC/realms/ebz-staff/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=staff-frontend -d username=staff -d password=staff \
    | jget "print(d.get('access_token','') if isinstance(d,dict) else '')"
}
admin_token(){
  curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin \
    | jget "print(d.get('access_token','') if isinstance(d,dict) else '')"
}

main(){
  command -v curl >/dev/null || fail "curl fehlt"
  echo "M6 Nachweis-Seam gegen $BACKEND (Lernender: $LERNENDER, WBT: $WBT_CODE, Soll-Stunden: $SOLL)"

  local ST AT SUB
  ST="$(staff_token)"; [ -n "$ST" ] || fail "Staff-Token leer (Keycloak up? Issuer ebz-staff?)"
  AT="$(admin_token)"; [ -n "$AT" ] || fail "Admin-Token leer"
  SUB="$(curl -s -H "Authorization: Bearer $AT" "$KC/admin/realms/ebz-customers/users?username=$LERNENDER" \
        | jget "print(d[0]['id'] if isinstance(d,list) and d else '')")"
  [ -n "$SUB" ] || fail "keycloak-sub von $LERNENDER nicht gefunden"
  local AUTH="Authorization: Bearer $ST" CT="Content-Type: application/json"
  ok "Aufgeloest: staff-token ok, $LERNENDER-sub=$SUB"

  # 0b) Nugget-Repo-Key dynamisch auflösen (wie lms-share, über den displayname) — der Import vergibt
  #     KEINEN über Builds stabilen Key. Explizit gesetztes NUGGET_KEY hat Vorrang.
  if [ -z "$NUGGET_KEY" ]; then
    NUGGET_KEY="$(curl -s -u "$OLA_USER:$OLA_PASS" -H 'Accept: application/json' "$OL/restapi/repo/entries" \
      | jget "d=d if isinstance(d,list) else d.get('repositoryEntries',[]); print(next((str(e['key']) for e in d if '$NUGGET_NAME' in (e.get('displayname') or '')),''))")"
    [ -n "$NUGGET_KEY" ] || fail "Nugget '$NUGGET_NAME' nicht in OpenOLAT gefunden (erst 'bash openolat/lms-import-seed.sh'?)"
    ok "Nugget aufgelöst: '$NUGGET_NAME' → Repo-Key $NUGGET_KEY"
  fi

  # 1) WbtKurs find-or-create (mit Soll-Stunden + Nugget als Inhaltsreferenz).
  local WID
  WID="$(curl -s -H "$AUTH" "$BACKEND/lms/kurse" | jget "print(next((str(k['id']) for k in (d if isinstance(d,list) else []) if k.get('code')=='$WBT_CODE'),''))")"
  if [ -z "$WID" ]; then
    WID="$(curl -s -H "$AUTH" -H "$CT" -X POST "$BACKEND/lms/kurse" \
          -d "{\"code\":\"$WBT_CODE\",\"titel\":\"Nachweis-Demo WBT\",\"status\":\"AKTIV\",\"shopVerkauf\":false,\"openolatKey\":$NUGGET_KEY,\"sollStundenAnrechenbar\":$SOLL}" \
          | jget "print(d.get('id','') if isinstance(d,dict) else '')")"
    [ -n "$WID" ] || fail "WbtKurs-Anlage fehlgeschlagen"
    ok "WbtKurs angelegt (id $WID, Soll-Stunden $SOLL)"
  else
    ok "WbtKurs existiert (id $WID)"
  fi

  # 2) Einschreibung find-or-create (customer in den WBT).
  local EID
  find_eid(){ curl -s -H "$AUTH" "$BACKEND/lms/einschreibungen" \
    | jget "print(next((str(e['id']) for e in (d if isinstance(d,list) else []) if str(e.get('wbtKursId'))=='$WID' and e.get('keycloakSub')=='$SUB'),''))"; }
  EID="$(find_eid)"
  if [ -z "$EID" ]; then
    curl -s -H "$AUTH" -H "$CT" -X POST "$BACKEND/lms/einschreibungen" \
      -d "{\"keycloakSub\":\"$SUB\",\"email\":\"$LERNENDER@ebz.de\",\"anzeigeName\":\"Carla Kundin\",\"wbtKursIds\":[$WID]}" >/dev/null
    EID="$(find_eid)"
    [ -n "$EID" ] || fail "Einschreibung-Anlage fehlgeschlagen"
    ok "Einschreibung angelegt (id $EID)"
  else
    ok "Einschreibung existiert (id $EID)"
  fi

  # 3) Warten, bis der Dispatcher die OpenOLAT-Identitaet provisioniert hat (openolatIdentityKey gesetzt).
  local IDK=""
  for i in $(seq 1 30); do
    IDK="$(curl -s -H "$AUTH" "$BACKEND/lms/einschreibungen" \
      | jget "print(next((str(e.get('openolatIdentityKey') or '') for e in (d if isinstance(d,list) else []) if str(e.get('id'))=='$EID'),''))")"
    [ -n "$IDK" ] && break
    sleep 2
  done
  [ -n "$IDK" ] || fail "Einschreibung $EID nicht in OpenOLAT provisioniert (Dispatcher?)"
  ok "OpenOLAT-Identitaet provisioniert (identityKey $IDK)"

  # 4) Trackbaren Nachweis-Kurs sicherstellen (idempotent).
  curl -s -o /dev/null -H "$AUTH" -X POST "$BACKEND/lms/nachweise/kurs/$WID/sicherstellen"
  ok "Nachweis-Kurs sichergestellt"

  # 5) Abschluss in OpenOLAT festhalten (Completion-Event) + 6) als LernleistungsFakt synchronisieren.
  curl -s -o /dev/null -H "$AUTH" -X POST "$BACKEND/lms/nachweise/einschreibung/$EID/abschluss-melden"
  curl -s -o /dev/null -H "$AUTH" -X POST "$BACKEND/lms/nachweise/einschreibung/$EID/synchronisieren"

  # 7) Gegenprobe: Nachweis lesbar?
  local FAKT
  FAKT="$(curl -s -H "$AUTH" "$BACKEND/lms/nachweise/fakten" \
    | jget "f=next((x for x in (d if isinstance(d,list) else []) if str(x.get('einschreibungId'))=='$EID'),None); print((str(f.get('sollStunden'))+'|'+str(f.get('bestanden'))+'|'+str(f.get('abgeschlossenAm'))) if f else '')")"
  [ -n "$FAKT" ] || fail "kein LernleistungsFakt fuer Einschreibung $EID"
  echo "${gruen}✓ Weiterbildungsnachweis erfasst (Einschreibung $EID): sollStunden|bestanden|am = $FAKT${aus}"
}
main "$@"
