#!/usr/bin/env bash
#
# showcase-aufbau.sh — EIN wiederholbarer Aufbau des kompletten Monorepos für die Kundenpräsentation.
# ====================================================================================================
# Setzt den Showcase reproduzierbar von Null auf: Volumes wipen → vollen Stack bauen+starten →
# Demo-Daten seeden → ALLE Testebenen grün fahren → Telemetrie (OTel-Spans) + BPMN generieren →
# und das laufende, voll geseedete System für die Präsentation stehen lassen.
#
# Die Postgres-Schemata enthalten keine produktiven Daten und werden bei jedem Voll-Lauf neu aufgebaut
# (docker compose down -v). Danach steht ein deterministischer Demo-Zustand mit Test-Case-Daten.
#
# AUFRUF
#   bash showcase/showcase-aufbau.sh                  # voller Lauf (reset → ... → summary)
#   bash showcase/showcase-aufbau.sh --liste          # alle Schritte auflisten
#   bash showcase/showcase-aufbau.sh --from test-java # ab Schritt 'test-java' (Stack bleibt wie er ist)
#   bash showcase/showcase-aufbau.sh --only seed      # nur den Seed-Schritt
#   bash showcase/showcase-aufbau.sh --keine-reset    # Volumes NICHT wipen (schnelles Re-Seed/-Test)
#
# Bei einem Fehler bricht der Lauf ab, lässt den Stack ABER oben (zum Inspizieren) — danach gezielt
# mit --from <schritt> fortsetzen. Re-Run-fähig: jeder Schritt ist für sich idempotent bzw. (Voll-Lauf)
# startet auf frischem Volume.
#
set -uo pipefail

HIER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HIER"

# ── Konfiguration (per Env überschreibbar) ──────────────────────────────────────────────────────
PROJEKT="${COMPOSE_PROJECT_NAME:-showcase}"          # Container heißen ${PROJEKT}-<svc>-1
COMPOSE=(docker compose --profile controlling)
KC="${KC:-http://keycloak.localhost:8080}"
RESOLVE=(--resolve "keycloak.localhost:8080:127.0.0.1")  # curl: *.localhost plattformneutral auf 127.0.0.1
API="${API:-http://localhost:8090}"                  # integration
SHOP="${SHOP:-http://localhost:3000}"                # vendure
MVN="${MVN:-mvn}"

# Schritt-Reihenfolge (Voll-Lauf führt sie genau so aus).
SCHRITTE=(preflight reset up seed mandanten-seed lms-share lms-nachweis test-java test-spa test-vendure test-e2e bpmn lightdash summary)

# ── Hübsche Ausgabe ─────────────────────────────────────────────────────────────────────────────
rot=$'\e[31m'; gruen=$'\e[32m'; gelb=$'\e[33m'; blau=$'\e[36m'; fett=$'\e[1m'; aus=$'\e[0m'
phase() { echo; echo "${blau}${fett}══════ $* ══════${aus}"; }
ok()    { echo "${gruen}✓${aus} $*"; }
warn()  { echo "${gelb}⚠${aus} $*"; }
err()   { echo "${rot}✗ $*${aus}" >&2; }
fail()  { err "$*"; echo "${rot}${fett}Abbruch.${aus} Stack bleibt oben — fortsetzen mit: bash showcase-aufbau.sh --from <schritt>" >&2; exit 1; }

# ── Helfer ──────────────────────────────────────────────────────────────────────────────────────
# Wartet bis 'cmd' (Exit 0) erfüllt ist; pollt sichtbar. $1=label $2=timeout-s $3..=cmd
warte_auf() {
  local label="$1" timeout="$2"; shift 2
  local start; start=$(date +%s)
  echo -n "   warte auf ${label} "
  while true; do
    if "$@" >/dev/null 2>&1; then echo " ${gruen}ok${aus}"; return 0; fi
    local now; now=$(date +%s)
    if (( now - start > timeout )); then echo; fail "${label} nicht bereit nach ${timeout}s"; fi
    echo -n "."; sleep 3
  done
}

gesund() { [ "$(docker inspect --format '{{.State.Health.Status}}' "${PROJEKT}-$1-1" 2>/dev/null)" = "healthy" ]; }
http_ok() { curl -fsS -o /dev/null --max-time 5 "$1"; }
# "Server antwortet" (beliebiger HTTP-Status, auch 401/403) — für Endpunkte mit Auth-Challenge (OpenOLAT-REST).
http_antwortet() { curl -sS -o /dev/null --max-time 5 "$1"; }

