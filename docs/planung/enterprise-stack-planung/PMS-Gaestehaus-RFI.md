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

## 3. Kandidaten (vier Wege)
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
- **Weg 4 — Hybrid: Eigenleistung erweitern + dünne Spezialisten** (nutzt aus, dass EBZ **bereits** eine
  Integrationsplattform besitzt — Party-Kern, Debitoren/Rechnung, Controlling, Keycloak, integration-Service):
  - **Vendure-als-POS** für **Kantine + Kiosk** (Order = Folio/Beleg) + **eure Rechnungslegung als das EINE
    Billing-Gehirn** für *alles* (Hotel-Folio + Seminar + Shop + F&B → Debitoren/ZUGFeRD/E-Rechnung). Fixkosten =
    Controlling-Kern.
  - **Gekauft bleibt nur das, was Commodity-Kern ist:** ein **dünnes PMS** (Apaleo) für Verfügbarkeit/Reservierung/
    Front-Desk **+** ein **dünnes MICE** (iVvy/EventTemple) für Bankett/Bestuhlung — deren Folios posten in euer Billing.
  - **Harte Grenze:** die **Datum-/Verfügbarkeits-/Raten-Engine, Front-Desk und Bankett-Planung NICHT** in Vendure
    nachbauen (= Commodity selbst bauen → Capability-Map-Verstoß + OpenEduCat-Customizing-Falle).
  - **Compliance-Preis:** Vendure-POS in DE erfordert **zertifizierte TSE (KassenSichV) + DSFinV-K** (z. B. fiskaly).

  | Funktion | Weg 4 (Hybrid) |
  |---|---|
  | Zimmer: Verfügbarkeit/Reservierung/Front-Desk | dünnes PMS (Apaleo) — **kaufen** |
  | Bankett/Bestuhlung/Eindecken | dünnes MICE (iVvy/EventTemple) — **kaufen** |
  | Kantine + Kiosk (POS) | **Vendure-als-POS** (+ TSE) — **selbst** |
  | Debitoren/Rechnung/E-Rechnung (alle Domänen) | **eure Rechnungslegung** — **selbst** (Differenzierer) |
  | Hausmeister / Fixkosten | kleines CMMS / Controlling-Kern |

  **Netto:** schrumpft den Kauf-Footprint auf **dünnes PMS + dünnes MICE** (POS+Billing selbst) → wenige Lizenzen,
  maximale Nutzung des vorhandenen Stacks. Für EBZ realistischer als für ein normales Hotel, weil die teure **Naht
  schon existiert**.

- *(Alternative protel/Xn protel (Planet) mit Conference & Banqueting als zweiter Suite-Kandidat möglich.)*

### Randnotiz — „warum nicht einfach OSS/selbst bauen?"
Freie Lösungen existieren (QloApps schmal; **Odoo/ERPNext breit** — PMS+Events+POS+Maintenance+FiBu in einer
OSS-Plattform), aber **Conference-&-Catering-/Fiskal-Tiefe + zertifizierte Integrationen** (Channel-Manager,
TSE, Meldeschein) sind der Grund, warum „free" hier selten produktionsreif ist — und **Odoo-für-alles ist exakt
die OpenEduCat-Falle**, der EBZ entkommt. Deshalb: Commodity-Kern (Verfügbarkeit/Front-Desk/Bankett) **kaufen**,
nur den **Differenzierer (Billing/Daten/Integration) selbst** — was Weg 4 genau so zuschneidet.

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
**Weg 4 (Hybrid):** Mapping in §3 — Zimmer/Bankett wie Weg 3 (dünn **gekauft**), Kantine/Kiosk-POS + Billing
**selbst** (Vendure-POS + eure Rechnung).

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

**Weg 4 (Hybrid) im Profil:** Scope-Abdeckung hoch (POS+Billing selbst, Reservierung/Bankett dünn gekauft) ·
**API/Abgrenzung am besten** (ihr ownt die Naht) · **Oracle-Ausstieg ja** · niedrigste Lizenzkosten, aber
**eigener Bau-/Betriebsaufwand** (Vendure-POS + **TSE/KassenSichV**) · Schnittstellen: PMS + MICE (wenige) ·
DACH-Fit: gut, sofern TSE/Fiskal sauber gelöst. **Stärkster Fit für EBZ** dank vorhandener Plattform.

## 6. Kosten-Schätzung (grob, angebotsabhängig)
- **Weg 1 — SIHOT (112 Zi, PMS+C&B+POS):** keine Listenpreise. Richtwerte: ab ~€150/User/Monat; DACH-Cloud-PMS
  €8–15/Zi/Mo; C&B/POS extra. Überschlag: **laufend ~€18–30k+/Jahr**; **einmalig** (Setup+Migration+Schulung+
  Schnittstellen) **~€10–40k**; **Jahr 1 ~€30–70k**. Plausi über User-Schiene (10–20 User × €150 ≈ €18–36k/J).
