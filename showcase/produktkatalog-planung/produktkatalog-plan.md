# Produktkatalog: ebz-training.de-Funktionen mit Vendure abbilden

> Status: **Roadmap abgestimmt 2026-06-18**; **P1-Detail-Ausführungsplan abgestimmt + freigegeben 2026-06-18** (7 Frage-Blöcke). Realisierung P1 als Nächstes. Nur Konzept/Planung — noch kein Code.

## Context
Kundenfrage: ebz-training.de-Funktionen mit Vendure abbilden. ebz-training.de = filterbarer **Schulungs-Katalog mit Buchung**: ~400 Angebote in vielen Formaten (Seminar, Zertifikatslehrgang, Fachtagung, Arbeitskreis, Führungsforum, E-Learning/EBZ4U, Studium, Kompaktstudiengang …), Filter **Thema/Branche/Region/Veranstaltungsart**, **Freitext-/Angebotsnummer-Suche** + Pagination, **mehrere Termine/Orte je Angebot** mit Preisen, **Warenkorb**, Kundenkonto, Inhouse; Detailseiten mit Beschreibung/Inhalten, Bildern, Downloads, Ansprechpartner/Referenten. URL-Muster verifiziert: `https://www.ebz-training.de/<slug>?termin=<Terminnummer>`.

**Antwort: Ja**, Vendure bildet das nativ ab. Dieser Plan setzt die in 5 Frage-Batches abgestimmten Entscheidungen um.

## Entscheidungen (festgezurrt)
**SoR & Verknüpfung**
- **Vendure = alleinige Quelle** für Katalog-/Detailinhalt. **MDM nur über die Nummer** verknüpft: Terminnummer = Varianten-SKU (z. B. `SVA015729`), Angebotsnummer am Produkt. **Keine neuen MDM-Tabellen/Spalten.**
- Bestehende `VendureProjektion` (+ Tests) **auf reinen Nummern-Abgleich reduzieren** (kein Content-Push mehr).
- Nummern: **Showcase bildet Format `SVA…/LEG…` nach**, in Vendure vergeben.

**Sortiment & Buchung**
- **Alle Angebotstypen erscheinen im Katalog.** Seminar/Tagung/Lehrgang/E-Learning sind **bestellbar** (Warenkorb). **Vertragsangebote (Berufsschule/Studium) sind NICHT bestellbar** → statt Warenkorb-Button ein **„Anmeldung/Vertragserstellung"-Link** auf den bestehenden Anmelde-/Vertragsprozess.
- **Variante = buchbare Durchführung** (Ort/Datum/Format). Mehrtägige/Meilenstein-Angebote (Lehrgang/Tagung) = **eine** Variante mit Termin-Liste als Info.
- **Format (Präsenz/Online/Hybrid)** = Eigenschaft der Durchführung **und** Filter-Facette; ein Produkt kann Online- und Präsenz-Durchführungen haben.
- **Teilnehmer**: Sammelbuchung mit **Teilnehmerliste** (Firma bucht n Mitarbeiter).
- **Gastbuchung erlaubt** (Konto optional; Party-Kern legt provisorische Person an, später claimbar).
- **E-Learning** = bestehende **OpenOLAT-WBTs**: im Katalog als Produkte, Kauf führt in den vorhandenen **LMS-Einschreibungs-/WBT-Pfad** (LMS→Vendure-Projektion + Einschreibungs-Outbox wiederverwenden).
- **Keine Kapazitätslogik** im Showcase (Plätze max. informativ).

**Kommerz**
- **Steuer**: Vendure-**TaxCategory je Produkt** — USt-befreit (§4 Nr. 21 UStG, 0 %-Rate) / 19 % / 7 %. Channel **netto** erfassen (`pricesIncludeTax=false`), Storefront zeigt Brutto.
- **Bezahlung**: **beides** — Kauf auf Rechnung (B2B, über das bestehende interne Rechnungs-Gehirn/ZUGFeRD) **und** Stripe/SEPA (Selbstzahler).

**Personen**
- **Ansprechpartner/Dozent** = Vendure-Custom-Entities, **aus dem CRM-Party-Kern synchronisiert** (CRM bleibt Personen-SoR; Änderung an 1 Stelle wirkt auf alle Produkte).

