# R0 — Klärung & Fragenkatalog (Rechnungsstellung → DATEV)

> Vorbereitung des Kunden-/StB-Gesprächs (Meilenstein **R0** aus
> [Konzept-Rechnungsstellung-DATEV.md](Konzept-Rechnungsstellung-DATEV.md) §7). Pro offenem Punkt:
> **Frage(n)** · **unsere Vor-Analyse/Empfehlung** · **Entscheidung/Antwort** (zu befüllen).
> Adressat je Frage markiert: **[EBZ]** = Fachbereich/IT der EBZ, **[StB]** = Steuerberater.
> Stand 2026-06-13. Rechtliche/steuerliche Aussagen = Recherchestand, **final mit StB bestätigen**.

---

## A. Entscheidungen, die wir jetzt schon festzurren können (keine Kundenfrage nötig)
Vorschlag zur Bestätigung — kein externer Input erforderlich:
- **E-Rechnungs-Format:** **ZUGFeRD ≥ 2.x** als Default (hybrid, B2B + B2C tauglich); **XRechnung**
  automatisch, sobald **Leitweg-ID** vorhanden (Behörden). Mustang kann beides.
- **Validator als Pflicht-Tor:** keine Auslieferung/Übergabe ohne erfolgreichen EN-16931-Report.
- **Architektur:** eigener **Quarkus-Billing-Service** (Option A), `erechnung`-Prototyp als ZUGFeRD/
  DATEV-Kern; Vendure bleibt Order-/Zahlungs-SoR. *(final mit Gesamt-Architektur freizugeben)*
- **GoBD-Archiv:** **MinIO + Object-Lock (WORM)**, 10 J. Retention — Lizenzkosten 0 €, ist im Stack.
- **Debitoren-Nummernkreis-Hoheit:** **eine** autoritative Vergabestelle in der MDM/Integration.

➡️ **Bestätigt? JA (User, 2026-06-13) — Abschnitt A festgezurrt.**

---

## B. Offene Punkte (für das Gespräch)

### B1. DATEV-Übergabeweg  🔴 *blockt R4*
**Hintergrund:** „DATEVconnect" heißt heute **DATEV-Datenservices/Cloud-Services**. Drei realistische
Wege (Mahnwesen + Lastschrift bleiben in jedem Fall in DATEV):
| Weg | Was läuft darüber | Passt für |
|---|---|---|
| **Buchungsdatenservice** (Cloud) | komplette **Buchungssätze + Stammdaten (Debitoren) + Belegbilder** → DATEV Kanzlei-Rechnungswesen | **Empfohlen** für die Buchungs-/Belegübergabe aller Ströme |
| **Rechnungsdatenservice 2.0** (Cloud) | aus **Onlineshop-Systemen** → **Buchungsvorschläge** in Kanzlei-Rechnungswesen | speziell für den **Vendure-Shop**-Anteil |
| **EXTF-Buchungsstapel (CSV)** | Datei-Export wie heute, aber aus **einer** Quelle automatisiert | **Brücke/Fallback**, wenn (noch) kein Cloud-Service |

**Fragen:**
- **[EBZ/StB]** Nutzt die Kanzlei/EBZ **DATEV Unternehmen online** + einen **Datenservice (Cloud)** —
  oder läuft DATEV rein **on-prem** (dann zunächst nur EXTF-Import)?
- **[EBZ/StB]** Welcher Service ist **lizenziert/verfügbar**: Buchungsdatenservice? Rechnungsdaten-
  service 2.0? Beides?
- **[EBZ]** Gibt es bereits eine **DATEV-Partner-/Marktplatz-Registrierung** (für API/OAuth2-Zugang)?
- **[StB]** Will die Kanzlei **fertige Buchungssätze** oder **Buchungsvorschläge** (Review in DATEV)?

**Vor-Analyse/Empfehlung:** Zielbild **Buchungsdatenservice** für die Buchungs-/Beleg-/Debitoren-
Übergabe (deckt genau unseren Bedarf), **Rechnungsdatenservice 2.0** zusätzlich für die Shop-
Transaktionen; **EXTF-CSV** als Übergangslösung, falls Cloud-Onboarding dauert. Wir kapseln den Weg
hinter dem Interface `DatevUebergabe` → austauschbar, R4 startet gegen Mock/Sandbox.

➡️ **Entscheidung: ____**

---

### B2. Umsatzsteuer je Leistung  🔴 *bestimmt Steuerausweis je Position*
**Hintergrund:** Bildungsleistungen sind häufig **umsatzsteuerbefreit**; relevant sind v. a.
**§ 4 Nr. 21 UStG** (Schul-/Bildungsleistungen mit Bescheinigung), **§ 4 Nr. 22** (Kurse/Vorträge
bestimmter Träger) und **§ 4 Nr. 23** (Beherbergung/Beköstigung **Jugendlicher zu Ausbildungs-
zwecken**). Die `erechnung`-Vorlage steht bereits auf **„befreit"**.

