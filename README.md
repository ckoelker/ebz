# EBZ Showcase — Best-of-Breed (Vendure-Shop + Controlling)

Ein integrierter On-prem-Showcase: **Headless-Shop (Vendure)** + **Vue-Storefront** und —
darauf aufbauend — die **Controlling-Schicht** (Seminar-Profitabilität, HubSpot-Pipeline,
GuV-Forecast). Alles in **einer** `docker-compose.yml`, auf **einem** Postgres.

> Doku: Shop-Plan/-Recherche in [`../shop-planung/`](../shop-planung/), Controlling-Plan in
> [`../controlling-planung/`](../controlling-planung/), Enterprise-Planung in
> [`../enterprise-stack-planung/`](../enterprise-stack-planung/). Memorys `showcase-shop-vendure.md`,
> `controlling-showcase.md`, `use-pnpm-not-npm.md`.

## Struktur (flach unter `showcase/`)

```
showcase/
  docker-compose.yml      # ein Stack: postgres · server · worker · keycloak · adminer (+ Controlling-Services M1+)
  .env / .env.example     # zentrale Variablen (Compose liest .env automatisch)
  postgres/initdb/        # legt DBs controlling + lightdash + dedizierte User an
  vendure/                # Shop-Backend (Vendure 3.6.4, pnpm)
  storefront/             # Nuxt-SSR-Storefront (Produktkatalog: Katalog/Suche/Warenkorb/Kasse, pnpm)
  integration/ dlt/ dbt/ lightdash/   # Controlling (M1–M4, geplant — neben vendure/, kein controlling/-Unterordner)
```

## Single Source of Truth: ein Postgres, drei DBs

| DB | Owner-User | Zweck |
|---|---|---|
| `vendure` | `vendure` (Superuser) | Shop (operativ) |
| `controlling` | `controlling` | Warehouse (dlt/dbt) |
| `lightdash` | `lightdash` | BI-Metadaten |

dlt liest `vendure` über den read-only-User **`controlling_reader`** (Quelle nur lesen). DBs/User
legt `postgres/initdb/` bei leerem Volume an; Variablen/Creds in **`.env`** (`.env.example` als Vorlage).

## Paketmanager: pnpm

Beide JS-Pakete nutzen **pnpm 11** (nicht npm). Settings in je `pnpm-workspace.yaml`
(`nodeLinker: hoisted`, `trustPolicy: off`, `minimumReleaseAge: 0`, `allowBuilds` für bcrypt/sharp/esbuild).
Das Vendure-Image baut via Corepack + `pnpm install --frozen-lockfile` (Node 22).

## Schnellstart

```bash
cd showcase
cp .env.example .env              # optional: Werte anpassen (sonst gelten Compose-Defaults)
docker compose up -d --build      # postgres · server · worker · keycloak · adminer
# Shop initialisieren (idempotent; nur nötig nach Volume-Reset): POST /shop/init am
# Integrationsbackend (Rolle katalog-pflege) — baut Katalog/Steuern/Facetten/Produkte in Vendure auf.
```

Storefront lokal:
```bash
cd showcase/storefront
pnpm install
pnpm dev                          # Nuxt-Dev-Server (Standard http://localhost:3000)
```

### Endpunkte
| Zweck | URL |
|---|---|
| Shop-API (GraphQL) | http://localhost:3000/shop-api |
| Admin-API | http://localhost:3000/admin-api |
| GraphiQL | http://localhost:3000/graphiql |
| Health | http://localhost:3000/health |
| Storefront (dev) | http://localhost:5173 |
| Adminer | http://localhost:8082 (Server `postgres`, DBs `vendure`/`controlling`/`lightdash`) |
| Keycloak | http://localhost:8088 (`admin`/`admin`) |

Superadmin `superadmin`/`superadmin`. SSO-Testnutzer `customer`/`customer` (Shop), `staff`/`staff` (Admin).

## Status

