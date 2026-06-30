#!/usr/bin/env bash
#
# Host-Dev-Mode für den integration-Service mit Live-Reload (Quarkus).
# Java ändern → speichern → beim nächsten HTTP-Request kompiliert Quarkus nur die geänderte Klasse
# (sub-Sekunde). Kein `mvn package`, kein Neustart. Ersetzt den langsamen Voll-Build-pro-Change-Zyklus.
#
# Voraussetzung:  nur Postgres aus dem Stack muss laufen (NICHT der integration-Container) —
#                   docker compose --profile controlling up -d postgres
#                 Den integration-Container ggf. stoppen, sonst Port-8090-Konflikt:
#                   docker compose stop integration
#
# Aufruf:   bash showcase/integration/dev.sh          # Mock-Senke (kein echtes HubSpot)
#           bash showcase/integration/dev.sh real      # Real-Modus gegen echtes HubSpot
#                                                       #   (liest HUBSPOT_TOKEN aus showcase/.env)
#
# Danach:   Dev-UI   http://localhost:8090/q/dev   (Beans/Config/Scheduler-Introspektion)
#           Tests    in der Konsole `r` (Continuous Testing) statt `mvn test`
#
set -uo pipefail

HIER="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DATEI="$HIER/../../.env"

# showcase/.env laden (HUBSPOT_TOKEN etc.), ohne die Datei zu committen.
if [[ -f "$ENV_DATEI" ]]; then
  set -a; . "$ENV_DATEI"; set +a
fi

MODUS="${1:-mock}"
if [[ "$MODUS" == "real" ]]; then
  if [[ -z "${HUBSPOT_TOKEN:-}" ]]; then
    echo "FEHLER: real-Modus, aber HUBSPOT_TOKEN ist nicht gesetzt (showcase/.env)." >&2
    exit 1
  fi
  export HUBSPOT_SYNC_MODE=real
  echo ">> integration dev (REAL — echtes HubSpot, Token aus .env)"
else
  export HUBSPOT_SYNC_MODE=mock
  echo ">> integration dev (mock — kein echtes HubSpot)"
fi

# DB-Default ist localhost:6543 (compose-Postgres) bereits in application.properties.
exec mvn -f "$HIER/pom.xml" quarkus:dev
