# Review & Neubewertung: Showcase-Realität gegen die Enterprise-Stack-Planung

> **Lieferobjekt:** Review (Validierung) + Neubewertung der ursprünglichen Annahmen aus
> [Grobkonzept](Grobkonzept-IT-Konsolidierung.md), [Capability-Map](Zielarchitektur-Capability-Map.md)
> und [Soll-Bebauungsplan](Soll-Bebauungsplan-und-Schnittstellen.md).
> **Stand:** 2026-06-13. **Geändert wird nichts außer diesem Dokument** — die Planungsdokumente bleiben
> unverändert; dies ist die Gegenüberstellung + Empfehlung.
> **Gegenstand:** Was wurde im `showcase/`-Monorepo tatsächlich gebaut (Shop/Vendure, Controlling
> M1–M3, Formularverwaltung P1.0–P1.3, Rechnungsstellung R1–R7) — und was bedeutet das für die
> Annahmen der Planung?

---

## 1. Kernbefund (Executive Summary)

Der Showcase ist **kein Abbild der empfohlenen Ziel-Bebauung**, sondern ein **funktionierender
Proof-of-Concept der ausdrücklich „nicht gesetzten" JVM-Kern-/Integrations-Option** aus der
[Capability-Map (§ „Architektur-Option (offen)")](Zielarchitektur-Capability-Map.md). Statt
Power Platform/Dataverse + iPaaS wurde der **Differenzierer (Daten-/Integrationskern)** als
**Quarkus-Service** (Camel Quarkus + LangChain4j) realisiert, Commodity (Shop) als **Vendure** (Node),
BI als **Lightdash**, SSO als **Keycloak**.

Drei Annahmen verdienen eine Neubewertung:

1. **„Rechnungsstellung = Commodity, niemals selbst bauen" (Capability-Map #6/#17, Leitprinzip).**
   Der Showcase hat **das Gegenteil getan** und ein vollständiges Billing-Gehirn (R1–R7: ZUGFeRD/
   GoBD/DATEV/Debitoren-Hoheit) gebaut. Das ist die größte Divergenz — und teils begründet (das
   Billing trägt die Debitoren-Nummernkreis-Hoheit = Kern-Differenzierer), teils ein bewusst zu
   treffender Make/Buy-Schnitt.
2. **„JVM-Eigenbau ist die teuerste Variante" (TCO §6).** Die in der Capability-Map formulierte
   Hypothese, *KI-gestützte Entwicklung + Standard-Specs verkleinern diesen Abstand*, ist durch das
   Bau-Tempo des Showcase **empirisch gestützt** — aber nur für die **Bau**-Kosten, nicht für
   **Betrieb/Wartung** (die eigentliche OpenEduCat-Lektion).
3. **Der wichtigste Geschäftsschmerz — N:M Person↔Company — ist im Showcase noch NICHT abgebildet.**
   Gebaut wurde Debitoren-Dedup/Golden-Record (R3), nicht das volle Personen-/Firmen-
   Beziehungsmodell. Der zentrale „verlorene" Datenmodell-Wert (Grobkonzept §3.2) ist damit noch offen.

**Gesamturteil:** Der Showcase **validiert die Machbarkeit** des Best-of-Breed-Mesh und insbesondere
der JVM-Kern-Option auf der Bau-Seite überzeugend; er **validiert NICHT** die risikoreichen Teile der
Planung (Migration aus den realen Altsystemen S1–S3, N:M-Stammdatenmodell, Betriebs-TCO,
EOL-Ablösungen).

---

## 2. Capability-Map — Soll-Empfehlung vs. Showcase-Realität

| # | Fähigkeit | Planung (Soll-Empfehlung) | Im Showcase realisiert | Urteil |
|---|---|---|---|---|
| 1 | **MDM / Single Point of Truth** | Dataverse (alt. EspoCRM/Salesforce); **N:M + Mehr-Debitoren** | MDM-Schema `mdm` (Angebote + `mdm.debitor`) mit Golden-Record/Match/Merge/Alias + zentraler Nummernhoheit (R3); **Mehr-Debitoren je Person rollenabhängig** | 🟡 **teilweise** — Debitoren-Dedup ja, **N:M Person↔Company fehlt** |
| 2 | Controlling/BI | Power BI (alt. Metabase/Superset) | **Lightdash** auf dbt-Marts (Break-even, Drei-Bucket-Forecast) | 🟢 validiert (andere OSS-BI als genannt) |
| 3 | Marketing + Sales-Pipeline | **HubSpot** 🔒, Sync via Airbyte+n8n (S14) | HubSpot-Ingestion via **Camel Quarkus + LangChain4j** (Controlling M1) | 🟢 Quelle wie gesetzt; **Sync-Technik divergiert** (JVM statt Airbyte/n8n) |
| 4 | SSO / Identity | **Entra ID (vorhanden)** | **Keycloak** (2 Realms: Kunde/Staff; mdm-cockpit, lightdash, rechnung-pflege) | 🟢 Muster validiert; **Keycloak statt Entra** (Showcase kann reale Entra nicht nutzen) |
| 6 | Integration/Sync-Layer | **Power Automate/iPaaS** (alt. n8n) | **Camel Quarkus** (JVM-Option, „nicht gesetzt") + dlt (EL) | 🔵 **bewusste Wahl der nicht-gesetzten Option** |
| 7 | Shop (Lehrmaterial) | Shopware/Shopify — „nie selbst bauen" | **Vendure** (Headless, Node) + Vue-Storefront | 🟢 Buy/OSS — anderes Produkt; zusätzlich für Buchung/Abo genutzt (s.u.) |
| 8/16 | Buchung / Abo-Billing | ERPNext/Odoo Admission · Kill Bill/Stripe | **Vendure Subscriptions/Installments** (F4–F6, Stripe-Plan) | 🟡 in Vendure gebündelt statt separater Abo-Engine |
| 6/17 | **Rechnungsstellung / E-Rechnung / FiBu** | **Business Central/DATEV / Invoice Ninja — Buy, niemals selbst** | **Eigenes Quarkus-Billing R1–R7**: Belegfluss, ZUGFeRD/XRechnung, GoBD-WORM (MinIO), DATEV hinter Interface (EXTF+Cloud-Mock), Debitoren-Hoheit, Hochschul-Split/Raten, Vendure-Quelle | 🔴 **größte Divergenz** — entgegen Leitprinzip selbst gebaut (Neubewertung §4) |
| 5/9/12/13/14/15 | Pflicht-/Spezial-Satelliten (Schild-NRW, WebUntis, Sket, Moodle, Teams) | behalten + anbinden (L6) | **nicht im Showcase** (synthetische/Fixture-Daten, Vendure als Quelle) | ⚪ nicht adressiert |
| 11/19 | Berufsschul-Java-App / Gästehaus-PMS | anbinden/schrumpfen bzw. klären | **nicht angebunden** (keine reale Quelle) | ⚪ nicht adressiert |

---

## 3. Schnittstellen (S1–S15) — Realisierungsgrad

| Connector | Planung | Showcase | Status |
|---|---|---|---|
| **S1 Java-App ↔ Kern** (kritischer Pfad, N:M/Debitoren-Referenzquelle) | 🔴 zuerst | **nicht gebaut** — Showcase hat keine reale Legacy-Quelle | ❌ **offen (kritisch)** |
| S2/S3 OpenEduCat/UniTop ⬅️ Kern | 🔴 | nicht gebaut | ❌ offen |
| S11 Kern → Billing/FiBu (ZUGFeRD/XRechnung, E-Rechnungs-Pflicht) | 🟠 | **gebaut + live** (R2 ZUGFeRD, R4 DATEV-EXTF/Cloud-Mock) | ✅ Muster validiert |
| S13 Kern ↔ Shop | 🟢 | **gebaut** (R7 `ExterneBestellung`-Naht + P1.3 Shop-Projektion Vendure) | ✅ validiert |
| S14 Kern ↔ HubSpot | 🟠 | **gebaut** (Controlling M1, JVM-Pull statt Airbyte) | ✅ (Technik divergiert) |
| S15 Kern → BI/Controlling | 🟠 | **gebaut** (dbt→Lightdash) | ✅ validiert |
| S5 Kern ↔ Entra/SSO | 🟠 | **Keycloak** statt Entra | 🟡 Muster validiert |
| S6–S10 (WebUntis/Schild/Sket/Moodle/Teams) | gemischt | nicht gebaut | ⚪ offen |

**Befund:** Der Showcase hat die **kaufmännisch-analytische Halbachse** (S11/S13/S14/S15 + SSO) gut
abgedeckt, aber die **Stammdaten-Migrationsachse aus den Altsystemen (S1–S3)** — laut Planung der
**kritische Pfad und größte Risikoträger** — gar nicht. Das ist erwartbar (kein Zugriff auf reale
Legacy-Systeme im Showcase), aber für die Risikoeinschätzung zentral: **das Teuerste/Riskanteste
bleibt unbewiesen.**

---

## 4. Neubewertung der ursprünglichen Annahmen

### A. „Billing niemals selbst bauen" — teilweise zu revidieren
Die Leitlinie behandelt Rechnungsstellung als Commodity-Satellit (#6/#17). Der Showcase zeigt: Das
**Rechnungs-Gehirn ist untrennbar mit dem Kern-Differenzierer verzahnt** — die
**Debitoren-Nummernkreis-Hoheit** (das eigentliche Doppel-Debitoren-Problem, #1) materialisiert sich
*in* der Rechnungsstellung; E-Rechnung (ZUGFeRD/XRechnung) + GoBD + DATEV-Übergabe sind
**Integrations-/Pflicht-Themen** (S11), keine Buchhaltung. **Reine** Buchhaltung/FiBu (Hauptbuch,
Bilanz, Steuer) wurde NICHT gebaut und bleibt zu Recht DATEV/StB.
→ **Neubewertung:** Die Make/Buy-Grenze bei #6/#17 verläuft feiner als „kaufen". Sinnvoller Schnitt:
**Debitoren-Hoheit + E-Rechnungs-/DATEV-Gateway = KERN-nah (bauen, wie gezeigt)**, **klassische FiBu
= Buy (DATEV)**. Der Showcase trifft diesen Schnitt faktisch bereits (DATEV hinter `DatevUebergabe`-
Interface) — er sollte **bewusst in die Capability-Map zurückgespielt** werden.

### B. „JVM-Eigenbau = teuerste Variante" (TCO §6) — Bau-Seite entkräftet, Betriebs-Seite offen
Der Showcase baute R2a→R7 (E-Rechnung, GoBD-WORM, Debitoren-Match/Merge, DATEV, Hochschul-Split,
Shop-Naht) in **sehr kurzer Zeit mit KI-Unterstützung**, mit grüner Testsuite und Live-Verifikation.
Das stützt die Capability-Map-Hypothese (§ „Ehrliche Spannungen": KI + Standard-Specs verkleinern den
Abstand) **für die Erstellung**. **Aber:** Genau die OpenEduCat-Lektion war **Wartung/
Versionsneutralität/Betrieb**, nicht Erstbau. Der Showcase sagt nichts über 5-Jahres-Wartung, Run-FTE,
Schlüsselpersonenrisiko.
→ **Neubewertung:** TCO-§6-Annahme „Java am teuersten" auf der **Build**-Linie senken, auf der
**Run/Maintain**-Linie **unverändert konservativ** lassen. Die Entscheidung darf sich nicht von einem
schnellen Showcase blenden lassen.

### C. „Single Source of Truth zuerst (Integrate-first)" — im Showcase invertiert
Grobkonzept §5 empfiehlt **Stammdaten-Kern zuerst** (Phase 1), Fachthemen danach. Der Showcase ist
**Fach-zuerst** gewachsen (Shop → Controlling → Formular-MDM → Rechnung) und hat den Stammdaten-Kern
nur *innerhalb* der Rechnung (Debitoren) gestreift. Das ist für einen Showcase legitim (sichtbarer
Nutzen je Schritt), **kehrt aber die empfohlene Risiko-Reihenfolge um**.
→ **Neubewertung:** Die Reihenfolge-Empfehlung bleibt gültig; der Showcase ersetzt nicht die
Phase-1-Priorität. Vor einer Produktentscheidung muss der **Person↔Company-N:M-Kern** (nicht nur
Debitoren) gezeigt/geprüft werden.

### D. Was die Planung-Annahmen **bestätigt**
- **Best-of-Breed-Mesh ist betreibbar:** ein Stack (Vendure + Quarkus + dbt + Lightdash + Keycloak,
  ein Postgres) läuft integriert — Leitprinzip „Commodity kaufen, Differenzierer bauen" ist praktisch
  tragfähig.
- **JVM-Kern-Differenzierer trägt:** LangChain4j-gestützte Konvertierung/Anreicherung (M1) +
  Golden-Record/Match/Merge (R3) zeigen genau die in der Capability-Map als JVM-Stärke benannten
  Aufgaben (Entity-Resolution, semantische Konvertierung).
- **E-Rechnungs-Pflicht** (S11, Soll-Bebauungsplan): ZUGFeRD/XRechnung + Validator-Tor + GoBD-WORM
  technisch gelöst — die regulatorische Anforderung ist machbar nachgewiesen.
- **SSO als Komfort-Hebel** (S5): ein Keycloak über mehrere Dienste bestätigt den Befund, dass
  SSO+Integration Best-of-Breed erst komfortabel macht.

---

## 5. Risiken, die der Showcase sichtbar macht

| Risiko | Beobachtung im Showcase | Konsequenz |
|---|---|---|
| **Scope-Drift Make>Buy** | Billing wurde gebaut, obwohl „buy" empfohlen | Bewusste Make/Buy-Entscheidung nötig, sonst Wartungslast wie befürchtet |
| **Kritischer Pfad ungetestet** | S1–S3 (reale Legacy-Migration) nicht angefasst | Das eigentliche Projektrisiko (Datenqualität/Dubletten/N:M) ist unbewiesen |
| **N:M-Lücke** | Nur Debitoren-Dedup, kein Person↔Company | Der #1-Geschäftswert ist noch nicht demonstriert |
| **Zwei Ökosysteme** | Node (Vendure) + JVM (Integration/Billing) parallel | Betriebs-/Personalprofil muss beides können (Capability-Map „Ehrliche Spannungen" bestätigt) |
| **Showcase-Vereinfachungen** | ein Postgres/Monorepo, Keycloak statt Entra, Platzhalter-SKR/Verkäufer, DATEV-Cloud=Mock, Vendure als Stammdatenquelle statt Java-App | Produktionsfähigkeit ≠ Showcase; klar als PoC kommunizieren |
| **Test-Isolation** | geteilte controlling-DB → wiederkehrende „get(0)"-Flakes (mehrfach gefixt) | Für Produktion echte Test-Isolation/Throwaway-DB nötig |

---

## 6. Empfehlung — nächste Schritte

**Priorisiert, risikoorientiert. Reihenfolge folgt dem „Integrate-first"-Prinzip des Grobkonzepts.**

1. **N:M Person↔Company im Kern zeigen (schließt die wichtigste Lücke).**
   Den Debitoren-Golden-Record aus R3 zum **Personen-/Firmen-Beziehungsmodell** ausbauen (eine Person,
   mehrere Firmen/Rollen, mehrere Debitoren) — das ist *der* in Grobkonzept §3.2 benannte verlorene
   Wert. Ohne diesen Nachweis ist der Kern-Differenzierer nur halb belegt.

2. **Make/Buy-Schnitt für Billing bewusst beschließen und dokumentieren.**
   Entscheidung herbeiführen: bleibt das gebaute Billing-Gehirn (Debitoren-Hoheit + E-Rechnungs-/
   DATEV-Gateway = KERN-nah) **oder** wird die reine Rechnungserzeugung später durch ein Kaufprodukt
   hinter demselben Interface ersetzt? Ergebnis als **Amendment zur Capability-Map #6/#17** festhalten
   (dieses Review ändert die Planung bewusst nicht — die Korrektur gehört in einen GF-Beschluss).

3. **Den kritischen Pfad S1 als Spike gegen EINE reale Quelle testen.**
   Ein Migrations-/Matching-Spike gegen einen echten Export (Java-App **oder** OpenEduCat/UniTop):
   reale Dubletten, reale N:M-Fälle, reales Mengengerüst → validiert das **teuerste/riskanteste**
   Stück, das der Showcase ausspart. Speist direkt den Phase-0-Punkt „Datenqualität/Dubletten".

4. **TCO §6 neu rechnen — Build und Run getrennt.**
   Bau-Aufwand mit der gezeigten KI-Velocity nach unten korrigieren; Run/Maintain/Schlüsselpersonen
   **konservativ halten**. Erst diese getrennte Sicht macht die Kern-Entscheidung (JVM-Eigenbau vs.
   Dataverse/Power Platform) belastbar.

5. **Den Showcase als RFI/PoC-Input deklarieren (#1/#6).**
   Der Showcase IST faktisch der PoC der „nicht gesetzten" JVM-Kern-Option. Seine Ergebnisse
   (machbar, schnell baubar, E-Rechnung gelöst; Migration/N:M/Betrieb offen) **direkt in die
   RFI-Bewertung gegen Power Platform/Dataverse** einspeisen (Soll-Bebauungsplan §5) — statt parallel
   zu laufen.

6. **Restliche Kundenfragen (B3/B5) + Phase-0-⚪-Punkte schließen.**
   Debitoren-Schema, Storno-Regeln, echte SKR-Kontierung/Steuersätze (StB) und die fünf
   ⚪-Klärungen (PMS, TarLemon, Moodle-Akademie, Fehltage-System, Shop) bleiben Voraussetzung für jede
   verbindliche Bebauung.

**Nicht empfohlen:** den Showcase ungeprüft als Zielarchitektur deklarieren. Er belegt Machbarkeit und
Tempo der Bau-Seite eindrucksvoll, lässt aber die migrations-, modell- und betriebsseitigen
Kernrisiken der Konsolidierung bewusst offen.

---

## 7. Quellenlage (Showcase-Stand 2026-06-13)

- **Shop:** Vendure 3.6.4 + Vue-Storefront, M1–M4 (Katalog/Custom-Fields/Subscriptions/Rechnungslauf),
  SSO Keycloak — `showcase/vendure/`, `showcase/frontend/`.
- **Controlling:** M1 HubSpot-Ingestion (Camel Quarkus + LangChain4j), M2 dlt-Load, M3 dbt
  (Break-even/Forecast) → Lightdash — `showcase/integration/`, `dlt/`, `dbt/`, `lightdash/`.
- **Formularverwaltung:** P1.0–P1.3 MDM-Masken (Quarkus + Vue, Stack B), Shop-Projektion nach Vendure
  — `showcase/integration/` (Package `bildung`), `showcase/mdm/`.
- **Rechnungsstellung:** R1–R7 (Berufsschule, ZUGFeRD/Korrekturbelege/XRechnung/GoBD-WORM,
  Debitoren-Hoheit, DATEV-EXTF+Cloud-Mock, Hochschul-Split/Raten, Vendure-Quelle) — `showcase/
  integration/` (Package `rechnung`); 39 Tests grün, live verifiziert.
- **Planungsbasis:** [Grobkonzept](Grobkonzept-IT-Konsolidierung.md),
  [Capability-Map](Zielarchitektur-Capability-Map.md),
  [Soll-Bebauungsplan](Soll-Bebauungsplan-und-Schnittstellen.md),
  [Anbieter-Vergleichsmatrix](Anbieter-Vergleichsmatrix.md), [TCO](TCO-Kostenaufstellung.md),
  [Anbieter-Recherche](Anbieter-Recherche-Softwareprodukte.md).