| Baustein | Status |
|---|---|
| M1 — Infrastruktur (Docker + PostgreSQL, Server/Worker) | ✅ |
| M2 — Katalog + Custom Fields (F1 physisch, F2 Download, F3 Seminar) | ✅ |
| M3 — Subscription-Strategy (F4 Abo, F5 Halbjahr, F6 Studium 36 Raten) | ✅ (Stripe-Charge = manueller Schritt) |
| M4 — interner Rechnungslauf (ScheduledTask) | ✅ (Mollie/SEPA designiert, nicht installiert) |
| SSO — Keycloak, 2 Realms strikt getrennt (Kunde + Staff) | ✅ |
| M5 — Vue-Frontend (PrimeVue) | 🟡 in Arbeit — F1-Buch-Flow end-to-end grün (Browser-verifiziert) |
| Umbau pnpm + Single-Postgres + `.env` | ✅ (2026-06-12) |
| **Controlling M1** — HubSpot-Ingestion (Camel Quarkus + LangChain4j/OpenAI) | ✅ |
| **Controlling M2** — Seminar-Kosten-Plugin + dlt-Load (Vendure→Warehouse) | ✅ |
| **Controlling M3** — dbt (Seminar-DB/Break-even + Drei-Bucket-Forecast) | ✅ |
| **Controlling M4–M5** — Lightdash (Keycloak-gesichert) · Auswertung | ⏭ geplant — siehe controlling-planung |

## Shop-Bausteine (Kurz)

- **Katalog & Custom Fields** (`vendure/src/vendure-config.ts`): `Customer` (studentNumber, birthDate),
  `Order` (enrollmentType, trainingCompany), `OrderLine` (participantName, participantEmail),
  `ProductVariant` (`fulfillmentType` = physical|digital|seminar — Discriminator für den Frontend-Flow).
- **Wiederkehrende Abrechnung:** datengetriebene `ShowcaseSubscriptionStrategy` (Pinelab Stripe-Subscription)
  über Varianten-Custom-Fields; `previewStripeSubscriptions` zeigt Zahlpläne ohne Stripe-Charge.
  Echte Stripe-Zahlung = manueller Schritt (Test-Keys + `stripe listen`).
- **Interner Rechnungslauf** (`vendure/src/plugins/recurring-invoice/`): `Installment`-Entity,
  Admin-API `materializeInstallments`/`runRecurringInvoiceRun`/`installmentsForOrder`, ScheduledTask
  täglich 02:00. Hier dockt produktiv E-Rechnung/DATEV an. Mollie/SEPA = designierter externer Weg.
- **SSO Keycloak:** eine Instanz, **zwei Realms** strikt getrennt — `ebz-customers` (Client `shop-frontend`,
  Shop-API) und `ebz-staff` (Client `staff-frontend`, Admin-API, Rolle `sso-staff`). `NativeAuthenticationStrategy`
  bleibt aktiv (Superadmin). Split-Horizon: Issuer öffentlich (`localhost:8088`), JWKS intern (`keycloak:8080`).

## Frontend (M5)

Vite 8 + Vue 3.5 + TS 6 + PrimeVue 4.5/Aura + Pinia + vue-router + @urql/vue + keycloak-js 26.
**Leitplanken §8a** (im Vendure-Plan): Vite-Proxy `/shop-api` **und** `/assets` (same-origin → Cookie-Session);
urql `credentials:'include'`; Keycloak-Login **erst am Checkout** (keine Gastbestellung), KC-Token einmalig
→ Vendure `authenticate`, danach eigene Vendure-Session; Preise sind **Cent** (→ `Intl.NumberFormat de-DE`);
Versandart Pflicht (auch 0-€ für Download/Seminar); GraphQL-Codegen (`pnpm codegen` → `src/gql/`, gitignored).

**F1 browser-verifiziert** (Playwright): Katalog→Cart→KC-Login→Adresse→Versand→Zahlung→PaymentSettled.
Offen: F2/F3/F4–F6; Versand-Eligibility je `fulfillmentType`; Ratenplan-Anzeige im Katalog
(`previewStripeSubscriptions`, §8a-6).

