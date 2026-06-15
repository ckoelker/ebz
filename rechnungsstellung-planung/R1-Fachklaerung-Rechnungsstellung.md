# R1 — Fachklärung Rechnungsstellung (Antworten zu den offenen Fragen)

> Mitschrift der Domänen-Klärung für die Code-Planung der kompletten Rechnungsstellung.
> Stand 2026-06-13. ✅ = beantwortet · 🟡 = offen · 💡 = Empfehlung.

## Architektur-Rollen (bestätigt 2026-06-13)
**Vendure bleibt eigene Säule** = **Commerce/Zahlung-SoR** (Katalog, Webshop, Online-Karte/Stripe,
SEPA, Subscription-Charging, Kunden-Self-Service). **`integration` (Quarkus) = MDM + Rechnung/Beleg/
Buchung-SoR.** **Naht:** eine **bezahlte Vendure-Order → Abrechnungsbasis** im Billing (quellen-
agnostisch, gleichberechtigt neben den `Anmeldung`en aus Berufsschule/Hochschule).

## Bereits aus R0 geklärt
- DATEV-Weg: **Cloud** (Buchungsdatenservice/Rechnungsdatenservice). Konkreter Service + Partner-Reg. offen.
- USt: **teilweise befreit** (gemeinnützige Stiftung) → **Steuer je Position**.
- Ausstellungspflicht B2B **ab 2027** (>800k €).
- E-Rechnung: **ZUGFeRD/Mustang** (Apache 2.0), eigene Implementierung **im `integration`-Service** (Alternativen evaluiert, Konzept §4f).

## 1. Abrechnungsquelle & Auslösung
- ✅ **Q1.1 (Empfehlung bestätigt): zwei Quellen, eine Abrechnungs-Abstraktion.**
  - **Vendure** = Quelle für im Shop Gebuchtes (Shop-Produkte, Shop-Seminare, Abos/Raten via `Installment`).
  - **Berufsschule/Hochschule** = **vertrags-/anmeldungsbasiert** → neues schlankes **`Anmeldung`/`Vertrag`-Modell im `integration`-Service** (referenziert Bildungsangebot-MDM + Teilnehmer + Zahlungspflichtiger + gebuchte Leistungen + Zeitraum).
  - Billing-Engine normalisiert beide auf gemeinsame **`Rechnungsposition`** und gruppiert je Debitor.
  - **Implikation:** schlankes `Anmeldung`-Modell ist Teil von R1 (MDM hat heute nur den Katalog).
- ✅ **Q1.2 Auslösung manuell** → Rechnungslauf-Endpoint (RBAC) erzeugt zunächst **Entwürfe** (Prüfung → Festschreibung), kein Auto-Timer.
- ✅ **Q1.3 Sammelrechnung** → Positionen je Firma/Ausbildungsbetrieb gruppiert (mehrere Teilnehmer/Leistungen = Positionen einer Rechnung).

## 2. Rechnungsempfänger & Debitoren
- ✅ **Q2.1 Nummernkreis je Bereich** (Berufsschule/Hochschule/Akademie eigener, nicht-überlappender Kreis). 🟡 **SKR03 vs SKR04 offen** [StB] — **aber erst für R4 (DATEV-Buchungssatz) relevant, NICHT R1-blockierend.** Erlöskonto/BU-Schlüssel werden nur für *komplette Buchungssätze* (Buchungsdatenservice) gebraucht; bei *Buchungsvorschlägen* (Rechnungsdatenservice 2.0) kontiert die Kanzlei. → Konten-Mapping als **konfigurierbare, dem StB gehörende Tabelle** (Leistungsart × Bereich × Steuerfall → Konto + BU-Schlüssel), befüllt bei R4. Im Modell je Position nur **Leistungsart + Steuerfall** vorhalten.
- ✅ **Q2.2** Der **Vertrag/die Anmeldung** bestimmt den **Zahlungspflichtigen** (Firma oder privat).
- ✅ **Q2.3** Eine Person kann **mehrere Debitoren** haben (rollen-/bereichsabhängig) — bewusst, kein Fehler.
- ✅ **Q2.4 Ist-Stand = Chaos (überlappende Kreise, Doppel-Debitoren). 💡 Empfohlene Auflösungs-Strategie:**
  1. **Inventarisieren:** alle Debitoren aus Quellsystemen **+ DATEV** in eine **Staging-Tabelle** (Quelle, Alt-Nummer, Name, Adresse, USt-IdNr., IBAN, Bereich, Rolle).
  2. **Match/Merge (Identitätsauflösung):** Dubletten erkennen — **primär harte Schlüssel** (USt-IdNr., IBAN, Steuernr.), **sekundär** normalisierte Name+Adresse (Fuzzy) → Kandidaten-Cluster.
  3. **Golden Record + Survivorship:** je Cluster ein führender Datensatz (vollständigste/neueste Quelle gewinnt je Feld); **Alt-Nummern als Aliase behalten** (Mapping Alt→Golden) — DATEV-Historie/offene Posten hängen daran.
  4. **Rollen respektieren (Q2.3):** nur verschmelzen bei **gleicher Rolle/gleichem Bereich**; verschiedene Rollen bleiben getrennte Debitoren (Golden Record je **Person+Rolle/Bereich**).
  5. **Going-forward-Nummernplan:** je Bereich **ein** zentral & **idempotent** vergebener Kreis (Q2.1); Quellsysteme vergeben **keine** Nummern mehr (sonst entsteht das Chaos neu).
  6. **Bestandsbehandlung — zwei Wege:**
     - **(A) Mapping/Alias-Layer [EMPFOHLEN, minimal-invasiv]:** Alt-Nummern bleiben in DATEV gültig; zentrale Neuvergabe nur für Neukunden; Konsolidierung rein über Mapping/Aliase. Kein DATEV-Umbuch-Risiko.
     - (B) Renumbering/Migration auf den neuen Kreis: sauberer, aber DATEV-Umbuchung offener Posten + StB-Abstimmung nötig, riskanter.
  7. **Review-Workflow:** unsichere Matches unter Schwellenwert → **manuelle Klärungsliste** (kein Auto-Merge); DATEV-seitige Zusammenführung offener Posten mit **StB**.
  - **Governance:** ab Cutover ist der `integration`/MDM-Service **einzige Vergabestelle** (Naht zur Formularverwaltung-MDM; Match/Merge/Survivorship = handgeschriebener MDM-Kern, Leitplanke F5).
  - 🟡 Zu klären [EBZ/StB]: ungefähre Debitoren-Anzahl, führende Quellsysteme, ob harte Schlüssel (USt-IdNr./IBAN) flächendeckend vorhanden, Weg A vs B.

