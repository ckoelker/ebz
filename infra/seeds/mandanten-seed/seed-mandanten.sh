#!/usr/bin/env bash
#
# seed-mandanten.sh — reproduzierbarer Seed der PoC-Test-Mandanten (M3).
# ====================================================================================================
# Legt die Test-Mandanten ueber die BACKEND-API an (POST /mandant -> das integration-Backend projiziert
# die OpenOLAT- UND die Keycloak-Organization selbst) und verknuepft den geseedeten, kundenindividuellen
# Demo-IdP mit der Org. Die Realm-VORAUSSETZUNGEN (Organizations-Flag, Broker-IdP kunde-demo, mandant-/
# organization-Claim-Mapper, mandant-pflege-Rolle, Realm ebz-kunde-demo) liegen als Import-JSON in
# vendure/keycloak/realms/ und ueberleben `down -v` automatisch — hier kommt nur die LAUFZEIT-Naht dazu.
#
# Idempotent: erneuter Lauf legt nichts doppelt an. Hook fuer tools/stack.sh (Schritt `mandanten-seed`).
#
# Scoping (bewusst): KC-Org-Anlage = Backend (POST /mandant). IdP-Verknuepfung + CI/Branding = hier geseedet
# (zu kundenindividuell, NICHT in integration verdrahtet).
set -uo pipefail
HIER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

KC="${KC:-http://keycloak.localhost:8080}"
RES=(--resolve keycloak.localhost:8080:127.0.0.1)
API="${API:-http://localhost:8090}"
KCADMIN="${KCADMIN:-bash $HIER/../../../services/vendure/keycloak/kc.sh}"

rot=$'\e[31m'; gruen=$'\e[32m'; aus=$'\e[0m'
ok(){ echo "${gruen}✓${aus} $*"; }
fail(){ echo "${rot}✗ $*${aus}" >&2; exit 1; }

jload() { python -c "import sys,json
try: d=json.load(sys.stdin)
except Exception: print(''); sys.exit()
$1"; }

staff_token() {
  curl -s "${RES[@]}" -X POST "$KC/realms/ebz-staff/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=staff-frontend -d username=staff -d password=staff \
    | jload "print(d.get('access_token',''))"
}

# Legt einen Mandanten (idempotent) + optional eine IdP-Foederation an, projiziert die Keycloak-Org und
# verknuepft den IdP mit der Org. Args: schluessel anzeigeName vertragstyp primaerFarbe sekundaerFarbe idpAlias domains
seed_mandant() {
  local schluessel="$1" name="$2" typ="$3" pf="$4" sf="$5" idp="${6:-}" domains="${7:-}"
  local AUTH="Authorization: Bearer $ST" CT="Content-Type: application/json"

  local mid
  mid=$(curl -s -H "$AUTH" "$API/mandant" | jload "print(next((m['id'] for m in d if m['schluessel']=='$schluessel'),''))")
  if [ -z "$mid" ]; then
    mid=$(curl -s -H "$AUTH" -H "$CT" -X POST "$API/mandant" \
      -d "{\"schluessel\":\"$schluessel\",\"anzeigeName\":\"$name\",\"vertragstyp\":\"$typ\",\"status\":\"AKTIV\",\"primaerFarbe\":\"$pf\",\"sekundaerFarbe\":\"$sf\",\"logoUrl\":\"/branding/${schluessel,,}/logo.svg\"}" \
      | jload "print(d.get('id',''))")
    [ -n "$mid" ] || fail "Mandant $schluessel konnte nicht angelegt werden (Backend erreichbar? staff mandant-pflege?)"
    ok "Mandant $schluessel angelegt (id $mid)"
  else
    ok "Mandant $schluessel existiert (id $mid)"
  fi

  # B2B mit IdP-Foederation: OpenOLAT-Org + Foederation + Keycloak-Org-Projektion + org<->IdP-Link.
  if [ -n "$idp" ] && [ -n "$domains" ]; then
    # OpenOLAT-Org (M2): Seat-/Mitglieder-Container + per-Org-Anker (cssClass). Backend-getrieben.
    curl -s -o /dev/null -H "$AUTH" -X POST "$API/mandant/$mid/projizieren"
    local olk=""
    for _ in $(seq 1 12); do
      olk=$(curl -s -H "$AUTH" "$API/mandant/$mid" | jload "print(d.get('openolatOrganisationKey') or '')")
      [ -n "$olk" ] && break; sleep 3
    done
    [ -n "$olk" ] && ok "OpenOLAT-Org projiziert (Backend): key $olk" || ok "OpenOLAT-Org-Projektion angefordert"

    local hasf
    hasf=$(curl -s -H "$AUTH" "$API/mandant/$mid/foederationen" | jload "print(len(d))")
    if [ "$hasf" = "0" ] || [ -z "$hasf" ]; then
      curl -s -o /dev/null -H "$AUTH" -H "$CT" -X POST "$API/mandant/$mid/foederationen" \
        -d "{\"idpAlias\":\"$idp\",\"emailDomains\":\"$domains\",\"protokoll\":\"OIDC\",\"status\":\"AKTIV\"}"
      ok "IdP-Foederation $idp ($domains) angelegt"
    fi
    curl -s -o /dev/null -H "$AUTH" -X POST "$API/mandant/$mid/keycloak-projizieren"
    local kid=""
    for _ in $(seq 1 12); do
      kid=$(curl -s -H "$AUTH" "$API/mandant/$mid" | jload "print(d.get('keycloakOrganizationId') or '')")
      [ -n "$kid" ] && break; sleep 3
    done
    [ -n "$kid" ] || fail "Keycloak-Org fuer $schluessel nicht projiziert (Dispatcher? Organizations enabled?)"
    ok "Keycloak-Org projiziert (Backend): $kid"
    # org<->IdP-Link (idempotent: 204 oder 409 wenn schon verknuepft)
    printf '%s' "\"$idp\"" > /tmp/idplink.json
    $KCADMIN POST "/admin/realms/ebz-customers/organizations/$kid/identity-providers" /tmp/idplink.json >/dev/null 2>&1 || true
    ok "org<->IdP-Link $idp <-> $schluessel gesetzt"
  fi
}

main() {
  command -v curl >/dev/null || fail "curl fehlt"
  ST="$(staff_token)"; [ -n "$ST" ] || fail "Staff-Token leer (laeuft Keycloak? Issuer keycloak.localhost:8080?)"
  ok "Staff-Token geholt"

  # EBZ-Kernmandanten (B2C/Intern) — kein IdP, kein Seat-Limit, Default-/EBZ-Branding.
  seed_mandant "EBZ_CUSTOMER" "EBZ (Shop-Kunden)"     "EBZ_CUSTOMER" "#e6007e" "#6f2da8"
  seed_mandant "EBZ_STAFF"    "EBZ (Mitarbeiter)"     "EBZ_STAFF"    "#6f2da8" "#e6007e"
  # B2B-Demo-Mandant — gebrokerter Kunden-IdP (geseedet) + kontrastreiche CI.
  seed_mandant "DEMO_AG"      "DEMO AG"               "ENTERPRISE_FLAT" "#ff6600" "#003366" "kunde-demo" "demo-ag.de"

  echo "${gruen}✓ Mandanten-Seed abgeschlossen.${aus}"
}
main "$@"
