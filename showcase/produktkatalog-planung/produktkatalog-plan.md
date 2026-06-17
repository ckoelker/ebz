# Produktkatalog: ebz-training.de-Funktionen mit Vendure abbilden

> Status: **abgestimmt + freigegeben 2026-06-18** (Plan-Modus, 20 Klärungsfragen in 5 Batches). Realisierung ab 2026-06-18. Nur Konzept/Planung — noch kein Code.

## Context
Kundenfrage: ebz-training.de-Funktionen mit Vendure abbilden. ebz-training.de = filterbarer **Schulungs-Katalog mit Buchung**: ~400 Angebote in vielen Formaten (Seminar, Zertifikatslehrgang, Fachtagung, Arbeitskreis, Führungsforum, E-Learning/EBZ4U, Studium, Kompaktstudiengang …), Filter **Thema/Branche/Region/Veranstaltungsart**, **Freitext-/Angebotsnummer-Suche** + Pagination, **mehrere Termine/Orte je Angebot** mit Preisen, **Warenkorb**, Kundenkonto, Inhouse; Detailseiten mit Beschreibung/Inhalten, Bildern, Downloads, Ansprechpartner/Referenten. URL-Muster verifiziert: `https://www.ebz-training.de/<slug>?termin=<Terminnummer>`.

**Antwort: Ja**, Vendure bildet das nativ ab. Dieser Plan setzt die abgestimmten Entscheidungen um.

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
- **Steuer**: Vendure-**TaxCategory je Produkt** — USt-befreit (§4 Nr. 21 UStG) / 19 % / 7 %.
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
- **Product-Custom-Fields**: `angebotsnummer` (string, MDM-Link, such-/filterbar), `inhalteHtml`/`lernzieleHtml`/`nutzenHtml`/`methodikHtml`/`voraussetzungenHtml`/`foerderhinweisHtml` (Rich-Text), `zielgruppe`, `abschluss`; **Relationen** `ansprechpartner` (1) + `dozenten` (N:M) auf die Custom-Entities; `verwandteProdukte` (Querverweise); typ-spezifische Felder (`dauerUE`/`studienform`/`regelstudienzeitSemester`/`akkreditierungBis` …). Downloads/Bilder = **Assets**.
- **ProductVariant-Custom-Fields** (= Durchführung): `terminDatum`, `ort`, `veranstaltungsformat` (PRAESENZ/ONLINE/HYBRID); SKU = Terminnummer; Preis = Varianten-Preis; **TaxCategory** je Produkt (befreit/19/7). (Subscription-Felder bleiben.)
- **Custom-Entities** (eigenes Plugin): `Ansprechpartner` (name/telefon/email/foto), `Dozent` (name/vita/foto) — **per Sync aus dem CRM** befüllt/aktualisiert.
- **ContentPage** (CMS-Plugin): slug/titel/inhaltHtml/metaTitle/metaDescription/published/`imMenu`/`menuTitel`/`menuSortierung`.
- **Laufzeitdaten** (idempotenter Seed/Admin): Facets `Veranstaltungsart/Thema/Branche/Region` + Werte; Collections je Thema/Branche; TaxCategories; OrderLine-Custom-Fields für Teilnehmerliste (Sammelbuchung).

**OrderLine/Teilnehmer**: Sammelbuchung → mehrere Teilnehmer je Position (Name/E-Mail). Vendure-OrderLine-Custom-Fields `participantName/Email` vorhanden → ggf. auf Liste/mehrere Plätze erweitern.

## Architektur / Schwerpunkt
- Arbeit liegt in **Vendure-Konfiguration/Plugins** (Custom-Fields, Custom-Entities, ContentPage, CRM-Sync, Promotion) + **neuer Nuxt-SSR-Storefront** + **Seed**. MDM-Backend nur: Alt-Projektion abspecken + Nummern-Mapping im Buchungspfad.
- **>1 Variante braucht OptionGroup „Termin"** (sonst nur 1 Variante erlaubt).
- **Facets/Collections/TaxCategories = Laufzeitdaten** → idempotenter Seed.
- **Vertragsangebote** (Berufsschule/Studium): im Katalog sichtbar, aber Detail-CTA = „Anmeldung/Vertragserstellung" → Deeplink in den bestehenden Anmelde-/Vertragsprozess (Portal), kein Warenkorb.