## 3. Berufsschule — konkrete Regeln (R1)
- ✅ **Q3.1** **4 Blöcke** je Halbjahr: **A, B, C, D**.
- ✅ **Q3.2** Eine Berufsschul-Rechnung = **1–2 Positionen** mit Halbjahresbeträgen „für alle Blöcke
  zusammen": **Unterricht** (immer, ~1.500 €/HJ) + **Übernachtung** (nur falls Zimmerart ≠ keine:
  Doppel ~1.300 / Einzel ~1.260). **⚠️ Beträge sind immer VARIABLEN** = Positionswerte, **keine
  Tarif-Tabelle / keine Konstanten**. ✅ Quelle = **Felder der `Anmeldung`** (Entscheidung a):
  Rechnungslauf erzeugt die 1–2 Positionen vorbefüllt, im Entwurf editierbar.
- ✅ **Q3.3 Krankheit:** Gutschrift **pro Nacht**, gemeldet vom **Teilnehmer**.
- ✅ **Q3.4 Abbruch:** **manuelle Position mit Betragseingabe** (keine Auto-Berechnung).
- ✅ **Q3.5 Nachträgliche Übernachtung (+400 €):** **wie Q3.4 — manuelle Position mit Betragseingabe.**
- ✅ **Q3.6 USt:** Übernachtung **befreit** → Berufsschule **durchgängig USt-befreit**.

**💡 Modell-Konsequenz R1 (Berufsschule):**
- **Standard-Positionen** automatisch aus der `Anmeldung`: Unterricht 1.500 €/HJ + Zimmer-Halbjahres-
  betrag je nach Zimmerart (KEINE/DOPPEL/EINZEL). Alle **steuerbefreit**.
- **Manuelle Korrektur-Positionen** (ein Mechanismus für Q3.3–Q3.5): freie Beschreibung + Betrag,
  **negativ = Gutschrift** (Abbruch; Krankheit = pro-Nacht-basiert, Eingabe durch Sachbearbeiter nach
  Teilnehmer-Meldung), **positiv = Nachberechnung** (z. B. Block-B-Übernachtung +400 €).
  → **Keine Auto-Pro-rata-Engine nötig.**
- **Sammelrechnung** (Q1.3): `Anmeldung`en je **Zahlungspflichtigem** (Firma) für das Halbjahr
  gruppiert → eine Rechnung, Positionen je Teilnehmer/Leistung.
- ✅ Q3.3 bestätigt: Krankheits-Gutschrift = **manuelle Position** (Sachbearbeiter trägt Betrag nach
  Teilnehmer-Meldung ein; keine Auto-Berechnung).
