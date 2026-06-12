# Formularverwaltung-Showcase — Realisierungsplan

> **These:** Best-of-Breed-**Formularverwaltung** — typsichere, spezifikations­getriebene, testbare CRUD-/Verwaltungsmasken aus **einer** Quelle, **harmonisch zum Vendure-Stack**, ohne sie selbst zu programmieren. Konkrete Beispiel-Domäne: die **operative Seminarverwaltung** (Durchführung/Dozent/Raum/Teilnehmer) als MDM-Kern.
>
> Ausgegliedert aus dem [Controlling-Showcase](../controlling-planung/) (2026-06-12): die Kostendeckung **je Durchführung** bleibt fachlich im Controlling — dieser Showcase liefert die **operativen Stammdaten + die Naht** dorthin. Shop-Doku: [`../shop-planung/`](../shop-planung/). Enterprise-Planung: [`../enterprise-stack-planung/`](../enterprise-stack-planung/).

## 0. Tech-Stack & Versionen (verbindlich, Stand 2026-06-12 — noch nichts gebaut, vor Bau gegenprüfen [[verify-version-compatibility]])

Pin-Politik: konkrete Tags/Versionen, kein `latest`. Reuse, was Shop/Controlling schon bringen.

| Schicht | Technologie (Pin) | Quelle |
|---|---|---|
| Backend-Runtime | **Quarkus 3.33 LTS** (3.33.2), **JDK 21** (Temurin), Maven 3.9.4 | reuse M1-Runtime |
| Auto-CRUD/REST | `quarkus-rest`(+`-jackson`) · **`quarkus-rest-data-panache`** | 3.33-LTS-BOM |
| Persistenz | `quarkus-hibernate-orm-panache` · `quarkus-jdbc-postgresql` · `quarkus-flyway` | 3.33-LTS-BOM |
| Validierung (Quelle) | **`quarkus-hibernate-validator`** (Jakarta Bean Validation) | 3.33-LTS-BOM |
| Spec | **`quarkus-smallrye-openapi`** (`/q/openapi`) | 3.33-LTS-BOM |
| AuthZ | `quarkus-oidc` (Resource-Server, Realm `ebz-staff`) + `@PermissionsAllowed` | reuse Keycloak 26.2.5 |
| Tests (Backend) | `quarkus-junit5` + `quarkus-rest-assured` | 3.33-LTS-BOM |
| *(optional)* JVM-Client-Gen | `quarkus-openapi-generator` (Quarkiverse) — **nur** falls der Service eine fremde OpenAPI-API ruft | — |
| Frontend-Runtime | **Vue 3.5 · Vite 8 · TypeScript**, ausgeliefert per **nginx** (eigener Container) | wie Storefront |
| Komponenten | **PrimeVue 4.5** (+ `@primeuix/themes` Aura, primeicons) · vue-router · pinia | reuse Storefront |
| Formular + Validierung | **`vee-validate` 4 + `@vee-validate/zod`** · `zod` (generiert) | npm |
| Generator (TS+zod+Client) | **`@hey-api/openapi-ts`** (Orval-Alternative) | npm |
| Server-State | `@tanstack/vue-query` | npm |
| Tests (Frontend) | `vitest` | npm |
| DB | **Postgres 16-alpine**, Schema **`seminar`** im gemeinsamen `postgres`-Container | reuse Single-Postgres |
| IdP | **Keycloak 26.2.5** (Realm `ebz-staff`), neuer Public/PKCE-Client `seminar-cockpit` | reuse Shop, Port 8088 |

**Verbote (entschieden):** kein Quinoa (Docker-Container-Trennung statt SPA-im-Quarkus) · kein Orval · kein Hibernate Envers (erst einmal) · **kein hand-geschriebenes zod / kein Ajv-Adapter** (Stack B, s. §5).

## 1. Ziel / These

Zeigen, dass man die **Verwaltungsmasken eines Stammdaten-/MDM-Kerns nicht mehr selbst programmiert**, sondern aus **einer** Quelle generiert — und zwar so, dass es nach Wichtigkeit erfüllt:

1. **Typsicher** — durchgängig, Eingabe → Validierung → Request ein Typ.
2. **Über die Spezifikation testbar** — die Spec (OpenAPI) ist prüfbarer Vertrag.
3. **Generierbar statt handgeschrieben** — Generator + offizielle Adapter, kein bespoke Glue-Code.

Leitlinien wie beim Shop/Controlling: on-prem, schlank, aktuelle Tech, harmonisch zum Vendure-Stack, „Commodity kaufen, nur den Differenzierer bauen".

**Beispiel-Domäne = operative Seminarverwaltung.** Auslöser: heute hängen Seminar-Kosten/Buchungen an der **Produktvariante** (Seminartyp), nicht an einer **Durchführung** → Kostendeckung wäre ein Durchschnitt über alle Buchungen aller Zeiten. Die operative Einheit **Durchführung** (Termin/Dozent/Raum/Kapazität) fehlt — genau der Stammdaten-Kern, an dem sich Formularverwaltung beweisen lässt.

## 2. Zielarchitektur

**Architektur-Entscheidung:** eigener **Quarkus-`seminar`-Service** + eigenes **Vue-Staff-Cockpit**, beide als **getrennte Docker-Container** in der bestehenden `showcase`-Compose. (Die schlankere Vendure-Plugin-Variante — alles im Shop-Stack, ein Backend/eine Transaktion — bleibt dokumentierte Alternative; bewusst verworfen zugunsten der Best-of-Breed-Story „zwei Domänen sauber komponiert".)

**Die nicht-verhandelbare Grenze (F1):**
```
Vendure  = System of Record für COMMERCE
  └─ Sitzplatz-Buchung = order_line · Zahlung · Raten (recurring-invoice) · SSO  — bleibt komplett hier.
Quarkus  = System of Record für OPERATIVE Seminar-Stammdaten (MDM-Kern)
  └─ SeminarRun/Termin/Dozent/Raum/Kapazität/Anwesenheit — Schema `seminar` im gemeinsamen Postgres.
Vue (Staff-Cockpit) → Quarkus (operativ) + liest Vendure wo nötig
  └─ Kalender, Lauf-Planung, Dozent/Raum, „trägt sich / trägt sich nicht"
Naht: order_line.seminarRunId (Custom-Field) → zeigt auf einen Quarkus-Lauf. Vendure kennt nur die ID.
```

```
┌─ BROWSER · Cockpit (eigener Container: Vue+Vite-Build → nginx) ─────────────────────────┐
│ ① Eingabe   ② PrimeVue 4 (Aura)   ③ vee-validate + @vee-validate/zod (toTypedSchema)     │
│ ④ Daten: @tanstack/vue-query auf dem generierten, typisierten Client                     │
└──────────────────────────── HTTP/JSON, durchgängig typisiert ───────────────────────────┘
          ▲  GENERIERUNG aus /q/openapi:  @hey-api/openapi-ts → TS-Typen + Client + zod
┌─ QUARKUS `seminar`-Service (eigener Container · 3.33 LTS · JDK 21) ──────────────────────┐
│ ⑤ smallrye-openapi → /q/openapi (JSON-Schema MIT Constraints)                            │
│ ⑥ rest(-data-panache) Auto-CRUD   ⑦ hibernate-validator = EINZIGE Quelle                 │
│ ⑧ hibernate-orm-panache (Entities)   ⑨ oidc + @PermissionsAllowed   ⑩ jdbc-pg + flyway   │
└──────────────────────────────────── JDBC ───────────────────────────────────────────────┘
          ▼   Postgres 16 · Schema `seminar` (gemeinsamer showcase-Container)
```

## 3. Datenmodell (Beispiel-Domäne)

```
SeminarRun (Durchführung)   productVariantId(FK Seminartyp) · code/title · status
                             PLANNED|CONFIRMED|RUNNING|DONE|CANCELLED · min/maxParticipants · raum · notes
SeminarSession (Termin)     seminarRunId · startsAt/endsAt        (mehrtägig = mehrere Sessions je Run)
SeminarTrainer + RunTrainer (n:m)                                 (Honorar treibt Fixkosten)
SeminarCost (aus Controlling-M2)  + seminarRunId NULLABLE   null→Template je Variante · gesetzt→Ist/Override je Lauf
```

## 4. Der MDM-Generator-Spine

„Schema einmal → API/Validierung/Client/Maske nach unten":
- **`quarkus-rest-data-panache`** → CRUD-REST je Entität aus *einem* Resource-Interface (0 Boilerplate), **`@PermissionsAllowed` pro Operation** (Quarkus ≥3.31, RBAC).
- **`quarkus-smallrye-openapi`** → `/q/openapi` als Vertrag; **Bean Validation wird ins JSON-Schema gespiegelt** (`@NotNull`→required, `@Size`→min/maxLength, `@Pattern`→pattern, `@Min/Max`→minimum/maximum, `@Email`→format, Enums→`enum` …).
- **Deployment:** Quarkus-Backend + Vue-Cockpit als **getrennte Compose-Container** (nginx serviert die SPA). Kein Quinoa.
- **Pro neuer Stammdaten-Entität ändert sich nur die Entity (+ Bean-Validation)** — Typen, Client, zod und Maskengerüst fallen ab.

## 5. Validierungs-/Generierungskette (Stack B — final, keine Alternative)

```
Entity + Bean-Validation (@NotBlank/@Size/@Min/@Pattern/enum)   ← EINZIGE Handarbeit & Quelle
  → quarkus-smallrye-openapi  →  /q/openapi   (JSON-Schema MIT Constraints)
  → @hey-api/openapi-ts (+zod-Plugin)  →  TS-Typen + API-Client + zod-Schemas
  → Vue: vee-validate 4 + @vee-validate/zod
         useForm({ validationSchema: toTypedSchema(zod.SeminarRun) }) + PrimeVue-Inputs
         Daten via @tanstack/vue-query auf dem generierten Client
```

- **Ein `pnpm gen`** regeneriert Typen+Client+zod aus der laufenden Spec.
- **Typsicher (Krit. 1):** `z.infer<typeof zod.SeminarRun>` = Form-Wert-Typ **und** API-Modelltyp (eine Generierung) → kein Bruch.
- **Über Spezifikation testbar (Krit. 2), zwei Tests:**
  1. **Backend rest-assured:** `/q/openapi` trägt `required`/`minLength`/`pattern`/`enum` → beweist, dass Bean Validation in der Spec ankommt (Single-Source verifiziert, fängt vergessene Annotationen).
  2. **Frontend vitest:** generierte zod gegen valid/invalid-Fixtures (aus Spec-`example`s) → beweist Vertrags-Durchsetzung + schließt die JSON-Schema→zod-Lücke.
- **Generierbar (Krit. 3):** null bespoke Glue — Generator + offizieller `@vee-validate/zod`-Adapter; vollständig scaffold-bar.
- **Warum nicht der Ajv-`TypedSchema`-Adapter?** Reiner (validiert direkt das Schema), aber **handgeschriebener** Glue → fällt durch Krit. 3 und koppelt die Typen nicht so stark wie `z.infer`. Verworfen.
- **L-Grenze:** **kein hand-geschriebenes zod** (zweite, driftende Wahrheit neben Bean Validation); **generiertes** zod ist konform. **Cross-Field-/Klassen-Constraints** (`@ScriptAssert`, eigene `ConstraintValidator`, „Ende ≥ Start") stehen **nicht** im JSON-Schema → bleiben **server-seitig** und erscheinen im Cockpit aus der **400-Violation-Antwort**.

## 6. Vendure-Naht + Kapazitätskonsistenz

- **Bindung:** OrderLine-Custom-Field `seminarRun` (Relation/ID), gesetzt beim `addItemToOrder` (wie heute `participantName/Email`) — **ohne Vendure-Core-Patch**.
- **Kapazität:** Soft-Check beim `addItem` gegen Quarkus, **harte Prüfung am Order-Placement** (= der eine Commit-Punkt); Quarkus **rekonziliert** per Event/Sync (kein verteiltes Dual-Write der Buchung; Letzter-Platz-Race am Placement entschieden).

## 7. Naht zum Controlling-Showcase (Übergabe, keine Doppelung)

Die **Kostendeckung je Durchführung** lebt fachlich im [Controlling-Showcase](../controlling-planung/). Dieser Showcase **liefert die Voraussetzung**:
- Das **Schema `seminar`** (run/session/trainer) + die Spalte **`order_line.seminarRunId`** stehen für dlt bereit.
- Das Controlling re-grained dann: **`fct_seminar_db` Grain Produktvariante → Durchführung**, neuer Mart **`fct_seminar_run_utilization`** (Auslastung, Deckungsgrad, Ampel *vor* dem Lauf), Erlös am **Session-Datum** (Europe/Berlin); CONFIRMED-Future-Run speist den Forecast. Kosten-Auflösung **run-override → sonst Template** (deterministisch, dbt-Unit-Test).
- **Datenvertrag** = die stabile Naht; das Re-Grain selbst ist **kein** Teil dieses Plans (designierter Übergabepunkt F4).

## 8. Milestones

> Eigenständige Sequenz; setzt nur den laufenden Shop-Postgres + Keycloak (`ebz-staff`) voraus. Baut **nach** dem Controlling-M2 auf (`seminar_cost` existiert), ist aber sonst unabhängig.

### F0 — Service-Gerüst + Cockpit-Gerüst
- **Wie:** Quarkus-`seminar`-Service als eigener Compose-Container (Profil z. B. `seminar`), Postgres-Schema `seminar`, `quarkus-oidc` als Resource-Server gegen `ebz-staff`, Flyway-Baseline. Vue-Cockpit-Container (Vite+PrimeVue+nginx) + neuer Keycloak-Client `seminar-cockpit` (public/PKCE).
- **Verifikation:** Service up, `/q/health` grün, `/q/openapi` erreichbar (leer); Cockpit lädt + SSO-Redirect.

### F1 — Generator-Spine an der ersten Entität (`SeminarRun`)
- **Wie:** `SeminarRun` + Bean-Validation-Annotationen + `rest-data-panache`-Resource + smallrye-openapi. `pnpm gen` (`@hey-api/openapi-ts`) → Typen+Client+zod. Die **zwei Spec-Tests** (rest-assured + vitest).
- **Verifikation:** CRUD über REST; `/q/openapi` trägt die Constraints (rest-assured grün); generierte zod akzeptiert/verwirft Fixtures (vitest grün).

### F2 — Cockpit-Masken (Liste/Detail/Edit) + weitere Entitäten
- **Wie:** PrimeVue-Masken + `vee-validate`+`@vee-validate/zod`(`toTypedSchema`) für `SeminarRun`; dann `SeminarSession`/`SeminarTrainer` → belegt „pro Entität nur die Entity ändern". RBAC via `@PermissionsAllowed`.
- **Verifikation:** Maske validiert clientseitig aus generierter zod; eine Cross-Field-Regel erscheint korrekt aus der 400-Antwort; zweite Entität in <1 Tag ergänzt.

### F3 — Vendure-Naht + Kapazität
- **Wie:** `order_line.seminarRun`-Custom-Field; Kapazitäts-Rekonziliation (Placement = Commit-Punkt).
- **Verifikation:** Buchung bindet einen Lauf; Kapazität bleibt konsistent (kein Overbooking; Warteliste bei voll).

### F4 — Übergabe ans Controlling (Datenvertrag)
- **Wie:** dlt liest zusätzlich `seminar` + `seminarRunId`; der **Datenvertrag** (Felder/Typen/Grain) wird dokumentiert. Das eigentliche Re-Grain passiert im Controlling-Showcase.
- **Verifikation:** dlt lädt `seminar`-Tabellen ins Warehouse; Übergabepunkt dokumentiert.

## 9. Leitplanken & Anti-Pattern

> Schweregrad 🔴 hoch / 🟠 mittel / 🟢 niedrig.

- 🔴 **F1 Grenze Commerce-SoR:** Vendure bleibt Buchungs-/Geld-SoR (order_line/Zahlung/Raten/SSO); der Quarkus-`seminar`-Service fasst **kein Geld** an. Kein zweites Buchungs-/Kundenmodell (sonst Odoo-Falle).
- 🔴 **F2 Kapazitätskonsistenz:** **Order-Placement in Vendure = der eine Commit-Punkt**; Quarkus rekonziliert per Event/Sync. Letzter-Platz-Race über **harte** Prüfung am Placement (addItem nur Soft-Check). Kein verteiltes Dual-Write.
- 🔴 **F3 Eine Validierungsquelle (Stack B):** **Bean Validation am Entity = einzige Wahrheit** → smallrye-openapi ins JSON-Schema → `@hey-api/openapi-ts` generiert zod → Frontend via `@vee-validate/zod`. **Kein hand-zod**; generiertes zod konform. Spec-Treue per zwei Tests (§5). Cross-Field-Regeln server-seitig (400).
- 🔴 **F4 Typsicherheit durchgängig:** `z.infer` = Form- **und** API-Typ aus *einer* Generierung; kein paralleles, handgepflegtes TS-Modell.
- 🟠 **F5 Generatoren sparen Masken, nicht MDM-Kern:** Match/Merge zum Golden Record, **Survivorship**, Dubletten, Lineage bleiben handgeschriebene Quarkus-Logik — kein Generator liefert das. Brücke zum Enterprise-MDM-Kern.
- 🟠 **F6 RBAC an der generierten CRUD:** `@PermissionsAllowed` pro Operation gegen `ebz-staff`; **Dozentenhonorare = sensibel** → nicht für jeden Staff sichtbar (Authn ≠ Authz).
- 🟠 **F7 DSGVO + (später) Historie:** Dozent/Raum = Stammdatum (kein Kunde); **keine Teilnehmer-PII** ins Warehouse. Audit/Lineage via Hibernate **Envers erst einmal NICHT** — optionale Ausbaustufe.
- 🟠 **F8 Erlös-/Re-Grain-Vertrag stabil halten:** Naht zum Controlling (Schema `seminar` + `seminarRunId`) ist der Vertrag; Kosten-Auflösung **run-override → sonst Template**, Erlös am **Session-Datum** (Europe/Berlin) — im Controlling getestet, hier nicht doppeln.
- 🟢 **F9 Versions-Disziplin:** Backend-Extensions aus dem Quarkus-**3.33-LTS**-BOM; Frontend `@hey-api/openapi-ts` + `vee-validate`/`@vee-validate/zod`/`zod` konkret taggen, gegen 3.33/Vue-3.5 prüfen ([[verify-version-compatibility]]). **Kein Quinoa/Orval/Envers/hand-zod.**
- 🔴 **F10 Polymorphie bleibt statisch typisiert:** Typ-Varianten (Bildungsangebot-Subtypen u. ä.) über **Vererbung/Subklassen** (Single Table → später Joined), **NICHT** über JSONB-/EAV-Attribut-Bags. Nur statische Felder tragen Bean Validation → OpenAPI → generierte zod/Maske. JSON-Attribut-Bags nur für seltene Ad-hoc-Zusatzfelder, nie für validierungs-/maskenrelevante Typ-Felder (sonst Bruch von F3/F4).
- 🔴 **F11 Polymorphie via per-Typ-API, nicht `oneOf`:** je Subtyp eigenes **flaches** Schema/Resource über einer STI-Registry; TS-Union frontend-seitig. **Kein `oneOf`-Supertype-Endpoint, kein `z.discriminatedUnion`** (Codegen-fragil: @hey-api-Lücke + zod-Deprecation, §11.9-A).
- 🟠 **F12 DTO statt roher Entity + Custom-Resources separat:** rest-data-panache nur für simple CRUD je Typ; Entities **nie roh exponieren** (Mass-Assignment); Custom-Endpoints (Projektion/Filter/Registry) als **eigene JAX-RS** (non-reactive ignoriert Interface-Methoden, §11.9-B).
- 🟠 **F13 Persistenz-Hygiene:** `@Version` (Optimistic Locking), **ASCII-Identifier**, **Enums als String ohne DB-Check** (`schema-update` ändert keine Constraints — M1-Bug), **Soft-Delete/`ARCHIVIERT`** statt Hard-Delete.
- 🟠 **F14 Cross-Field server-seitig + Schema je Typ fix:** Klassen-Validator → 400-Anzeige; Frontend-Schema je Typ **fix** (kein computed `toTypedSchema`, Typing-Verlust #4588).

## 10. Abgrenzung / designierte Pfade

- **Kostendeckung/Forecast je Durchführung** = [Controlling-Showcase](../controlling-planung/) (dieser Plan liefert nur Stammdaten + Naht).
- **Vendure-Plugin-Variante** (Seminarverwaltung als Shop-Plugin) = dokumentierte Alternative, bewusst zugunsten der Best-of-Breed-Komposition verworfen.
- **Low-Code-Plattformen** (NocoBase/Directus/NocoDB über Postgres) = dokumentierte „wenn-noch-schlanker"-Alternative; widerspricht der Quarkus+Vue-Entscheidung, daher hier nur als Hybrid-Hinweis.
- **Hibernate Envers / Match-Merge / Golden-Record-Logik** = spätere Ausbaustufen bzw. Enterprise-MDM-Kern.

## 11. PoC P1 — Verwaltungsmaske „Bildungsangebote" (MDM-Teilbereich, Stack-B-Test)

> **Zweck:** Stack B an einem realen, **polymorphen** MDM-Teilbereich testen (nicht flaches CRUD) — der erste konkret gebaute Slice. Ergebnis ist zugleich Beleg für die Enterprise-Kern-Weiche **#1 MDM als Make/Custom** (vgl. [Capability-Map](../enterprise-stack-planung/Zielarchitektur-Capability-Map.md)).

### 11.1 Abgrenzung
- **IN (MDM-Kern führt es als Stammdatum):** Bildungsangebote = **Seminar · Tagung/Kongress · Berufsschuljahr · Studiengang (zu Startsemester)** — die verkaufbaren Bildungsleistungen.
- **OUT (bleibt reiner Vendure-Katalog):** **Versand** (physische Medien, F1) + **digitale Medien** (F2) — Commerce-Commodity, **nicht** im MDM-Kern.
- Granularität: das **konkrete, verkaufbare Angebot** = eine Zeile (Studiengang *WS2026*, Berufsschuljahr *2026/27*), nicht der abstrakte Typ.
- Ebenen-Verhältnis: `Bildungsangebot` = **Katalog-/Stammdatenebene**; die operative `SeminarRun`/Durchführung (§3) hängt später unter einem Seminar-Bildungsangebot — **nicht** Teil von P1.

### 11.2 Modellierung — Persistenz **A (Single Table Inheritance)**, API **per-Typ (E-förmig)**
**Entkoppelte Entscheidung** (Konsequenz der Fallstrick-Recherche §11.9):
- **Persistenz = Single Table Inheritance** (`@Inheritance(SINGLE_TABLE)` + `@DiscriminatorColumn typ`): EINE Registry/Tabelle/ID für alle Bildungsangebote. Nullable Typ-Spalten sind architektur-konsistent (Validierung App-Layer, F3); spätere Normalisierung auf Joined = reiner DB-Refactor.
- **API/Codegen = per-Typ, KEIN `oneOf`-Supertype:** je konkretem Subtyp ein eigenes, **flaches** Schema/Resource (`/seminar`, `/tagung`, …) → `@hey-api` generiert je Typ saubere Typen+zod (**kein** `z.discriminatedUnion`). Dazu ein read-only **Registry-List** (gemeinsame Header-Felder). Die TS-Union baut das **Frontend selbst** (`type Bildungsangebot = Seminar | Tagung | …`, Komponentenwahl per `typ`).
- **Warum nicht der polymorphe `oneOf`-Endpoint:** an drei Stellen gleichzeitig brüchig (§11.9-A) — smallrye-Mapping-Keys, `@hey-api`-oneOf-Lücke (Union → `never`), `z.discriminatedUnion`-Deprecation. **Bewusst vermieden, nicht nur Fallback.**

> Verworfen: **C (Table-per-Class)** — keine gemeinsame Registry. **D (JSONB/EAV)** — bricht Typsicherheit/Generierung (F10). **Polymorpher `oneOf`-Supertype-Endpoint** — Codegen-fragil (§11.9-A).

### 11.3 Datenmodell
**Supertyp `Bildungsangebot` (gemeinsam):** `code` · `titel` · `bereich` (BERUFSSCHULE|HOCHSCHULE|AKADEMIE) · `kurzbeschreibung` · `status` (ENTWURF|AKTIV|ARCHIVIERT) · `gueltigAb/bis` · `verantwortlich` · `preisModell` (EINMALIG|ABO|RATEN → F3/F4/F5/F6) · `shopVerkauf` (bool) · `vendureProductId` (Naht) · `zielgruppe`.

| Subtyp (`typ`) | Typ-spezifische Felder (mit Beispiel-Constraints) |
|---|---|
| **SEMINAR** | `kategorie` (Enum) · `dauerUE` `@Min(1)` · `abschluss/Zertifikat` · `minTN/maxTN` |
| **TAGUNG** | `thema` · `terminVon/Bis` · `ort` · `programmUrl` `@Pattern(url)` · `maxTN` |
| **BERUFSSCHULJAHR** | `fachrichtung` · `schuljahr` `@Pattern("\\d{4}/\\d{2}")` · `jahrgang` · `beginn` · `schildNrwSchluessel` (Reporting-Bezug, nur Schlüssel) · `plaetze` |
| **STUDIENGANG** | `abschluss` (B.A./M.A.) · `studienform` (VOLLZEIT|DUAL|BERUFSBEGLEITEND) · `startsemester` `@Pattern("(WS|SS)\\d{4}")` · `regelstudienzeitSemester` · `akkreditierungBis` · `ratenAnzahl` · `plaetze` |

### 11.4 Stack-B-Kette (per-Typ über STI-Registry — robust statt `oneOf`)
```
STI-Registry (1 Tabelle) ── je konkretem Subtyp eine rest-data-panache-Resource + DTO
  → smallrye-openapi → /q/openapi   je Typ ein FLACHES Schema (kein oneOf)
  → @hey-api/openapi-ts → je Typ saubere TS-Typen + zod   (kein z.discriminatedUnion)
  → Cockpit: Typ wählen → FIXES useForm je Typ (kein computed Schema) → vee-validate gegen zod[typ]
  Registry-Liste: read-only Endpoint mit gemeinsamen Header-Feldern
  TS-Union: frontend-seitig  type Bildungsangebot = Seminar | Tagung | …
```
**PoC-Beweis (neu gefasst):** lässt sich die polymorphe Familie **als per-Typ-Endpoints über einer STI-Registry** typsicher + spec-getestet generieren? → robuster und ehrlicher als die `oneOf`-Frage; belegt den **Make-MDM-Kern für echte, nicht-flache Stammdaten**.

### 11.5 Verwaltungsmaske (UX)
- **Liste:** PrimeVue DataTable, Filter `typ`/`bereich`/`status`/`shopVerkauf`, Spalten code/titel/typ/bereich/status/shop.
- **Neu/Bearbeiten:** Schritt 1 **Typ wählen** → Schritt 2 gemeinsame + **typ-spezifische Felder (conditional)**; Client-Validierung aus generierter zod; **Cross-Field server-seitig** (`gueltigBis ≥ gueltigAb`, Startsemester-Plausibilität) → 400-Anzeige.
- **RBAC:** `@PermissionsAllowed("katalog:pflege")` gegen `ebz-staff`.

### 11.6 Naht (Kern → Satelliten)
- **Vendure:** `shopVerkauf=true` projiziert das Angebot als Produkt (preisModell → Variant/SubscriptionStrategy F3–F6), `vendureProductId` zurück. Versand/digitale Medien bleiben nativ in Vendure.
- **Controlling:** `Bildungsangebot` wird Profitabilitäts-Dimension (knüpft an `seminar_cost`/Durchführung).

### 11.7 Milestones (klein, testbar)
- **P1.0** Service + Schema `bildung`, Supertyp + **1 Subtyp (Seminar)** → Stack-B-Kette grün (2 Spec-Tests §5).
- **P1.1** **Polymorphie als per-Typ-Endpoints:** Subtypen Tagung/Berufsschuljahr/Studiengang als eigene Resources über der STI-Registry; je Typ saubere Typen+zod+Maske; Registry-Liste. **Kernbeweis** (kein `oneOf`/`discriminatedUnion`).
- **P1.2** Liste/Filter + RBAC + Cross-Field-400.
- **P1.3** Naht: eine Typ-Projektion nach Vendure (`shopVerkauf`).

### 11.8 Erfolgskriterien
- ✅ **Skalierungsbeweis:** ein neuer Subtyp = nur Entity + Bean-Validation + ein Resource-Interface → Typen/Client/zod/Maske fallen ab.
- ✅ Typsicher (`z.infer` = Form- **und** API-Typ je Typ) bis in die Maske; beide Spec-Tests grün.
- ✅ Schild-NRW (Berufsschuljahr): nur **Bezugsschlüssel**, kein Pflicht-Reporting nachbauen.

### 11.9 Fallstricke & Design-Entscheidungen (Recherche 2026-06, vor Bau)

- **A — Polymorphie-Codegen (🔴, design-vermieden):** `@hey-api` erzeugt aus `oneOf` **keine** sauberen Unions ([Lücke #3270](https://github.com/hey-api/openapi-ts/issues/3270); Mapping→`never` [#3399](https://github.com/hey-api/openapi-ts/issues/3399); „too complex" [#1613](https://github.com/hey-api/openapi-ts/issues/1613)); **`z.discriminatedUnion` wird abgekündigt** + nicht komponierbar ([zod #2106](https://github.com/colinhacks/zod/issues/2106)); smallrye-Mapping-Keys unzuverlässig. → **Entscheidung: per-Typ-API statt `oneOf` (§11.2), Union frontend-seitig.**
- **B — rest-data-panache (🟠):** (1) Entities **roh exponieren = Mass-Assignment-Risiko** ([#53263](https://github.com/quarkusio/quarkus/issues/53263)) → **DTO je Typ**, Entity nicht roh. (2) **Custom-Methoden im Resource-Interface werden (non-reactive) ignoriert** ([#30330](https://github.com/quarkusio/quarkus/issues/30330)) → Custom-Endpoints (Vendure-Projektion, Filter, Registry-Liste) als **separate JAX-RS-Resource**.
- **C — Persistenz/STI (🟠, teils M1-Erfahrung):** (1) `schema-management=update` ändert **keine Check-Constraints** (= M1-Bug) → **Enums als String ohne DB-Check** oder Flyway. (2) **`@Version`** (Optimistic Locking) ab P1.0 gegen Lost-Update. (3) **ASCII-Identifier** (`gueltigAb`, nicht `gültigAb`). (4) STI-Subtyp-Felder nullable → App-Layer-Validierung.
- **D — Validierung (🟠):** `typ` zusätzlich als **lesbares Enum-Feld** (der `@DiscriminatorColumn` ist nicht direkt validier-/anzeigbar); Cross-Field (`gueltigBis ≥ gueltigAb`, Startsemester) **server-seitig** (Klassen-Validator) → 400; `@Valid`-Cascade bewusst setzen.
- **E — Frontend (🟠):** `vee-validate`+`toTypedSchema` **verliert Typing bei *computed* Schema** ([#4588](https://github.com/logaretm/vee-validate/issues/4588)) → **fixes `useForm` je Typ / Komponententausch**, kein reaktiv umgerechnetes Schema.
- **F — MDM-Design (🟠):** `code` = natürlicher Schlüssel mit **Unique + Format**; **Soft-Delete/Status `ARCHIVIERT`** statt Hard-Delete (Vendure/Controlling referenzieren); Status als **Lebenszyklus** (ENTWURF→AKTIV→ARCHIVIERT), nicht frei; kein EAV (F10).