**Fragen (alle [StB]):**
- Unterricht **Berufsschule** / **Hochschule** — befreit nach § 4 Nr. 21? Mit welcher **Bescheinigung**?
- **Übernachtung (Doppel-/Einzelzimmer) + ggf. Verpflegung** für Azubis — befreit nach § 4 Nr. 23,
  oder **steuerpflichtig** (Beherbergung 7 %, sonstige 19 %)?
- **Seminare** (Akademie, auch Privat-/Firmenkunden) — befreit oder steuerpflichtig?
- **Shop-Produkte** (Bücher 7 %, sonstiges 19 %) — bereits in Vendure korrekt?
- Pro Rechnung können **mehrere Steuersätze/Kategorien** zusammentreffen (z. B. Unterricht befreit +
  Übernachtung steuerpflichtig) → bestätigen, dass **Steuer je Position** geführt werden muss.

**Vor-Analyse:** Datenmodell trägt Steuer **je Position** (EN-16931-Kategoriecodes: `E` befreit mit
Grund, `S` Standard, …); Default „befreit" nur für die nachweislich befreiten Leistungen.

➡️ **Entscheidung/Steuermatrix je Leistung: ____**

---

### B3. Debitoren-Nummernkreise & Migration  🟠 *Kernproblem heute*
**Hintergrund:** Heute überschneiden sich Nummernkreise mehrerer Systeme → **Doppel-Debitoren**.
Ziel: **eine** Vergabestelle, **ein** lückenloses Schema, Mapping auf bestehende DATEV-Debitoren.

**Fragen:**
- **[StB]** Welcher **Kontenrahmen** (SKR03/SKR04) und welcher **Debitoren-Nummernkreis** in DATEV
  (typisch 10000–69999)? Gibt es **reservierte Bereiche** je Bereich (Berufsschule/Hochschule/…)?
- **[EBZ]** Welche Systeme vergeben **heute** Debitorennummern (führendes System)? Wie viele
  Debitoren-Bestände, wo liegen die **Dubletten**?
- **[EBZ/StB]** **Migrationsstrategie:** Bestand übernehmen + Dubletten bereinigen, oder Neuvergabe
  mit Mapping-Tabelle?
- **[EBZ]** Eine Person kann **mehrere Rollen** haben (Azubi *und* Privatkunde) — ein Debitor oder
  mehrere? Firmen (Ausbildungsbetrieb, dual-Studium-Arbeitgeber) als **eigene Debitoren**?

**Vor-Analyse/Empfehlung:** Debitoren-Stammdaten + Nummernvergabe in der **MDM/Integration** (Naht zur
Formularverwaltung); **idempotente** Vergabe, eindeutige Person/Firma → eine Debitorennummer; Mapping-
Tabelle Kunde ↔ DATEV-Debitor; Dubletten-Erkennung als eigener R3-Schritt.

➡️ **Entscheidung: ____**

---

### B5. Storno- & Gutschrift-Regeln  🟠 *zentral für Berufsschule*
**Hintergrund:** Berufsschule erzeugt **Gutschriften** für ungenutzte Teile (Krankheit, Abbruch) und
**nachträgliche Berechnungen** (z. B. Block-B-Übernachtung +400 €). Buchhalterisch heißt „Gutschrift"
oft **Storno-/Korrekturrechnung** mit Bezug auf die Originalrechnung (nicht Gutschrift i. S. § 14 UStG).

**Fragen:**
- **[EBZ/StB]** Korrektur als **Stornorechnung + Neurechnung** oder als **Teilgutschrift** zur
  Originalrechnung? **Bezug** (Originalrechnungsnummer) verpflichtend?
- **[EBZ]** **Berechnungslogik** der anteiligen Gutschrift bei Krankheit/Abbruch: pro **Block**, pro
  **Tag/Nacht**, Stichtag? Beispiel Doppelzimmer 1.300 € über N Blöcke — Anteil je Block?
- **[EBZ/StB]** **Nummernkreise**: eigener Kreis für Gutschriften/Stornos? Eigene **Belegart** für DATEV?
- **[EBZ]** **Fristen** (bis wann Storno vor/nach Rechnungslauf)? Reaktion bei bereits per Lastschrift
  eingezogener Rechnung (Rückerstattung über DATEV)?

**Vor-Analyse:** Datenmodell **Gutschrift/Korrektur** mit **Pflicht-Referenz** auf Originalrechnung,
Teil-/Vollbetrag, eigener Belegart/Nummernkreis; anteilige Berechnung als handgeschriebene Domänen-
regel (R5). Eingezogene Beträge → Rückabwicklung bleibt in DATEV (Lastschrift/Mahnwesen).

