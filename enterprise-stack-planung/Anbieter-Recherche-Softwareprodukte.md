# Anbieter-Recherche: Passende Softwareprodukte

> Ergänzung zum [Grobkonzept](Grobkonzept-IT-Konsolidierung.md). Marktüberblick möglicher Produkte je Baustein der Zielarchitektur. **Stand: Juni 2026, Vorauswahl-Niveau** — Eignung ist je Produkt im RFI/PoC zu verifizieren (siehe Muss-Kriterien §0).

---

## 0. Bewertungsraster — Muss-Kriterien (vor jeder Auswahl)

Jeder Kandidat ist gegen diese Kriterien zu prüfen; die ersten beiden sind **K.-o.-Kriterien** aus der Ist-Analyse:

1. **N:M Person↔Unternehmen + mehrere Debitoren je Company/Person.** Das in der Java-App vorhandene, in OpenEduCat verlorene Modell. **Kein öffentliches Produktdatenblatt bestätigt das — zwingend je Anbieter abfragen.**
2. **Updatefähigkeit / Versionsneutralität (Leitplanke L4).** Standard-Customizing über unterstützte Erweiterungspunkte, keine Core-Modifikation (Lektion OpenEduCat).
3. **Moderne technische Basis (Pflicht) + zukunftssicheres Betriebsmodell.** Entscheidungsregel: **Cloud/SaaS nur mit state-of-the-art Weboberfläche** (moderne, responsive, performante Web-UX). Ist die Cloud-/Web-Oberfläche nicht erstklassig → **moderne On-Premise-Lösung bevorzugt**. In keinem Fall veralteter Stack oder EOL-Pfad (wie UniTop/NAV on-prem). Veraltete oder träge Web-Frontends scheiden aus, auch wenn fachlich passend.
4. **Offene API/Schnittstellen** zu den Pflicht-/Satellitensystemen: Moodle, WebUntis, MS Teams/Entra, Schild-NRW (SVWS-API), Sket, FiBu/DATEV, E-Rechnung.
5. **DSGVO / Hosting in DE/EU.**
6. **Betreibbar durch die reale Mannschaft** (interne Junioren + definierte DL-Rolle), keine Single-Vendor-/Single-Person-Abhängigkeit beim Stammdaten-Kern.

---

## A. Multi-Domänen-Konsolidierung (Buy, ein Anbieter über mehrere Säulen)

Kandidaten, die Berufsschule **und** Hochschule **und** Seminar/Akademie aus einer Plattform abbilden — passt auf die „Buy"-Option des Grobkonzepts.

