# Konzept Rechnungsstellung & E-Rechnung → DATEV (Planung, kein Code)

> Stand 2026-06-13. Liefergegenstand dieser Runde = **Konzept + Architektur + Milestone-Roadmap**,
> **kein Code**. Der **DATEV-Übergabeweg bleibt offen** (beim Kunden zu klären) und wird hinter einer
> austauschbaren Schnittstelle geplant.

## Context

Die EBZ rechnet über **vier Erlösströme** mit komplexen Regeln ab (Berufsschule halbjährlich
mit Unterricht + Übernachtung, Hochschule semesterweise mit Split/Raten, Seminare auf Rechnung,
Shop-Produkte Karte/Rechnung) — inkl. Gutschriften, nachträglicher Berechnung, Stornos.
**Heute** erzeugt eine heterogene Softwarelandschaft je Komponente
**DATEV-CSV** (EXTF-Buchungsstapel), die manuell synchronisiert werden → fehleranfällig;
**Debitoren-Nummernkreise überschneiden sich** → doppelte Debitoren; Mahnwesen + Lastschrift
laufen in DATEV und sollen dort bleiben. Dazu kommt die **E-Rechnungs-Pflicht** (B2B).

**Ziel:** Lösungen evaluieren, wie die Rechnungsstellung (inkl. E-Rechnung) künftig **in der
Software** verwaltet und **sauber an DATEV** übergeben wird.

---

## 1. Ist-Zustand — Antwort auf die drei Fragen

