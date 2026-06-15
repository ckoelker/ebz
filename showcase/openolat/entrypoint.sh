#!/usr/bin/env bash
# Schreibt die OpenOLAT-Konfiguration (olat.local.properties) + die JNDI-Datasource (ROOT-Context)
# aus Umgebungsvariablen und startet Tomcat. So bleibt das Image konfigurierbar (DB/Server/SMTP).
set -e

: "${DB_HOST:=postgres}"; : "${DB_PORT:=5432}"; : "${DB_NAME:=openolat}"
: "${DB_USER:=openolat}"; : "${DB_PASS:=openolat}"
: "${OLAT_SERVER_DOMAIN:=localhost}"; : "${OLAT_SERVER_PORT:=8089}"
: "${OLAT_CONTEXTPATH:=}"
: "${SMTP_HOST:=mailpit}"; : "${SMTP_PORT:=1025}"

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
EOF

echo "OpenOLAT-Konfiguration geschrieben (DB ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}). Starte Tomcat…"
exec catalina.sh run