## 4. Hochschule (R6)
- ✅ **Q4.1** Semesterbetrag **verschieden** (je Studiengang) → liegt als Datum an der `Anmeldung`/am Vertrag, kein fixer Wert.
- ✅ **Q4.2** **Verschiedene Zahlmodelle abhängig von der Anmeldung** (komplett vs. Raten, Anzahl/Intervall je Vertrag) → Zahlplan ist Attribut der `Anmeldung` (nicht aus Vendure; Hochschule ist vertragsbasiert).
- ✅ **Q4.3** **Variabler Split**, vom **Sachbearbeiter** je Fall festgelegt (Anteil Firma vs. Studierende:r).
- ✅ **Q4.4** Zahlt die Firma nicht → **keine** Restschuld bei der/dem Studierenden.

**💡 Modell-Konsequenz (Hochschule):**
- Aus 4.3 + 4.4 (unabhängige Forderungen) folgt: **zwei getrennte Rechnungen** je Semester — eine an
  die **Firma** (Anteil, ggf. Sammelrechnung über mehrere Studierende), eine an **die/den
  Studierende:n** (Restanteil); jede Forderung **eigenständig** (keine gegenseitige Haftung).
- `Anmeldung` (Hochschule) trägt: Studierende:r, Studiengang, Startsemester, **Semesterbetrag**,
  **Zahlmodell** (komplett/Raten + n/Intervall), **Split** (Firma-Anteil als €/% + Firmen-Debitor).
- Ratenzahlung: der **manuelle Rechnungslauf** erzeugt je Lauf die fällige Rate/Rechnung aus dem
  Zahlplan der Anmeldung (kein Vendure-Installment-Pfad für Hochschule).
- ✅ Bestätigt: **zwei getrennte Rechnungen** (Firma + Studierende:r), je eigenständige Forderung.
## 5. Seminare & Shop
- ✅ **Q5.1** Seminare **nicht immer aus Vendure** → auch **Direktrechnung** möglich (deckt die
  zweite Quelle / `Anmeldung`-Pfad ab). **Zahlungsziel auf Rechnung: ja.** **Kundentyp (Firma/privat)
  wird bei der Bestellung/Anmeldung erfasst** (steuert Empfänger/USt-Ausweis).
- ✅ **Q5.2** Shop-Zahlart **nach Kundenwunsch** (Kreditkarte via Stripe **oder** Rechnung), **keine**
  Schwelle/Bonitätsregel.
  - 💡 Konsequenz: **Karte = sofort beglichen** (Stripe) → kein Mahnwesen; Rechnung = unser Billing/
    DATEV-Debitor-Pfad. Beleg/Buchung entsteht in beiden Fällen; Karten-Erlöse buchen über ein
    Stripe-/Geldtransit-Konto (Detail bei R4/R7).
## 7. Storno & Gutschrift
- ✅ **Q7.1 (Empfehlung):** **Korrekturbeleg mit Typ** + **Pflicht-Referenz auf Originalrechnung**:
  - **Teilgutschrift** (negativ) — Berufsschule Krankheit/Abbruch (Q3.3/Q3.4).
  - **Vollstorno** (Stornorechnung, kehrt die ganze Rechnung um) + ggf. neue korrigierte Rechnung.
  - **Nachberechnung** (positiv, eigene Rechnung) — Block-B-Übernachtung (Q3.5).
  - Vor Ausstellung = Position im Entwurf; **nach Festschreibung = eigener Korrekturbeleg** (Normalfall).
- ✅ **Q7.2 (Empfehlung):** **eigener, lückenloser Nummernkreis je Belegart und je Bereich**
  (z. B. `RE-BS-…` Rechnung, `GS-BS-…` Gutschrift); Referenz auf Originalrechnung pflicht.
- ✅ **Q7.3** Bereits per Lastschrift eingezogen → **Rückerstattung über DATEV**.
## 8. Nummernkreise, Belegfluss & Buchung
- ✅ **Q8.1** Rechnungsnummern **je Bereich**, **lückenlos** (GoBD).
- ⏭️ **Q8.2** Erlöskonten/SKR → **nach R4** verschoben (siehe Q2.1).
- ✅ **Q8.3** **SEPA:** wir liefern **Buchung + Debitor (mit Bankverbindung)**; **DATEV zieht per Lastschrift ein** (Mahnwesen/Einzug bleiben in DATEV).
- ✅ **Q8.4 Festschreibung ab Ausstellung.**

**💡 Beleg-Lebenszyklus (aus Q1.2 + Q8.1 + Q8.4):**
`ENTWURF` (aus manuellem Lauf; **editierbar**, **noch keine Nummer**) → **Ausstellung/Festschreibung**
(vergibt **lückenlose Nummer je Bereich**, Beleg **unveränderbar**) → `AUSGESTELLT` →
(`BEZAHLT` via DATEV-Rückmeldung) / `STORNIERT`. Korrekturen nach Festschreibung **nur** über
Storno/Gutschrift-Beleg (Gruppe 7). Nummern werden **erst bei Festschreibung** vergeben → keine Lücken
durch verworfene Entwürfe.