# Staff-Token (Realm ebz-staff, Client staff-frontend, staff/staff → Rolle katalog-pflege).
staff_token() {
  curl -s "${RESOLVE[@]}" -X POST "$KC/realms/ebz-staff/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=staff-frontend -d username=staff -d password=staff \
    | python -c "import json,sys;print(json.load(sys.stdin)['access_token'])"
}

# ── Schritte ──────────────────────────────────────────────────────────────────────────────────────

schritt_preflight() {
  phase "PREFLIGHT — Werkzeuge & Artefakte"
  for c in docker "$MVN" pnpm node python; do
    command -v "$c" >/dev/null 2>&1 && ok "$c $(command -v "$c")" || fail "$c nicht im PATH"
  done
  command -v py >/dev/null 2>&1 && ok "py (Python-Launcher)" || warn "py fehlt — prozessdoku/build.sh nutzt py -3.13"
  # OpenOLAT-Build-Artefakte (gitignored) — ohne sie kann das openolat-Image nicht bauen.
  if ls openolat/openolat_*.war >/dev/null 2>&1 && [ -f openolat/postgresql.jar ] && [ -f openolat/dart-sass.tar.gz ]; then
    ok "OpenOLAT-Artefakte vorhanden"
  else
    fail "OpenOLAT-Artefakte fehlen → erst: bash openolat/fetch-artifacts.sh"
  fi
  [ -f .env ] && ok ".env vorhanden" || warn ".env fehlt — Compose nutzt Defaults (.env.example)"
}

schritt_reset() {
  phase "RESET — Volumes wipen (frischer DB-/Stack-Zustand)"
  "${COMPOSE[@]}" down -v --remove-orphans || warn "down -v meldete einen Fehler (evtl. erster Lauf)"
  ok "Volumes entfernt — initdb läuft beim nächsten Start frisch"
}

schritt_up() {
  phase "UP — vollen Stack bauen & starten (--profile controlling)"
  "${COMPOSE[@]}" up -d --build || fail "docker compose up fehlgeschlagen"
  echo "   Health-Checks:"
  warte_auf "postgres (healthy)"      120 gesund postgres
  warte_auf "vendure server (healthy)" 240 gesund server
  warte_auf "integration (/q/openapi)" 180 http_ok "$API/q/openapi"
  warte_auf "keycloak (token)"        180 bash -c "curl -fsS ${RESOLVE[*]} -o /dev/null '$KC/realms/ebz-staff/.well-known/openid-configuration'"
  warte_auf "storefront (SSR)"        180 http_ok "http://localhost:${STOREFRONT_PORT:-3001}/"
  warte_auf "mdm-cockpit"             120 http_ok "http://localhost:${MDM_PORT:-5174}/"
  warte_auf "portal"                  120 http_ok "http://localhost:${PORTAL_PORT:-5175}/"
  # OpenOLAT bootet beim Erststart lange (Schema-Anlage); großzügiges Fenster.
  warte_auf "openolat (REST-API)"     420 http_antwortet "http://localhost:${OPENOLAT_PORT:-8089}/restapi/repo/entries"
  # Lightdash zieht das dbt-Projekt → kann dauern; nicht hart blockierend.
  if warte_auf_weich "lightdash" 180 http_ok "http://localhost:${LIGHTDASH_PORT:-8084}/api/v1/health"; then :; fi
  ok "Stack oben"
}

# weiche Variante: gibt bei Timeout nur eine Warnung (nicht-blockierend)
warte_auf_weich() {
  local label="$1" timeout="$2"; shift 2
  local start; start=$(date +%s)
  echo -n "   warte (weich) auf ${label} "
  while true; do
    if "$@" >/dev/null 2>&1; then echo " ${gruen}ok${aus}"; return 0; fi
    local now; now=$(date +%s)
    if (( now - start > timeout )); then echo " ${gelb}übersprungen (Timeout)${aus}"; return 1; fi
    echo -n "."; sleep 3
  done
}