- **Weg 3 — Best-of-Breed (Zimmer-PMS-Anteil):** Apaleo ab €8–9/Zi/Mo (Min. €400/Mo) → 112 Zi ≈ **~€11–12k/J**;
  Beds24 günstiger (Trial gratis 14 Tage). **Dazu** MICE/Bankett (iVvy/EventTemple) + POS + Mensa + CMMS — je
  eigene, nutzungsabhängige Posten → Summe stark vom Zuschnitt abhängig.
- **Weg 2 — OPERA Cloud (S&E):** Oracle, teuer/teils metered — oberer Anker.
- **Weg 4 — Hybrid:** **niedrigste Lizenzen** (nur dünnes PMS ~€11–12k/J + dünnes MICE; POS+Billing selbst), aber
  **Eigenaufwand**: Vendure-POS-Erweiterung + **TSE/KassenSichV-Anbindung** (z. B. fiskaly, ~€ niedrig 3-/4-stellig/J)
  + interner Entwicklungsaufwand (Aktiv-Dev — passt zum aktuellen Fokus). Laufend voraussichtlich am günstigsten,
  Einmal-Aufwand verlagert sich von „Lizenz/Setup" zu „Eigenentwicklung".
- **Fehlende Baseline:** reale **Suite8-Kosten + genutzte Module** (EBZ beizustellen). Erst damit ist „SIHOT/Mesh
  ist ein Bruchteil von Suite8" **belegt** statt vermutet.

## 7. Vorläufige Empfehlung (vor Baseline)
Die Entscheidung läuft im Kern auf **kaufen vs. Eigenleistung ausspielen** hinaus:
- **Weg 4 (Hybrid) — stärkster Fit für EBZ, wenn Aktiv-Dev gewollt:** nutzt aus, dass die **teure Naht schon
  existiert**; POS (Kantine/Kiosk) + **EINE Rechnungslegung** selbst (= euer Differenzierer), nur **dünnes PMS +
  dünnes MICE** gekauft. Niedrigste Lizenzen, beste Abgrenzung/API-Hoheit, raus aus Oracle. **Preis:** Eigenbau +
  **TSE/KassenSichV**, und die **Verfügbarkeits-/Bankett-Engine bleibt gekauft** (nicht nachbauen!).
- **Weg 1 (SIHOT) — risikoärmste „ein-System"-Ablöse:** direkteste 1:1-Deckung des Suite8-Umfangs (inkl.
  C&B/Bestuhlung), eine Anbindung (S4), wenig Eigenbau. Gut, wenn schnelle Ablöse > maximale Eigenhoheit.
- **Weg 3 (reines Best-of-Breed)** = wie Weg 4, aber **ohne** den Eigenleistungs-Hebel (kauft auch POS/Billing) →
  meist nur sinnvoll, falls die eigene Rechnungslegung doch *nicht* der Billing-SoR werden soll.
- **Weg 2 (OPERA Cloud)** nur Vergleichsanker — widerspricht „Oracle-Lock-in verlassen".

**Tendenz:** Für EBZ konkurrieren realistisch **Weg 4 (Plattform ausspielen, mehr selbst)** und **Weg 1 (SIHOT,
schnell & risikoarm kaufen)**. Weg 4 maximiert Hoheit/Wiederverwendung und minimiert Lizenzen, kostet aber
Eigenentwicklung + Fiskal-Compliance; Weg 1 ist der schnellste saubere Suite8-Ersatz. **Finale Entscheidung erst
nach §8** (v.a. echte Suite8-Kosten + Bau-Appetit/Kapazität).

## 8. Offene Punkte (vor Entscheidung zu klären)
1. **Suite8-Baseline:** reale Jahres-/Lizenzkosten + genutzte Module (PMS, C&B, POS, Schnittstellen) — vom EBZ.
2. **Seminarraum-Hoheit:** gehören Raum-/Bankettplanung künftig **ins Haus-System (PMS/MICE)** oder in die
   bestehende **Akademie-/Seminar-Domäne** (Capability-Map #8/#10)? Vermeidet Doppel-Belegungslogik.
3. **Rechnungs-Datenrichtung:** Rechnung **im Kern** erzeugen (Debitoren-/Nummernkreis-Hoheit, ZUGFeRD) und PMS-
   Folio nur als Quelle — empfohlen — vs. PMS-Finance führend.
4. **Pflichten:** E-Rechnung-Pflicht, **BFSG/Barrierefreiheit**, DSGVO/Hosting-Region je Kandidat prüfen.
5. **Angebote:** RFI an SIHOT (Weg 1) + ggf. protel; bei Weg 3/4 Apaleo + iVvy/EventTemple (+ POS nur bei Weg 3).
6. **Nur Weg 4 — TSE/Fiskal:** zertifizierte **TSE (KassenSichV) + DSFinV-K** für Vendure-POS klären (Cloud-TSE
   z. B. fiskaly vs. Hardware); **harte Guardrail:** Verfügbarkeits-/Raten-Engine + Bankett **nicht** selbst bauen.
7. **Bau-Appetit/Kapazität:** Weg 4 verlagert Kosten von Lizenz zu **Eigenentwicklung** — passt das zum aktuellen
   Fokus „saubere Entwicklung fehlender Funktionen" und zur Team-Kapazität?

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
