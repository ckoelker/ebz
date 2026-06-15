#!/bin/bash
# Single Source of Truth: EIN Postgres-Container hält alle DBs (vgl. 01-controlling-db.sh).
# Läuft nur beim ERST-Init eines leeren Daten-Volumes. Legt für OpenOLAT (LMS-Anbindung L0) an:
#   - Rolle + DB `openolat` (eigener User; OpenOLAT legt sein Schema beim Erststart selbst an)
# Bei BESTEHENDEM Volume wird dies NICHT ausgeführt → DB einmalig manuell anlegen
# (siehe lms-anbindung-planung/L1-Code-Plan-LMS-Anbindung.md §3).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  DO \$\$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${OPENOLAT_USER}') THEN
      CREATE ROLE "${OPENOLAT_USER}" LOGIN PASSWORD '${OPENOLAT_PASSWORD}';
    END IF;
  END \$\$;

  SELECT 'CREATE DATABASE "${OPENOLAT_DB}" OWNER "${OPENOLAT_USER}"'
   WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${OPENOLAT_DB}')\gexec

  ALTER DATABASE "${OPENOLAT_DB}" OWNER TO "${OPENOLAT_USER}";
EOSQL

# PG15+: DB-Owner besitzt das public-Schema nicht automatisch (kein CREATE) → übertragen,
# damit OpenOLAT seine Tabellen beim Erststart anlegen kann.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${OPENOLAT_DB}" \
  -c "ALTER SCHEMA public OWNER TO \"${OPENOLAT_USER}\";"

echo "initdb: DB 'openolat' + User '${OPENOLAT_USER}' sichergestellt."
