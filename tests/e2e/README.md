# ebz-e2e — projektweite Browser-/End-to-End-Tests (Playwright)

Service-übergreifende Tests, die gegen den **laufenden** `showcase/docker-compose.yml`-Stack
fahren (sie starten selbst keine Services). Geeignet für SSO-Flows über Service-Grenzen
hinweg (Keycloak → Portal/OpenOLAT/Shop), UI-Smoke-Tests und sichtbare Belege (Screenshots).

## Setup

```bash
cd showcase/e2e
pnpm install
pnpm run install:browser   # einmalig: Chromium herunterladen
```

## Ausführen

```bash
# Stack muss laufen:  docker compose -f ../docker-compose.yml up -d
pnpm test                       # headless
pnpm run test:headed            # sichtbarer Browser
pnpm test openolat-theme        # einzelne Spec
pnpm run report                 # HTML-Report (CI)
```

## Service-URLs

Per Env überschreibbar (Defaults = compose-Defaults):

| Env            | Default                          |
| -------------- | -------------------------------- |
| `OPENOLAT_URL` | `http://localhost:8089`          |
| `PORTAL_URL`   | `http://localhost:5175`          |
| `MDM_URL`      | `http://localhost:5174`          |
| `KEYCLOAK_URL` | `http://keycloak.localhost:8080` |

Login-Helper und Test-Credentials liegen in `tests/helpers/sso.ts` (wiederverwendbar):
`CUSTOMER` (Realm `ebz-customers`, `customer/customer`) und `STAFF` (Realm `ebz-staff`,
`staff/staff`, Cockpit-Rollen).

## Specs

- `tests/openolat-theme.spec.ts` — verifiziert das externe EBZ-„Regenbogen"-SCSS-Theme
  (außerhalb der .war geladen): CSS wird serviert, aktives Theme ist `ebz`, der
  Regenbogen-Verlauf ist auf der Navbar berechnet; legt einen Screenshot ab.
- `tests/storefront.spec.ts` — Produktkatalog-SSR (View-Source enthält Vendure-Inhalt,
  Speaking-URLs/canonical, Facetten-Sidebar).
- `tests/cockpit-rechnungen.spec.ts` — mdm-Rechnungs-Cockpit (Staff-SSO): Belegliste +
  Status-Badges, Detailansicht (Positionen/ZUGFeRD), und der volle Sonderrechnung-Flow
  durch die UI (anlegen → Position → ausstellen → festgeschriebene Nummer).
```
