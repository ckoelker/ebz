# Controlling-Showcase — Realisierungsplan

> Controlling-Erweiterung des bestehenden **`showcase/`-Stacks** — der **Shop-Backend (Vendure) ist integraler Teil** desselben Showcase und liefert die operativen Daten. Controlling-Code liegt **flach unter `showcase/`** (`integration/`, `dlt/`, `dbt/`, `lightdash/` — neben `vendure/`/`frontend/`), **eine** Compose, **ein** Postgres. Code: [../showcase/](../showcase/), Shop-Doku: [../shop-planung/](../shop-planung/), Memorys `showcase-shop-vendure.md` + `controlling-showcase.md`.
> Stand: 2026-06-12. Architektur konvergiert, Leitplanken §9 ergänzt, Bau noch nicht begonnen.
> Verwandt: Enterprise-Planung (`enterprise-stack-planung/`, §0-Decision-Log HubSpot + Architektur-Option „JVM-Integrations-/Kern-Layer").

## 0. Tech-Stack & Versionen (verbindlich, Stand 2026-06-12)

> Übergreifend für **M0–M5** recherchiert: jeweils **aktuellste stable**, die **zueinander** passt.
> **Pin-Politik:** konkrete Tags/Versionen in `compose`/`pom.xml`/`requirements.txt` festschreiben — **kein `latest`, keine offenen Ranges** (reproduzierbarer Showcase). Quelle: Herstellerseiten/Releases, Juni 2026.

| Komponente | Version (stable) | M | Pin-Ort | Harmonie / Begründung |
|---|---|---|---|---|
| Docker Engine / Compose | 29 / v5 *(lokal verifiziert)* | M0 | Host | vorhanden |
| **PostgreSQL** | **16-alpine** | M0 | compose (Shop) | **der EINE Shop-Postgres = Single Source of Truth**; Warehouse = **DB `controlling`** im selben Container (Port 6543), kein zweiter Postgres |
| Adminer | **Reuse Shop** (Port 8082) | M0 | showcase-compose | DB-UI des Shops mitgenutzt; kein eigener Adminer |
| **JDK** (Temurin) | **21** *(verifiziert)* | M1 | — | Baseline für Quarkus 3.33 LTS |
| **Maven** | **3.9.4** (≥3.9.x) *(verifiziert)* | M1 | — | Quarkus 3.33 verlangt Maven 3.9.x |
| **Quarkus Platform** (`quarkus-bom`) | **3.33 LTS** (3.33.2) | M1 | `pom` property | **LTS**, gepflegt bis **2027-03** |
| **Camel Quarkus** (Camel 4.18.2 LTS) | **3.33.x LTS** (platform-managed) | M1 | `pom` (kein expl. Ver.) | alignt mit Quarkus 3.33 LTS |
| **quarkus-langchain4j-ollama** | **1.10.0** | M1 | `pom` (explizit) | **gebaut auf Quarkus 3.33.1** ✔ |
| Hibernate ORM Panache · `jdbc-postgresql` | platform-managed (3.33) | M1 | `pom` | über Quarkus-Platform |
| **Ollama** (Server) | beim M1-Bau auf aktuellen Patch pinnen | M1 | compose | rasche Kadenz → Tag fixieren |
| **Ollama-Modell** | **`qwen2.5:7b-instruct`** | M1 | compose/Pull | instruct-getunt, mehrsprachig (DE-Firmennamen), bessere Feldgenauigkeit als 1–3B; + Ollama **structured `format`** (JSON-Schema) → §9 **L9** |
| HubSpot CRM API | **v3** (Private-App-Token) | M1 | extern | Real-Modus; sonst Fixture-Mock |
| **Vendure** (`@vendure/core` u. a.) | **3.6.4** *(= Shop, Reuse)* | M2 | `package.json` | Plugin lebt im Shop-Projekt |
| TypeScript / Node.js / `pg` | **5.8.2 / 22 LTS / 8.21** *(= Shop)* | M2 | Shop | identisch zur Storefront-Linie; **Paketmanager pnpm** |
| **Python** | **3.12** | M2/M3 | venv | gemeinsamer Nenner dlt + dbt |
| **dlt** (`dlt[postgres]`) | **1.27.2** | M2 | `requirements` | MIT, leicht; Postgres-Destination |
| **dbt-core** | **1.11.8** | M3 | `requirements` | „compatible track" Juni 2026 |
| **dbt-postgres** | **1.10.0** | M3 | `requirements` | **offiziell mit core 1.11.8 gebündelt** |
| **Lightdash** | **0.3059.0** | M4 | compose | Tag **pinnen** (nicht `latest`) |
| **Keycloak** | **26.2.5** *(Reuse Shop, Port 8088)* | M4 | showcase-compose | **keine zweite IdP**; nur neuer Client `lightdash` (L24) |

**Kompatibilitätsketten (das eigentlich Wichtige):**
- **M1 LTS-Triade:** Quarkus **3.33 LTS** ↔ Camel Quarkus **3.33 LTS** (Camel 4.18.2) ↔ quarkus-langchain4j **1.10.0** (auf 3.33.1 gebaut). Bewusst **LTS statt 3.35** (non-LTS) → der Showcase steht auf einer ein-Jahr-gepflegten Linie (Quarkus 3.33 EOL **2027-03**). Camel-Extensions ohne eigene Version deklarieren → die Platform setzt die zu 3.33 passende Camel-Quarkus-Version; nur `quarkus-langchain4j-ollama` wird mit `1.10.0` explizit gepinnt.
- **M3 dbt-Paarung:** **dbt-core 1.11.8 + dbt-postgres 1.10.0** = die offiziell zusammen freigegebene „compatible-track"-Paarung (Juni 2026) — nicht mischen mit dbt-postgres-Pre-Releases (1.11.0b1).
- **Ein Postgres (Single Source of Truth):** **ein** `postgres`-Container hält **drei DBs** — `vendure` (Shop), `controlling` (Warehouse) und `lightdash` (BI-Metadaten) — je mit **eigenem User**. dlt liest `vendure` über einen **read-only-User** `controlling_reader` (L20). Variablen zentral in `showcase/.env`. Kein zweiter Postgres, kein Port 6544.
- **Keycloak nicht hochziehen:** **26.2.5** aus dem Shop wird wiederverwendet (Reuse-Pflicht §6) — der laufende Realm `ebz-staff` darf nicht durch ein Upgrade riskiert werden; es kommt nur ein neuer OIDC-Client hinzu.

## 1. Ziel

Ein on-premise, schlanker Showcase, der drei Controlling-Fähigkeiten am realen Best-of-Breed-Stack zeigt:

1. **Seminar-Profitabilität** — Kosten je Seminar gegen Erlöse → Deckungsbeitrag, Break-even, Gewinn/Verlust.
2. **Inhouse-Angebote / Pipeline** — potenzielle Umsätze aus HubSpot, erwartungsgewichtet.
3. **Unternehmensweiter Forecast** — Ist + vertraglich gesichert + gewichtete Pipeline − Kosten → Monats-GuV.

Leitlinien: **on-prem Pflicht**, schlank/kostenlos, aktuelle Tech, **harmonisch zum Vendure-Stack** — und „Commodity kaufen, nur den Differenzierer bauen" (Capability-Map der Enterprise-Planung).

## 2. Zielarchitektur

Zwei bewusst **unterschiedliche Integrationsmuster** — der Showcase demonstriert, *wann welches*:

- **Commodity-EL** (plain extract+load) → **dlt** (MIT, federleicht).
- **Differenzierer-Ingestion** (semantische Konvertierung, Entity-Resolution) → **Camel Quarkus + LangChain4j** (die JVM-Integrations-Option der Enterprise-Planung, hier praktisch erprobt).

```
 QUELLEN                         INGESTION                        WAREHOUSE        TRANSFORM        BI
 ─────────                       ─────────                        (DB controlling) (dbt Core)       (Lightdash)
 Vendure-DB                                                       ┌───────────┐
  • orders / order_lines   ──►  dlt  (Commodity-EL)         ──►   │ stg_vendure│   ┌──────────┐
  • recurring installments      Vendure → Warehouse               │ _*        │   │ marts/   │
  • seminar_cost (Plugin)                                         │           │   │ fct_*    │  ──► Dashboards
                                                                  │           │   │ DB-Stufen│      • Seminar-P&L
 HubSpot (Deals)          ──►  Camel Quarkus + LangChain4j  ──►   │ stg_hubspot│   │ Forecast │      • Break-even
  (Inhouse-Pipeline)            (Pull + KI-Konvertierung           │ _deal     │   └──────────┘      • Forecast
                                 + Entity-Resolution)              │           │        ▲
 dbt seeds (CSV, on-prem)  ─────────────────────────────────►     │ seeds     │────────┘
  • Gemeinkosten-Plan                                             └───────────┘
  • Umlageschlüssel · Stage-Wahrscheinlichkeiten
 FiBu/DATEV (Ist-Gemeinkosten) ── designiert, später ──►  (dlt)
```

Alles on-prem in **der gemeinsamen `showcase/docker-compose.yml`** (Shop **und** Controlling, kein zweiter Compose). Der **Shop-Backend (Vendure)** ist Teil desselben Showcase und die operative Quelle; das **Warehouse ist die DB `controlling` im selben `postgres`-Container** (Single Source of Truth, kein zweiter Postgres). Kein Cloud-Dienst zwingend.
**Zugriff:** das BI (Lightdash) ist ausschließlich für EBZ-Mitarbeiter über **Keycloak (OIDC, `ebz-staff`-Realm)** erreichbar — kein anonymer Zugang (Detail M4).

## 3. Was kommt von wo (Datenflüsse)

| Forecast-Baustein | Quelle | Ingestion | Sicherheit |
|---|---|---|---|
| **Ist-Umsatz** (historisch + laufend) | Vendure `order`/`order_line` | **dlt** | hoch (gebucht) |
| **Gesicherter Zukunfts-Umsatz** | Vendure `installment` (recurring-invoice-Plugin) | **dlt** | hoch (vertraglich) |
| **Potenzieller Umsatz** | HubSpot Deals (Inhouse-Pipeline) | **Camel + LangChain4j** | gewichtet (Stage × p) |
| **Einzelkosten** je Seminar | Vendure `seminar_cost` (neues Plugin) | **dlt** | erfasst |
| **Gemeinkosten (Plan)** | dbt seed `overhead_plan.csv` | — (im dbt-Repo) | geplant |
| **Gemeinkosten (Ist)** | FiBu/DATEV | dlt (designiert, später) | gebucht |
| **Umlageschlüssel / Stage-p** | dbt seeds | — | Controlling-Setzung |

## 4. Kostenerfassung (Recap)

dbt/Lightdash **erfassen nichts** — sie rechnen/zeigen. Erfassung passiert in den Quellen:
- **Einzelkosten** (Honorar/Raum/Catering/Material, fix je Event **+** variabel je TN) → **Vendure seminar-cost-Plugin** (Admin). Die Entity **muss** `isVariable` + `perParticipant` führen — ohne diese Aufteilung ist kein Break-even rechenbar (§9 L18).
- **Gemeinkosten** → dbt-seed-CSV (Plan) bzw. FiBu (Ist), per **Umlageschlüssel** in dbt auf Seminare verteilt.
- dbt rechnet die **DB-Stufenrechnung**: `DB I = Umsatz − var. Einzelkosten` · `DB II = DB I − fixe Einzelkosten` · `Ergebnis = DB II − umgelegte Gemeinkosten`; Break-even = Fixkosten / DB I je TN.

## 5. Milestone-Sequenz (Reihenfolge + „Wie")

> Gesamtreihenfolge: **M0 → M1 (HubSpot/Camel/LC4j, zuerst gebaut) → M2 (Vendure + dlt) → M3 (dbt) → M4 (Lightdash) → M5**.
> M1 und M2 sind nach M0 fachlich unabhängig (parallelisierbar); gebaut wird **M1 zuerst** (Differenzierer, User-Priorität).
> M3 setzt M1 **und** M2 voraus (joint beide Quellen). M4 setzt M3 voraus.

### M0 — Fundament (Warehouse-DB + Ordner)
- **Wie:** Kein neuer Service/Port — der **eine Shop-`postgres`-Container ist die Single Source of Truth**. Das initdb-Script (`showcase/postgres/initdb/`) legt darin die DBs **`controlling`** (Warehouse) und **`lightdash`** (BI-Metadaten) an, **je mit eigenem User**, plus einen **read-only-User `controlling_reader`** auf `vendure` (L20). Creds/Ports zentral in **`showcase/.env`**. **Adminer** (8082) und **Keycloak** (8088) des Shops werden mitgenutzt. Controlling-Code liegt **flach unter `showcase/`**: `integration/`, `dlt/`, `dbt/`, `lightdash/` (neben `vendure/`/`frontend/`, **kein `controlling/`-Zwischenordner**).
- **Abhängig von:** laufender Shop-Stack (`postgres`).
- **Verifikation:** `docker compose up -d`, Postgres healthy, DB `controlling` in Adminer sichtbar (leer).

### M1 — HubSpot-Ingestion: Camel Quarkus + LangChain4j  *(Differenzierer — zuerst)*
- **Wie:** Quarkus-Service in `showcase/integration/`.
  - **Camel-Route** (`camel-quarkus-timer` + `-http` + `-jackson`): zeitgesteuerter Pull. **Default Fixture-Mock** (`fixtures/hubspot-deals.sample.json`), **realer HTTP-Pull** sobald `HUBSPOT_TOKEN` gesetzt (Private-App-Token, v3 CRM API).
  - **LangChain4j AI-Service** (`quarkus-langchain4j-ollama`, on-prem Ollama-Container): Deal → strukturierte Felder (`seminarKategorie`, `deliveryType` inhouse/offen, **normalisierter Firmenname** für Entity-Resolution, `konfidenz`). **Robuster Fallback** (regelbasiert) wenn LLM nicht erreichbar → `enrichedBy=fallback`.
  - **Persistenz:** Upsert in `stg_hubspot_deal` (JPA/Panache) im Warehouse.
- **Abhängig von:** M0.
- **Verifikation:** `mvn package` grün (Kompilierung); Service zieht Fixture, reichert an, schreibt N Deals (in Adminer prüfbar); LLM best-effort (Fallback dokumentiert).
- **Externe Deps:** HubSpot Private-App-Token (für Real-Modus), Ollama-Modell-Pull (für echte KI-Konvertierung) — beides optional dank Mock/Fallback.
- **Härtung (§9):** L4 inkrementell+Pagination+Backoff · L5 idempotenter Upsert auf Deal-ID · L6 Inhouse-Pipeline filtern + EUR/netto · L7 Stage-p aus Seed · **L8 LLM nie für Zahlen** · L9 Structured-Output+Enum-Validierung · L10 Entity-Resolution kein Auto-Merge + Provenance · L11 PII nur on-prem-LLM · L12 Delta-Cache + Prompt-Injection-Schutz.

### M2 — Vendure-Anbindung: Seminar-Kosten-Plugin + dlt-Load  *(Commodity-EL)*
- **Wie, zwei Teile:**
  - **2a — Vendure seminar-cost-Plugin** (`showcase/vendure/src/plugins/seminar-cost/`): Custom-Entity `SeminarCost` (Kostenart, Betrag, fix/variabel, Bezug zum Seminar-Produkt/Variant), Admin-API zum Erfassen. Reuse der vorhandenen Seminar-Produkte (F3, `fulfillmentType=seminar`). Seed um Beispielkosten ergänzen.
  - **2b — dlt-Pipeline** (`showcase/dlt/vendure_to_warehouse.py`): DB `vendure` → DB `controlling` (im selben Postgres), Tabellen `order`, `order_line`, `installment`, `seminar_cost`. **Bewusst dlt statt Camel** = gelebte Leitlinie „Commodity kaufen": plain EL, inkrementell, on-prem, MIT. (L20: dlt nutzt den **read-only-User `controlling_reader`** auf `vendure` und schreibt als `controlling` nach `controlling`.)
- **Abhängig von:** M0 (Warehouse); 2b auf 2a (Kostentabelle muss existieren).
- **Verifikation:** Admin legt Beispielkosten an; `dlt run` lädt die 4 Tabellen; Zeilen in Adminer; kleiner Smoke (Zeilen > 0, Beträge plausibel).
- **Härtung (§9):** L18 `isVariable`/`perParticipant` erzwingen (Pflichtfelder) · L19 DB II vor Umlage als „harte" Wahrheit · L20 Vendure-Quelle read-only/Replica, Schema-Kopplung pinnen.

### M3 — dbt Core: Transform + Forecast-Logik  *(das Gehirn)*
- **Wie:** dbt-Projekt in `showcase/dbt/`.
  - **staging/**: `stg_vendure_orders`, `stg_vendure_installments`, `stg_vendure_seminar_cost`, `stg_hubspot_deals` (aus M1) — säubern, Cent→€, Typisierung.
  - **seeds/**: `overhead_plan.csv`, `allocation_keys.csv`, `stage_probabilities.csv`.
  - **marts/**: `fct_revenue_actual`, `fct_revenue_contracted`, `fct_pipeline_weighted` (Deal × Stage-p), `fct_seminar_db` (DB-Stufen + Break-even je Seminar), `fct_forecast` (Monats-GuV = Ist + gesichert + gewichtete Pipeline − Kosten-Run-Rate).
  - **tests**: not_null/relationships + Uniqueness aufs Grain + **No-Double-Count-Test** (Buckets paarweise disjunkt, L1) + Umlage-Reconciliation (L16) + Werte-Test gegen das Rechenbeispiel.
- **Abhängig von:** **M1 und M2** (beide Quellen im Warehouse).
- **Verifikation:** `dbt build` grün; Marts gefüllt; Beispiel-Seminar (600 €/TN, Fix 1.800 €, var. 25 €/TN, Umlage 800 € → Break-even 5 TN) rechnerisch korrekt im Mart.
- **Härtung (§9):** **L1 Drei-Bucket-Recognition** (kein €-Doppelzählen) · L13 Grain+Uniqueness · L14 Date-Spine · L15 Monats-Bucketing Europe/Berlin (UTC-Quelle!) · L16 Umlage Div-0 + Reconciliation · L17 eine Währung erzwingen.

### M4 — Lightdash  *(Self-Serve-BI, Keycloak-gesichert)*
- **Wie:** Lightdash self-host (Docker) auf das dbt-Projekt + Warehouse. **Metadaten in eigener DB `lightdash`** (eigener User) im selben Postgres; liest die Marts aus DB `controlling`. Metriken/Dimensionen als YAML neben den dbt-Modellen. Dashboards: **Seminar-P&L + Break-even-Linie**, **Unternehmens-Forecast** (gestapelt Ist/gesichert/gewichtete Pipeline minus Kosten), Drilldown Unternehmen → Bereich → Seminar.
- **Zugriff nur für EBZ-Mitarbeiter über Keycloak (Pflicht):**
  - Lightdash mit **generischem OIDC** an den **bestehenden `ebz-staff`-Realm** (Shop, Port 8088) anbinden — neuer OIDC-Client `lightdash` im Realm-Import (`showcase/vendure/keycloak/realms/`), Redirect-URI auf den Lightdash-Port.
  - **Passwort-Login deaktivieren** (`AUTH_DISABLE_PASSWORD_AUTHENTICATION=true`) → **SSO-Zwang**, kein anonymer/lokaler Zugang. JIT-Provisioning legt Mitarbeiter beim ersten Login an.
  - Zugang auf Mitarbeiter eingrenzen über Realm-Mitgliedschaft (`ebz-staff`); optional zusätzlich auf eine KC-Gruppe/Rolle (z. B. `controlling`) beschränken und als Lightdash-Rolle mappen.
  - Strikte Trennung bleibt gewahrt: Kunden-Realm `ebz-customers` hat **keinen** Zugriff (analog SSO-Negativtest im Shop).
- **Abhängig von:** M3; laufender Keycloak aus dem Shop (`ebz-staff`).
- **Verifikation:** Lightdash-Container startet, verbindet dbt, Dashboards rendern mit echten Marts. **Security-Smoke:** anonymer Aufruf wird auf Keycloak umgeleitet; Login `staff/staff` (ebz-staff) gewährt Zugriff; Passwort-Login-Maske ist weg; ein `ebz-customers`-Token wird **nicht** akzeptiert.
- **Härtung (§9):** L21 Bootstrap-Admin VOR Passwort-Login-Aus (kein Lockout) · L22 JIT-Default-Rolle = Viewer, nicht Admin · **L23 Authn≠Authz: Gehälter in Gemeinkosten → Unternehmens-GuV nur KC-Gruppe `controlling`, Lightdash-Spaces je Sichtbarkeit** · L24 OIDC-Client `lightdash` getrennt von `sso-staff` · L25 Adminer/Postgres/Ollama nicht öffentlich, Reverse-Proxy+HTTPS.

### M5 — Auswertung / Demo
- **Wie:** Kurzbewertung gegen die Ziele; expliziter Vergleich der **zwei Integrationsmuster** (dlt-Commodity vs. Camel+LangChain4j-Differenzierer) als Erkenntnis für die Enterprise-Integrationsschicht.
- **Verifikation:** End-to-end-Demo (HubSpot-Deal → angereichert → Forecast verändert sich; Seminar-Kosten → DB/Break-even sichtbar).

## 6. Endzustand der Compose (on-prem)

**Eine** `showcase/docker-compose.yml` enthält am Ende — zusätzlich zu den Shop-Services (`postgres` mit den DBs `vendure`, `controlling` **und** `lightdash`, `server`, `worker`, `keycloak`, `adminer`) — die Controlling-Services: **`integration`** (Quarkus/Camel/LC4j) · **`ollama`** (on-prem LLM) · **`lightdash`** (+ dessen interne DB), **abgesichert über Keycloak OIDC (`ebz-staff`)**. **Postgres, Adminer und Keycloak** werden vom Shop mitgenutzt (kein zweiter Postgres/Adminer/IdP). dlt und dbt laufen als geplante Jobs/CLI (kein Dauer-Service) aus `showcase/`.

## 7. Abgrenzung / designierte Pfade

- **Management-Controlling** (DB/Forecast), **keine doppelte Buchführung/Bilanz** — statutarische GuV + DATEV bleiben die „Naht" zur FiBu (Ist-Gemeinkosten später via dlt).
- **Echtzeit-/operative HubSpot↔Vendure-Flows** (Deal-Won → Vendure-Auftrag, Webhooks) sind operativ und gehören in die n8n/Quarkus-Flow-Orchestrierung — hier **nicht** Teil des Forecast-Showcase.
- **Airbyte** nur als designierte Ausbaustufe für *breite* Enterprise-Integration (viele Quellen) — im Showcase bewusst dlt.
- **Operative Seminarverwaltung ausgegliedert:** Durchführung/Dozent/Raum/Teilnehmer + die MDM-Verwaltungsmasken liegen im eigenen **[Formularverwaltung-Showcase](../formularverwaltung-planung/)** (Quarkus-`seminar`-Service + Vue-Cockpit). Die **Kostendeckung je Durchführung** (Re-Grain `fct_seminar_db` Grain → Durchführung, neuer Mart `fct_seminar_run_utilization`, Erlös am Session-Datum) ist designierte **Erweiterung dieses Controllings**, getriggert über dessen Datenvertrag (Schema `seminar` + `order_line.seminarRunId`).

## 8. Externe Abhängigkeiten (wie Stripe/Keycloak im Shop)

- **HubSpot Private-App-Token** (Real-Modus M1) — sonst Fixture-Mock.
- **Ollama-Modell** (echte KI-Konvertierung M1) — sonst regelbasierter Fallback.
- **Laufender Keycloak aus dem Shop** (`ebz-staff`-Realm, Port 8088) für die Lightdash-Absicherung (M4) — neuer OIDC-Client `lightdash` im Realm-Import.
- Sonst alles self-contained in der gemeinsamen `showcase`-Compose.

## 9. Leitplanken & Anti-Pattern

> Vor Bau des jeweiligen Milestones zu beachten (vgl. §8a im Shop). Schweregrad 🔴 hoch / 🟠 mittel.
> **Die drei, die wirklich beißen:** L1 (Doppelzählung), L8/L10 (LLM produziert Zahlen / merged ungeprüft), L23 (jeder sieht Gehälter).

### 9.1 Umsatz-Recognition — die Kardinalregel (M3, Querschnitt)
- 🔴 **L1 Drei-Bucket-Statemachine:** `pipeline` (offener Deal) → `contracted` (gewonnen / Raten geplant) → `actual` (fakturiert/bezahlt). **Jeder € liegt pro Monat in genau einem Bucket.** Test: Buckets paarweise disjunkt.
- 🔴 **L2** Nur **offene** HubSpot-Deals der Inhouse-Pipeline zählen als `pipeline`; gewonnene/zu Vendure konvertierte raus (sonst potenziell *und* gesichert gezählt).
- 🟠 **L3** Raten periodengerecht: bezahlte Rate → `actual`, künftige → `contracted`; Monatsschnitt wandert (keine Upfront-Erfassung).

### 9.2 HubSpot-Ingestion (M1)
- 🔴 **L4** Real-Modus: inkrementell (Watermark `hs_lastmodifieddate`) + Pagination (`after`) + Rate-Limit-Backoff — vom Fixture-Mock verdeckt.
- 🔴 **L5** Idempotenter Upsert auf **Deal-ID** (`ON CONFLICT`), nie auf Name.
- 🟠 **L6** Nur die Inhouse-Pipeline filtern; Währung → **EUR erzwingen**, netto (passend zu Vendure-Channel); Fremdwährung laut scheitern.
- 🟠 **L7** Stage-Wahrscheinlichkeit aus Stage-Config/Seed, nicht hardcoden.

### 9.3 KI-Konvertierung / LangChain4j (M1)
- 🔴 **L8 LLM nur für Text** (Klassifikation/Normalisierung) — **NIE für Beträge/Daten/Wahrscheinlichkeiten**. Finanzwerte bleiben deterministisch.
- 🔴 **L9** Structured Output + **Enum-Allowlist** + Validierung; niedrige Temperatur; halluzinierte Werte verwerfen, nicht durchreichen.
- 🔴 **L10** Entity-Resolution: **kein Auto-Merge** in den Golden Record bei niedriger Konfidenz — Review-Pfad; Konfidenz + **Provenance** (Modell-/Prompt-Version, `enrichedBy`, Zeitstempel) speichern.
- 🔴 **L11** **PII nur on-prem-LLM** (Ollama) — kein Cloud-Endpoint mit Kontaktdaten (DSGVO).
- 🟠 **L12** Nur Deltas anreichern (Content-Hash-Cache); Deal-Notizen als **Daten** behandeln (Prompt-Injection), Output-Schema erzwingen.

### 9.4 dbt-Modellierung (M3)
- 🔴 **L13** Grain je Fakt definieren + Uniqueness-Test; Fan-out-Joins (1:n) vermeiden.
- 🟠 **L14** Date-Spine für lückenlose Monate (auch ohne Aktivität).
- 🟠 **L15** Monats-Bucketing in **Europe/Berlin** (Vendure speichert UTC → Off-by-one an Monatsgrenzen).
- 🟠 **L16** Umlage: Division-durch-0 (Seminar ohne Umsatz) abfangen; **Reconciliation-Test** (Σ umgelegte Gemeinkosten = Σ Gemeinkosten).
- 🟠 **L17** Eine Währung erzwingen; Tests über `not_null` hinaus (Summen-Checks, No-Double-Count L1).

### 9.5 Kostenmodell (M2)
- 🔴 **L18** `seminar_cost`-Entity **muss** `costType`, `amount` (Cent), `isVariable`, `perParticipant` führen — sonst kein Break-even.
- 🟠 **L19** DB II (vor Umlage) ist die harte Wahrheit; Umlageschlüssel dokumentiert + umschaltbar.
- 🟠 **L20** Vendure-Quelle read-only/Replica; Schema-Kopplung beim DB-Lesen pinnen.

### 9.6 Lightdash + Security (M4)
- 🔴 **L21** Vor Deaktivieren des Passwort-Logins **Bootstrap-Admin via SSO** sichern (kein Lockout).
- 🔴 **L22** JIT-Default-Rolle = **Viewer/Member, nicht Admin**; Elevation explizit.
- 🔴 **L23 Authn ≠ Authz:** Gemeinkosten enthalten **Gehälter** → Unternehmens-GuV nur für KC-Gruppe `controlling`; Lightdash-Spaces/Rollen je Sichtbarkeit. Keycloak regelt *wer rein darf*, Lightdash *wer was sieht*.
- 🟠 **L24** Eigener OIDC-Client `lightdash`, getrennt von der Vendure-`sso-staff`-Rolle.
- 🟠 **L25** Adminer/Warehouse-Postgres/Ollama **nicht öffentlich** exponieren; Reverse-Proxy + HTTPS (Dev-Cookie nicht `secure`).

### 9.7 Orchestrierung / Ops / DSGVO (Querschnitt)
- 🟠 **L26** Reihenfolge erzwingen: dlt-Load → dbt-build → BI-Refresh (dbt `source freshness`); kein Lesen halbgeladener Daten.
- 🟠 **L27** Secrets (HubSpot-Token, OIDC-Secret, DB-Creds) **nicht im Compose-Klartext** → `.env`, gitignore.
- 🟠 **L28** Observability: Row-Counts, dbt-Tests als Gate, Run-Status/Logs.
- 🔴 **L29** **PII-Minimierung:** nur Firma/Betrag/Stage ins Warehouse; Kontakt-PII möglichst nicht replizieren; Retention/Löschkonzept.
- 🟢 **L30** Scope-Disziplin: kein Voll-FP&A/ERP nachbauen; Quarkus native-image optional (JVM-Modus reicht).