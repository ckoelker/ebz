# Ist-/Lücken-Analyse Rechnungsstellung (Stand 2026-06-15)

> Code + Tests gegen [Konzept](Konzept-Rechnungsstellung-DATEV.md) abgeglichen. Ziel: scharfe Definition
> von „komplett". **Wichtig:** Die übrigen Planungs-Docs sind veraltet (sagen teils „Konzept/R2+ offen",
> obwohl R1–R7 im Kern gebaut sind) — siehe „Doku-Begradigung" unten.

## Ist — gebaut + getestet (29 rechnung-Tests grün)
| Baustein | Code | Tests |
|---|---|---|
| Beleg-Lebenszyklus (Entwurf→Ausstellen/Festschreibung→Storno/Gutschrift/Nachberechnung) | `RechnungService`, `RechnungslaufService` | `RechnungLebenszyklusTest` (5) |
| Lückenlose Nummernkreise (atomar, je Bereich×Belegart) | `NummernkreisService`/`-Initializer` | `NummernkreisServiceTest` (2) |
| Debitoren-Hoheit: Anlage idempotent, **Match/Merge**, **Bestandsimport/Altnummern** | `DebitorHoheitService`, `DebitorNummernService`, `DebitorAlias` | `DebitorHoheitTest` (3) |
| **ZUGFeRD/Factur-X** (PDF/A-3) + Validator-Tor | `ZugferdService`, `PdfA3Generator`, `RechnungZugferdMapper` | `ZugferdPipelineTest` (3), `ZugferdEndpointTest` (2) |
| **GoBD-Archiv** (MinIO-WORM, Object-Lock/Retention) | `GobdArchivService`, `MinioProducer` | `GobdArchivTest` (1) |
| **DATEV-Übergabe hinter Interface** (EXTF-CSV-Buchungsstapel **und** Cloud-Mock) + Buchungssatz | `DatevUebergabe`, `ExtfCsvUebergabe`, `DatevCloudMockUebergabe`, `BuchungssatzService`, `DatevKonten` | `DatevExportTest` (2), `ExtfBuchungsstapelTest` (1) |
| Berufsschule end-to-end (Sammelrechnung je Debitor) | `RechnungslaufService` | (E2E + Lebenszyklus) |
| **Hochschule** (Semester, Raten, Firmen-Split duales Studium) | `RechnungslaufService`, Validatoren | `HochschulLaufTest` (4) |
| **Shop/externe Bestellung → Beleg** (quellen-agnostisch, idempotent `quelle|externeId`) | `BestellungBillingService` | `BestellungBillingTest` (2) |
| RBAC + OpenAPI-Vertrag | `RechnungResource` | `RechnungOpenApiSpecTest` (4) |

## Bewusst außerhalb (delegiert/dokumentiert) — kein versehentliches Loch
- **Zahlungseingang / OP-Verwaltung / Mahnwesen / SEPA-Lastschrift** → bleibt in **DATEV** (explizit in
  `Zahlungsart` + `Debitor.iban`). `RechnungStatus.BEZAHLT` existiert, hat aber **keinen Übergang** (kein
  Zahlungsabgleich im Service). → Entscheidung nötig: delegiert lassen oder minimalen OP-Status bauen.
- **erechnung-Repo** wird nicht eingezogen (nur Referenz).

## Lücken — Code (Kandidaten für „komplett")
1. 🟠 **Rechnungsversand an den Kunden:** Der Beleg wird erzeugt + GoBD-archiviert, aber **nicht
   zugestellt** (kein E-Mail-Versand des ZUGFeRD-PDF an den Debitor; `quarkus-mailer` ist vorhanden,
   wird in `rechnung` aber nicht genutzt). Für eine „komplette" E-Rechnung gehört Zustellung dazu.
2. 🟡 **Zahlungseingang/BEZAHLT-Übergang** (s. o.) — minimaler manueller „als bezahlt markieren"-Schritt
   wäre für einen runden Lebenszyklus sinnvoll, auch wenn OP/Mahnung in DATEV bleibt.
3. 🟡 **USt-Differenzierung Übernachtung/Verpflegung:** Steuerfall ist je Position modellierbar
   (BEFREIT/STANDARD/ERMAESSIGT vorhanden), aber der Berufsschul-Lauf setzt alles `BEFREIT`. Falls
   Übernachtung/Verpflegung 7 % sind, fehlt die Regel im Lauf (hängt an §6.2-Klärung).

## Lücken — Entscheidungen (Kunde/StB, kein Code)
- **DATEV-Weg final** (Cloud-Buchungsdatenservice vs. DATEVconnect) + **Partner-Registrierung**; der Code
  hält beide Wege als Mock/Brücke bereit (`datev.modus`).
- **Echte SKR03/04-Erlöskonten + BU-Schlüssel** (aktuell konfigurierbare Platzhalter in `DatevKonten`).
- **USt je Leistung** (§6.2), **E-Rechnungs-Pflicht-Timeline** (§6.6).

## Doku-Begradigung (veraltet → real)
- `rechnungsstellung-planung/README.md` + `Konzept §7`: Roadmap-Status R2–R7 von „Konzept/offen" auf
  **GEBAUT** heben (mit Test-Belegen oben); offene Punkte auf die echten Lücken/Entscheidungen eindampfen.
- Memory `rechnungsstellung-showcase.md`: analog aktualisieren (sagt noch „nur Konzept/Planung").

## Vorschlag Priorisierung bis „komplett"
1. **Rechnungsversand** (E-Mail mit ZUGFeRD-PDF an Debitor) — rundet die E-Rechnung ab.
2. **BEZAHLT-Übergang** (minimaler manueller Zahlungseingang) — abgerundeter Lebenszyklus.
3. **USt-Regel Übernachtung** — nur falls §6.2 das verlangt (sonst Entscheidung).
4. Doku/Memory begradigen (parallel, günstig).
