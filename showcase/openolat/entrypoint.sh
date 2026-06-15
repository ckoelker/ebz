#!/usr/bin/env bash
# Schreibt die OpenOLAT-Konfiguration (olat.local.properties) + die JNDI-Datasource (ROOT-Context)
# aus Umgebungsvariablen und startet Tomcat. So bleibt das Image konfigurierbar (DB/Server/SMTP).
set -e

: "${DB_HOST:=postgres}"; : "${DB_PORT:=5432}"; : "${DB_NAME:=openolat}"
: "${DB_USER:=openolat}"; : "${DB_PASS:=openolat}"
: "${OLAT_SERVER_DOMAIN:=localhost}"; : "${OLAT_SERVER_PORT:=8089}"
: "${OLAT_CONTEXTPATH:=}"
: "${SMTP_HOST:=mailpit}"; : "${SMTP_PORT:=1025}"
: "${RESTAPI_ENABLED:=true}"
# Keycloak-OIDC (Login-Übergabe aus dem Portal). context MUSS leer sein (Keycloak-Quarkus ohne /auth).
: "${OIDC_ENABLED:=true}"
: "${KC_ENDPOINT:=http://localhost:8088}"; : "${KC_CONTEXT:=}"
: "${KC_REALM:=ebz-customers}"
: "${KC_CLIENT_ID:=openolat}"; : "${KC_CLIENT_SECRET:=openolat-dev-secret}"
: "${KC_BACKEND:=keycloak:8080}"

# Back-Channel-Bridge: OpenOLAT (Java) ruft den Issuer KC_ENDPOINT auf. Damit Browser UND Server
# DENSELBEN Issuer nutzen können (localhost:8088 = euer kanonischer ebz-customers-Issuer), leitet
# ein lokaler socat 127.0.0.1:<port> an den Keycloak-Container weiter. Grund: glibc ≥2.34 verdrahtet
# *.localhost fest auf Loopback (ignoriert /etc/hosts) → keycloak.localhost aus dem Container unbrauchbar.
KC_PORT="$(echo "$KC_ENDPOINT" | sed -E 's#^https?://[^/]*:([0-9]+).*#\1#')"; : "${KC_PORT:=80}"
case "$KC_ENDPOINT" in
  *localhost*) socat TCP4-LISTEN:${KC_PORT},bind=127.0.0.1,fork,reuseaddr TCP4:${KC_BACKEND} &
              echo "socat back-channel: 127.0.0.1:${KC_PORT} -> ${KC_BACKEND}" ;;
esac

# JNDI-Datasource für den ROOT-Context (OpenOLAT web.xml referenziert jdbc/openolatDS).
mkdir -p "$CATALINA_HOME/conf/Catalina/localhost"
cat > "$CATALINA_HOME/conf/Catalina/localhost/ROOT.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<Context>
  <Resource name="jdbc/openolatDS" auth="Container" type="javax.sql.DataSource"
            driverClassName="org.postgresql.Driver"
            url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
            username="${DB_USER}" password="${DB_PASS}"
            maxTotal="20" maxIdle="10" maxWaitMillis="10000"
            testOnBorrow="true" validationQuery="SELECT 1" />
</Context>
EOF

# OpenOLAT liest olat.local.properties vom Classpath ($CATALINA_HOME/lib).
cat > "$CATALINA_HOME/lib/olat.local.properties" <<EOF
db.source=jndi
db.jndi=java:comp/env/jdbc/openolatDS
db.vendor=postgresql
installation.dir=/opt/openolat
userdata.dir=/opt/openolat/olatdata
log.dir=/opt/openolat/olatdata/logs
server.domainname=${OLAT_SERVER_DOMAIN}
server.port=${OLAT_SERVER_PORT}
server.port.ssl=0
server.contextpath=${OLAT_CONTEXTPATH}
server.modjk.enabled=false
smtp.host=${SMTP_HOST}
smtp.port=${SMTP_PORT}
# ── Keycloak-OIDC-SSO (Realm ebz-customers) ──
oauth.keycloak.enabled=${OIDC_ENABLED}
oauth.keycloak.client.id=${KC_CLIENT_ID}
oauth.keycloak.client.secret=${KC_CLIENT_SECRET}
oauth.keycloak.endpoint=${KC_ENDPOINT}
oauth.keycloak.context=${KC_CONTEXT}
oauth.keycloak.realm=${KC_REALM}
oauth.registration.allowUserCreation=true
# REST-API (Provisionierung Einschreibung/Kurse über das integration-/Outbox-Muster)
restapi.enable=${RESTAPI_ENABLED}
EOF

echo "OpenOLAT-Konfiguration geschrieben (DB ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}). Starte Tomcat…"
exec catalina.sh run
