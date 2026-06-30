#!/usr/bin/env bash
#
# Smoke-Test + reproduzierbarer Demo-Seed: Kunden-Rechnungsabruf im Außenportal (party/portal).
# Ersetzt den früheren Ad-hoc-curl-Seed durch ein versioniertes, wiederholbares Skript.
#
# Läuft gegen den laufenden Showcase-Stack (Profil `controlling`): seedt eine festgeschriebene
# Firmen-Rechnung für den Demo-Kunden `customer@ebz.de` und verifiziert dann den kompletten
# Portal-Flow mit ECHTEN OIDC-Tokens — Staff-Token (Realm ebz-staff) für den Aufbau, Customer-Token
# (Realm ebz-customers) für den Abruf. Prüft damit auch die Tenant-Trennung (customers vs. staff).
#
# Voraussetzung:  docker compose --profile controlling up -d   (integration:8090, keycloak:8080)
# Aufruf:         bash showcase/integration/smoke-portal-rechnungen.sh
# Ergebnis:       Exit 0 = PASS, sonst FAIL. Re-run-fähig (jeder Lauf legt eine frische Org an).
#
# Hinweis Browser-Test danach: Portal http://localhost:5175 → Login customer/customer (Carla Kundin)
# → „Meine Rechnungen" → Firmenkontext zeigt die geseedete Rechnung inkl. ZUGFeRD-PDF.
#
set -uo pipefail

API="${API:-http://localhost:8090}"
KC="${KC:-http://keycloak.localhost:8080}"
# curl muss keycloak.localhost erreichen, damit der Token-Issuer zu dem passt, den das Backend
# validiert. Browser/Container lösen *.localhost nativ; hier erzwingen wir es plattformneutral.
RESOLVE=(--resolve "keycloak.localhost:8080:127.0.0.1")

CUST_EMAIL="${CUST_EMAIL:-customer@ebz.de}"   # = Login customer/customer; Portal claimt diese Person
CUST_NAME="${CUST_NAME:-Carla Kundin}"

fail() { echo "❌ FAIL: $*" >&2; exit 1; }
ok()   { echo "✅ $*"; }
jget() { python -c "import json,sys;d=json.load(sys.stdin);print($1)"; }  # python = reiner Parser

