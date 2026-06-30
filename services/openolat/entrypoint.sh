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
# Endpoint = keycloak.localhost:8080 = DERSELBE Issuer-Host wie Portal/Shop → der Browser teilt die
# Keycloak-SSO-Session (Cookie ist domain-gebunden) → nahtlose Übergabe ohne erneuten Login. Dank
# glibc-2.35-Basisimage (Dockerfile) löst keycloak.localhost auch SERVER-seitig den Compose-Alias auf
# (keine socat-Bridge nötig). OIDC_ROOT=true: OpenOLAT leitet unauthentifizierte Aufrufe direkt zu
# Keycloak (Auto-Redirect), statt die DMZ-Loginseite zu zeigen.
: "${OIDC_ENABLED:=true}"
: "${OIDC_ROOT:=true}"
: "${KC_ENDPOINT:=http://keycloak.localhost:8080}"; : "${KC_CONTEXT:=}"
: "${KC_REALM:=ebz-customers}"
: "${KC_CLIENT_ID:=openolat}"; : "${KC_CLIENT_SECRET:=openolat-dev-secret}"
# Eigenes SCSS-Theme: wird AUSSERHALB der .war aus olatdata/customizing/themes geladen.
: "${OLAT_THEME:=ebz}"

# Theme-Seed (im Image unter themes-seed/ kompiliert) in das per-Volume persistierte
# olatdata/customizing/themes kopieren — also AUSSERHALB der .war. OpenOLAT lädt Themes von
# dort dank layout.custom.themes.dir (s.u.). Bei jedem Start frisch überschrieben, damit ein
# Rebuild des Themes sofort greift; vorhandene andere Custom-Themes bleiben unberührt.
THEMES_DIR="/opt/openolat/olatdata/customizing/themes"
if [ -d /opt/openolat/themes-seed ]; then
  mkdir -p "$THEMES_DIR"
  for seed in /opt/openolat/themes-seed/*/; do
    [ -d "$seed" ] || continue
    name="$(basename "$seed")"
    rm -rf "$THEMES_DIR/$name"
    cp -r "$seed" "$THEMES_DIR/$name"
    echo "Custom-Theme '$name' nach $THEMES_DIR/$name kopiert (außerhalb der .war)."
  done
fi

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
# ── Eigenes SCSS-Theme, AUSSERHALB der .war geladen ──
# layout.custom.themes.dir zeigt auf das per-Volume persistierte olatdata; der Entrypoint
# hat das kompilierte Theme dorthin kopiert. layout.theme wählt es aus (statt 'openolat').
layout.theme=${OLAT_THEME}
layout.custom.themes.dir=/opt/openolat/olatdata/customizing/themes
layout.theme.values=light,openolat,${OLAT_THEME}
# ── Keycloak-OIDC-SSO (Realm ebz-customers) ──
oauth.keycloak.enabled=${OIDC_ENABLED}
oauth.keycloak.root=${OIDC_ROOT}
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
