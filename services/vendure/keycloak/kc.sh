#!/usr/bin/env bash
# Keycloak-Admin-Helfer (Dev): holt ein master/admin-cli-Token und ruft die Admin-REST — als EIN
# allowlist-fähiges Kommando (kein TOKEN=$(curl …)-Wrapper, der die curl-Whitelist aushebelt).
#
#   bash services/vendure/keycloak/kc.sh <METHODE> <PFAD> [JSON-DATEI]
#   bash services/vendure/keycloak/kc.sh GET  /admin/realms/ebz-customers/organizations
#   bash services/vendure/keycloak/kc.sh POST /admin/realms/ebz-customers/organizations org.json
#   bash services/vendure/keycloak/kc.sh PUT  /admin/realms/ebz-customers realm.json
#
# Env-Overrides: KC (Default http://localhost:8088), KC_ADMIN (admin), KC_ADMIN_PW (admin).
set -euo pipefail
KC="${KC:-http://localhost:8088}"
M="${1:?Methode fehlt}"; P="${2:?Pfad fehlt}"; DATA="${3:-}"
TOKEN=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  -d username="${KC_ADMIN:-admin}" -d password="${KC_ADMIN_PW:-admin}" \
  | python -c "import json,sys;print(json.load(sys.stdin)['access_token'])")
if [ -n "$DATA" ]; then
  # Inhalt inline lesen (statt @pfad) — natives Windows-curl liest msys-Pfade wie /c/... nicht.
  curl -s -w '\n[HTTP %{http_code}]\n' -X "$M" "$KC$P" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data-binary "$(cat "$DATA")"
else
  curl -s -w '\n[HTTP %{http_code}]\n' -X "$M" "$KC$P" -H "Authorization: Bearer $TOKEN"
fi
