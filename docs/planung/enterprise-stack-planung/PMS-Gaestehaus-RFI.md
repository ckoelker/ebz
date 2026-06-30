# RFI / Marktvergleich — Gästehaus-Software (Suite8-Ablöse)

> Löst den offenen Punkt **⚪ #19 / Integration S4** aus [Soll-Bebauungsplan](Soll-Bebauungsplan-und-Schnittstellen.md)
> und [Capability-Map #11](Zielarchitektur-Capability-Map.md). Stand 2026-06-30. **Status: RFI — Entscheidung offen**
> (es fehlt die reale Suite8-Kosten-/Modul-Baseline, vom EBZ beizustellen). Recherche-Quellen am Ende.

## 1. Ausgangslage
Das **EBZ-Campushotel** (Springorumallee 20, Bochum) läuft auf **Oracle Hospitality Suite8** (ex Micros-Fidelio)
inkl. dessen **Conference-&-Catering- / Meeting-Planner-Modul** (Conference Diary + Event Management, Bankett von
Anfrage bis Rechnung).

**Eckdaten (recherchiert):** **112 Zimmer / 226 Betten**, **~40.000 Übernachtungen/Jahr**, **>25 Veranstaltungsräume**
(je ≤50 Pers.) + Konferenz-/Eventhalle. Nutzungsprofil: **Mo–Fr v.a. Schüler/Gruppen, Fr–So Hotelgäste/Tagungen**.

**Treiber für die Ablöse:**
1. **Kosten enorm** (Aussage EBZ; reale Zahl als Baseline noch zu erheben).
2. **Zukunft offen:** Oracle investiert ausschließlich in **OPERA Cloud** (>10.000 Installs; Accor/PPHE migrieren
   2025). Suite8 ist EMEA-Legacy/on-prem ohne Modernisierung — strategisch am Ende der Straße. Ein hartes
   öffentliches EOL-Datum nennt Oracle nicht (genaue Premier/Extended-Termine nur über Oracle-Account-Manager).

## 2. Anforderungs-Scope (Nutzer-Vorgabe 2026-06-30)
**Nicht „nur Zimmer/Schlafen"**, sondern der **volle Conference-House-Betrieb**:
Zimmer · Seminarräume · **Bankett (Bestuhlungspläne, Eindecken)** · Kantine · Kiosk · Rezeption ·
Hausmeister-Service · Fixkosten.
Zusätzlich: **klar als eigener Satellit abgrenzbar**, **vollständiger API-Zugriff**; **on-prem nicht zwingend**.

**Kernbefund:** Das ist **Conference & Catering + Facility** — genau der Suite8-Funktionsumfang. **Kein reines
API-first-Zimmer-PMS** (Apaleo/Beds24/Cloudbeds/Mews/Guesty/QloApps) deckt Bankett/Bestuhlung/Eindecken +
Kantine + Kiosk nativ ab.

## 3. Kandidaten (drei Wege)
- **Weg 1 — SIHOT** (GUBSE AG): integrierte DACH-Conference-Hotel-Suite. PMS + **SIHOT.MICE/C&B** (Räume,
  Equipment, Verträge, **Bestuhlung/Eindecken**) + **SIHOT.POS** (Kantine/Kiosk). Cloud/SaaS/on-prem.
  Schnittstellen/API; DACH-Middleware **MICE DESK** koppelt SIHOT/Opera/protel/apaleo/Mews an CRM/Controlling.
- **Weg 2 — OPERA Cloud + Sales & Event Management** (Oracle): offizieller Suite8-Nachfolger; function space +
  catering + seating; offene APIs. **Verbleibt bei Oracle** (Lock-in, teuer/teils metered) — als oberer
  Vergleichsanker.
- **Weg 3 — Best-of-Breed-API-first-Mesh** (Capability-Map-Linie „Commodity kaufen + integrieren"):
  **Apaleo** (Zimmer, API-first, Dev-Sandbox vorhanden) + **iVvy/EventTemple/MeetingPackage** (Bankett/Bestuhlung,
  Sales&Catering, BEOs/Function-Diary) + **POS** (TCPOS/APRO/Lightspeed) + **Mensa** (MensaMax/KantinePlus für
  Schüler-Mo–Fr) + **Kiosk** (APRO Self-Order / Agilysys) + **CMMS** (Hausmeister) + **Controlling-Kern** (Fixkosten).
- *(Alternative protel/Xn protel (Planet) mit Conference & Banqueting als zweiter Suite-Kandidat möglich.)*

## 4. Scope-Abdeckungs-Matrix
| Geforderte Funktion | Weg 1: SIHOT | Weg 2: OPERA Cloud S&E | Weg 3: Best-of-Breed-Mesh |
|---|---|---|---|
| Zimmer / Rezeption / Folio | ✓ nativ | ✓ nativ | Apaleo |
| Seminarräume + **Bestuhlung/Eindecken** + Bankett | ✓ SIHOT.MICE/C&B | ✓ Sales & Event | iVvy/EventTemple/MeetingPackage |
| Kantine (F&B) | ✓ SIHOT.POS | ⚬ via POS-Integration | TCPOS/APRO/Lightspeed → Folio |
| Schüler-Mensa (Mo–Fr) | ⚬ + Mensa-Abrechnung extern | ⚬ extern | MensaMax/KantinePlus |
| Kiosk (Retail/Self-Order) | ✓ POS/Kiosk-Modul | ⚬ via Integration | APRO Self-Order / Agilysys IG Kiosk |
| Hausmeister / Wartung | ⚬ Housekeeping/Maintenance | ⚬ Modul | kleines CMMS/Ticketing |
| Fixkosten / Controlling | ⚬ Reporting (begrenzt) | ⚬ Reporting | **Controlling-Kern** (dlt→dbt→Lightdash)+FiBu |
| Gäste-Identität, Debitoren, E-Rechnung | über **S4** an den Kern | über **S4** an den Kern | über **S4** an den Kern |

✓ nativ · ⚬ teilweise/über Integration. **In allen Wegen identisch:** der Kern-Mehrwert = Anbindung an
Party-Kern (Gäste = teils dieselben Kunden), Debitoren/Rechnung (ZUGFeRD/E-Rechnung), Controlling — via S4.

## 5. Bewertungskriterien
| Kriterium | Weg 1: SIHOT | Weg 2: OPERA Cloud S&E | Weg 3: Best-of-Breed-Mesh |
|---|---|---|---|
| Scope-Abdeckung (ein System) | **hoch** (PMS+C&B+POS) | hoch | verteilt (mehrere Satelliten) |
| API/Integrations-Tiefe | gut (Schnittstellen/MICE DESK) | offene APIs | **am höchsten** (API-first je Komponente) |
| Klare Abgrenzung (Satellit) | gut (ein Satellit) | gut | gut, aber **viele** Satelliten |
| **Oracle-Lock-in-Ausstieg** | **ja** | **nein** (bleibt Oracle) | **ja** |
| Betrieb | SaaS/on-prem, DACH-Support | SaaS (Oracle) | mehr Integrationsbetrieb |
| Schnittstellen-Aufwand (S4) | 1 Anbindung | 1 Anbindung | **mehrere** Anbindungen |
| Reife / DACH-Marktfit Tagungshotel | **sehr hoch** | hoch | mittel (Zusammenbau) |
| Kosten (grob, s. §6) | mittel | **hoch** | niedrig–mittel + Integrationsaufwand |

## 6. Kosten-Schätzung (grob, angebotsabhängig)
- **Weg 1 — SIHOT (112 Zi, PMS+C&B+POS):** keine Listenpreise. Richtwerte: ab ~€150/User/Monat; DACH-Cloud-PMS
  €8–15/Zi/Mo; C&B/POS extra. Überschlag: **laufend ~€18–30k+/Jahr**; **einmalig** (Setup+Migration+Schulung+
  Schnittstellen) **~€10–40k**; **Jahr 1 ~€30–70k**. Plausi über User-Schiene (10–20 User × €150 ≈ €18–36k/J).
- **Weg 3 — Best-of-Breed (Zimmer-PMS-Anteil):** Apaleo ab €8–9/Zi/Mo (Min. €400/Mo) → 112 Zi ≈ **~€11–12k/J**;
  Beds24 günstiger (Trial gratis 14 Tage). **Dazu** MICE/Bankett (iVvy/EventTemple) + POS + Mensa + CMMS — je
  eigene, nutzungsabhängige Posten → Summe stark vom Zuschnitt abhängig.
- **Weg 2 — OPERA Cloud (S&E):** Oracle, teuer/teils metered — oberer Anker.
- **Fehlende Baseline:** reale **Suite8-Kosten + genutzte Module** (EBZ beizustellen). Erst damit ist „SIHOT/Mesh
  ist ein Bruchteil von Suite8" **belegt** statt vermutet.

## 7. Vorläufige Empfehlung (vor Baseline)
- **Wenn „ein System, wenig Schnittstellen, raus aus Oracle" Priorität hat → Weg 1 (SIHOT).** Direkteste 1:1-Ablöse
  des Suite8-Funktionsumfangs (inkl. C&B/Bestuhlung) im DACH-Tagungshotel-Markt; eine Anbindung an den Kern (S4).
- **Wenn API-first-Reinheit/maximale Modularität Priorität hat → Weg 3 (Best-of-Breed).** Passt zur Capability-Map,
  aber spürbar mehr Schnittstellen-/Betriebsaufwand und Integrations-TCO.
- **Weg 2 (OPERA Cloud) nur als Vergleichsanker** — widerspricht dem Ziel „Oracle-Lock-in verlassen".

**Tendenz:** für ein 112-Zimmer-Tagungshotel mit Bankett/Bestuhlung und schlankem Team ist **SIHOT** der
pragmatischere Suite8-Ersatz; das Best-of-Breed-Mesh bleibt die Option, wenn der API-first-/Modularitätswert die
Mehr-Schnittstellen rechtfertigt. **Finale Entscheidung erst nach §8.**

## 8. Offene Punkte (vor Entscheidung zu klären)
1. **Suite8-Baseline:** reale Jahres-/Lizenzkosten + genutzte Module (PMS, C&B, POS, Schnittstellen) — vom EBZ.
2. **Seminarraum-Hoheit:** gehören Raum-/Bankettplanung künftig **ins Haus-System (PMS/MICE)** oder in die
   bestehende **Akademie-/Seminar-Domäne** (Capability-Map #8/#10)? Vermeidet Doppel-Belegungslogik.
3. **Rechnungs-Datenrichtung:** Rechnung **im Kern** erzeugen (Debitoren-/Nummernkreis-Hoheit, ZUGFeRD) und PMS-
   Folio nur als Quelle — empfohlen — vs. PMS-Finance führend.
4. **Pflichten:** E-Rechnung-Pflicht, **BFSG/Barrierefreiheit**, DSGVO/Hosting-Region je Kandidat prüfen.
5. **Angebote:** RFI an SIHOT (Weg 1) + ggf. protel; bei Weg 3 Apaleo + iVvy/EventTemple + POS einholen.

## 9. Nächster Schritt
Nach Klärung §8 → Entscheidung dokumentieren, **⚪#19 / S4** im Bebauungsplan auf Status setzen, dann **Phase B
(PoC-Anbindung an den Kern)** gemäß Plan (PMS-Port/Adapter; Gäste→Party-Kern, Folio→Debitoren/ZUGFeRD,
Belegung→Controlling).

---
## Quellen (Recherche 2026-06-30)
- Capability-Map / Bebauungsplan (intern): [Zielarchitektur-Capability-Map](Zielarchitektur-Capability-Map.md),
  [Soll-Bebauungsplan](Soll-Bebauungsplan-und-Schnittstellen.md).
- EBZ Campushotel Eckdaten: e-b-z.de/tagungshotel · ebz-business-school.de (Gästehaus/Ausstattung).
- Suite8 / OPERA Cloud: oracle.com/hospitality (OPERA Cloud, Sales & Event Management); Suite8 C&C Manual
  (docs.oracle.com); Accor/PPHE OPERA-Cloud-Announcements 2025.
- SIHOT: sihot.com (Hotel-MICE-Software); hoteltechreport.com/…/sihot; softwarefinder.com; micedesk.de.
- Best-of-Breed: apaleo.com/open-apis · apaleo.dev (POS-Integration); ivvy.com; meetingpackage.com;
  eventtemple.com; beds24.com (API V2/Pricing); rapideyeinspections.com (Open-API-Scorecard 2026).
- POS/Mensa/Kiosk: tcpos.com · apro.at · agilysys.com (IG Kiosk) · mensamax.de · kantineplus.de.