| Anbieter | Domänen-Abdeckung | Einordnung |
|---|---|---|
| **ANTRAGO** (Markert Software) | **Vier Produktlinien:** `seminar` (Akademie/Seminar/Kongress), `berufsbildung` (Berufsschule inkl. Stundenplanung), `campus` (Hochschule, Student-Life-Cycle), `kompetenz` (wiss. Weiterbildung) — „gesamter Wertschöpfungsprozess eines Bildungsanbieters in einer Software" | **Stärkster 1:1-Fit.** Seit 1996 Erwachsenenbildung, seit 2003 Hochschulen; >240 Bildungsanbieter, >30 Hochschulen. Bildet **genau die drei Säulen** dieses Hauses ab. → Priorisiert in RFI; N:M/Mehr-Debitoren, Cloud-Betrieb, Schnittstellen (Moodle/WebUntis/Schild) konkret abfragen. |
| **TraiNex** (NeS GmbH) | Campus-Management für private Hochschulen **und** Berufsakademien; teils LMS-Funktionen | Auf private Hochschulen/Akademien spezialisiert. Hinweis: ggf. identisch mit der heute in der Akademie genutzten Anwendung („TarLemon"? in Phase 0 verifizieren). |
| **HIS eG** | Hochschul-Geschäftsprozesse ganzheitlich, ~250 Kunden | Eher klassische/öffentliche Hochschulen; Akademie/Berufsschule weniger im Fokus. |
| **GECKO** | Campus-/Dokumenten-/Ressourcen-Management, Student-Life-Cycle | Dienstleister mit Individualanteil — Customizing-Disziplin (L4) besonders prüfen. |

---

## B. Zentraler Stammdaten-/CRM-Kern (SSoT) — für die „Integrate-first"-Option

Falls die Fachsysteme zunächst bleiben und nur ein **SSoT + Marketing/Controlling-Schicht** vorgeschaltet wird (empfohlener erster Schritt im Grobkonzept).

| Anbieter | Profil | Einordnung |
|---|---|---|
| **cobra CRM** (Bildungs-Branchenpaket) | Für private Bildungsträger/Schulen/Erwachsenenbildung; hohe Anpassbarkeit, **REST-API** | DE-Anbieter, branchennah, integrationsfähig — guter Kandidat für SSoT/Marketing-Kern. N:M/Debitoren prüfen. |
| **combit CRM** | CRM mit Seminar-/Kursverwaltung | DE-Anbieter, schlank, gut anpassbar. |
| **HubSpot** (Education-Vertical) | Cloud-CRM, sehr starkes Marketing/Kampagnen, bricht Datensilos auf | Marketing-/Controlling-Schmerz stark adressiert; aber Datenmodell N:M/Debitoren begrenzt → eher Marketing-Layer **über** einem MDM als selbst SSoT. |
| **Zoho CRM** (Education) | Anpassbares Cloud-CRM, günstiges Lizenzmodell | Flexibel, kostengünstig; Tiefe der Bildungsprozesse begrenzt. |

> **Architektur-Hinweis:** Ein klassisches CRM ist nicht automatisch ein **MDM**. Wenn N:M/Mehr-Debitoren + Golden-Record zentral geführt werden sollen, ggf. CRM (Marketing) **+** dedizierte MDM-Funktion (siehe §E / iPaaS mit MDM) kombinieren oder das ANTRAGO-Stammdatenmodell als Kern prüfen.

---

## C. Domänen-Fachsysteme (falls föderiertes Modell statt Einzelanbieter)

### Hochschule (Ablösung OpenEduCat)
ANTRAGO `campus` · TraiNex · HIS eG · GECKO · S4Campus. Prüfungswesen **Sket** als Satellit anbinden (Leitplanke L6).

### Akademie / Seminar / Kongress (Ablösung UniTop)
ANTRAGO `seminar` · **evidenz** (Akademien/Bildungszentren) · **Linear Seminarverwaltung** (Seminare/Tagungen/Kongresse) · **TCmanager** (SoftDeCC) · **AcademyManager** · **LAN Seminarverwaltung**. Moodle ggf. als LMS-Satellit anbinden.

### Berufsschule (Modernisierung der Java-App, Pflicht-Reporting)
**OrgaEasy** (private Berufsschulen: Kurse/Unterricht/Abrechnung/Rechnung) · **ATLANTIS** (Berufsschulen, Schnittstellen) · ANTRAGO `berufsbildung`.
**Pflichtsystem:** Schild-NRW / **SVWS-NRW** (SchILD-NRW 3 mit **API-Schnittstellen**, browserbasierter SVWS-Client) bleibt für Landes-Reporting — anbinden, nicht ersetzen.

### Gästehaus (PMS — bisher unbeleuchtete Domäne)
Für 120 Betten / ~30–120 Zimmer, Cloud, mit **E-Rechnung** (ab 2025/26 verpflichtend):
**ASA** (30–120 Zimmer, Hybrid/Cloud) · **WINHOTEL** · **3RPMS** (DE-Cloud) · **igumbi** · **RoomRaccoon** · **resavio**. Debitoren/Stammdaten an den SSoT anbinden (Gäste = teils dieselben Kunden).

---

## D. Integrations-/iPaaS-Schicht (Leitplanke L3 — entkoppelte Synchronisation)

Für „Integrate-first": Sync der Stammdaten zwischen Kern und Fachsystemen, statt Punkt-zu-Punkt.

| Anbieter | Profil |
|---|---|
| **Locoia** | iPaaS, **DSGVO-konform, Hosting in DE** — naheliegend für diesen Kontext |
| **Scheer PAS** | Integrations-/Prozessautomatisierung, DE/CH |
| **Boomi** | Umfassende Plattform inkl. **MDM-Modul** (Master Data Management) + Integration/API |
| **SAP Integration Suite** | iPaaS, falls perspektivisch SAP-Nähe; eher schwergewichtig |

---

## Einordnung & empfohlener nächster Schritt

Aus der Recherche kristallisieren sich **zwei realistische Zielbilder** heraus, die im RFI gegeneinander zu stellen sind:

- **Zielbild 1 — Konsolidierung auf einen Bildungsanbieter (Buy):** ANTRAGO über alle drei Säulen (seminar/berufsbildung/campus), Gästehaus-PMS separat, Schild-NRW/Sket/Moodle/WebUntis/Teams als Satelliten. Vorteil: ein Datenmodell, ein Vertragspartner. Risiko: Migrationsumfang, Abhängigkeit von einem Anbieter — N:M/Mehr-Debitoren **muss** ANTRAGO sauber können.
- **Zielbild 2 — Integrate-first (SSoT + iPaaS):** CRM/MDM-Kern (cobra/Boomi-MDM) + iPaaS (Locoia), Fachsysteme bleiben vorerst und werden einzeln (UniTop, OpenEduCat) abgelöst. Vorteil: schneller Marketing-/Controlling-Nutzen, geringeres Risiko. Risiko: mehr Systeme im Parallelbetrieb.

**Konkreter nächster Schritt (zu Phase 0 / §11 des Grobkonzepts):**
1. **Shortlist** je Baustein festlegen (Vorschlag: ANTRAGO + cobra + Locoia + 1 PMS).
2. **RFI/Kriterienkatalog** mit den Muss-Kriterien §0 an die Anbieter — **N:M/Mehr-Debitoren, Cloud-Betriebsmodell und Reifegrad der Weboberfläche als erste Fragen** (bei ANTRAGO `smart`/Cloud konkret die Web-UX bewerten; falls nicht state-of-the-art → moderne On-Premise-Variante prüfen).
3. **DL-Angebot (Java-Dienstleister) als Vergleichsbasis** gegen ANTRAGO/Integrate-first stellen (Make vs. Buy, Interessenkonflikt beachten).
4. **PoC** mit echten Stammdaten (Dubletten-/Matching-Fall) für die 1–2 Favoriten.

> **Disclaimer:** Funktions- und Eignungsaussagen stammen aus öffentlichen Anbieterangaben/Vergleichsportalen (Juni 2026) und sind **nicht** durch Tests verifiziert. Insbesondere die K.-o.-Kriterien (N:M/Mehr-Debitoren, Cloud-Roadmap, Preise) sind direkt beim Anbieter zu bestätigen.

---

## Quellen

- ANTRAGO: [antrago.de](https://www.antrago.de/) · [Campus](https://www.antrago.de/campus/) · [Seminar](https://www.antrago.de/seminar/)
- Hochschul-/Campus-Markt: [softguide.de – Hochschulen/Akademien](https://www.softguide.de/software/hochschulen-akademien) · [Capterra Hochschulsoftware](https://www.capterra.com.de/directory/30913/higher-education/software) · [HIS / Hochschulinformationssystem (Wikipedia)](https://de.wikipedia.org/wiki/Hochschulinformationssystem) · [GECKO](https://www.gecko.de/individuelle-software-entwicklung/gecko-hochschulwesen/) · [S4Campus](https://www.s4campus.ag/cloud-angebot/erweiterungen-digitalisierung-hochschulverwaltung/)
- Seminar/Akademie: [induux Vergleich](https://www.induux.de/anbieter/seminarverwaltung-software) · [Linear](https://linear-software.de/seminarverwaltung/) · [TCmanager/SoftDeCC](https://www.softdecc.com/en/lms/training-administration.html) · [evidenz](https://www.evidenz.de/) · [LAN](https://www.lansoftware.de/branchen/seminarverwaltungssoftware/)
- CRM Bildung: [cobra](https://www.cobra.de/crm-nach-branchen/bildungsanbieter) · [combit](https://www.combit.net/crm-software/seminarverwaltung/) · [HubSpot Education](https://blog.hubspot.de/sales/crm-hoehere-bildung) · [Zoho Education](https://www.zoho.com/de/crm/verticals/education/) · [All4Schools](https://www.all4schools.de/schulsoftware/schulerverwaltung/)
- Berufsschule/Schild-NRW: [OrgaEasy](https://orgaeasy.com/private-berufsschule/) · [ATLANTIS](https://www.atlantis-schulsoftware.de/berufsschulen) · [SVWS/Schild-NRW](https://www.svws.nrw.de/) · [Bildungsportal NRW](https://www.schulministerium.nrw/dezentrale-schulverwaltungsanwendungen)
- Hotel-PMS: [Hotelsoftware-Vergleich 2026](https://changing-hospitality.com/hotelsoftware-vergleich-2026/) · [igumbi](https://www.igumbi.com/de/tour/pension-buchungssoftware) · [RoomRaccoon](https://roomraccoon.de/loesungsansatz-fuer/hotelsoftware-fur-kleine-hotels/) · [resavio](https://resavio.com/de/hotelsoftware) · [softguide Hotelsoftware](https://www.softguide.de/software/hotels-hotelsoftware)
- iPaaS/MDM: [Locoia](https://www.locoia.com/ipaas/) · [Boomi – What is iPaaS](https://boomi.com/de/platform/what-is-ipaas/) · [SAP Integration Suite](https://www.sap.com/germany/products/technology-platform/integration-suite/what-is-ipaas.html) · [Capterra iPaaS](https://www.capterra.com.de/directory/32296/ipaas/software)
