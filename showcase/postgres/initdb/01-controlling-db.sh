#!/bin/bash
# Single Source of Truth: EIN Postgres-Container hält alle DBs.
# Dieses Script läuft nur beim ERST-Init eines leeren Daten-Volumes (Postgres-Entrypoint),
# als Superuser $POSTGRES_USER, verbunden mit der Shop-DB $POSTGRES_DB.
# Legt zusätzlich an (idempotent):
#   - Rolle + DB `controlling` (Warehouse, Owner = eigener User)
#   - Rolle + DB `lightdash`   (BI-Metadaten, Owner = eigener User)
#   - Read-only-Rolle für dlt auf der Shop-DB (L20: Quelle nur lesen)
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  -- Rollen (mit Login) anlegen, falls fehlen
  DO \$\$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${CONTROLLING_USER}') THEN
      CREATE ROLE "${CONTROLLING_USER}" LOGIN PASSWORD '${CONTROLLING_PASSWORD}';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${LIGHTDASH_USER}') THEN
      CREATE ROLE "${LIGHTDASH_USER}" LOGIN PASSWORD '${LIGHTDASH_PASSWORD}';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${READER_USER}') THEN
      CREATE ROLE "${READER_USER}" LOGIN PASSWORD '${READER_PASSWORD}';
    END IF;
  END \$\$;

  -- Datenbanken anlegen, falls fehlen (CREATE DATABASE kann nicht in DO/Transaktion)
  SELECT 'CREATE DATABASE "${CONTROLLING_DB}" OWNER "${CONTROLLING_USER}"'
   WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${CONTROLLING_DB}')\gexec
  SELECT 'CREATE DATABASE "${LIGHTDASH_DB}" OWNER "${LIGHTDASH_USER}"'
   WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${LIGHTDASH_DB}')\gexec

  -- Read-only-Zugriff des dlt-Users auf die Shop-DB (${POSTGRES_DB}):
  GRANT CONNECT ON DATABASE "${POSTGRES_DB}" TO "${READER_USER}";
  GRANT USAGE ON SCHEMA public TO "${READER_USER}";
  -- bestehende Tabellen (falls schon vorhanden) ...
  GRANT SELECT ON ALL TABLES IN SCHEMA public TO "${READER_USER}";
  -- ... und alle künftig von ${POSTGRES_USER} angelegten Tabellen
  ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA public
    GRANT SELECT ON TABLES TO "${READER_USER}";
EOSQL

# PG15+: der DB-Owner besitzt NICHT automatisch das public-Schema (kein CREATE).
# Damit die Owner-User ihre Tabellen anlegen können, public-Schema je Warehouse-DB übertragen.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${CONTROLLING_DB}" \
  -c "ALTER SCHEMA public OWNER TO \"${CONTROLLING_USER}\";"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${LIGHTDASH_DB}" \
  -c "ALTER SCHEMA public OWNER TO \"${LIGHTDASH_USER}\";"

echo "initdb: DBs 'controlling' + 'lightdash' und User (controlling/lightdash/${READER_USER}) sichergestellt."