schritt_seed() {
  phase "SEED — deterministische Demo-/Test-Case-Daten"
  # Startup-Seeder (Lookups, Party-Demo 'Carla Kundin' + Muster Immobilien GmbH, Kommunikation) laufen
  # bereits beim Boot des integration-Containers. Hier kommen die getriebenen Seeds dazu:

  echo "── Shop-Katalog (POST /shop/init, idempotent) ──"
  local ST; ST="$(staff_token)" || fail "Staff-Token (läuft Keycloak?)"
  [ -n "$ST" ] || fail "Staff-Token leer"
  curl -fsS -X POST -H "Authorization: Bearer $ST" "$API/shop/init" \
    | python -c "import json,sys;d=json.load(sys.stdin);print('   ',d)" || fail "/shop/init fehlgeschlagen"
  ok "Shop-Katalog initialisiert"

  echo "── Demo-Bestellungen (Vendure → Bewegungsdaten) ──"
  ( cd vendure && node scripts/seed-demo-orders.mjs ) || fail "seed-demo-orders.mjs fehlgeschlagen"
  ok "Demo-Bestellungen geseedet"

  echo "── Portal-Rechnungen (Firmen-/Privat-Beleg + ZUGFeRD) ──"
  bash integration/smoke-portal-rechnungen.sh || fail "smoke-portal-rechnungen.sh fehlgeschlagen"
  ok "Portal-Rechnungen geseedet"

  echo "── LMS-Seed-Kurse (SCORM → OpenOLAT) ──"
  # lms-fetch-testdata.sh legt die Pakete im REPO-Wurzel-testdata/scorm ab (nicht showcase/), daher ../.
  local scorm="$HIER/../testdata/scorm"
  if [ ! -d "$scorm" ] || [ -z "$(ls -A "$scorm" 2>/dev/null)" ]; then
    bash lms-fetch-testdata.sh || warn "lms-fetch-testdata.sh fehlgeschlagen (Netz?) — LMS-Import übersprungen"
  fi
  if [ -d "$scorm" ] && [ -n "$(ls -A "$scorm" 2>/dev/null)" ]; then
    bash openolat/lms-import-seed.sh || warn "lms-import-seed.sh fehlgeschlagen — Kurse evtl. nicht importiert"
    ok "LMS-Kurse importiert"
  fi

  echo "── Controlling-Warehouse (dlt: vendure → controlling) ──"
  ( cd dlt && .venv/Scripts/python vendure_to_warehouse.py ) || fail "dlt-Load fehlgeschlagen"
  ok "dlt-Load erledigt"

  echo "── Controlling-Marts (dbt build inkl. Unit-Tests) ──"
  ( cd dbt && .venv/Scripts/dbt build --profiles-dir . ) || fail "dbt build fehlgeschlagen"
  ok "dbt-Marts gebaut + Tests grün"
}

schritt_mandanten_seed() {
  phase "MANDANTEN-SEED — Test-Mandanten (M3) backend-getrieben + gebrokerter Demo-Kunden-IdP-Link"
  # Realm-Voraussetzungen (Organizations, Broker-IdP kunde-demo, mandant-/organization-Scopes, Realm
  # ebz-kunde-demo) liegen als Import-JSON in vendure/keycloak/realms/ und sind beim frischen Boot da.
  # Hier kommt die Laufzeit-Naht: EBZ_CUSTOMER/EBZ_STAFF/DEMO_AG über die Backend-API anlegen (Backend
  # projiziert OpenOLAT- + Keycloak-Org) + org<->IdP-Link. Idempotent.
  bash mandanten-seed/seed-mandanten.sh || fail "Mandanten-Seed fehlgeschlagen"
  ok "Test-Mandanten geseedet (Brokering-Strecke bereit für e2e)"
}

schritt_lms_share() {
  phase "LMS-SHARE — Content-share-once (M4): ein Repo-Entry, n org-skopierte Curricula (Storage x 1)"
  # Setzt den Content-Import (Schritt seed → lms-import-seed) UND die DEMO_AG-Org (mandanten-seed) voraus.
  # Catalog-2.0-Offers sind nicht REST-faehig → org-skopiertes Sharing via Curriculum (offizieller Weg).
  bash openolat/lms-share-seed.sh || fail "Content-share-once-Seed fehlgeschlagen"
  ok "Content-share-once geseedet (EBZ-Default-Org + DEMO_AG teilen dasselbe Nugget)"
}

schritt_lms_nachweis() {
  phase "LMS-NACHWEIS — Weiterbildungsnachweis (M6): OpenOLAT-Completion → LernleistungsFakt (Soll-Stunden)"
  # Setzt Content-Import (seed) UND ein laufendes Backend (up) voraus. Treibt rein die Backend-Endpunkte
  # (Staff-OIDC); der trackbare Nachweis-Kurs ist die REST-lesbare Completion-Quelle (Nugget unberuehrt).
  bash openolat/lms-nachweis-seed.sh || fail "Nachweis-Seam-Seed fehlgeschlagen"
  ok "Weiterbildungsnachweis geseedet (customer schliesst WBT ab → Soll-Stunden-Fakt im MDM)"
}