### 1a. Wo werden die Rechnungen heute geschrieben?
- **Im Showcase: noch nirgends als Dokument.** Vendure hält die **Bestellungen**; das Plugin
  `showcase/vendure/src/plugins/recurring-invoice/` materialisiert aus Subscription-/Raten-Positionen
  `Installment`-Datensätze und schaltet sie im „Rechnungslauf" nur **`scheduled → invoiced`**
  (reiner Status). **Kein Rechnungsdokument, keine Buchung, kein DATEV.** Der Code markiert die
  Stelle explizit: `recurring-invoice.service.ts:64-65` („Hier würde später die E-Rechnung/DATEV-
  bzw. SEPA-Übergabe andocken").
- **Separates Repo `c:\dev\workspacesIJ\erechnung\`** existiert als eigenständiger Quarkus-Prototyp
  (ZUGFeRD via Mustang + DATEV-Upload). **Wird hier NICHT eingezogen / nicht gemergt** — dient
  ausschließlich als **Referenz/Machbarkeitsnachweis**, dass der **Library-Stack** (Mustang/PDFBox/
  openapi-generator) auf **Quarkus 3.33.2** lauffähig ist. Die E-Rechnung wird im Showcase
  **unabhängig** umgesetzt (§4f).
- **Realwelt EBZ:** jede Fachsoftware erzeugt ihre Rechnungen selbst.

### 1b. Wie werden sie verbucht?
- **Heute:** jede Komponente exportiert **DATEV-Format (EXTF-Buchungsstapel, CSV)**; Import in DATEV,
  **manuelle Synchronisation**. **Im Showcase: gar keine Verbuchung.**

### 1c. Wie kommen sie nach DATEV?
- **Heute:** CSV-Dateien pro Tool → manuell → fehleranfällig. **Debitoren** über **Nummernkreise**
  organisiert, die sich bei der heterogenen Landschaft **überschneiden → Doppelvergabe**, hoher
  Korrekturaufwand. **Mahnwesen + Lastschrift** laufen vollständig in DATEV und **bleiben dort**.

---

## 2. Zielbild — „EIN Rechnungs-Gehirn"

Statt N Tools mit je eigenem CSV-Export: **eine Stelle**, die für **alle** Erlösströme die Rechnung
erzeugt, daraus die **E-Rechnung** (ZUGFeRD) bildet, **Debitorennummern aus einer Quelle** vergibt,
**GoBD-konform archiviert** und **Buchungs-/Belegdaten hinter einer austauschbaren Schnittstelle an
DATEV** übergibt — während **Mahnwesen + Lastschrift in DATEV** bleiben.

**Fünf Säulen:**
1. **Billing-Engine** (Domänenregeln je Rechnungsart) — *handgeschrieben* (Leitplanke F5: den MDM-/
   Abrechnungs-Kern liefert kein Generator).
2. **E-Rechnung** (ZUGFeRD/XRechnung, PDF/A-3 + EN-16931-XML, Validator als Pflicht-Tor) —
   **eigene Implementierung im `integration`-Service**; Erzeugungs-**Alternativen in §4f evaluiert**
   (Empfehlung: Mustang/PDFBox, Apache-2.0). Das separate `erechnung`-Repo wird **nicht** eingezogen.
3. **Debitoren-Stammdaten / Nummernkreis-Hoheit** — *eine* autoritative Quelle (MDM/Integration);
   löst die Überschneidungen. Naht zur bestehenden **Formularverwaltung-MDM**.
4. **GoBD-Archiv** (revisionssicher, WORM) — MinIO Object-Lock vs. ecoDMS (siehe `gobd-archiv-optionen`
   aus dem erechnung-Projekt).
5. **DATEV-Übergabe hinter Interface** `DatevUebergabe` — konkreter Weg **offen** (siehe §4c).

### Architektur-Verortung (evaluiert, Empfehlung)
| Option | Beschreibung | Bewertung |
|---|---|---|
| **A — im bestehenden `integration`-Quarkus-Service** | Billing/E-Rechnung als **neues Package** neben der `bildung`-MDM; **eigene Implementierung** mit dem Library-Stack §4f; zieht Bestellungen/Raten aus Vendure (Pull). | **EMPFOHLEN (User-Vorgabe).** Nutzt vorhandene Infra (DB/OIDC/Compose stehen), keine neue Einheit; eine Java-Codebasis neben MDM. |
| **B — separater Quarkus-Billing-Service** | Eigener Service nur für Billing. | Sauberere Trennung, aber neue Compose-/Build-Einheit — **jetzt nicht nötig**. Spätere Ausgliederung bleibt möglich. |
| **C — Vendure-Plugin (Node) ausbauen** | recurring-invoice-Plugin zur E-Rechnungs-Engine erweitern. | **Nicht empfohlen:** ZUGFeRD/DATEV-Bibliotheken sind Java; in Node schwächer. |

**Empfehlung: A** — die E-Rechnung wird **unabhängig im `integration`-Service** umgesetzt (Pull aus
Vendure). Naht analog zur P1.3-Projektion gespiegelt: Vendure bleibt **Order-/Zahlungs-SoR**, das
Billing-Package ist **Rechnungs-/Beleg-SoR**. Das separate `erechnung`-Repo wird dabei **ignoriert**
(nur Library-/Versionswahl übernommen, §4f).

---

## 3. Domänen-Rechnungsarten (Abrechnungsmatrix)

| Strom | Takt | Leistungen / Struktur | Besonderheiten |
|---|---|---|---|
| **Berufsschule** | halbjährlich, komplett | Unterricht **1.500 €/HJ**; + **Doppelzimmer ~1.300 €** *oder* **Einzelzimmer ~1.260 €** (alle Blöcke zusammen); *oder* ohne Übernachtung | **Gutschriften** für ungenutzte Teile (Krankheit, Ausbildungsabbruch); **nachträgliche Berechnung** (z. B. Block B Übernachtung **+400 €**) |
| **Hochschule** | semesterweise | Komplettbetrag **oder Ratenzahlung**; **Rechnungs-Split** (z. B. Unternehmen zahlt die Hälfte — duales/berufsbegleitendes Studium) | Aufteilung Rechnungsempfänger (Unternehmen ↔ Studierende) |
| **Seminare** | je Buchung | auf Rechnung | Unternehmens- vs. Privatkunde |
| **Shop-Produkte** | je Bestellung | Kreditkarte; bei Unternehmenskunden auch auf Rechnung | bereits in Vendure abgebildet |
| **Querschnitt** | — | **Storno**, **Gutschriften** (Korrektur-/Teilgutschrift) | **USt:** Bildungsleistungen i. d. R. **befreit** (§4 UStG) — Übernachtung/Verpflegung ggf. anders → **mit StB klären** |

→ Datenmodell muss tragen: **Rechnung** (Kopf + Positionen + **Steuer je Position** — wichtig, weil
EBZ als gemeinnützige Stiftung **teilweise** USt-befreit ist), **Gutschrift** (Bezug auf
Originalrechnung, Teil-/Voll), **Ratenplan** (kommt aus Vendure-Installments), **Split-Empfänger**,
**Debitor** (Nummernkreis). Dafür ein **eigenes EBZ-Rechnungs-
Domänenmodell** (fachlich an der EBZ-Rechnungsvorlage orientiert) — **unabhängig** vom erechnung-Repo.

---

## 4. Evaluierte Lösungsfragen

### 4a. Wo lebt die Rechnungsstellung? → §2-Tabelle (Empfehlung A).

### 4b. E-Rechnungs-Format
- **ZUGFeRD ≥ 2.x (hybrid PDF/A-3 + CII-XML)** = Default: menschenlesbar + maschinell, passt für
  B2B **und** B2C, deckt Seminar-/Studien-/Berufsschulrechnungen. *(Mustang kann es bereits.)*
- **XRechnung (reines XML)** nur falls Behörden-Empfänger mit **Leitweg-ID** (Feld ist im Modell schon
  vorgesehen). → **automatische Profilwahl:** `leitwegId` gesetzt ⇒ XRechnung, sonst ZUGFeRD.
- **Pflicht-Tor:** nie ohne erfolgreichen `ZUGFeRDValidator`-Report ausliefern/übergeben.

### 4c. DATEV-Übergabeweg — **OFFEN (beim Kunden klären, Details + Fragenkatalog in [R0](R0-Klaerung-Fragenkatalog.md) §B1)**
Hinter **einem** Interface `DatevUebergabe` kapseln, damit der Weg austauschbar ist. Hinweis:
„DATEVconnect" heißt heute **DATEV-Datenservices/Cloud-Services**.
- **Buchungsdatenservice (Cloud)** — **empfohlen:** komplette **Buchungssätze + Stammdaten
  (Debitoren) + Belegbilder** → DATEV Kanzlei-Rechnungswesen. Deckt unseren Bedarf für alle Ströme.
- **Rechnungsdatenservice 2.0 (Cloud)** — aus **Onlineshop-Systemen** → **Buchungsvorschläge**;
  passend speziell für den **Vendure-Shop**-Anteil. (Der erechnung-Prototyp zielt bereits hierauf.)
- **EXTF-Buchungsstapel (CSV), automatisiert** — **Brücke/Fallback** wie heute, aber aus **einer**
  Quelle (keine manuelle Sync, keine Nummernkreis-Überschneidungen).
- **Konstant:** **Mahnwesen + Lastschrift bleiben in DATEV.** Wir liefern Beleg + Buchungssatz +
  korrekte **Debitorennummer**; DATEV mahnt/zieht ein.
- **Zu klären (R0):** DATEV Unternehmen online/Cloud-Service vs. rein on-prem? Welcher Service
  lizenziert? Partner-/Marktplatz-Registrierung vorhanden? Fertige Buchungssätze vs. -vorschläge?

### 4d. Debitoren-Nummernkreis-Hoheit (löst das Kernproblem)
- **Eine autoritative Vergabestelle** in der Integration/MDM-Schicht: zentrale Debitoren-Stammdaten,
  **idempotente** Nummernvergabe je Kunde, **ein** Nummernkreis-Schema (keine überlappenden Kreise).
- **Mapping-Tabelle** Kunde ↔ Debitorennummer; **Abgleich/Migration** bestehender DATEV-Debitoren
  (Dubletten-Erkennung) als eigener Schritt. Naht zur **Formularverwaltung-MDM** (gleicher MDM-Kern).

### 4e. GoBD-Archiv
- **MinIO + Object-Lock (Compliance/WORM), 10 J. Retention** = empfohlen (0 € Lizenz, S3-SDK aus
  Quarkus, MinIO ist im Stack schon vorhanden). Alternative **ecoDMS** (89 € einmalig) wenn die
  Compliance-Last minimiert werden soll. **Verfahrensdokumentation ist in jedem Fall Pflicht.**
  Detail: `gobd-archiv-optionen` (erechnung-Projekt).

### 4f. Erzeugung der E-Rechnung — Lösungsalternativen (evaluiert)
**Verortung (gesetzt):** unabhängig im bestehenden `integration`-Service (Quarkus 3.33.2), neben der
`bildung`-MDM — **kein Code-Merge aus dem `erechnung`-Repo**. Offen ist die **Erzeugungs-Lösung**;
daher hier evaluiert. Drei Achsen sauber trennen: **(1) Erzeugung** des EN-16931-Dokuments ·
**(2) Übermittlung** an den Empfänger · **(3) Buchungsübergabe** an DATEV (= §4c).

**Achse 1 — Wer erzeugt die E-Rechnung?**
| Ansatz | Beschreibung | Bewertung |
|---|---|---|
| **(a) Selbst im `integration`-Service (JVM-Library)** | eigenes EBZ-Domänenmodell → Library erzeugt ZUGFeRD/XRechnung + validiert | **EMPFOHLEN:** volle Kontrolle, Daten bleiben im System, passt zu „EIN Rechnungs-Gehirn"; Aufwand = Implementierung |
| (b) DATEV E-Rechnungsplattform / **SmartTransfer** | DATEV erzeugt/überträgt (Netzwerke TRAFFIQX®/Peppol) | stark v. a. für **Übermittlung/Empfang** (Achse 2); die Rechnungs-/Positionsdaten müssen trotzdem aus dem System kommen; Abhängigkeit + Kosten |
| (c) Externer Dienstleister / **Peppol-Access-Point** (SaaS) | Erzeugung/Versand ausgelagert | wenig Eigenbau, aber Fremdabhängigkeit, Daten extern, laufende Kosten/OpenPeppol-Mitgliedschaft |
| (d) Vendure-Invoice-Plugin (Node) | PDF-Rechnungen in Vendure | **nein:** kein EN-16931/ZUGFeRD nativ |

**Achse 1 · Detail — falls (a): welche JVM-Library?**
| Option | Lizenz | Kann | Bewertung |
|---|---|---|---|
| **Mustang (mustangproject)** | **Apache 2.0** (kommerziell frei) | ZUGFeRD 2.4/1, Factur-X, **XRechnung (CII 3.0.2)**; lesen/schreiben/**validieren**; PDF/A + XML kombinieren | **EMPFOHLEN:** permissive Lizenz, breite Format-+Validator-Abdeckung, aktiv |
| Konik | **AGPL** (+ kommerziell) | ZUGFeRD (Java/.NET) | AGPL-Copyleft = Risiko für proprietär/SaaS → nur mit kommerzieller Lizenz; **nachrangig** |
| Eigenes XML (JAXB/Template) + **KoSIT-Validator** | — | EN-16931 selbst mappen; KoSIT = amtlicher Validator | maximale Kontrolle, **hoher Eigenbau**; nur falls Library nicht reicht. KoSIT ggf. **zusätzlich** als unabhängiges Prüf-Tor |
| PDF/A-3-Träger | **PDFBox = Apache 2.0** (von Mustang genutzt) ↔ **iText = AGPL/kommerziell** | PDF/A-3 erzeugen | **PDFBox** wegen Lizenz |

**Empfohlener Stack (Ergebnis des Vergleichs):** (a) + **Mustang** (`library` + `validator`) auf
**PDFBox/xmpbox** (Version exakt an Mustangs PDFBox pinnen, sonst Klassenpfad-Konflikt) + eingebettete
**TTF** (PDF/A-3-Pflicht); optional **KoSIT-Validator** als zweite, amtsnahe Prüfung. DATEV-Client für
R4 via **`quarkus-openapi-generator`** (+`-oidc`, `-lts`) + `quarkus-rest-client-oidc-filter`.
**Pipeline:** eigenes `RechnungDaten` → PDF/A-3 → ZUGFeRD-XML (Profil EN16931; `leitwegId` ⇒ XRechnung)
→ **Validator-Gate** → GoBD-Archiv (§4e) → `DatevUebergabe` (§4c). **Steuer je Position.**

**Achse 2 — Übermittlung an den Empfänger (eigene Entscheidung, später):** E-Mail (ZUGFeRD-PDF),
**Peppol** (über Access-Point-Dienstleister, z. B. DATEV/TRAFFIQX) oder **DATEV E-Rechnungsplattform/
SmartTransfer**. B2C (Studierende/Azubis) i. d. R. Mail/Portal; B2B künftig ggf. Peppol. **Nicht Teil
von R2** — als Achse offen halten.

---

## 5. Nähte zu den bestehenden Showcases
- **Vendure** (Bestellungen/Zahlungen/Installments) → Billing-Service zieht die abrechenbaren Vorgänge.
- **Formularverwaltung-MDM** → Debitoren-Stammdaten + Nummernkreis-Hoheit.
- **Controlling** → Rechnungen/Erlöse als Profitabilitäts-Dimension (knüpft an `seminar_cost`).
- **erechnung-Repo** → **nicht Teil der Lösung** (kein Code-Merge). Dient nur als **Referenz**, dass
  der Library-Stack (Mustang/PDFBox/openapi-generator) mit Quarkus 3.33.2 funktioniert. Die ZUGFeRD-
  Funktion entsteht **unabhängig** im `integration`-Service (§4f).

---

## 6. Offene Punkte — beim Kunden / mit StB zu klären
1. **DATEV-Weg:** API/Rechnungsdatenservice vs. DATEVconnect; läuft DATEV on-prem? Partner-Reg.?
2. **USt je Leistung:** Unterricht (befreit) vs. Übernachtung/Verpflegung (befreit? 7 %?).
3. **Bestehende Debitoren-Nummernkreise** + Migrations-/Dubletten-Strategie.
4. **Storno-/Gutschrift-Regeln** (Fristen, Teil-/Vollgutschrift, Bezug auf Originalrechnung).
5. **E-Rechnungs-Pflicht-Timeline** für die EBZ (B2B-Anteil; Umsatzgrenze) — Übergangsfristen mit StB.

---

## 7. Milestone-Roadmap (für die spätere Bauphase — hier nur skizziert)
- **R0** Konzept-Freigabe + offene Punkte (§6) mit Kunde/StB klären.
- **R1** Billing-Datenmodell + **eine** Rechnungsart end-to-end (Vorschlag: **Berufsschule halbjährlich**,
  3 Leistungen) → interne Rechnung erzeugt.
- **R2** **ZUGFeRD eigenständig** im `integration`-Service umsetzen (Library-Stack §4f: Mustang/PDFBox)
  + Validator-Pflicht-Tor + GoBD-Archiv (MinIO-WORM). **Kein Code aus dem erechnung-Repo.**
- **R3** **Debitoren-Nummernkreis-Hoheit** (MDM) + Mapping.
- **R4** **DATEV-Übergabe hinter Interface** — zunächst gegen **Mock/Sandbox** (Weg offen), Buchungssatz
  + Debitor + Beleg.
- **R5** **Gutschriften / Storno / nachträgliche Berechnung** (Querschnitt).
- **R6** **Hochschule** (Semester, Ratenzahlung aus Vendure-Installments, **Rechnungs-Split**).
- **R7** Seminare/Shop auf Rechnung anschließen; Controlling-Naht (Erlös-Dimension).

---

## 8. Verifikation
- **Diese Runde (Doku):** Review/Freigabe durch Stakeholder; §6 mit Kunde/StB abgleichen.
- **Spätere Milestones:** jeweils end-to-end gegen den laufenden Stack (Smoke-Skripte wie bei
  P1.x), **ZUGFeRD-Validator** als hartes Gate, DATEV gegen Sandbox/Mock, Archiv gegen lokales MinIO.
