# Lightdash — Self-Serve-BI (Showcase M4)

Lightdash visualisiert die dbt-Marts (Schema `analytics` in DB `controlling`): die
**Seminar-P&L + Break-even-Linie** und den **Unternehmens-Forecast** (gestapelt
Ist/gesichert/gewichtete Pipeline − Gemeinkosten). Metriken/Dimensionen kommen aus den
`meta`-Annotationen neben den dbt-Modellen (`../dbt/models/marts/_marts.yml`).

Zugang **nur für EBZ-Mitarbeiter über Keycloak** (Realm `ebz-staff`, Client `lightdash`).
Es gibt keinen eigenen Compose-Code hier — der Service steht in `../docker-compose.yml`
(Profil `controlling`); dieser Ordner enthält Runbook + Security-Smoke.

## Architektur-Eckpunkte
- **Metadaten** in eigener DB `lightdash` (eigener User) im **gemeinsamen** Postgres.
- **Warehouse-Verbindung** (im Lightdash-Setup einzutragen): Host `postgres`, Port `5432`,
  DB `controlling`, User/PW `controlling`, **Schema `analytics`**.
- **dbt-Projekt**: lokal gemountet nach `/usr/app/dbt` (`DBT_PROJECT_DIR`).
- **OIDC-URL für Browser UND Container**: `http://keycloak.localhost:8080` — Browser lösen
  `*.localhost`→127.0.0.1 (RFC 6761) auf, Container über den Compose-Netz-Alias. So bleibt
  die Shop-SSO auf `localhost:8088` unangetastet (kein hosts-Datei-Eingriff).

## Start (nach M1–M3, Reihenfolge L26: dlt → dbt → BI)
```bash
cd showcase
docker compose --profile controlling up -d lightdash
# UI: http://localhost:8084
```

## Automatischer Bootstrap (empfohlen) — `bootstrap.py`
Eine **frische** Lightdash-Instanz (leere Metadaten-DB nach Volume-Wipe) wird vollständig per API
eingerichtet, sodass nach dem SSO-Login sofort das Dashboard **„EBZ Controlling-Cockpit"** dasteht:

```bash
cd showcase/dbt && .venv/Scripts/python ../lightdash/bootstrap.py
# oder als Teil des Voll-Aufbaus:  bash showcase/showcase-aufbau.sh --only lightdash
```

`bootstrap.py` fährt denselben **OIDC-Authorization-Code-Flow wie ein Browser** (Lightdash → Keycloak
`staff/staff` → Callback — Passwort-Login ist deaktiviert), wird so zum **Org-Admin** (L21) und legt
**idempotent** an: Organisation `EBZ` · Projekt `EBZ Controlling` (Warehouse `controlling`/`analytics`
+ lokales dbt unter `/usr/app/dbt`, inkl. dbt-Compile) · öffentlicher Space `Controlling` · fünf
Auswertungen · das Dashboard. Re-Runs sind No-ops (legt nur Fehlendes an). Konfiguration via ENV
(`LIGHTDASH_URL`, `LIGHTDASH_BOOTSTRAP_USER/PASS`, `CONTROLLING_*`) mit Defaults aus `.env`.

**Auswertungen im Cockpit:** Kunden nach Umsatz · Umsatz je Bereich · Fakturierter Erlös je Monat ·
Seminar-Deckungsbeitrag (Umsatz/DB II/Ergebnis je Variante) · Unternehmens-Forecast (Ist/gesichert/
gewichtete Pipeline gestapelt + Ergebnis-Linie). Die Metriken/Dimensionen stammen aus den `meta`-
Annotationen neben den dbt-Modellen (`../dbt/models/marts/_marts.yml`).

> Wird Lightdashs Volume neu initialisiert, sind in der UI angelegte Charts/Dashboards **weg** — dieser
> Bootstrap stellt sie reproduzierbar als Code wieder her. Darum ist er fester Schritt in
> `showcase-aufbau.sh`.

## Erst-Setup + L21-Bootstrap (kein Lockout) — manuell (Fallback)
1. **Passwort-Login bleibt zunächst AN** (`LIGHTDASH_DISABLE_PW=false`). Der **erste**
   Login legt die Organisation an und macht den Nutzer zum **Org-Admin**.
2. Per **SSO** einloggen (Button „Sign in with SSO" → Keycloak `staff`/`staff`). Dieser
   erste SSO-Nutzer ist damit der Bootstrap-Admin (L21).
3. Projekt anlegen: Warehouse = obige `controlling`/`analytics`-Verbindung; dbt-Projekt =
   „dbt local server", Verzeichnis `/usr/app/dbt`, Target `dev`.
4. **Dann Passwort-Login deaktivieren** (SSO-Zwang): in `showcase/.env`
   `LIGHTDASH_DISABLE_PW=true`, danach `docker compose --profile controlling up -d lightdash`.
5. **L22**: JIT-Default-Rolle der per SSO neu angelegten Nutzer auf **Viewer/Member** lassen
   (nicht Admin); Elevation explizit.

## L23 — Authn ≠ Authz (Gehälter in Gemeinkosten)
Keycloak regelt *wer rein darf* (Realm `ebz-staff`), Lightdash *wer was sieht*:
- Keycloak liefert den Claim `groups` (Mapper im Client `lightdash`); `staff` ist in
  Gruppe `/controlling`, `staff2` **nicht**.
- Mit `AUTH_ENABLE_GROUP_SYNC=true` entsteht in Lightdash eine Gruppe `controlling`.
- Die **Unternehmens-GuV / Forecast**-Inhalte in einen **Space** legen, der nur der Gruppe
  `controlling` Zugriff gibt → `staff2` sieht ihn nicht (Negativtest).

## L25 — Exposition
Adminer/Postgres/Lightdash im Showcase nur lokal; für Produktion hinter Reverse-Proxy +
HTTPS (dann `SECURE_COOKIES=true`, `TRUST_PROXY=true`). Dev-Cookies sind nicht `secure`.

## Verifikation
```bash
node smoke-lightdash-security.mjs    # curl-/fetch-basierte Security-Checks
```
Plus manuell im Browser: anonymer Aufruf → Keycloak-Redirect; `staff`/`staff` → Zugriff;
nach Schritt 4 ist die Passwort-Maske weg; ein `ebz-customers`-Login ist nicht möglich
(der Client `lightdash` existiert nur im Realm `ebz-staff`).