**Front & Inhalt**
- **Reine Nuxt-SSR-Storefront** (SEO/Speaking-URLs/Prerender); ersetzt die bestehende Vite/Vue-SPA `showcase/frontend`. **HubSpot-CMS-Variante verworfen** (HubSpot bleibt Marketing/CRM).
- **Branding**: an ebz-akademie angelehnt (EBZ-Navy); **UI-Lib Nuxt UI 4**. **Sprache: nur Deutsch.**
- **URL-Schema**: `/<slug>?termin=<nr>` mit **canonical** auf die Produktseite.
- **CMS**: eigenes Vendure-Plugin **„ContentPage"** (ein Pflege-Backend); Nuxt rendert serverseitig.
- **Navigation**: responsive **Burger-/Hauptmenü** bündelt **Produktkategorien (Collections)** + **CMS-Seiten (`imMenu`)**.

**RBAC**
- **Globale Rollen**: „Katalog-Lesen" (nur `Read*`) und „Katalog-Pflege" (Create/Update/Delete). **Keine Channel-/Produktgruppen-Grenze** (Channel-Nebenwirkungen vermieden). Keycloak-Gruppe→Rolle im `KeycloakAdminAuthStrategy`.

**Umfang**
- **Repräsentative Beispiele je Typ** (inkl. der 3 verifizierten), **Vollimport ~400 später**. **Assets = Platzhalter.**
- **MVP zuerst** (s. Phasen): Modell+Seed → Nuxt-Gerüst → Suche/Filter → Detail+Warenkorb/Checkout; RBAC/Promotions/CMS+Menü/LMS-Kopplung danach.