## Smoke-Tests (alle grün)

```bash
cd showcase/vendure
node scripts/smoke-shop.mjs            # M2 Katalog + Custom Fields
node scripts/smoke-subscriptions.mjs   # M3 Zahlpläne F4/F5/F6
node scripts/smoke-rechnungslauf.mjs   # M4 Ratenplan + Rechnungslauf
node scripts/smoke-sso.mjs             # SSO Kunde/Staff getrennt + Negativtest
node scripts/smoke-checkout-f1.mjs     # F1 Checkout end-to-end
```

## Controlling

Best-of-Breed-Erweiterung: **dlt** (Vendure→Warehouse) + **Camel Quarkus + LangChain4j** (HubSpot-Pull
+ KI-Konvertierung) → **dbt** (DB-Stufen/Break-even/Forecast) → **Lightdash** (Keycloak-gesichert).
Code liegt flach in `integration/`, `dlt/`, `dbt/`, `lightdash/`. Plan & Versionsmatrix:
[../controlling-planung/Showcase-Realisierungsplan-Controlling.md](../controlling-planung/Showcase-Realisierungsplan-Controlling.md).

**M1 — HubSpot-Ingestion** (`integration/`, fertig): Quarkus/Camel/LangChain4j-Service, nur mit Profil
`controlling`: `docker compose --profile controlling up -d --build`. Details: [integration/](integration/).

**M2 — Vendure-Anbindung** (fertig):
- *Seminar-Kosten-Plugin* (`vendure/src/plugins/seminar-cost/`): Custom-Entity `SeminarCost`
  (fix/variabel, je Seminar/je Teilnehmer:in; Admin-API + Dashboard) auf den Seminar-Varianten.
- *dlt-Pipeline* (`dlt/`, Commodity-EL): kopiert `order`/`order_line`/`installment`/`seminar_cost`
  aus DB `vendure` (read-only `controlling_reader`, **ohne PII**) ins Warehouse-Schema
  `controlling.vendure`. Idempotent (Merge auf `id` + Watermark `updatedAt`).
  ```bash
  # Katalog/Kosten: POST /shop/init am Integrationsbackend (Rolle katalog-pflege), dann Bewegungsdaten:
  cd vendure && node scripts/seed-demo-orders.mjs                     # Beispiel-Bestellungen
  cd ../dlt  && python -m venv .venv && .venv/Scripts/python -m pip install -r requirements.txt
  .venv/Scripts/python vendure_to_warehouse.py                       # Load → DB controlling
  ```

**M3 — dbt (Transform + Forecast)** (fertig): Seminar-Deckungsbeitrag/Break-even
(`fct_seminar_db`) + treiberbasierter Monats-Forecast aus drei disjunkten Erlös-Buckets
(`fct_forecast`); Tests erzwingen Drei-Bucket-Disjunktheit (L1), Umlage-Reconciliation
(L16) und das Plan-Rechenbeispiel (Unit-Test, Break-even 5 TN). Reihenfolge **dlt → dbt → BI** (L26).
  ```bash
  cd dbt && python -m venv .venv && .venv/Scripts/python -m pip install -r requirements.txt
  .venv/Scripts/dbt build --profiles-dir .                           # → Schema analytics in DB controlling
  ```

## Dev-/Architektur-Hinweise

- **DB-Schema:** im Dev legt Vendure das Schema automatisch an (`synchronize` bei `APP_ENV=dev`);
  Prod = Migrationen.
- **Worker:** eigener Prozess (DB-basierte Job-Queue, kein Redis), startet nach „healthy" des Servers.
- **Admin-Dashboard** im Container aus (`SERVE_DASHBOARD=false`); lokal via `pnpm dev` in `vendure/`.
- **Secrets/Variablen** zentral in `showcase/.env` (gitignored); strukturelle Werte/Defaults in der Compose.
