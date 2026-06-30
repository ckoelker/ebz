#!/usr/bin/env bash
#
# lms-zertifikat-seed.sh — Auto-Zertifikat-Demo (mandantenfaehig, REIN per API konfiguriert).
# ====================================================================================================
# Richtet reproduzierbar einen DEMO_AG-Zertifikatskurs ein und beweist die "flat verkaufte, per API
# konfigurierte" Strecke: ein geteiltes SCORM-Nugget wird als trackbarer Kursknoten eingehaengt und der
# Kurs auf NATIVE OpenOLAT-Auto-Zertifizierung gestellt (Lernpfad-Wurzelregel: bestanden = 100 %
# abgeschlossen). Ein DEMO_AG-Mitarbeiter (max) ist eingeschrieben UND Org-Mitglied (→ Mandanten-Branding
# via [[mandanten-vermarktung-openolat]] M0). Schliesst max das Nugget im Browser ab, stellt OpenOLAT
# OHNE Zutun ein herunterladbares Zertifikat aus — kein Seed-Cert, kein manuelles REST-Generieren.
#
# Treibt ausschliesslich REST:
#   - EBZ-Extension  PUT /restapi/ebz/nachweis/courses/{id}/scorm-zertifikat   (SCORM-Knoten +
#                    passed.progress-Wurzelregel + Auto-Zertifizierung + Publish; ebz-nachweis-cert.jar)
#   - OpenOLAT-Std   /restapi/repo/courses, /restapi/users, /restapi/organisations/{k}/users/{id},
#                    /restapi/repo/entries/{re}/participants/{id}, /restapi/repo/courses/{id}/organisations/{org}
#
# Idempotent (find-or-create je Schritt; der Kurs wird ueber externalRef genau einmal konfiguriert, da das
# Anhaengen des SCORM-Knotens NICHT idempotent ist). Hook fuer tools/stack.sh (Schritt `lms-zertifikat`,
# nach `lms-share`/`lms-nachweis`). Voraussetzung: Image mit ebz-nachweis-cert.jar, mandanten-seed (DEMO_AG-
# Org key 2, cssClass mandant-demo-ag), lms-import-seed (Nugget 884736).
set -uo pipefail

OL="${OL:-http://localhost:8089}"
NUGGET_NAME="${NUGGET_NAME:-H5P Showcase}"  # Anzeigename des geteilten Nuggets (Inhalt des Zertifikatskurses)
NUGGET_KEY="${NUGGET_KEY:-}"                # LEER = dynamisch über NUGGET_NAME auflösen (Key NICHT
                                            # deterministisch über Builds — wie lms-share). Gesetzt = Vorrang.
ORG_KEY="${ORG_KEY:-2}"                      # DEMO_AG OpenOLAT-Org (Branding-Anker + Kurs-Scope)
EXTREF="${EXTREF:-EBZ-ZERT-DEMO}"            # Idempotenz-Schluessel des Kurses
COURSE_TITLE="${COURSE_TITLE:-DEMO AG - Golf-Knigge (Zertifikat)}"
ADMIN_USER="${OPENOLAT_ADMIN_USER:-administrator}"
ADMIN_PASS="${OPENOLAT_ADMIN_PASSWORD:-openolat}"
MAX_EMAIL="${MAX_EMAIL:-max.mustermann@demo-ag.de}"   # max meldet sich an SEINEM Firmen-IdP an
MAX_PASS="${MAX_PASS:-demo}"                           # Passwort im DEMO-AG-Keycloak (keycloak-demo-ag:8085)

A="Authorization: Basic $(printf '%s:%s' "$ADMIN_USER" "$ADMIN_PASS" | base64)"
CT="Content-Type: application/json"
AC="Accept: application/json"

