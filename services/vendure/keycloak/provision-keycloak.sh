#!/usr/bin/env bash
# Idempotentes Keycloak-Post-Import-Provisioning für den Realm ebz-customers.
#
# Hängt den SMS-OTP-Authenticator (SPI in providers/keycloak-sms-otp.jar) als REQUIRED-Schritt in den
# Registrierungs-Flow. Eingebaute Flows sind read-only → wir KOPIEREN "registration" nach
# "registration-sms", ergänzen den SMS-Schritt und binden die Kopie als Realm-Registrierungs-Flow.
# Bewusst NICHT in der Realm-JSON: ein partielles authenticationFlows-Array würde die autogenerierten
# Default-Flows (Browser/Login!) verdrängen.
#
# Muss nach jedem Neu-Erstellen des keycloak-Containers laufen (start-dev nutzt flüchtiges H2 →
# Realm wird frisch importiert). Aufruf:  bash provision-keycloak.sh
set -euo pipefail

KC="${KEYCLOAK_URL:-http://localhost:8088}"
REALM="${REALM:-ebz-customers}"
ADMIN="${KEYCLOAK_ADMIN:-admin}"
PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
PROVIDER="sms-otp-authenticator"
NEWFLOW="registration-sms"

echo "→ warte auf Keycloak ($KC) ..."
for i in $(seq 1 60); do
  if curl -sf "$KC/realms/master/.well-known/openid-configuration" >/dev/null 2>&1; then break; fi
  sleep 2
done

AT=$(curl -s -d grant_type=password -d client_id=admin-cli -d "username=$ADMIN" -d "password=$PASS" \
  "$KC/realms/master/protocol/openid-connect/token" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "$AT" ] || { echo "FEHLER: kein Admin-Token"; exit 1; }
AH=(-H "Authorization: Bearer $AT")
BASE="$KC/admin/realms/$REALM"

current_reg_flow() {
  curl -s "${AH[@]}" "$BASE" | python -c "import sys,json;print(json.load(sys.stdin).get('registrationFlow',''))"
}

if [ "$(current_reg_flow)" = "$NEWFLOW" ]; then
  echo "✓ Registrierungs-Flow bereits '$NEWFLOW' (SMS aktiv) — nichts zu tun."
  exit 0
fi

# 1) Kopie des Registrierungs-Flows anlegen (falls noch nicht vorhanden).
if ! curl -s "${AH[@]}" "$BASE/authentication/flows" | grep -q "\"$NEWFLOW\""; then
  echo "→ kopiere 'registration' → '$NEWFLOW' ..."
  curl -s -o /dev/null -X POST "${AH[@]}" -H "Content-Type: application/json" \
    -d "{\"newName\":\"$NEWFLOW\"}" "$BASE/authentication/flows/registration/copy"
fi

# 2) SMS-Schritt zur Kopie hinzufügen (falls fehlt) und auf REQUIRED setzen.
execs() { curl -s "${AH[@]}" "$BASE/authentication/flows/$NEWFLOW/executions"; }
if ! execs | grep -q "\"$PROVIDER\""; then
  echo "→ füge $PROVIDER hinzu ..."
  curl -s -o /dev/null -X POST "${AH[@]}" -H "Content-Type: application/json" \
    -d "{\"provider\":\"$PROVIDER\"}" "$BASE/authentication/flows/$NEWFLOW/executions/execution"
fi
EXEC=$(execs | python -c "import sys,json;print(next((json.dumps(e) for e in json.load(sys.stdin) if e.get('providerId')=='$PROVIDER'),''))")
[ -n "$EXEC" ] || { echo "FEHLER: SMS-Execution nicht gefunden"; exit 1; }
REQ=$(printf '%s' "$EXEC" | python -c "import sys,json;e=json.load(sys.stdin);e['requirement']='REQUIRED';print(json.dumps(e))")
curl -s -o /dev/null -X PUT "${AH[@]}" -H "Content-Type: application/json" \
  -d "$REQ" "$BASE/authentication/flows/$NEWFLOW/executions"

# 3) Kopie als Registrierungs-Flow des Realms binden (volle Realm-Rep lesen, Feld setzen, zurückschreiben).
echo "→ binde '$NEWFLOW' als Registrierungs-Flow ..."
curl -s "${AH[@]}" "$BASE" \
  | python -c "import sys,json;r=json.load(sys.stdin);r['registrationFlow']='$NEWFLOW';print(json.dumps(r))" \
  > /tmp/ebz-realm.json
curl -s -o /dev/null -X PUT "${AH[@]}" -H "Content-Type: application/json" --data-binary @/tmp/ebz-realm.json "$BASE"

[ "$(current_reg_flow)" = "$NEWFLOW" ] && echo "✓ SMS-OTP im Registrierungs-Flow aktiv." || { echo "FEHLER: Bindung nicht übernommen"; exit 1; }