## Phasen (MVP-first)
### P1 — Vendure-Modell + Beispiel-Seed (MVP-Basis)
Custom-Fields (Product/Variant inkl. `angebotsnummer`, Termin-Felder, TaxCategory) + Custom-Entities `Ansprechpartner`/`Dozent`; idempotenter Seed: Facets/Collections/TaxCategories + Beispielprodukte je Typ (die 3 verifizierten) mit Durchführungs-Varianten (OptionGroup „Termin", SKU=Terminnummer), Platzhalter-Assets, Custom-Field-Texte, Relationen. Nummernformat `SVA/LEG…` nachbilden. Alt-`VendureProjektion` auf Nummern-Abgleich reduzieren.
### P2 — Nuxt-SSR-Storefront-Gerüst
Neue Nuxt-Storefront (Nuxt UI 4, EBZ-Navy, DE) ersetzt `showcase/frontend`: SSR, Speaking-URLs `/<slug>?termin=<nr>` + canonical, GraphQL-Codegen gegen Shop-API, Keycloak-SSO (Shop-Strategy), Warenkorb-/Checkout-Store portieren. Eigener Compose-Service.
### P3 — Katalog: Suche/Filter/Pagination
`search`-Query (term, facetValueFilters, collectionSlug, skip/take, sort) → Trefferliste + Facetten-Sidebar (Thema/Branche/Region/Veranstaltungsart) + Freitext-/Angebotsnr-Suche + Pagination/Trefferzahl, SSR.
### P4 — Detailseite + Durchführungs-Auswahl + Warenkorb/Checkout (MVP-Abschluss)
Detail rendert alle Blöcke bedingt je Veranstaltungsart; Durchführungs-Varianten wählbar → Warenkorb (Sammelbuchung mit Teilnehmerliste) → Checkout (Kauf auf Rechnung **und** Stripe/SEPA), Gastbuchung. **Vertragsangebote**: „Anmeldung/Vertragserstellung"-Deeplink statt Warenkorb.
### P5 — RBAC (global)
Rollen „Katalog-Lesen"/„Katalog-Pflege"; `KeycloakAdminAuthStrategy` mappt Keycloak-Gruppe→Rolle. (Keine Channels.)
### P6 — CMS „ContentPage" + Burger-Navigation
ContentPage-Plugin (Entity + Dashboard-UI + Shop-API) + Nuxt-Navigation: Collections + `imMenu`-Seiten als responsives Burger-/Hauptmenü.
### P7 — E-Learning-Kopplung + Promotions
E-Learning-Produkte → bestehender LMS-Einschreibungspfad (OpenOLAT). Frühbucher = custom `PromotionCondition` (Tage vor `terminDatum`). CRM→Vendure-Personen-Sync.
### P8 — (später) Vollimport ~400 + Inhouse-Anfrage
Massenmigration aus Sitemap/Bestand; Inhouse „Angebot anfragen" → HubSpot/Controlling.

## Risiken / Gotchas
- **SoR-Umkehr**: Alt-Projektion/Tests müssen auf Nummern-Abgleich umgestellt werden (sonst zwei Quellen).
- **Vertragsstrom-Kopplung**: Berufsschule/Studium-Anmeldung + Rechnungslauf hängen an `mdm.bildungsangebot` — der Deeplink-Pfad + Nummern-Mapping muss diese Welt sauber bedienen.
- **Steuerbefreiung Bildung** korrekt je Produkt (TaxCategory) — falsch = falsche Brutto/Netto-Anzeige.
- **Kauf auf Rechnung** = eigener Payment-Handler/Checkout-Zweig + Übergabe ans interne Rechnungs-Gehirn (nicht der `dummyPaymentHandler`).
- **OptionGroup-Pflicht** für >1 Variante; **Facets/Collections-Seed idempotent**; **Search-Reindex** nach Seed.
- **CRM→Vendure-Personen-Sync**: Richtung/Trigger/Schlüssel definieren (Doppelpflege vermeiden).
- Vendure-Custom-Field-DB-Änderungen: Dev `synchronize`, Prod Migration (L4); Versionskette beim Promotion-Plugin prüfen.

## Verifikation
- **P1**: Admin/Shop-API zeigt Beispielprodukte mit Durchführungs-Varianten, Facetten, TaxCategory, `angebotsnummer`, Ansprechpartner/Dozent-Relationen; Seed 2× idempotent; Ansprechpartner-Änderung an 1 Stelle wirkt überall.
- **P2–P4**: Nuxt headless (Playwright `showcase/e2e/`): **SSR-HTML** (View-Source enthält Inhalt), Speaking URL + canonical, Facetten-Filter/Suche/Pagination, Detail je Typ, Durchführung → Warenkorb (Teilnehmerliste) → Checkout (Rechnung & Stripe/SEPA, Gast). Vertragsangebot zeigt Anmelde-Deeplink statt Warenkorb. 0 Konsolenfehler.
- **P5**: Staff mit „Lesen"-Rolle kann nicht schreiben; „Pflege"-Rolle kann; Keycloak-Mapping greift.
- **P6**: CMS-Seite im Dashboard pflegbar, SSR-gerendert, im Burger-Menü neben Kategorien.
- **P7**: E-Learning-Kauf löst LMS-Einschreibung aus; Frühbucher senkt Preis (Order-Preview).

## Offene Punkte (klein, vor jeweiliger Phase)
- CRM→Vendure-Personen-Sync: Mechanismus (Event/Outbox vs. Batch) bei P7 festlegen.
- „Kauf auf Rechnung"-Checkout: genaue Naht zum internen Rechnungslauf bei P4 detaillieren.