schritt_test_java() {
  phase "TEST — Java-Integration (Quarkus, rest-assured + E2E-Spans)"
  # Läuft gegen das laufende Postgres; die DispatcherPauseExtension pausiert die Container-@Scheduled
  # automatisch (BETRIEB_BASE_URL=localhost:8090). Erzeugt target/prozess-log/spans.jsonl (E2E-Tests).
  "$MVN" -q -f integration/pom.xml test || fail "Java-Integrationstests rot"
  ok "Java-Integrationstests grün"
}

schritt_test_spa() {
  phase "TEST — SPAs (vitest + typecheck)"
  for p in mdm portal; do
    echo "── $p ──"
    # --passWithNoTests: portal hat (bewusst) keine vitest-Specs (Abdeckung via Playwright/Backend);
    # vitest würde sonst mit Exit 1 „No test files found" abbrechen.
    ( cd "$p" && pnpm install --frozen-lockfile >/dev/null 2>&1 || pnpm install >/dev/null 2>&1; \
      pnpm exec vitest run --passWithNoTests && pnpm run typecheck ) || fail "$p Tests/Typecheck rot"
    ok "$p grün"
  done
  echo "── storefront (Production-Build = realer Compile-Gate) ──"
  # storefront hat (bewusst) keine vitest-Specs; der maßgebliche Compile-Check ist der Nuxt-
  # Production-Build (identisch zum Docker-Image). UI-Verhalten deckt zusätzlich der Playwright-
  # storefront-Spec gegen das laufende SSR ab. (`nuxt typecheck` ist hier nicht verdrahtet.)
  ( cd storefront && pnpm install --frozen-lockfile >/dev/null 2>&1 || pnpm install >/dev/null 2>&1; \
    pnpm run build ) || fail "storefront nuxt build rot"
  ok "storefront grün"
}

schritt_test_vendure() {
  phase "TEST — Vendure-Smoke-Tests (Shop-API)"
  for s in smoke-shop smoke-subscriptions smoke-rechnungslauf smoke-sso smoke-checkout-f1; do
    echo "── $s ──"
    ( cd vendure && node "scripts/$s.mjs" ) || fail "vendure/$s rot"
    ok "$s grün"
  done
}

schritt_test_e2e() {
  phase "TEST — projektweite Playwright-E2E (gegen laufenden Stack)"
  ( cd e2e && pnpm install --frozen-lockfile >/dev/null 2>&1 || pnpm install >/dev/null 2>&1; \
    pnpm run install:browser >/dev/null 2>&1 || pnpm exec playwright install chromium; \
    pnpm test ) || fail "Playwright-E2E rot"
  ok "Playwright-E2E grün"
}

schritt_bpmn() {
  phase "BPMN — Living Documentation (OTel-Spans → PM4py → showcase/docs/bpmn/)"
  bash prozessdoku/build.sh || fail "BPMN-Generierung fehlgeschlagen"
  ok "BPMN aktualisiert (showcase/docs/bpmn/)"
}

schritt_lightdash() {
  phase "LIGHTDASH — BI-Dashboard per API einrichten (Org/Projekt/Charts/Cockpit)"
  # Lightdash startet mit leerer Metadaten-DB (frisches Volume). Da Passwort-Login deaktiviert ist
  # (SSO-Zwang), fährt bootstrap.py denselben OIDC-Flow wie der Browser (Keycloak staff/staff) und
  # legt Org + Projekt (Warehouse controlling/analytics + lokales dbt) + Space + alle Auswertungen +
  # das Dashboard „EBZ Controlling-Cockpit" idempotent an. Nutzt das requests-Paket aus dem dbt-venv.
  local LD="${LIGHTDASH_URL:-http://localhost:${LIGHTDASH_PORT:-8084}}"
  warte_auf "Lightdash (${LD})" 180 bash -c \
    "curl -fsS '${LD}/api/v1/health' | grep -q '\"healthy\":true'"
  ( cd dbt && LIGHTDASH_URL="$LD" .venv/Scripts/python ../lightdash/bootstrap.py ) \
    || fail "Lightdash-Bootstrap fehlgeschlagen"
  ok "Lightdash-Dashboard eingerichtet (Cockpit sichtbar nach SSO-Login staff/staff)"
}