## Mapping ebz-training → Vendure
| Funktion | Vendure | Ist |
|---|---|---|
| Angebotsarten | Product + Facet „Veranstaltungsart" | Naht ✓ |
| Filter Thema/Branche/Region/Format | Facets + FacetValues (Search) | ✗ |
| Freitext-/Angebotsnr-Suche, Pagination | `search`-Query (Name + SKU) | ✗ |
| Mehrere Durchführungen | ProductVariants (+ OptionGroup „Termin") | ✗ |
| Detailinhalt (Texte/Bilder/Downloads/Personen/Querverweise) | description + Custom-Fields + Assets + Relationen | ✗ |
| Kategorien-Browsing + Menü | Collections + ContentPage (Burger-Menü) | ✗ |
| Frühbucher | Promotion (custom Condition) | ✗ |
| Warenkorb/Buchung, Konto, SSO | Order-Flow + R7-Naht, Customer + Keycloak | ✓ |

## Detailseiten-Inhalt (verifiziert an 3 Live-Beispielen)
Blöcke unterscheiden sich je Typ v. a. in der Befüllung — Storefront rendert **bedingt je Veranstaltungsart** (Block erscheint, wenn Vendure-Feld/Asset gefüllt). Online-Seminar schlank (1 Durchführung, kein Referent, kein Bild); Zertifikatslehrgang (6 Module + Dozenten + Flyer-PDF, 1 buchbare Einheit); Fachtagung (mehrtägiges Programm + Speaker + Programm-PDF + Übernachtung im Preis). Quelle/Umfang für den Seed: `https://www.ebz-training.de/sitemap.xml`.

Verifizierte Beispiele: `…/linkedin-als-business-netzwerk-nutzen?termin=SVA015729` (Online-Seminar, 495 €), `…/kundenbetreuerin-im-mieterservice-ebz-quereinsteiger-programm-wohnungswirtschaft?termin=LEG001614` (Zertifikatslehrgang, 2.800 €), `…/sommerakademie-der-wohnungswirtschaft?termin=SVA015717` (Fachtagung, 990 €).

## Datenmodell
**MDM (`mdm`-Schema): KEINE neuen Tabellen/Spalten.** Link = Terminnummer (SKU)/Angebotsnummer ↔ `mdm.bildungsangebot.code` (Lese-Mapping).

**Vendure (eigene DB; `vendure-config.ts` Dev `synchronize`/Prod Migration):**
- **Product-Custom-Fields**: `angebotsnummer` (string, MDM-Link, such-/filterbar), `inhalteHtml`/`lernzieleHtml`/`nutzenHtml`/`methodikHtml`/`voraussetzungenHtml`/`foerderhinweisHtml`/`ablaufHtml`/`leistungenHtml`/`faqHtml` (Rich-Text), `zielgruppe`, `abschluss`; **Relationen** `ansprechpartner` (1) + `dozenten` (N:M) auf die Custom-Entities; `verwandteProdukte` (Querverweise); typ-spezifische Felder (`dauerUE`/`studienform`/`regelstudienzeitSemester`/`akkreditierungBis` …). Downloads/Bilder = **Assets**.
- **ProductVariant-Custom-Fields** (= Durchführung): `terminDatum`, `ort`, `veranstaltungsformat` (PRAESENZ/ONLINE/HYBRID); SKU = Terminnummer; Preis = Varianten-Preis; **TaxCategory** je Produkt (befreit/19/7). (Subscription-Felder bleiben.)
- **Custom-Entities** (eigenes Plugin `produktkatalog`): `Ansprechpartner` (name/telefon/email/fotoAssetId/crmPersonId), `Dozent` (name/vita/fotoAssetId/crmPersonId), `Bewertung` (productId/autor/text/sterne/datum) — Personen **per Sync aus dem CRM** befüllt/aktualisiert.
- **ContentPage** (CMS-Plugin, P6): slug/titel/inhaltHtml/metaTitle/metaDescription/published/`imMenu`/`menuTitel`/`menuSortierung`.
- **Laufzeitdaten** (idempotenter Java-Init): Facets `Veranstaltungsart/Thema/Branche/Region/Format` + Werte; Collections je Thema/Branche; TaxCategories; OrderLine-Custom-Fields für Teilnehmerliste (Sammelbuchung).

**OrderLine/Teilnehmer**: Sammelbuchung → mehrere Teilnehmer je Position (Name/E-Mail). Vendure-OrderLine-Custom-Fields `participantName/Email` vorhanden → ggf. auf Liste/mehrere Plätze erweitern.

## Architektur / Schwerpunkt
- Arbeit liegt in **Vendure-Konfiguration/Plugins** (Custom-Fields, Custom-Entities, ContentPage, CRM-Sync, Promotion) + **neuer Nuxt-SSR-Storefront** + **Java-Initializer (Integrationsbackend)**. MDM-Backend nur: Alt-Projektion abspecken + Nummern-Mapping im Buchungspfad.
- **>1 Variante braucht OptionGroup „Termin"** (sonst nur 1 Variante erlaubt).
- **Facets/Collections/TaxCategories = Laufzeitdaten** → idempotenter Init.
- **Vertragsangebote** (Berufsschule/Studium): im Katalog sichtbar, aber Detail-CTA = „Anmeldung/Vertragserstellung" → Deeplink in den bestehenden Anmelde-/Vertragsprozess (Portal), kein Warenkorb.

## Phasen (MVP-first)
### P1 — Vendure-Modell + reproduzierbare Java-Initialisierung (MVP-Basis) → **Detail unten (§ P1-Detail)**
Custom-Fields (Product/Variant) + Custom-Entities `Ansprechpartner`/`Dozent`/`Bewertung`; **reproduzierbarer, idempotenter Java-Initializer im Integrationsbackend** (`POST /shop/init`), der Vendure über die Admin-API (generierter GraphQL-Client) aufbaut: Grundkonfig + Facets/Collections/TaxCategories + voll ausgestattete Beispielprodukte je Kategorie + Vertragsangebote + Assets + Bewertungen. Alt-`VendureProjektion` auf Nummern-Abgleich reduzieren. **Löst `seed.mjs` ab.**
### P2 — Nuxt-SSR-Storefront-Gerüst  ✅ GEBAUT+verifiziert 2026-06-18 (Branch feature/produktkatalog-p1)
Umgesetzt: `showcase/storefront` (Nuxt 4 SSR, @nuxt/ui 4, EBZ-Navy, DE), SSR-Katalog (Shop-API `search`) + Detailseite Speaking-URL `/<slug>?termin=<sku>`+canonical+bedingte Rich-Text-Blöcke, Vertragsangebot→Anmelde-Deeplink, Shop-API server-only über Nuxt-Server-Routen, Compose-Service `storefront` (:3001). Playwright-SSR-Smokes grün. Offen in P3/P4: Codegen, Facetten-Filter, Warenkorb/Checkout, SSO.
Neue Nuxt-Storefront (Nuxt UI 4, EBZ-Navy, DE) ersetzt `showcase/frontend`: SSR, Speaking-URLs `/<slug>?termin=<nr>` + canonical, GraphQL-Codegen gegen Shop-API, Keycloak-SSO (Shop-Strategy), Warenkorb-/Checkout-Store portieren. Eigener Compose-Service.
### P3 — Katalog: Suche/Filter/Pagination  ✅ GEBAUT+verifiziert 2026-06-18 (Branch feature/produktkatalog-p1)
Umgesetzt: Server-Route `server/api/catalog` nutzt `search` mit `facetValueFilters` (AND zwischen Facetten, OR innerhalb), `collectionSlug`, `skip/take`, `sort` + liefert Facetten-/Collection-**Aggregat** (Counts) für die Sidebar. Katalogseite (SSR): Facetten-Sidebar (Veranstaltungsart/Thema/Branche/Region/Format) mit Counts, Freitext-/Angebotsnr-Suche, Sortierung (Relevanz/Titel/Preis), Pagination — **gesamter Filterzustand in der URL** (teilbar, Back-Button-fähig). Gotcha: `USelect` erlaubt keinen leeren Item-Value → `'relevanz'` als Default. Playwright: 3 neue SSR-/API-Smokes grün (6 gesamt). GraphQL-Codegen weiter offen (handgeschriebene Shop-Queries bisher ausreichend).
Ursprünglich geplant: `search`-Query (term, facetValueFilters, collectionSlug, skip/take, sort) → Trefferliste + Facetten-Sidebar (Thema/Branche/Region/Veranstaltungsart) + Freitext-/Angebotsnr-Suche + Pagination/Trefferzahl, SSR.
### P4 — Detailseite + Durchführungs-Auswahl + Warenkorb/Checkout (MVP-Abschluss)
Detail rendert alle Blöcke bedingt je Veranstaltungsart; Durchführungs-Varianten wählbar → Warenkorb (Sammelbuchung mit Teilnehmerliste) → Checkout (Kauf auf Rechnung **und** Stripe/SEPA), Gastbuchung. **Vertragsangebote**: „Anmeldung/Vertragserstellung"-Deeplink statt Warenkorb.
### P5 — RBAC (global)  ✅ GEBAUT+verifiziert 2026-06-18 (Branch feature/produktkatalog-p1)
Umgesetzt: globale Vendure-Rollen **`katalog-lesen`** (nur Read*) + **`katalog-pflege`** (ReadCatalog + Create/Update/Delete + Read Order/Customer/Settings), im Seed `ensureRole` (kein Channel-Scoping). `oidc-verifier` liest `realm_access.roles` (+ `groups`) → `VerifiedIdentity.roles`; `KeycloakAdminAuthStrategy` mappt Keycloak-Realm-Rolle `katalog-pflege`→`katalog-pflege`, sonst `katalog-lesen`, und **synchronisiert die Rolle bei jedem Login** (fremde Rollen am Nutzer bleiben unberührt). Live verifiziert über `authenticate`-Mutation: `staff`→ReadCatalog/CreateCatalog/UpdateCatalog/DeleteCatalog, `staff2`→nur ReadCatalog.
Ursprünglich geplant: Rollen „Katalog-Lesen"/„Katalog-Pflege"; `KeycloakAdminAuthStrategy` mappt Keycloak-Gruppe→Rolle. (Keine Channels.)
### P6 — CMS „ContentPage" + Burger-Navigation
ContentPage-Plugin (Entity + Dashboard-UI + Shop-API) + Nuxt-Navigation: Collections + `imMenu`-Seiten als responsives Burger-/Hauptmenü.
### P7 — E-Learning-Kopplung + Promotions
E-Learning-Produkte → bestehender LMS-Einschreibungspfad (OpenOLAT). Frühbucher = custom `PromotionCondition` (Tage vor `terminDatum`). CRM→Vendure-Personen-Sync.
### P8 — (später) Vollimport ~400 + Inhouse-Anfrage
Massenmigration aus Sitemap/Bestand; Inhouse „Angebot anfragen" → HubSpot/Controlling.

## P1 — Umsetzungsstand (GEBAUT + live verifiziert 2026-06-18)
P1 ist gebaut und gegen das laufende Vendure verifiziert (uncommitted). **Abweichung von §A0:** Der GraphQL-Client wurde auf Nutzerwunsch NICHT mit graphql-java-codegen (kobylynskyi) realisiert, sondern mit **`quarkus-smallrye-graphql-client` (Dynamic Client)** — Quarkus-nativ, keine Fremd-Runtime, „nur genutzte Operationen". Transport `integration/.../shop/VendureAdmin` (Raw-Login behält das Header-Token `vendure-auth-token`, dann Dynamic Client mit Bearer; Dokumente per DSL + Variablen-Maps). Sonst wie geplant: §A Modell+Plugin (Ansprechpartner/Dozent/Bewertung), §B `POST /shop/init` (`ShopInitService`/`KatalogBeispiele`/Asset-Multipart über `quarkus-rest-client`), §C Projektion auf Nummern-Abgleich, **seed.mjs abgelöst+gelöscht**. Verifiziert: Init 2× idempotent, alle 5 Smokes grün, Such-/Relations-/Bewertungs-/Asset-Checks, Nummern-Match-Test (200/409). Gotchas: nullable-`@Column(type)`, `VendureAdmin.vt()` für `!`/`[]`, expliziter Asset-MIME, `reindex→Job`.

## P1-Detail — Ausführungsplan (abgestimmt + freigegeben 2026-06-18)

### Kontext & Schnitt
Ziel: das **Shop-Backend reproduzierbar & idempotent initialisieren**. Der eigentliche „Aufbau" (Daten) liegt als **editierbarer Java-Code im Integrationsbackend** (`showcase/integration`) und treibt Vendure über die Admin-API. Die GraphQL-Operationen laufen über einen **aus dem Vendure-Admin-GraphQL-Schema generierten, typsicheren Java-Client** (graphql-java-codegen, s. § A0) statt handgeschriebener Query-Strings; **Transport + Superadmin-Login bleiben `bildung/vendure/VendureAdminApi`** (RestClient `configKey=vendure-admin`, Container-URL `http://server:3000`, Bearer-Token-Header `vendure-auth-token`). **Nur das Modell** (Vendure-Schema) bleibt in TypeScript (Vendure bietet keine Laufzeit-API für Custom-Fields/Entities).
- **Stufe 1 (jetzt):** je Produktkategorie 1–2 **voll ausgestattete** Beispielprodukte — alle Detailblöcke gefüllt, damit die spätere Storefront-Darstellung vollumfänglich demonstrierbar ist.
- **Stufe 2 (später):** Komplettimport **aller** Bildungsprodukte aus Website-Sitemap + **Bildungs-PDF** (s. u.).

### A) Vendure-Modell (TypeScript, minimal) — `showcase/vendure`
1. **Custom-Fields** in `src/vendure-config.ts` (Block `customFields`, bestehende bleiben):
   - **Product**: `angebotsnummer` (string, such-/filterbar), `inhalteHtml`/`lernzieleHtml`/`nutzenHtml`/`methodikHtml`/`voraussetzungenHtml`/`foerderhinweisHtml`/`ablaufHtml`/`leistungenHtml`/`faqHtml` (string, `ui:'rich-text'` — `ablaufHtml`=Programm/Module mehrtägiger Angebote, `leistungenHtml`=Übernachtung/Verpflegung bei Tagungen, `faqHtml`=FAQ-Akkordeon), `zielgruppe`, `abschluss`, typ-spezifisch `dauerUE`(int)/`studienform`/`regelstudienzeitSemester`(int)/`akkreditierungBis`(datetime); **Relationen** `ansprechpartner` (relation→Ansprechpartner), `dozenten` (relation list→Dozent), `verwandteProdukte` (relation list→Product).
   - **ProductVariant** (= Durchführung): `terminDatum`(datetime), `ort`(string), `veranstaltungsformat`(string, options PRAESENZ|ONLINE|HYBRID). Subscription-/fulfillment-Felder bleiben.
2. **Plugin** `src/plugins/produktkatalog/` (Vorbild `plugins/seminar-cost/`): Entities `Ansprechpartner` (name/email/telefon/`fotoAssetId`/`crmPersonId`) + `Dozent` (name/vita/`fotoAssetId`/`crmPersonId`) + `Bewertung` (productId/autor/text/sterne/datum); `produktkatalog.service.ts`/`.resolver.ts`/`api-extensions.ts` mit idempotenten Admin-Mutations `upsertAnsprechpartner`/`upsertDozent` (Schlüssel `crmPersonId`) + `upsertBewertung` + Queries (`ansprechpartner`/`dozenten`/`bewertungen(productId)` inkl. Aggregat Durchschnitt/Anzahl). `fotoAssetId` referenziert ein hochgeladenes Vendure-Asset (Vorschau via Asset-Resolver). Entities in `@VendurePlugin({ entities: [...] })` registrieren (Voraussetzung für die Relation-Custom-Fields). Plugin in `vendure-config.ts` eintragen.
3. **TaxCategory** ist Vendure-Stammdaten (kein Custom-Field) → setzt der Java-Initializer.

### A0) GraphQL-Client-Codegen (graphql-java-codegen, Maven) — generiert, aber NICHT bei jedem Build
- **Schema-Quelle**: Admin-GraphQL-Schema per Introspection ziehen → committen als `showcase/integration/src/main/graphql/vendure-admin.graphqls`. **Erst NACH** dem Vendure-Modell/Plugin (§ A) introspizieren, damit die Custom-Mutations `upsertAnsprechpartner`/`upsertDozent`/`upsertBewertung` + die neuen Custom-Fields im Schema sind. Refresh nur bei API-Änderung (kleines Helfer-Skript/Maven-Goal via Introspection).
- **Plugin**: `io.github.kobylynskyi:graphql-codegen-maven-plugin` (Version pinnen + Kette gegen Java 21/Quarkus 3.33 prüfen). Generiert Request/Response-POJOs + typsichere Query/Mutation-Klassen aus dem Schema; **operation-getrieben** (nur benötigte `.graphql`-Dokumente → schlanker Client).
- **Kein Regen bei jedem Build (Nutzerwunsch)**: generierte Sources in ein **versioniertes** Verzeichnis `src/generated/graphql-client` (Paket `de.netzfactor.ebz.controlling.integration.shop.gen`), via `build-helper-maven-plugin` als Source-Root. Codegen-Goal **nicht** im Standard-Lifecycle, sondern im **Profil** `-Pgql-codegen`. Normale `mvn package`/Container-Builds **kompilieren** nur die eingecheckten Sources (Target wiederverwendet); `clean` löscht sie nicht. **Neu generiert nur** per `mvn -Pgql-codegen generate-sources` (Schema-/API-Änderung) oder explizitem Komplett-Neubau.
- **Verwendung**: Initializer (§ B) + reduzierte Projektion (§ C) bauen ihre Queries/Mutations mit den generierten Typen, Versand über `VendureAdminApi` (Transport/Login unverändert). Handgeschriebene Query-Strings entfallen.

### B) Java-Initializer (Integrationsbackend) — der reproduzierbare „Shop-Aufbau"
Neues Package `de.netzfactor.ebz.controlling.integration.shop`:
- `web/ShopInitResource` — `POST /shop/init`, `@RolesAllowed("katalog-pflege")`, idempotent; Vorbedingungs-Check (412, wenn Vendure-Modell/Plugin fehlt); liefert Zusammenfassung (angelegt/aktualisiert/übersprungen).
- `service/ShopInitService` — Superadmin-Login (wie `VendureProjektion`) → idempotente `ensure*`-Schritte über den **generierten GraphQL-Client** (§ A0; Transport `VendureAdminApi`):
  1. **Grundkonfig + Stamm-Setup** (vollständige **Ablösung von `seed.mjs`**): Land DE, Zone, Channel (EUR, `defaultLanguageCode=de`, `pricesIncludeTax=false`), Versandarten (standard/digital), Zahlart `rechnung` (dummyPaymentHandler), Rolle `sso-staff`.
  2. **TaxCategories + TaxRates**: „Bildung steuerfrei (§4 Nr. 21 UStG)" 0 %, „Standard 19 %", „Ermäßigt 7 %".
  3. **Facets + Values**: `veranstaltungsart`, `thema`, `branche`, `region`, `format`.
  4. **Collections** je `thema`/`branche` (FacetValue-Filter) — Kategorie-Browsing + späteres Menü.
  5. **Platzhalter-Assets hochladen** über den Multipart-Helfer (s. § B-Assets): 1–2 Bilder + 1 PDF + Personen-Fotos → Asset-IDs für Produkte/Personen.
  6. **Ansprechpartner/Dozent** upsert (Schlüssel `crmPersonId`, inkl. `fotoAssetId`).
  7. **Demoprodukte F1–F6** (Buch/PDF/Tagesseminar/Abo/Berufsschule-Rate/Studiengang-Rate) **1:1 mit identischen Slugs/SKUs** wie bisher `seed.mjs` + die Seminar-Kosten-Positionen (M2) — damit die bestehenden Smoke-Tests grün bleiben.
  8. **Beispielprodukte je Kategorie** (~8) + **Vertragsangebote** (1× Studiengang, 1× Berufsschuljahr, nicht bestellbar) voll ausgestattet: `angebotsnummer`, alle Rich-Text-Felder, Facet-Werte, TaxCategory, Relationen (ansprechpartner/dozenten/verwandteProdukte), Assets, Bewertungen, OptionGroup „Termin" + Durchführungs-Varianten (SKU=Terminnummer, `terminDatum`/`ort`/`veranstaltungsformat`/Preis; Zukunftstermine 2026/2027).
  9. **Search-Reindex**.
- **Asset-Upload (§ B-Assets)**: Vendure-`createAssets` ist GraphQL-**Multipart** (Upload-Scalar) — der generierte JSON-Client kann das nicht. Daher ein **kleiner separater Multipart-HTTP-Helfer** in Java (eigener RestClient/`MultipartForm`), der die Platzhalter-Dateien hochlädt und die Asset-IDs liefert; die Verknüpfung läuft dann normal über den generierten Client. Platzhalter-Dateien als Init-Ressourcen im Integrationsbackend.
- **Modus**: ausschließlich **idempotenter Upsert** (kein Reset/Delete). `seed.mjs` + dessen npm-Script werden **entfernt** (einzige Vendure-Aufbau-Quelle ist jetzt der Java-Initializer).
- `service/KatalogBeispiele` — **die Beispieldaten als editierbare Java-Records/Konstanten** (genau der Feinjustier-Punkt). Kategorien an ebz-training.de angelehnt mit **sprechenden Nummernpräfixen**: Seminar/Tagung `SVA…`, Zertifikatslehrgang `LEG…`, Studium `STU…`, E-Learning `WBT…`, Arbeitskreis `AK…`, Führungsforum `FF…`, Kompaktstudiengang `KS…`. Enthält die 3 verifizierten Beispiele mit echten Inhalten (`SVA015729` Online-Seminar 495 €, `LEG001614` Zertifikatslehrgang 2.800 €, `SVA015717` Fachtagung 990 €) + je übrige Kategorie ein repräsentatives mit plausiblen Platzhalter-Texten; je Beispiel 1–3 `Bewertung`-Einträge.
- **Idempotenz**: jeder Schritt prüft per `angebotsnummer`/SKU/Facet-Code/Name → Update bzw. skip. Mehrfacher `POST /shop/init` ist sicher.
- **Config**: `quarkus.rest-client.vendure-admin.url` + `vendure.superadmin.*` existieren bereits.

### C) Alt-Projektion auf Nummern-Abgleich reduzieren (gewählt)
- `bildung/vendure/VendureProjektion.java`: `projiziere()` baut **kein** Produkt/Variante mehr (kein Content-/Preis-Push). Stattdessen **Resolve-by-Number**: Produkt per Product-Custom-Field `angebotsnummer == e.code` bzw. Variante per `sku == e.code` über den **generierten Client** suchen, nur `vendureProductId`/`vendureVariantId` zurückgeben; kein Treffer → leeres Ergebnis. Die handgeschriebenen GraphQL-Strings in dieser Klasse weichen den generierten Typen.
- `bildung/web/BildungResource.java`: `projiziereInShop` → Semantik „**mit Shop verknüpfen**" (nur ID-Rückschreibung), 409 „in Vendure pflegen" wenn keine Nummer matcht.
- **LMS `lms/vendure/WbtVendureProjektion` + LMS-Tests bleiben unberührt** (eigener E-Learning-Pfad, P7).
- Tests anpassen: `bildung/BildungOpenApiSpecTest` (Spec, sollte grün bleiben); ggf. Projektion-Smoke.

### D) DB-Migration der Custom-Fields (Gotcha)
Vendure-Container läuft mit `synchronize=IS_DEV`. Neue Custom-Fields/Entities → entweder Service mit `APP_ENV=dev` neu bauen (synchronize legt Spalten/Tabellen an) **oder** `pnpm vendure migrate` + `runMigrations` (prod-sauber, L4). Im Schritt entscheiden (Showcase: dev-synchronize schnell, Migration sauber).

### Verifikation P1
- **Codegen**: `mvn -Pgql-codegen generate-sources` erzeugt den Client aus `vendure-admin.graphqls`; danach `mvn package` **ohne** Profil baut allein aus den eingecheckten Sources (kein Regen, kein Netz) → reproduzierbar; `git status` nach normalem Build = sauber.
- Vendure-Container neu bauen (Modell), dann **`POST /shop/init` 2×** → 2. Lauf nur „übersprungen" (idempotent).
- Admin-/Shop-API (GraphiQL :3000): je Kategorie ein voll ausgestattetes Produkt mit Varianten (SKU=Terminnr.), `angebotsnummer`, Facetten, TaxCategory, `ansprechpartner`/`dozenten`-Relationen.
- `search(input:{term/skus, facetValueFilters, take})` liefert Treffer per Freitext + SKU; eine Ansprechpartner-`upsert`-Änderung wirkt auf alle verknüpften Produkte.
- MDM: `POST /bildung/angebote/{id}/shop-projektion` für `code=SVA015729` matcht das geseedete Produkt; unbekannte Nummer → 409.
- **seed.mjs-Ablösung belegen**: bestehende Smokes `scripts/smoke-shop|smoke-subscriptions|smoke-rechnungslauf|smoke-checkout-f1|smoke-sso` laufen **unverändert grün** gegen den Java-Initializer (identische Slugs/SKUs der F1–F6-Demoprodukte).
- Tests: neuer `ShopInitResourceTest` (Init + 2. Lauf idempotent, rest-assured) + `mvn test -Dtest=BildungOpenApiSpecTest` + LMS-Tests grün.

### Stufe 2 — Komplettimport (späterer Plan, NICHT in P1)
Importer im Integrationsbackend (gemeinsamer Upsert-Kern mit dem Initializer): Quelle = **Bildungs-PDF** (alle Produkte gelistet; Parsing z. B. Apache PDFBox) + **Website** (`ebz-training.de/sitemap.xml` → Detailseiten-Scrape, robots/SSRF beachten). Mapping Kategorien/Termine/Preise/Personen → identische Vendure-Strukturen. Upsert-by-Number; im Quell-Datensatz fehlende Produkte auf „disabled" (kein Hard-Delete). **Benötigt: das Bildungs-PDF (Pfad vom Kunden).**

## Risiken / Gotchas
- **SoR-Umkehr**: Alt-Projektion/Tests müssen auf Nummern-Abgleich umgestellt werden (sonst zwei Quellen).
- **Vertragsstrom-Kopplung**: Berufsschule/Studium-Anmeldung + Rechnungslauf hängen an `mdm.bildungsangebot` — der Deeplink-Pfad + Nummern-Mapping muss diese Welt sauber bedienen.
- **Steuerbefreiung Bildung** korrekt je Produkt (TaxCategory) — falsch = falsche Brutto/Netto-Anzeige.
- **Kauf auf Rechnung** = eigener Payment-Handler/Checkout-Zweig + Übergabe ans interne Rechnungs-Gehirn (nicht der `dummyPaymentHandler`).
- **OptionGroup-Pflicht** für >1 Variante; **Facets/Collections-Init idempotent**; **Search-Reindex** nach Init.
- **CRM→Vendure-Personen-Sync**: Richtung/Trigger/Schlüssel definieren (Doppelpflege vermeiden).
- Vendure-Custom-Field-DB-Änderungen: Dev `synchronize`, Prod Migration (L4); Versionskette beim Promotion-Plugin prüfen.
- **GraphQL-Codegen**: Schema MUSS nach dem Plugin (§ A) neu introspiziert werden (sonst fehlen die Custom-Mutations); `graphql-codegen-maven-plugin`-Version + Kette gegen Java 21/Quarkus 3.33 prüfen; generierte Sources eingecheckt + Codegen nur im Profil (kein Regen je Build).
- **Asset-Upload**: nicht über den generierten JSON-Client möglich (Multipart/Upload-Scalar) → separater Multipart-Helfer nötig; Platzhalter-Dateien mitliefern.
- **seed.mjs-Ablösung**: F1–F6-Demoprodukte + Grundkonfig + Versand/Zahlart/Rolle müssen mit **identischen Slugs/SKUs** in Java reproduziert werden, sonst brechen die bestehenden Smoke-Tests. seed.mjs erst nach grünem Smoke-Lauf entfernen.

## Verifikation (Roadmap-Ebene)
- **P1**: s. § P1-Detail/Verifikation.
- **P2–P4**: Nuxt headless (Playwright `showcase/e2e/`): **SSR-HTML** (View-Source enthält Inhalt), Speaking URL + canonical, Facetten-Filter/Suche/Pagination, Detail je Typ, Durchführung → Warenkorb (Teilnehmerliste) → Checkout (Rechnung & Stripe/SEPA, Gast). Vertragsangebot zeigt Anmelde-Deeplink statt Warenkorb. 0 Konsolenfehler.
- **P5**: Staff mit „Lesen"-Rolle kann nicht schreiben; „Pflege"-Rolle kann; Keycloak-Mapping greift.
- **P6**: CMS-Seite im Dashboard pflegbar, SSR-gerendert, im Burger-Menü neben Kategorien.
- **P7**: E-Learning-Kauf löst LMS-Einschreibung aus; Frühbucher senkt Preis (Order-Preview).

## Offene Punkte (klein, vor jeweiliger Phase)
- CRM→Vendure-Personen-Sync: Mechanismus (Event/Outbox vs. Batch) bei P7 festlegen.
- „Kauf auf Rechnung"-Checkout: genaue Naht zum internen Rechnungslauf bei P4 detaillieren.
- Stufe 2: Bildungs-PDF (Pfad/Format) vom Kunden.