➡️ **Entscheidung: ____**

---

### B6. E-Rechnungs-Pflicht-Timeline der EBZ  🟢 *weitgehend beantwortbar*
**Recherchestand (Wachstumschancengesetz):**
- **Seit 1.1.2025:** **Empfang** strukturierter E-Rechnungen (B2B) **verpflichtend**; PDF nur noch mit
  Zustimmung; reines PDF ist **keine** E-Rechnung mehr.
- **Ab 1.1.2027:** **Ausstellungspflicht** B2B für Unternehmen mit **Vorjahresumsatz > 800.000 €**.
- **Ab 1.1.2028:** Ausstellungspflicht **für alle** B2B.
- **B2C** (Azubis/Studierende/Privatpersonen) ist **nicht** erfasst — ZUGFeRD dort dennoch unschädlich.

**Fragen:**
- **[EBZ/StB]** **Vorjahresumsatz > 800.000 €?** (sehr wahrscheinlich) → dann **Ausstellungspflicht
  B2B ab 2027**; sonst 2028.
- **[EBZ]** Welche Ströme sind **B2B** (Ausbildungsbetrieb zahlt Berufsschule, Arbeitgeber zahlt dualen
  Studienanteil, Firmen-Seminare/Shop) vs. **B2C** (privat zahlende Azubis/Studierende)?
- **[EBZ]** Ist der **Empfang** von E-Rechnungen heute schon gelöst (Pflicht seit 2025)?

**Vor-Analyse/Empfehlung:** Unabhängig vom Stichtag **früh ausstellungsfähig** werden (ZUGFeRD-Default
deckt B2B **und** B2C in einem Format ab → keine Sonderfälle). Empfangsseite separat prüfen.

➡️ **Entscheidung: ____**

---

## C. Ergebnis von R0 (Stand 2026-06-13, User-Antworten; StB-Details offen)
- Gesperrte Entscheidungen aus A: **bestätigt** (ZUGFeRD-Default, Validator-Gate, Architektur A, MinIO-WORM, Debitoren-Hoheit MDM).
- DATEV-Weg (B1): **DATEV Cloud** (DATEV Unternehmen online) → Buchungsdatenservice/Rechnungsdatenservice-Weg; konkreter Service + Partner-Registrierung noch zu klären.
- Steuermatrix (B2): EBZ = **gemeinnützige Stiftung des Privatrechts → teilweise umsatzsteuerbefreit** → **Steuer je Position** (befreit + steuerpflichtig gemischt). Genaue Zuordnung je Leistung mit **StB** final klären.
- Debitoren-Schema + Migration (B3): **offen** [EBZ/StB].
- Storno/Gutschrift-Logik (B5): **offen** [EBZ/StB].
- Ausstellungspflicht-Stichtag EBZ (B6): **Umsatz > 800.000 € → Ausstellungspflicht B2B ab 1.1.2027** (B2C nicht erfasst).
→ **Ansatz festgelegt:** ZUGFeRD/E-Rechnung wird **unabhängig** im bestehenden `integration`-Service
umgesetzt (Library-Stack Mustang/PDFBox, Konzept §4f); das separate `erechnung`-Repo wird **ignoriert**
(kein Code-Merge, nur Bibliotheks-/Versionswahl übernommen). B3/B5 weiter mit Kunde/StB zu klären.

---

## Quellen (Recherche 2026-06-13)
- E-Rechnungspflicht/Zeitplan: [IHK Frankfurt](https://www.frankfurt-main.ihk.de/recht/uebersicht-alle-rechtsthemen/steuerrecht/umsatzsteuer-national/e-rechnungspflicht-ab-2025-6055774) ·
  [DATEV: gesetzliche Regelungen](https://www.datev.de/web/de/berufsgruppenuebergreifend/themen-im-fokus/e-rechnung-mit-datev/gesetzliche-regelungen) ·
  [E-Rechnung Bund (B2B)](https://e-rechnung-bund.de/e-rechnung/e-rechnung-zwischen-unternehmen-b2b/)
- DATEV-Schnittstellen: [Buchungsdatenservice (DATEV)](https://www.datev.de/web/de/berufsgruppenuebergreifend/mydatev/datenservices/buchungsdatenservice-einrichten) ·
  [Rechnungsdatenservice 2.0 (DATEV-Magazin)](https://www.datev-magazin.de/produkte-services/rechnungsdatenservice-2-0-startet-33207) ·
  [DATEV-Datenservices (früher DATEVconnect)](https://intercom.help/stb-d004887ee699/de/articles/4298855-datev-datenservices-fruher-datevconnect-einfach-erklart)