schritt_summary() {
  phase "FERTIG — System läuft & ist voll geseedet"
  cat <<EOF
${fett}Stack (docker compose --profile controlling):${aus}
$( "${COMPOSE[@]}" ps --format '  {{.Service}}\t{{.Status}}' 2>/dev/null )

${fett}Zugänge / Demo-URLs:${aus}
  Storefront (Shop, SSR) ..... http://localhost:${STOREFRONT_PORT:-3001}
  Vendure Admin/Dashboard .... http://localhost:3000/dashboard   (superadmin/superadmin)
  MDM-Cockpit (Staff) ........ http://localhost:${MDM_PORT:-5174}   (SSO staff/staff)
  Außenportal (Kunde) ........ http://localhost:${PORTAL_PORT:-5175}  (SSO customer/customer — Carla Kundin)
  OpenOLAT (LMS) ............. http://localhost:${OPENOLAT_PORT:-8089} (administrator/openolat; SSO customer)
  Lightdash (BI) ............. http://localhost:${LIGHTDASH_PORT:-8084} (SSO staff/staff → Dashboard „EBZ Controlling-Cockpit")
  Keycloak ................... http://localhost:8088   (admin/admin)
  Adminer (DBs) .............. http://localhost:8082   (Server postgres · vendure/controlling/lightdash)
  Mailpit (Mails) ............ http://localhost:8025
  Jaeger (Tracing) ........... http://localhost:16686
  Phoenix (LLM-Tracing) ...... http://localhost:6006

${fett}Telemetrie/BPMN:${aus} showcase/docs/bpmn/ · Live-Spans in Jaeger (Service ebz-integration)
${fett}Daten bleiben stehen.${aus} Erneuter sauberer Aufbau: bash showcase/showcase-aufbau.sh
EOF
}

# ── Runner ──────────────────────────────────────────────────────────────────────────────────────
fuehre_aus() {
  case "$1" in
    preflight)    schritt_preflight ;;
    reset)        schritt_reset ;;
    up)           schritt_up ;;
    seed)         schritt_seed ;;
    mandanten-seed) schritt_mandanten_seed ;;
    lms-share)    schritt_lms_share ;;
    lms-nachweis) schritt_lms_nachweis ;;
    test-java)    schritt_test_java ;;
    test-spa)     schritt_test_spa ;;
    test-vendure) schritt_test_vendure ;;
    test-e2e)     schritt_test_e2e ;;
    bpmn)         schritt_bpmn ;;
    lightdash)    schritt_lightdash ;;
    summary)      schritt_summary ;;
    *)            fail "Unbekannter Schritt: $1" ;;
  esac
}

main() {
  local von="" nur="" keine_reset=0
  local args=()
  while [ $# -gt 0 ]; do
    case "$1" in
      --liste)       printf 'Schritte: %s\n' "${SCHRITTE[*]}"; exit 0 ;;
      --from)        von="$2"; shift 2 ;;
      --only)        nur="$2"; shift 2 ;;
      --keine-reset) keine_reset=1; shift ;;
      -h|--help)     sed -n '2,30p' "$0"; exit 0 ;;
      *)             args+=("$1"); shift ;;
    esac
  done

  local plan=()
  if [ -n "$nur" ]; then
    plan=("$nur")
  elif [ ${#args[@]} -gt 0 ]; then
    plan=("${args[@]}")
  else
    plan=("${SCHRITTE[@]}")
    if [ -n "$von" ]; then
      local i ab=()
      local treffer=0
      for i in "${plan[@]}"; do
        [ "$i" = "$von" ] && treffer=1
        [ "$treffer" = 1 ] && ab+=("$i")
      done
      [ "$treffer" = 1 ] || fail "Schritt '$von' unbekannt"
      plan=("${ab[@]}")
    fi
  fi

  # --keine-reset filtert den reset-Schritt aus dem Plan.
  if [ "$keine_reset" = 1 ]; then
    local gefiltert=()
    for s in "${plan[@]}"; do [ "$s" = reset ] || gefiltert+=("$s"); done
    plan=("${gefiltert[@]}")
  fi

  echo "${fett}Plan:${aus} ${plan[*]}"
  for s in "${plan[@]}"; do fuehre_aus "$s"; done
  echo; echo "${gruen}${fett}✓ Lauf abgeschlossen: ${plan[*]}${aus}"
}

main "$@"
