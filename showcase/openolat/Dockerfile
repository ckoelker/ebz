# Eigenes OpenOLAT-Image (Build 2034 = OpenOLAT 20.1) gemäß offizieller Anleitung
# https://docs.openolat.org/de/manual_admin/installation/installGuide/ (20.1+):
#   Tomcat 10.1 + JDK 17, JNDI-Datasource, DB-Schema wird beim ERSTSTART automatisch angelegt.
# WAR + Postgres-JDBC-Treiber kommen aus dem Build-Context (via openolat/fetch-artifacts.sh geladen,
# gitignored). Der Entrypoint schreibt olat.local.properties + JNDI-Context aus Env und startet Tomcat.
#
# Basis bewusst -jammy (Ubuntu 22.04, glibc 2.35): glibc ≥2.39 (Ubuntu 24.04/-noble) verdrahtet
# *.localhost FEST auf Loopback (ignoriert Docker-DNS/-hosts) → der Container könnte den kanonischen
# Issuer keycloak.localhost:8080 nicht erreichen. Mit glibc 2.35 löst keycloak.localhost den Compose-
# Alias auf → OpenOLAT nutzt DENSELBEN Keycloak-Host wie Portal/Shop → echtes SSO (geteiltes Cookie).
FROM tomcat:10.1-jdk17-temurin-jammy

# Mitgelieferte Standard-Webapps entfernen; OpenOLAT wird als ROOT-Context deployt.
RUN rm -rf "$CATALINA_HOME/webapps/ROOT" "$CATALINA_HOME/webapps.dist"

# OpenOLAT-WAR nach webapps/ROOT entpacken (jar liegt im JDK-Image vor → kein apt nötig).
COPY openolat_2034.war /tmp/openolat.war
RUN mkdir -p "$CATALINA_HOME/webapps/ROOT" \
 && cd "$CATALINA_HOME/webapps/ROOT" \
 && jar xf /tmp/openolat.war \
 && rm -f /tmp/openolat.war

# Postgres-JDBC-Treiber in den Tomcat-Common-Classloader (für die JNDI-Datasource).
COPY postgresql.jar "$CATALINA_HOME/lib/postgresql.jar"

# OpenOLAT-Datenverzeichnis (per Volume persistiert).
RUN mkdir -p /opt/openolat/olatdata

# ── EBZ-Mandanten-Branding-Extension (offizieller AfterLogin-Hook, KEIN Core-Fork) ──────
# Kompiliert eine kleine OpenOLAT-Extension gegen die WAR-Bibliotheken und legt das Jar
# (Interceptor-Klasse + _spring-Context) in WEB-INF/lib. Der Interceptor hängt die org-cssClass
# (mandant-<schlüssel>, vom Integration-Backend gesetzt) via ChiefController.addBodyCssClass an
# den <body> → das EBZ-Theme targetet .mandant-<schlüssel>. Spring lädt das _spring-XML via
# classpath*:/**/_spring/*.xml automatisch. javac ist im -jdk17-Basisimage vorhanden.
COPY ext/ /tmp/ext/
RUN ROOT="$CATALINA_HOME/webapps/ROOT" \
 && PKG=/tmp/ext-build/de/netzfactor/ebz/olat/branding \
 && mkdir -p "$PKG/_spring" \
 && CP="$ROOT/WEB-INF/classes:$(echo "$ROOT"/WEB-INF/lib/*.jar | tr ' ' ':'):$(echo "$CATALINA_HOME"/lib/*.jar | tr ' ' ':')" \
 && javac -cp "$CP" -d /tmp/ext-build /tmp/ext/MandantBrandingInterceptor.java /tmp/ext/EbzNachweisCertWebService.java \
 && cp /tmp/ext/mandantBrandingContext.xml "$PKG/_spring/" \
 && (cd /tmp/ext-build && jar cf "$ROOT/WEB-INF/lib/ebz-mandant-branding.jar" de) \
 && (cd /tmp/ext-build && jar cf "$ROOT/WEB-INF/lib/ebz-nachweis-cert.jar" org) \
 && rm -rf /tmp/ext /tmp/ext-build
# OpenOLAT lädt Spring-Contexts NUR über explizite <import> in mainContext.xml (kein Auto-Scan
# für Fremd-Jars). Wir registrieren unseren Context genau so — per Overlay der mainContext.xml in
# WEB-INF/classes (hat Classloader-Vorrang vor dem Jar): aus dem openolat-lms-Jar extrahieren, EINE
# <import>-Zeile ergänzen. Kein Java-Core-Fork; exakt die Zeile, die OpenOLAT für jedes Modul setzt.
RUN ROOT="$CATALINA_HOME/webapps/ROOT" \
 && LMS=$(ls "$ROOT"/WEB-INF/lib/openolat-lms-*.jar) \
 && (cd "$ROOT/WEB-INF/classes" && jar xf "$LMS" org/olat/_spring/mainContext.xml) \
 && grep -q "mandantBrandingContext" "$ROOT/WEB-INF/classes/org/olat/_spring/mainContext.xml" \
    || sed -i 's#</beans>#\t<import resource="classpath:/de/netzfactor/ebz/olat/branding/_spring/mandantBrandingContext.xml"/>\n</beans>#' \
       "$ROOT/WEB-INF/classes/org/olat/_spring/mainContext.xml"

# ── Eigenes EBZ-SCSS-Theme („Regenbogen") AUSSERHALB der .war ──────────────────
# Dart-Sass-Standalone (gitignored, via fetch-artifacts.sh; kein Node nötig) entpacken.
COPY dart-sass.tar.gz /tmp/dart-sass.tar.gz
RUN tar xzf /tmp/dart-sass.tar.gz -C /opt && rm -f /tmp/dart-sass.tar.gz
# Theme-Quelle (nur Variablen-Overrides + @import des light-Themes) in den Build holen.
COPY theme/ /opt/openolat/theme-src/
# Vollständigen Theme-Ordner als Kopie des mitgelieferten light-Themes seeden (Fonts/
# Images/meta), das fehlende theme.js aus dem openolat-Theme ergänzen und dann das aus
# unserem SCSS kompilierte theme.css darüberlegen. Kompiliert wird mit load-path auf
# <war>/static/themes, damit `@import "light/theme"` + die framework-relativen Bootstrap-
# Imports aufgelöst werden. Das Ergebnis liegt als „Seed" im Image unter themes-seed/ebz;
# der Entrypoint kopiert es beim Start nach olatdata/customizing/themes (= außerhalb der .war).
RUN STATIC_THEMES="$CATALINA_HOME/webapps/ROOT/static/themes" \
 && mkdir -p /opt/openolat/themes-seed \
 && cp -r "$STATIC_THEMES/light" /opt/openolat/themes-seed/ebz \
 && cp "$STATIC_THEMES/openolat/theme.js" /opt/openolat/themes-seed/ebz/theme.js \
 && /opt/dart-sass/sass --no-source-map --style=compressed --quiet \
      --load-path="$STATIC_THEMES" \
      /opt/openolat/theme-src/ebz/theme.scss /opt/openolat/themes-seed/ebz/theme.css

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
