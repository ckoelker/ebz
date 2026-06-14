# rechnungsstellung-planung

Planung für die **Rechnungsstellung & E-Rechnung → DATEV** im EBZ-Best-of-Breed-Showcase.
Analog zu `formularverwaltung-planung/`, `controlling-planung/`, `shop-planung/` — reine
Planungs-/Konzept-Doku, **kein Code**.

## Dokumente
- **[Konzept-Rechnungsstellung-DATEV.md](Konzept-Rechnungsstellung-DATEV.md)** — Ist-Analyse
  (wo werden Rechnungen geschrieben / wie verbucht / wie nach DATEV), Zielbild „EIN Rechnungs-
  Gehirn", evaluierte Architektur- und DATEV-Optionen (mit Empfehlung), Abrechnungsmatrix,
  offene Kundenfragen und Milestone-Roadmap R0–R7.
- **[R0-Klaerung-Fragenkatalog.md](R0-Klaerung-Fragenkatalog.md)** — Vorbereitung des Kunden-/StB-
  Gesprächs (Meilenstein R0): jetzt festzurrbare Entscheidungen + Fragenkatalog je offenem Punkt
  (DATEV-Weg, USt je Leistung, Debitoren-Nummernkreise, Rabatte, Storno/Gutschrift, E-Rechnungs-
  Timeline) mit Vor-Analyse/Empfehlung und Antwortfeldern. Inkl. verifizierter Recherchestände.
- **[R1-Fachklaerung-Rechnungsstellung.md](R1-Fachklaerung-Rechnungsstellung.md)** — Domänen-Klärung
  für die Code-Planung (Antworten Gruppe 1–8): Abrechnungsquelle, Debitoren-Strategie, Berufsschul-/
  Hochschul-Regeln, Storno/Gutschrift, Belegfluss/Festschreibung + Architektur-Rollen (Vendure bleibt).
- **[R1-Code-Plan-Rechnungsstellung.md](R1-Code-Plan-Rechnungsstellung.md)** — konkreter
  Implementierungsplan im `integration`-Service (Schema `rechnung`, Datenmodell, Lebenszyklus/
  Festschreibung, Services, REST-Endpunkte, Teststrategie); R1-Slice = Berufsschule end-to-end.
  **STATUS: GEBAUT + VERIFIZIERT (2026-06-13)** — 11 Tests grün + live end-to-end gegen den Stack.

## Kurzfassung
- **Ist:** Im Showcase entstehen noch keine echten Rechnungen (Vendure = Bestellungen + Raten-
  Status). Ein separates Repo `c:\dev\workspacesIJ\erechnung\` kann ZUGFeRD → DATEV, wird hier aber
  **NICHT eingezogen** (nur Referenz für die Library-Wahl). Real heute: heterogene CSV-Exporte,
  Nummernkreis-Kollisionen, Mahnwesen/Lastschrift in DATEV.
- **Ziel (Empfehlung):** die E-Rechnung **unabhängig im bestehenden `integration`-Service** umsetzen
  (Library-Stack Mustang/PDFBox, Konzept §4f): Bestellungen/Raten aus Vendure abrechnen, ZUGFeRD
  erzeugen, GoBD-konform archivieren, Debitoren-Nummern aus **einer** Quelle vergeben und Beleg-/
  Buchungsdaten **hinter einer austauschbaren Schnittstelle** an DATEV übergeben.
- **Offen (beim Kunden zu klären):** DATEV-Weg (API/Rechnungsdatenservice vs. DATEVconnect on-prem),
  USt-Behandlung je Leistung, bestehende Debitoren-Nummernkreise, Rahmenrabatte, Storno-/Gutschrift-
  Regeln, E-Rechnungs-Pflicht-Timeline.

> Status: **R1 (Berufsschule end-to-end) GEBAUT + VERIFIZIERT** (Backend im `integration`-Service,
> Schema `rechnung`). Die nächsten Milestones (R2 ZUGFeRD, R3 Debitoren-Match/Merge, R4 DATEV …)
> bleiben Konzept; der DATEV-Weg ist weiter beim Kunden zu klären.

## Optionale Erweiterung — Billing-Erlös ins Controlling/Lightdash
Heute speist nur der **Vendure-Shop** (per dlt) und HubSpot die Analytics-Marts; die ausgestellten
**Rechnungen (`rechnung.*`) sind in Lightdash unsichtbar** — d. h. Berufsschul-/Hochschul-Erlöse
fehlen in der BI. **GEBAUT (2026-06-14):** dbt liest `rechnung` direkt als Source (gleicher
`controlling`-DB-Owner), **PII-minimiert im Staging** (keine Teilnehmer-/Zeitraum-Klartexte):
`stg_rechnung_belege` + `stg_rechnung_positionen` → Mart **`fct_revenue_billed`** (fakturierter
Netto-Erlös je Monat × Bereich; Storno/Gutschrift sind negativ gespeichert und netten automatisch).
Damit zeigt Lightdash den Umsatz aus dem **Billing-SoR über alle Bereiche** (Schule/Hochschule/
Akademie/Shop), nicht nur den Shop-Strom.
