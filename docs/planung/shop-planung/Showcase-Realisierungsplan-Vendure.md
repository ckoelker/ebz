# Showcase „Shop-Anbindung" — Realisierungsplan (Vendure)

> Konkreter Umsetzungsplan zum Showcase aus der [Recherche](Showcase-Headless-Shop-Recherche.md). Teil des Best-of-Breed-Evaluierungsstrangs (vgl. [Capability-Map](../enterprise-stack-planung/Zielarchitektur-Capability-Map.md) #7 Shop).
> **Getroffene Richtungsentscheidungen:** Backend = **Vendure** · Recurring = **shop-intern (`SubscriptionStrategy`)** · Zahlweg = **Stripe + SEPA/Rechnung (beide gezeigt)**.
> Stand Juni 2026. Versionsspezifische Details (Plugin-APIs) beim Setup gegen die aktuelle Vendure-/Plugin-Doku abgleichen.

---

## 1. Showcase-Ziel & Scope

**Ziel:** beweisen, dass ein **Headless-Shop (Vendure) + selbst gebautes Vue-Frontend** alle sieben Warengruppen trägt — insbesondere die **wiederkehrende/ratierliche Abrechnung** (W5–W7), die der eigentliche Differenzierer ist.

**Repräsentativer Scope (eine pro Commerce-Klasse, statt alle 7 auszuprogrammieren):**

| Showcase-Fall | deckt ab | beweist |
|---|---|---|
| **F1 — physisches Buch + Versand** | W1 | klassischer Warenkorb + Fulfillment/Versand |
| **F2 — Download (Skript/PDF)** | W2 | digitales Gut, kein Versand, Entitlement |
| **F3 — Tagesseminar mit Teilnehmerdaten** | W3, W4 | Event-Buchung + **Custom Fields** (Teilnehmer ≠ Besteller) |
| **F4 — Abo „Veranstaltungsreihe"** | W5 | Subscription, unbefristet, monatlich |
| **F5 — Berufsschule Halbjahr** | W6 | **SubscriptionStrategy**: Intervall ½ Jahr |
| **F6 — Studiengang 36 Monatsraten** | W7 | **SubscriptionStrategy**: 36 Raten, festes Enddatum |

> F4–F6 werden **je einmal über Stripe und einmal über Mollie/SEPA** durchgespielt → der „beide Zahlwege"-Nachweis.

---

## 2. Zielarchitektur des Showcase

```
        ┌─────────────────────────────────────────────┐
        │  Vue 3 Frontend (Eigenbau, je Warengruppe    │
        │  eigener Flow + Zusatzdaten-Formulare)        │
        │  GraphQL-Client + TS-Codegen auf Shop-API     │
        └───────────────┬───────────────────────────────┘
                        │  GraphQL (/shop-api)
        ┌───────────────▼───────────────────────────────┐
        │  Vendure Server (NestJS)                       │
        │   • Shop-API + Admin-API + Admin-UI            │
        │   • Custom Fields (Schüler/Studi/Teilnehmer)   │
        │   • StripeSubscription-Plugin (SubscriptionStrategy)
        │   • Mollie-Plugin (SEPA + Mandat/Recurring)    │
        │   • Digital-Fulfillment / ShippingEligibility  │
        ├───────────────┬───────────────────────────────┤
        │  Vendure Worker (Job-Queue, DB-basiert)        │
        │   • Recurring-Charges / Rechnungslauf          │
        └───────────────┼───────────────────────────────┘
                        │  TypeORM
                ┌───────▼────────┐      ┌──────────────┐
                │  PostgreSQL    │      │ Stripe /     │
                │  (Docker)      │      │ Mollie (Test)│
                └────────────────┘      └──────────────┘
```

**Bewusst weggelassen (Showcase-Vereinfachung):** kein Redis (Vendure-Default-Job-Queue läuft DB-basiert — anders als Saleor kein Redis/Celery nötig), keine Stammdaten-Kern-Anbindung (Connector **S13** der [Schnittstellenliste](../enterprise-stack-planung/Soll-Bebauungsplan-und-Schnittstellen.md) bleibt im Showcase lokal in der Shop-DB; echte SSoT-Anbindung später).

---

## 3. Docker-Umgebung (Topologie)

`docker-compose.yml` mit drei Diensten (Vue-Dev kann lokal außerhalb laufen):

```yaml
services:
  postgres:        # PostgreSQL 16, Volume für Persistenz
  vendure-server:  # Vendure: Shop-/Admin-API + Admin-UI (Port 3000)
  vendure-worker:  # Vendure: Job-Queue (Recurring/Rechnungslauf)
  # adminer:       # optional, DB-Inspektion
```

- **Postgres** als einziger Zustand; Vendure server + worker teilen sich `dbConnectionOptions` (TypeORM).
- Konfiguration der DB-Verbindung in `vendure-config.ts` auf **Postgres** (nicht das Dev-SQLite).
- Stripe-/Mollie-**Testschlüssel** als Env-Variablen; Webhooks im Showcase via Tunnel (z. B. Stripe CLI / ngrok).

---

## 4. Setup-Schritte (Backend)

1. **Scaffold:** im Repo-Root `showcase/` → `npx @vendure/create vendure` (ergibt `showcase/vendure/`) → bei Datenbankwahl **Postgres** angeben.
2. **Dockerisieren:** server + worker + postgres in Compose; `.env` für DB/Payment-Keys.
3. **Plugins ergänzen** in `vendure-config.ts`:
   - `@pinelab/vendure-plugin-stripe-subscription` (Stripe-Recurring)
   - `MolliePlugin` aus `@vendure/payments-plugin` (SEPA/Mandat-Recurring)
   - Custom-Plugin „showcase" (eigene `SubscriptionStrategy`, Digital-Fulfillment, Custom Fields)
4. **Seed-Daten:** Produkte/Collections der 6 Showcase-Fälle anlegen (Admin-UI oder Seed-Script).
5. **GraphQL-Codegen** fürs Frontend gegen `/shop-api` aufsetzen.

---

## 5. Produktmodellierung der Warengruppen

Vendure-Bordmittel reichen für die Abbildung; die Unterscheidung läuft über **Collections/Facets + Custom Fields + Fulfillment-/Subscription-Logik**:

| Fall | Vendure-Abbildung | Versand? | Recurring? | Zusatzdaten (Custom Fields) |
|---|---|:--:|:--:|---|
| F1 Buch | normale Variante, physisch | ✓ | – | – |
| F2 Download | Variante, **Digital-Fulfillment-Handler**, `ShippingEligibilityChecker` blockt Versand | ✗ | – | – |
| F3 Seminar | Variante mit Termin/Kontingent (Stock = Plätze) | ✗ | – | **OrderLine-CustomField**: Teilnehmername, -mail, ggf. Allergien |
| F4 Abo-Reihe | Subscription-Variante (unbefristet, monatlich) | ✗ | ✓ | Kunde |
| F5 Berufsschule | Subscription-Variante, **Strategy: Intervall 6 Monate** | ✗ | ✓ | **Order/Customer-CustomFields**: Schüler, Ausbildungsbetrieb, Klasse |
| F6 Studiengang | Subscription-Variante, **Strategy: 36× monatlich, Enddatum** | ✗ | ✓ | **CustomFields**: Studiengang, Matrikel, Studienbeginn |

---

## 6. Recurring-Design — der Kern des Showcase

### 6.1 Eigene `SubscriptionStrategy`
Das Pinelab-Plugin erlaubt eine eigene Implementierung der `SubscriptionStrategy` — darin definieren wir **Intervall, Startzeitpunkt, Laufzeit und Anzahl der Abbuchungen** pro Produkt:

- **F4 Abo-Reihe:** `monthly`, unbefristet, kein Enddatum.
- **F5 Berufsschule:** Intervall **6 Monate**, Erststart bei Einschreibung, Betrag = Halbjahresgebühr.
- **F6 Studiengang:** Intervall **monatlich**, **`durationCount = 36`** mit festem Enddatum, optional anteilige erste Rate.

> Die Strategy ist reine Backend-Logik (Leitplanke L4: konfigurieren/erweitern statt Core-Modifikation).

### 6.2 Zwei Zahlwege parallel

| Pfad | Mechanik | passt zu |
|---|---|---|
| **Stripe** | Pinelab Stripe-Subscription-Plugin: Erstzahlung/Karten-Mandat bei Checkout, danach Stripe zieht periodisch ein | schneller Mechanik-Nachweis (F4–F6) |
| **Mollie/SEPA** | Mollie-Plugin: **Erstzahlung erzeugt SEPA-Mandat**, danach wiederkehrende Charges (Mollie `sequenceType: recurring`, ausgelöst per Vendure-**Worker-Job**) | realistischer DE-Fall: Schulgebühr per Lastschrift |
| **(reine Rechnung)** | Manuelle Zahlungsmethode + **geplanter Rechnungslauf** im Worker (kein Auto-Einzug, nur Rechnungserzeugung) | falls Lastschrift nicht gewünscht — E-Rechnung später über FiBu (Connector **S11**) |

> **Wichtige Designgrenze für die spätere Zielarchitektur:** Stripe/Mollie übernehmen im Showcase die Einzugsmechanik. Für den Produktivbetrieb (E-Rechnung-Pflicht, DATEV) gehört der **Rechnungs-/Abrechnungslauf** perspektivisch an eine **dedizierte Billing-/FiBu-Schicht** (Connector **S11/S12** der [Schnittstellenliste](../enterprise-stack-planung/Soll-Bebauungsplan-und-Schnittstellen.md)) — der Showcase zeigt die Mechanik bewusst shop-intern (so entschieden), markiert die Naht aber explizit.

---

## 7. Zusatzdaten (Custom Fields)

In `vendure-config.ts` werden Custom Fields deklariert (Beispiele):
- **Customer:** `studentNumber`, `birthDate`
- **Order:** `enrollmentType` (Berufsschule/Studium/Seminar), `trainingCompany`
- **OrderLine:** `participantName`, `participantEmail` (Seminar, Teilnehmer ≠ Besteller)

Diese erscheinen automatisch in Admin-UI **und** Shop-API → das Vue-Frontend liest/schreibt sie über GraphQL. Genau hierfür wurde Vue-Eigenbau gewählt: pro Warengruppe ein passendes Zusatzdaten-Formular.

---

## 8. Vue-Frontend-Anbindung

- **Client:** Vue 3 SPA (oder Nuxt) + GraphQL-Client (urql/Apollo) gegen `/shop-api`.
- **Typsicherheit:** GraphQL-Codegen erzeugt TS-Typen aus dem Vendure-Schema inkl. der Custom Fields → End-to-End-Typen.
- **Pro Warengruppe ein eigener Flow** (genau die Begründung fürs Eigenbau-Frontend):
  - physisch/Download → Standard-Warenkorb-/Checkout-Flow.
  - Seminar → Buchungs-Flow mit Teilnehmer-Formular (OrderLine-CustomFields).
  - Berufsschule/Studium → Einschreibe-Flow mit Schüler-/Studi-Formular + Abo-/Raten-Bestätigung + Mandat (Stripe-Karte oder Mollie-SEPA).
- Optional als Starthilfe: offizielle **Vendure Vue-Storefront-Composables** für die Standard-Teile (Cart/Account), Custom-Flows selbst.

---

## 8a. Frontend-Leitplanken (M5) — zwingend zu beachten

> Vor dem ersten Flow festzurren; sonst baut man um. Gegen die reale Backend-Config (`vendure/src/vendure-config.ts`, `authOptions.tokenMethod=['bearer','cookie']`, Channel netto/EUR) abgeglichen.

**Auth & Session**
1. **Vendure-Session ≠ Keycloak-Token.** Keycloak-Token authentifiziert nur einmalig `authenticate(input:{ keycloak:{ token }})`; danach trägt eine **eigene Vendure-Session** alle Calls. Keycloak-Token wandert NICHT an jeden Shop-API-Request.
2. **Token-Methode = `cookie`** (über Vite-Proxy same-origin). Kein manuelles Header-Handling, Cart übersteht Reloads. `bearer` nur falls cross-origin (dann `vendure-auth-token`-Response-Header lesen + Backend `exposeHeaders`).
3. **Anonymer Cart → Login beim Checkout** (Vorgabe „kein Gast"). `activeOrder` hängt an der Session, nicht am Login. Beim Checkout Keycloak-Login → `authenticate` → anonymer Order wird dem Kunden zugeordnet. Übergang testen (Cart-Erhalt), Checkout hart gaten bis `activeCustomer` gesetzt.
15. **Keycloak-Client `shop-frontend`:** Vite-Dev-Origin (z. B. `http://localhost:5173`) in Redirect URIs + Web Origins; **public client + PKCE**. **Single-Logout:** Vendure-`logout` UND Keycloak end-session.
16. **Dev-Cookie nicht `secure`** (läuft über `http://localhost`), sonst wird Session-Cookie nie gesetzt → Login schlägt lautlos fehl.

**Daten & Custom Fields**
4. **Custom Fields explizit** abfragen/setzen (kommen nicht automatisch): `participantName/Email` als `customFields`-Arg bei `addItemToOrder` (pro OrderLine); `enrollmentType/trainingCompany` via `setOrderCustomFields`; `studentNumber/birthDate` am Customer. UI-Discriminator = `ProductVariant.fulfillmentType` (`physical|digital|seminar|subscription`).
5. **Preise netto / EUR:** `priceWithTax` (Brutto) anzeigen, `languageCode: de` mitgeben (sonst CF-Labels nicht deutsch).
10. **Preise sind Integer in Cent** (`2900` = 29,00 €): `/100` + `Intl.NumberFormat('de-DE',{style:'currency',currency:'EUR'})`.
14. **Pflichtfelder erzwingt das Backend nicht** (alle CF `nullable`) → Validierung (Teilnehmerdaten etc.) komplett im Frontend.

**Checkout & Order-State-Machine**
7. **Mutations liefern Union-/ErrorResult-Typen** (kein Throw): auf `__typename` prüfen (`InsufficientStockError`, `OrderModificationError`, …). Zentraler ErrorResult-Handler von Anfang an.
11. **Versandart Pflicht** für `ArrangingPayment` — auch F2 (Download) und F3 (Seminar) brauchen eine **0-€-Versandart** („digitale Bereitstellung"), sonst kein Checkout. Seed prüfen.
12. **Feste Mutations-Reihenfolge:** `addItemToOrder` → `setOrderShippingAddress` → `eligibleShippingMethods` (erst nach Adresse befüllt) → `setOrderShippingMethod` → `transitionOrderToState('ArrangingPayment')` → `addPaymentToOrder`.
18. **Seminar-Kapazität = `saleableStockLevel`:** „ausgebucht" sauber behandeln (`InsufficientStockError`).

**Subscriptions & Raten**
6. **Zahlplan vor Kauf zeigen:** Varianten-Preis ist nur die Einzelrate → `previewStripeSubscriptions` für Intervall/Anzahl/Gesamt (aus Variant-Custom-Fields). Nie nur Unit-Preis rendern.
8. **Stripe-Zahlung braucht Stripe.js** im Frontend (clientSecret/PaymentIntent vom Pinelab-Plugin → Stripe Elements + Confirm) + `stripe listen`-Webhook (Test-Mode).
13. **`installmentsForOrder` ist Admin-API** — Kunde sieht seinen materialisierten Ratenplan im Shop-API NICHT. Für „Meine Raten" entweder `previewStripeSubscriptions` (nur Vorschau) oder eigenen Shop-API-Resolver.

**Infra & Tooling**
9. **Asset-Proxy:** nicht nur `/shop-api`, auch **`/assets`** proxien (Produktbilder), sonst keine Bilder.
17. **GraphQL-Cache:** nach Mutationen zurückgegebenes Order-Objekt in den Cache schreiben/refetchen (sonst `activeOrder`-Staleness im Warenkorb).
19. **Codegen-Script** einrichten + bei jeder Custom-Field-/Schema-Änderung neu generieren (End-to-End-Typen inkl. CF).

---

## 9. Umsetzungs-Etappen (Milestones)

| Etappe | Inhalt | Ergebnis/Nachweis |
|---|---|---|
| **M1 — Infra** | Docker-Compose: Postgres + Vendure server/worker, Admin-UI erreichbar | Vendure läuft auf Postgres im Container |
| **M2 — Katalog** | F1+F2+F3 modelliert (physisch, Download, Seminar) + Custom Fields | Warenkorb, Versand-/Digital-Unterscheidung, Seminar-Zusatzdaten |
| **M3 — Recurring** | StripeSubscription-Plugin + eigene Strategy für F4/F5/F6 | Abo, Halbjahres-, 36-Monats-Plan über **Stripe** |
| **M4 — SEPA** | Mollie-Plugin + SEPA-Mandat + Worker-Recurring-Job | dieselben Fälle über **Mollie/SEPA**; reiner Rechnungslauf als Variante |
| **M5 — Vue** | Vue-Frontend, Codegen, je-Warengruppe-Flows inkl. Zusatzdatenformulare | End-to-End: Bestellung/Einschreibung aus dem Vue-UI |
| **M6 — Auswertung** | Showcase-Demo + Bewertung gegen die Kriterien der [Recherche](Showcase-Headless-Shop-Recherche.md) | Entscheidungsreife Aussage zu Eignung Vendure |

---

## 10. Offene technische Detailpunkte / Risiken

- **Recurring ohne Karte (reine Rechnung):** kein Plugin out-of-the-box → Worker-Job für Rechnungslauf selbst bauen (überschaubar, aber Eigenleistung). Im Showcase als „Variante" kennzeichnen.
- **Mollie-Mandat-Flow:** Erstzahlung-für-Mandat-UX im Vue-Frontend sauber abbilden (Mollie-Redirect).
- **Webhooks lokal:** Stripe/Mollie brauchen erreichbare Webhook-URLs → Tunnel (Stripe CLI/ngrok) für die Showcase-Demo.
- **Digital-Fulfillment:** Download-Auslieferung/Token — im Showcase einfacher Handler genügt; Produktiv-Entitlement (Moodle-Anbindung, Connector **S9**) ist später.
- **Steuer/E-Rechnung (DE):** USt-Konfiguration für die Demo; vollständige E-Rechnung (XRechnung/ZUGFeRD) bewusst **außer Scope** des Showcase (gehört an die FiBu-Schicht).
- **Vendure-Versionsstand:** Plugin-Kompatibilität (Pinelab/Mollie) gegen die eingesetzte Vendure-Major-Version prüfen.

---

## 11. Nächster Schritt
Sobald der Plan bestätigt ist: **M1 starten** — Docker-Compose + Vendure-Scaffold auf Postgres aufsetzen. Ich kann das Compose-Setup und die initiale `vendure-config.ts` (Postgres + Plugin-Registrierung + Custom-Fields-Deklaration) direkt anlegen.

---

## Quellen
- Vendure: [docs.vendure.io](https://docs.vendure.io/) · [Custom Fields](https://docs.vendure.io/guides/developer-guide/custom-fields/) · [Stripe Subscription Plugin](https://docs.vendure.io/plugins/stripe-subscription) · [Pinelab Plugin](https://plugins.pinelab.studio/plugin/vendure-plugin-stripe-subscription/)
- Mollie: [Recurring payments](https://docs.mollie.com/docs/recurring-payments) · [SEPA Direct Debit](https://docs.mollie.com/docs/sepa-direct-debit) · Vendure Mollie-Plugin: [@vendure/payments-plugin](https://docs.vendure.io/reference/core-plugins/payments-plugin/mollie/)
- Vue-Integration: [Vendure + Vue Storefront](https://vendure.io/blog/2022/01/vendure-vue-storefront-integration-v1-0)