rot=$'\e[31m'; gruen=$'\e[32m'; aus=$'\e[0m'; hk=$'\xe2\x9c\x93'; kr=$'\xe2\x9c\x97'
ok(){ echo "  ${gruen}${hk}${aus} $*"; }
warn(){ echo "  ${rot}!${aus} $*" >&2; }
fail(){ echo "${rot}${kr} $*${aus}" >&2; exit 1; }

# JSON von stdin auswerten; `d` ist das geladene Objekt/Liste.
jget(){ python -c "import sys,json
try: d=json.load(sys.stdin)
except Exception: print(''); sys.exit()
$1"; }

main(){
  command -v curl >/dev/null || fail "curl fehlt"
  echo "Auto-Zertifikat-Demo gegen $OL (Nugget $NUGGET_KEY, DEMO_AG-Org $ORG_KEY, externalRef $EXTREF)"

  # 0) Erreichbarkeit + Extension registriert?
  curl -s -o /dev/null -w '%{http_code}' "$OL/" | grep -q '30\|200' || fail "OpenOLAT nicht erreichbar ($OL)"
  local probe
  probe=$(curl -s -X PUT -H "$A" "$OL/restapi/ebz/nachweis/courses/0/scorm-zertifikat?nuggetKey=0")
  echo "$probe" | grep -q 'fehler' || fail "EBZ-Extension nicht registriert (ebz-nachweis-cert.jar im Image?)"
  ok "OpenOLAT up, EBZ-Nachweis-Extension registriert"

  # 0b) Nugget-Repo-Key dynamisch auflösen (wie lms-share, über den displayname) — der Import vergibt
  #     KEINEN über Builds stabilen Key. Explizit gesetztes NUGGET_KEY hat Vorrang.
  if [ -z "$NUGGET_KEY" ]; then
    NUGGET_KEY="$(curl -s -H "$A" -H "$AC" "$OL/restapi/repo/entries" \
      | jget "d=d if isinstance(d,list) else d.get('repositoryEntries',[]); print(next((str(e['key']) for e in d if '$NUGGET_NAME' in (e.get('displayname') or '')),''))")"
    [ -n "$NUGGET_KEY" ] || fail "Nugget '$NUGGET_NAME' nicht in OpenOLAT gefunden (erst 'bash openolat/lms-import-seed.sh'?)"
    ok "Nugget aufgelöst: '$NUGGET_NAME' → Repo-Key $NUGGET_KEY"
  fi

  # 1) Zertifikatskurs find-or-create (externalRef = Idempotenz; nur bei Neuanlage konfigurieren).
  local CID RE
  CID=$(curl -s -H "$A" -H "$AC" "$OL/restapi/repo/courses?externalRef=$EXTREF" \
        | jget "print((d[0].get('key','') if isinstance(d,list) and d else ''))")
  if [ -n "$CID" ]; then
    RE=$(curl -s -H "$A" -H "$AC" "$OL/restapi/repo/courses?externalRef=$EXTREF" \
         | jget "print((d[0].get('repoEntryKey','') if isinstance(d,list) and d else ''))")
    ok "Zertifikatskurs existiert (courseId $CID, repoEntry $RE)"
  else
    local CR TENC
    TENC=$(printf '%s' "$COURSE_TITLE" | python -c "import sys,urllib.parse;print(urllib.parse.quote(sys.stdin.read()))")
    CR=$(curl -s -X PUT -H "$A" -H "$AC" \
         "$OL/restapi/repo/courses?shortTitle=$TENC&title=$TENC&externalRef=$EXTREF")
    CID=$(echo "$CR" | jget "print(d.get('key','') if isinstance(d,dict) else '')")
    RE=$(echo "$CR" | jget "print(d.get('repoEntryKey','') if isinstance(d,dict) else '')")
    [ -n "$CID" ] && [ -n "$RE" ] || fail "Kurs-Anlage fehlgeschlagen ($CR)"
    ok "Zertifikatskurs angelegt (courseId $CID, repoEntry $RE)"

    # EBZ-Extension: SCORM-Knoten (Nugget) + Auto-Zertifizierung + Publish — der Kern der Demo.
    local ER
    ER=$(curl -s -X PUT -H "$A" \
         "$OL/restapi/ebz/nachweis/courses/$CID/scorm-zertifikat?nuggetKey=$NUGGET_KEY&shortTitle=Golf-Knigge&longTitle=Golf-Knigge%20Lern-Nugget")
    echo "$ER" | grep -q '"status":"ok"' || fail "Extension-Konfiguration fehlgeschlagen ($ER)"
    ok "SCORM-Knoten + Auto-Zertifikat konfiguriert + publiziert"

    # Kurs auf DEMO_AG-Org scopen (mandantenfaehig — wie M4/M6).
    curl -s -o /dev/null -X PUT -H "$A" "$OL/restapi/repo/courses/$CID/organisations/$ORG_KEY"
    ok "Kurs auf DEMO_AG-Org ($ORG_KEY) gescoped"
  fi

  # 2) DEMO_AG-Mitarbeiter max kommt per Broker-SSO ueber den EIGENEN Firmen-Keycloak des Kunden
  #    (Container keycloak-demo-ag:8085 → ebz-customers IdP "kunde-demo") ins OpenOLAT — JIT-Anlage beim
  #    ersten Login. Der Seed legt ihn NICHT lokal an (kein lokaler Login), sondern findet die per SSO
  #    entstandene Identitaet und ergaenzt nur Org-Mitgliedschaft (Branding) + Einschreibung. Vor dem
  #    ersten SSO-Login existiert max nicht → klarer Hinweis + sauberer Abbruch (Kurs steht trotzdem).
  local MID
  MID=$(curl -s -H "$A" -H "$AC" "$OL/restapi/users?email=$MAX_EMAIL" \
        | jget "print(d[0]['key'] if isinstance(d,list) and d else '')")
  if [ -z "$MID" ]; then
    warn "max ($MAX_EMAIL) noch nicht in OpenOLAT — einmal per Kunden-SSO einloggen, dann Seed erneut:"
    warn "    $OL/  →  Button 'DEMO AG (Kunden-Login)'  →  $MAX_EMAIL / $MAX_PASS  (DEMO-AG-Keycloak)"
    echo "${gruen}${hk} Zertifikatskurs steht bereit. max-Schritt (Branding+Einschreibung) nach erstem SSO-Login wiederholen.${aus}"
    return 0
  fi
  ok "max gefunden (per Kunden-SSO JIT-angelegt, identityKey $MID)"

  # 3) max in DEMO_AG-Org (Rolle users) → Mandanten-Branding beim Login. Idempotent (PUT).
  curl -s -o /dev/null -X PUT -H "$A" "$OL/restapi/organisations/$ORG_KEY/users/$MID"
  ok "max in DEMO_AG-Org (Branding mandant-demo-ag — greift beim naechsten Login)"

  # 4) max in den Zertifikatskurs einschreiben. Idempotent (PUT).
  curl -s -o /dev/null -X PUT -H "$A" "$OL/restapi/repo/entries/$RE/participants/$MID"
  ok "max in Zertifikatskurs eingeschrieben"

  echo ""
  echo "${gruen}${hk} Auto-Zertifikat-Demo bereit (Kunden-SSO ueber externen DEMO-AG-Keycloak).${aus}"
  echo "  Login:    $OL/  →  Button 'DEMO AG (Kunden-Login)'  →  $MAX_EMAIL / $MAX_PASS"
  echo "  Branding: nach (Neu-)Login orange DEMO-AG-Optik (Org-Mitgliedschaft → AfterLogin-Interceptor)"
  echo "  Kurs:     \"$COURSE_TITLE\"  →  SCORM-Knoten \"Golf-Knigge\" bis zum Ende durchspielen"
  echo "  Ergebnis: OpenOLAT stellt automatisch ein Zertifikat aus (Visitenkarte → Zertifikate)."
}
main "$@"
