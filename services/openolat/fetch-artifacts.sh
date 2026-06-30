#!/usr/bin/env bash
# Lädt die (gitignored) Build-Artefakte für das OpenOLAT-Image:
#   - OpenOLAT-WAR (Build 2034 = OpenOLAT 20.1) von openolat.com (resumebar via -C -)
#   - Postgres-JDBC-Treiber von Maven Central
#   - Dart-Sass-Standalone (Linux x64) — kompiliert im Build das eigene SCSS-Theme
#     (showcase/openolat/theme/ebz) gegen die Framework-Partials der WAR; kein Node nötig.
# Danach ist `docker compose build olat` offline-fähig. Nutzung:  bash showcase/openolat/fetch-artifacts.sh
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OLAT_BUILD="${OLAT_BUILD:-2034}"
PG_JDBC="${PG_JDBC:-42.7.4}"
# Dart Sass 1.x: unterstützt weiterhin die @import-Syntax, die die OpenOLAT-Themes nutzen
# (in Dart Sass 3.0 entfällt @import — daher bewusst auf der 1.x-Linie gepinnt).
DART_SASS="${DART_SASS:-1.83.4}"

echo "→ OpenOLAT WAR (Build ${OLAT_BUILD})"
curl -fL -C - -o "$DIR/openolat_${OLAT_BUILD}.war" "https://www.openolat.com/releases/openolat_${OLAT_BUILD}.war"

echo "→ Postgres JDBC ${PG_JDBC}"
curl -fL -o "$DIR/postgresql.jar" "https://repo1.maven.org/maven2/org/postgresql/postgresql/${PG_JDBC}/postgresql-${PG_JDBC}.jar"

echo "→ Dart Sass ${DART_SASS} (Theme-Compiler, linux-x64)"
curl -fL -o "$DIR/dart-sass.tar.gz" \
  "https://github.com/sass/dart-sass/releases/download/${DART_SASS}/dart-sass-${DART_SASS}-linux-x64.tar.gz"

ls -lh "$DIR"/openolat_*.war "$DIR/postgresql.jar" "$DIR/dart-sass.tar.gz"