# ─────────────────────────── 1) Aufbau mit Staff-Token ───────────────────────────
ST=$(curl -s "${RESOLVE[@]}" -X POST "$KC/realms/ebz-staff/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=staff-frontend -d username=staff -d password=staff \
  | jget "d['access_token']") || fail "Staff-Token (läuft der Stack? keycloak erreichbar?)"
[ -n "${ST:-}" ] || fail "Staff-Token leer"
SH="Authorization: Bearer $ST"; JS="Content-Type: application/json"
ok "Staff-Token geholt"

N=$RANDOM
SJ="88$(printf %02d $((N % 90)))/88$(printf %02d $(((N % 90)+1)))"   # frisches 4/4-Schuljahr

ORG=$(curl -s -H "$SH" -H "$JS" -X POST "$API/party/organisationen" \
  -d "{\"name\":\"Demo Bau GmbH (Smoke) $N\",\"plz\":\"45657\",\"ort\":\"Recklinghausen\",\"land\":\"DE\",\"ustId\":\"DE9$((N+100000))\"}" \
  | jget "d['id']") || fail "Organisation anlegen"
ok "Organisation $ORG"

BEST=$(curl -s -H "$SH" -H "$JS" -X POST "$API/party/organisationen/$ORG/teilnehmer" \
  -d "{\"email\":\"$CUST_EMAIL\",\"anzeigeName\":\"$CUST_NAME\",\"rolle\":\"AUSBILDER\",\"buchungsberechtigt\":true}" \
  | jget "d['id']") || fail "Besteller (Carla) anlegen"
ok "Besteller/Carla $BEST (E-Mail $CUST_EMAIL, buchungsberechtigt)"

AZ=$(curl -s -H "$SH" -H "$JS" -X POST "$API/party/organisationen/$ORG/teilnehmer" \
  -d "{\"email\":\"azubi-smoke+$N@firma.de\",\"anzeigeName\":\"Azubi Smoke\",\"rolle\":\"AZUBI\",\"buchungsberechtigt\":false}" \
  | jget "d['id']") || fail "Azubi anlegen"
ok "Azubi $AZ"

BST=$(curl -s -o /dev/null -w '%{http_code}' -H "$SH" -H "$JS" -X POST "$API/party/buchungen/berufsschule" \
  -d "{\"teilnehmerPersonId\":$AZ,\"bestellerPersonId\":$BEST,\"kontextOrganisationId\":$ORG,\"schuljahr\":\"$SJ\",\"halbjahr\":1,\"zimmerart\":\"KEINE\",\"unterrichtBetragCent\":150000}")
[ "$BST" = "201" ] || fail "Buchung berufsschule (HTTP $BST)"
ok "Berufsschul-Buchung ($SJ)"

RID=$(curl -s -H "$SH" -H "$JS" -X POST "$API/rechnung/laeufe" -d "{\"schuljahr\":\"$SJ\",\"halbjahr\":1}" \
  | jget "max(r['id'] for r in d if r['status']=='ENTWURF')") || fail "Rechnungslauf"
NUMMER=$(curl -s -H "$SH" -H "$JS" -X POST "$API/rechnung/rechnungen/$RID/ausstellen" \
  | jget "d['nummer'] if d['status']=='AUSGESTELLT' else (_ for _ in ()).throw(SystemExit('nicht AUSGESTELLT'))") \
  || fail "Ausstellen (Rechnung $RID)"
ok "Firmen-Rechnung festgeschrieben: $NUMMER (Entwurf $RID)"

# Shop-Bestellung DERSELBEN Person als Selbstzahler (Zahlungsart RECHNUNG, KEIN Org-Kontext → privat).
# Käufer per E-Mail = dieselbe Identität wie der Portal-Login; der private Debitor wird projiziert.
SHOP_RID=$(curl -s -H "$SH" -H "$JS" -X POST "$API/party/quellen/shop-bestellung" \
  -d "{\"quelle\":\"SHOP-SMOKE\",\"externeId\":\"smoke-shop-$N\",\"zahlungsart\":\"RECHNUNG\",\"bereich\":\"SHOP\",\"kaeuferEmail\":\"$CUST_EMAIL\",\"kaeuferName\":\"$CUST_NAME\",\"positionen\":[{\"beschreibung\":\"Fachbuch Immobilienbewertung\",\"betragCent\":4990,\"steuerfall\":\"STANDARD\",\"steuersatz\":19}]}" \
  | jget "d['rechnungId']") || fail "Shop-Bestellung (privat, auf Rechnung)"
SHOP_NUMMER=$(curl -s -H "$SH" -H "$JS" -X POST "$API/rechnung/rechnungen/$SHOP_RID/ausstellen" \
  | jget "d['nummer'] if d['status']=='AUSGESTELLT' else (_ for _ in ()).throw(SystemExit('nicht AUSGESTELLT'))") \
  || fail "Shop-Beleg ausstellen (Rechnung $SHOP_RID)"
ok "Shop-Bestellung als Rechnung festgeschrieben: $SHOP_NUMMER (Selbstzahler/privat, Entwurf $SHOP_RID)"

# ─────────────────────────── 2) Abruf mit Customer-Token ───────────────────────────
CT=$(curl -s "${RESOLVE[@]}" -X POST "$KC/realms/ebz-customers/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=ebz-portal -d username=customer -d password=customer \
  | jget "d['access_token']") || fail "Customer-Token"
[ -n "${CT:-}" ] || fail "Customer-Token leer"
CH="Authorization: Bearer $CT"
ok "Customer-Token geholt"

LOGIN=$(curl -s -o /dev/null -w '%{http_code}' -H "$CH" -H "$JS" -X POST "$API/party/personen/login" \
  -d "{\"email\":\"$CUST_EMAIL\",\"anzeigeName\":\"$CUST_NAME\"}")
[ "$LOGIN" = "200" ] || fail "Portal-Login/Claim (HTTP $LOGIN — Tenant 'customers' verdrahtet?)"
ok "Portal-Login (Person geclaimt)"

# Tenant-Trennung: Staff-Token darf NICHT auf die Portal-Endpunkte (erwartet 401/403, nie 200-Liste)
SX=$(curl -s -o /dev/null -w '%{http_code}' -H "$SH" "$API/party/portal/rechnungs-kontexte")
[ "$SX" = "200" ] && fail "Tenant-Trennung verletzt: Staff-Token bekommt Portal-Kontexte (HTTP 200)"
ok "Tenant-Trennung ok (Staff-Token auf Portal → HTTP $SX)"

KONTEXTE=$(curl -s -H "$CH" "$API/party/portal/rechnungs-kontexte")
echo "$KONTEXTE" | jget "1 if any(k['art']=='PRIVAT' for k in d) else (_ for _ in ()).throw(SystemExit('kein PRIVAT-Kontext'))" >/dev/null \
  || fail "Kontexte: PRIVAT fehlt"
echo "$KONTEXTE" | jget "1 if any(k.get('organisationId')==$ORG for k in d) else (_ for _ in ()).throw(SystemExit('Firmenkontext fehlt'))" >/dev/null \
  || fail "Kontexte: Firmenkontext $ORG fehlt"
ok "Kontexte: PRIVAT + Firma $ORG"

BELEGE=$(curl -s -H "$CH" "$API/party/portal/rechnungen?organisationId=$ORG")
echo "$BELEGE" | jget "next(r for r in d if r['nummer']=='$NUMMER' and r['status']=='AUSGESTELLT')" >/dev/null \
  || fail "Firmenbeleg $NUMMER nicht im Firmenkontext sichtbar"
echo "$BELEGE" | jget "(_ for _ in ()).throw(SystemExit('Shop-Beleg im Firmenkontext!')) if any(r['nummer']=='$SHOP_NUMMER' for r in d) else 1" >/dev/null \
  || fail "Datentrennung: Shop-/Privatbeleg taucht im Firmenkontext auf"
ok "Firmenkontext: $NUMMER sichtbar, Shop-Beleg nicht"

# Privatkontext (Selbstzahler): zeigt die Shop-Rechnung, NICHT den Firmenbeleg
PRIV=$(curl -s -H "$CH" "$API/party/portal/rechnungen")
echo "$PRIV" | jget "next(r for r in d if r['nummer']=='$SHOP_NUMMER' and r['status']=='AUSGESTELLT')" >/dev/null \
  || fail "Shop-Rechnung $SHOP_NUMMER nicht im Privatkontext sichtbar"
echo "$PRIV" | jget "(_ for _ in ()).throw(SystemExit('Firmenbeleg im Privatkontext!')) if any(r['nummer']=='$NUMMER' for r in d) else 1" >/dev/null \
  || fail "Datentrennung: Firmenbeleg taucht im Privatkontext auf"
ok "Privatkontext: Shop-Rechnung $SHOP_NUMMER sichtbar, Firmenbeleg nicht"

CTYPE=$(curl -s -o /tmp/smoke-rechnung.pdf -w '%{content_type}' -H "$CH" "$API/party/portal/rechnungen/$RID/zugferd")
case "$CTYPE" in application/pdf*) ;; *) fail "PDF-Download Content-Type=$CTYPE" ;; esac
head -c4 /tmp/smoke-rechnung.pdf | grep -q '%PDF' || fail "PDF-Download kein gültiges PDF"
ok "ZUGFeRD-PDF geladen ($(wc -c </tmp/smoke-rechnung.pdf) Bytes)"

echo ""
echo "🎉 PASS — Portal-Rechnungsabruf end-to-end ok (Firma $NUMMER, privat $SHOP_NUMMER)."
echo "    Browser-Test: http://localhost:5175 -> Anmelden -> customer / customer -> Meine Rechnungen."
echo "    Firmenkontext Demo Bau GmbH (Smoke) $N -> $NUMMER (1.500,00 EUR);"
echo "    Privatkontext (Selbstzahler) -> Shop-Rechnung $SHOP_NUMMER (49,90 EUR); beide inkl. ZUGFeRD-PDF."
