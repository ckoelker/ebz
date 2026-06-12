# Anbieter-Vergleichsmatrix (11 kommerzielle Optionen + Open-Source-Ebene)

> 8 Fachanbieter + 3 moderne Plattformen + Open-Source-/kostenfreie Lösungen (Abschnitt unten).

> Ergänzung zu [Grobkonzept](Grobkonzept-IT-Konsolidierung.md) und [Anbieter-Recherche](Anbieter-Recherche-Softwareprodukte.md).
> Eigenrecherche, Stand Juni 2026, **Vorauswahl-Niveau** auf Basis öffentlicher Anbieter-/Vergleichsportal-Angaben — **nicht getestet**. K.-o.-Kriterien (N:M/Mehr-Debitoren, Web-UX-Reifegrad, Preise) im RFI/PoC verifizieren.

## Lesehilfe
✓ = ja/gut abgedeckt · ◑ = teilweise/eingeschränkt · ✗ = nein/nicht im Fokus · ❓ = öffentlich nicht belegt, **zu verifizieren**

**L8-Regel (Pflicht):** moderne Basis zwingend; Cloud nur mit state-of-the-art Web-UI, sonst moderne On-Premise-Lösung.

---

## Hauptmatrix

| # | Anbieter / Produkt | Berufs­schule | Hoch­schule | Akademie/Seminar | Betriebsmodell | Moderne Web-UI (L8) | N:M + Mehr-Debitoren (K.O.) | Schnittstellen/API | Hosting / DSGVO (DE/EU) |
|---|---|:--:|:--:|:--:|---|:--:|:--:|---|---|
| 1 | **ANTRAGO** (Markert) | ✓ | ✓ | ✓ | On-Prem (Win Client-Server) **+** SaaS „smart" (Azure EU) **+** Hybrid | ◑ Kern = Windows-Desktop; „smart"-Web vorhanden, Funktionsumfang prüfen | ❓ | DATEV/FiBu, Moodle/ILIAS/AcademyMaker, „schnittstellenoffen" | ✓ Azure, EU-RZ |
| 2 | **TraiNex** (Trainings-Online) | ◑ | ✓ | ◑ (Berufsakademie) | Gehostet / webbasiert (SaaS) | ✓ durchgängig web | ❓ | LMS integriert / Moodle anbindbar; bewusst wenige Schnittstellen | ❓ DE-Anbieter, Details prüfen |
| 3 | **HISinOne** (HIS eG) | ✗ | ✓ | ✗ | On-Prem **+** HIS-SaaS (Cloud) | ✓ web-basiert | ❓ (auf Hochschulstrukturen, nicht B2B-Debitoren ausgelegt) | offene Schnittstellen, Web­services (E-Learning, Raum, FM) | ✓ DE, SaaS |
| 4 | **evidenz** | ✗ | ◑ | ✓ | SaaS (kein Install/Wartung) | ✓ web-basiert | ❓ | HR-Landschaft-Integration, optionale Schnittstellen | ✓ DE-RZ, ISO 27001 |
| 5 | **cobra CRM** | – CRM-Querschnitt – | | | Cloud (DE) **+** On-Prem | ◑ Cloud-Web schlanker; Classic = Win-Desktop | ◑ beziehungsorientiert (Personen/Firmen/Partner), Mehr-Debitor verifizieren | REST-API (CXM Web Connect), viele Integrationen | ✓ Made in Germany, DE-Cloud |
| 6 | **TCmanager** (SoftDeCC) | ✗ | ✗ | ✓ (+ LMS) | On-Prem **+** Cloud, modular | ✓ web-basiert | ❓ (B2B-Trainingsfokus) | voll REST-API, HR/Finance/CRM, mehrsprachig, mobil | ✓ ISO 9001 & 27001, DSGVO |
| 7 | **SEMCO** | ✗ | ✗ | ✓ | Cloud/SaaS (seit 2006 web) | ✓ modern, alle Browser | ❓ (Firmenansprechpartner-Konzept) | RESTful Webservices, FiBu, Video | ✓ ISO-RZ DE, DSGVO |
| 8 | **OrgaEasy** | ✓ | ✗ | ◑ (Kurse) | Cloud/SaaS (Web-App) | ✓ web-basiert, „intuitiv" | ❓ (KMU-orientiert) | begrenzt/unklar; Schild-NRW nicht belegt | ❓ SSL, Drittanbieter-Server, DSGVO prüfen |

---

## Empfohlene Rolle je Anbieter (Einordnung in die Zielarchitektur)

| Anbieter | Rolle im Zielbild | Kurzbewertung |
|---|---|---|
| **ANTRAGO** | **Konsolidierungsplattform** (alle 3 Säulen) — Top-Buy-Kandidat | Einziger mit 1:1-Abdeckung Berufsschule + Hochschule + Akademie. **Wichtigster Vorbehalt L8:** der mächtige Kern ist eine Windows-Client-Server-Anwendung; „ANTRAGO smart" ist die Web-Variante — Funktionsumfang & Web-UX-Reifegrad zwingend im PoC prüfen. Bei voller Web-Eignung → starker Buy-Kandidat; sonst moderner On-Prem-Betrieb möglich (regelkonform). |
| **cobra CRM** | **Zentraler CRM-/Stammdaten-Kern (SSoT)** für „Integrate-first" + Marketing/Controlling | Bestes Profil als Querschnitts-SSoT (Beziehungen, REST-API, Cloud DE **oder** On-Prem). Kein Fachsystem für Campus/Schule. Mehr-Debitor-Modell verifizieren. |
| **TraiNex** | **Hochschul-Fachsystem** (OpenEduCat-Ablösung) | Modern, web, auf private Hochschulen/Berufsakademien spezialisiert. Ggf. Identität mit heutiger Akademie-App („TarLemon") in Phase 0 klären. |
| **HISinOne** | Hochschul-Fachsystem (Alternative) | Schwergewicht, eher öffentliche/große Hochschulen → vermutlich überdimensioniert und nicht auf privates B2B-/Debitoren-Modell ausgelegt. |
| **SEMCO** | **Akademie/Seminar-Fachsystem** (schlank, modern) | Moderne Cloud-Web-UI, günstiger Einstieg, REST-API. Reine Seminar-/Kursdomäne. |
| **TCmanager** | Akademie/Seminar-Fachsystem (Enterprise + LMS) | Stark bei Integration/LMS, On-Prem **oder** Cloud, ISO-zertifiziert. Für Kongress-/Tagungstiefe prüfen. |
| **evidenz** | Akademie/Seminar-Fachsystem (Alternative) | SaaS, DE-Hosting, ISO 27001; HR-/Seminarfokus. |
| **OrgaEasy** | **Berufsschul-Fachsystem** (KMU) | Web, intuitiv, für private Berufsschulen — **Skalierung auf 1.500 Schüler gleichzeitig** und Schild-NRW-Anbindung verifizieren. |

---

## Moderne Plattform-Ebene (Datenkern / SSoT) — die eigentliche Empfehlung

> Diese drei sind **keine Bildungs-Fachsysteme**, sondern moderne, cloud-native, API-first **Plattformen für den strategischen Kern** (Stammdaten, CRM, N:M/Debitoren, Marketing). Genau hier — **nicht** in den Nischen-Suiten — gehört die „moderne Basis" hin.

| # | Plattform | Rolle | Betriebsmodell | Web-UI (L8) | N:M + Mehr-Debitoren | Schnittstellen/API | Hosting/DSGVO |
|---|---|---|---|:--:|:--:|---|---|
| 9 | **MS Dynamics 365 + Dataverse + Power Platform** | **Datenkern/SSoT + CRM + Low-Code** (Empfehlung) | Cloud SaaS (Azure), **EU-Region wählbar** | ✓ modern (model-driven/canvas) | **✓✓ nativ** (N:N automatisch via Junction-Table; komplexe Debitor-Modellierung) | ✓✓ API-first, Power Automate, **native Teams/M365/Entra**, iPaaS | ✓ EU data residency wählbar |
| 10 | **Salesforce Education Cloud** | Datenkern/SSoT + Bildungs-CRM (Alternative) | Cloud SaaS (**Hyperforce EU**) | ✓ modern | ✓ Account-Contact-Relationships / Affiliations | ✓✓ API-first, großes Ökosystem | ✓ Hyperforce EU, DSGVO, EU-Staff-Zugriff |
| 11 | **HubSpot** | **Marketing-/Kampagnen-Layer** über dem Kern | Cloud SaaS | ✓ modern | ◑ mehrere Unternehmen je Kontakt (Pro/Ent.) | ✓ API, viele Integrationen | EU-Hosting verfügbar |

**Stärken der Datenkern-Ebene:** native N:M-/Beziehungs- und Debitor-Modellierung (**genau das verlorene Modell**), erstklassige Web-UI (L8 ✓), API-first/iPaaS-ready, von einem Junior-Team per Low-Code erweiterbar.
**Ehrliche Trade-offs:** spürbare **Lizenzkosten** (Dynamics Dataverse-Premium ab ~40 €/User/Monat + Storage; Salesforce premium, Preis auf Anfrage) und **Konfigurations-Disziplin** zwingend (sonst droht die OpenEduCat-Customizing-Falle erneut → konfigurieren statt hart customizen). Bildungs-**Fachtiefe** (Prüfungswesen, Stundenplan, Schild-NRW) kommt **nicht** out-of-the-box — dafür bleiben Fachsysteme als Satelliten.
**Microsoft-Bonus:** Dynamics/Dataverse passt direkt auf euren bestehenden Teams/M365/Entra-Footprint; das **Education Accelerator**-Datenmodell (Open Source) liefert Hochschul-/Schul-Entitäten als Startpunkt.

---

## Open-Source-/kostenfreie Ebene (keine Lizenzkosten ≠ kostenlos)

> **Wichtige Einordnung vorab:** „Open Source" spart die **Lizenzgebühr**, nicht den **Betrieb**. Die Kosten verlagern sich auf **Hosting, Integration, Wartung, Updates, Sicherheit und (gekauften) Support** — siehe [TCO-Kostenaufstellung](TCO-Kostenaufstellung.md). **Mahnendes Eigen-Beispiel:** Eure Hochschule läuft heute auf **OpenEduCat (Odoo, Open Source)** und soll genau deshalb abgelöst werden — Open Source schützt **nicht** vor der Customizing-/Wartungsfalle (Leitplanke: konfigurieren statt hart customizen). Lizenz-Hinweise je Produkt unten beachten: einige sind **„fair-code"/source-available** (z. B. n8n, Invoice Ninja), nicht klassisch OSI-Open-Source.

**Lizenz-Kürzel:** OSI = klassische Open-Source-Lizenz (GPL/AGPL/MIT/Apache) · *fair-code* = quelloffen, aber kommerzielle Nutzung eingeschränkt.

### A) Open-Source-Suiten (Fachsystem-Kandidaten, mehrere Säulen)

| # | Lösung | Berufs­schule | Hoch­schule | Akademie/Seminar | Betriebsmodell | Web-UI (L8) | N:M + Mehr-Debitoren (K.O.) | API | Lizenz / Hosting (DE/EU) |
|---|---|:--:|:--:|:--:|---|:--:|:--:|---|---|
| 12 | **ERPNext / Frappe Education** | ◑ | ◑ | ◑ | Self-host **+** Frappe Cloud | ✓ modern (v16, 2026 Redesign) | ◑ ERP-Parteien-/Debitorenmodell vorhanden, N:M je Doctype — verifizieren | ✓ REST (Frappe API) | OSI (GPLv3) · Self-host EU möglich |
| 13 | **Odoo Community + OpenEduCat** | ◑ | ✓ (**= euer heutiges System**) | ✓ (Events + eLearning + Sales) | Self-host | ✓ modern | ◑ (relationale Modellierung möglich) | ✓ XML-RPC/REST | OSI (LGPL/AGPL) · **Vollbuchhaltung, Studio, viele Module nur Enterprise (kostenpflichtig)** |

### B) Open-Source-Bausteine je Ebene (Best-of-Breed, kombinierbar)

| Ebene | Lösung | Rolle im Zielbild | Web-UI (L8) | API | Lizenz | Kurz-Caveat |
|---|---|---|:--:|:--:|---|---|
| **Kern/SSoT** | **EspoCRM** | **Open-Source-Datenkern-Kandidat** (SSoT/CRM) | ✓ SPA, modern | ✓ REST | OSI (AGPLv3) | Custom Entities + Beziehungen frei modellierbar (**N:M nativ**) → bestes OSS-Profil für „verlorenes Datenmodell"; Mehr-Debitor im PoC bauen/prüfen |
| Kern/CRM | **SuiteCRM** | CRM-Alternative (reifer SugarCRM-Fork) | ◑ funktional, älter | ✓ REST/V8 | OSI (AGPLv3) | mächtig, aber UI weniger modern (L8 prüfen) |
| Kern/CRM | **Twenty** | moderne CRM-Alternative | ✓ sehr modern | ✓ GraphQL/REST | OSI (AGPLv3) | jung, Funktionsreife/Stabilität verifizieren |
| **LMS** | **Moodle** (vorhanden) / **ILIAS** | Lernplattform (Pflicht-Satellit) | ✓ | ✓ Webservices | OSI (GPL) | **ILIAS = DACH-Stärke** (Nr. 2 nach Moodle an DE-Hochschulen); Moodle bleibt anschließbar |
| **Kongress/Tagung** | **Indico** (CERN) | Tagungen/Kongresse, Abstracts, **Raumbuchung** | ✓ | ✓ REST | OSI (MIT) | Genau für wissenschaftl. Veranstaltungen/Kongresse; UN/CERN-erprobt, DSGVO-Features (3.2) |
| **Seminar-Ticketing** | **Pretix** | Veranstaltungs-/Seminar-Anmeldung + Ticketing | ✓ modern | ✓ REST | OSI (AGPL, Self-host frei) | **deutscher Anbieter, DSGVO-stark**; reine Ticketing-/Anmeldedomäne |
| **PMS Gästehaus** | **QloApps** | Hotel-PMS + Booking-Engine + Website (120 Betten) | ✓ web | ◑ (PrestaShop-basiert) | OSI (OSL/AFL) | Mehr-Säulen-Belegung (Schüler/Hotel/Tagung) im PoC prüfen |
| PMS Gästehaus | **HotelDruid** | schlanke PMS-Alternative (bis hunderte Zimmer) | ◑ web, funktional | ◑ | OSI (AGPL/kommerz.) | sehr flexibel, UI weniger modern (L8 prüfen); akt. v3.0.8 (12/2025) |
| **Abo-Billing** | **Kill Bill** | Abo-/Subscription-Engine (z. B. >300 €/Monat Studierende, Schulgebühren) | ◑ Admin-UI | ✓ REST | OSI (Apache 2.0) | sehr mächtig (usage/prepaid/multi-tenant), **steile Lernkurve**, Betrieb anspruchsvoll |
| Rechnung/Abo | **Invoice Ninja** | Rechnung + einfache wiederkehrende Abos | ✓ modern | ✓ REST | *source-available* (Elastic License) | leichter zu betreiben; für komplexe Abos schwächer als Kill Bill |
| **Shop Lehrmaterial** | **Shopware CE** / WooCommerce | Webshop Lehrmaterial | ✓ modern | ✓ REST | OSI (Shopware CE: MIT/MPL) | Shopware = deutscher Anbieter; CE-Funktionsumfang vs. kommerz. Editionen prüfen |
| **SSO/IdP** | **Keycloak** | SSO/Identity (Alternative/Ergänzung) | ✓ | ✓ OIDC/SAML | OSI (Apache 2.0) | **ihr habt bereits Entra ID** → Keycloak nur falls IdP-Unabhängigkeit gewünscht |
| **Integration/iPaaS** | **n8n** | Sync-/Integrationsschicht (Power-Automate-Alternative) | ✓ modern | ✓ REST | ***fair-code*** (SUL) | deutscher Anbieter, self-host frei für Eigennutzung; **kein klassisches OSI-OSS**, Wiederverkauf eingeschränkt |

---

## Open-Source: ehrliche Einordnung & Empfehlung

**Wo Open Source im Zielbild wirklich passt:**
- **Pflicht-/Satellitenebene, die ihr ohnehin betreibt:** Moodle (vorhanden), ggf. ILIAS — hier ist OSS Standard und unkritisch.
- **Eng umrissene Spezialdomänen mit klarem Funktionsschnitt:** **Indico** (Kongresse/Tagungen), **Pretix** (Seminar-Anmeldung/Ticketing), **QloApps/HotelDruid** (Gästehaus-PMS) — schlank, ersetzbar, geringe Kopplung an den Kern.
- **Integrationsschicht:** **n8n** als kostengünstige iPaaS-Alternative, falls Power Automate-Lizenzkosten vermieden werden sollen.

**Wo Open Source als *Kern/SSoT* mit Vorsicht zu genießen ist:**
- **EspoCRM** ist der einzige OSS-Kandidat mit überzeugendem **N:M-/Beziehungsmodell** (das „verlorene Datenmodell") und moderner Web-UI (L8 ✓) — ein realer, **lizenzkostenfreier** Gegenentwurf zu Dynamics/Salesforce. **Aber:** Betrieb, Backup, Updates, Sicherheit und Erweiterungen liegen vollständig bei euch/dem Java-DL.
- **ERPNext** und **Odoo+OpenEduCat** könnten theoretisch mehrere Säulen abdecken — aber **OpenEduCat ist genau das System, das ihr ablöst.** Eine erneute Wette auf eine Odoo-Suite reaktiviert exakt das Risiko, dem ihr entkommen wollt. **ERPNext** ist die frischere ERP-Option, müsste aber für N:M/Mehr-Debitoren und Schild-NRW im PoC hart geprüft werden.

**Kernaussage (verbindet sich mit der TCO-Logik):** Open Source senkt **Lizenz-**, nicht **Gesamtkosten**. Für ein **Junior-Team mit begrenzter Betriebsmannschaft** ist eine SaaS-Plattform (Dynamics/Power Platform) oft günstiger im **TCO**, weil Betrieb/Sicherheit/Updates inklusive sind. Open Source rechnet sich, wenn (a) der Java-DL den Betrieb dauerhaft trägt, (b) bewusst **konfiguriert statt customized** wird und (c) eine echte Exit-/Wartungsstrategie existiert. → Bewertung gehört in die [TCO-Kostenaufstellung](TCO-Kostenaufstellung.md) (Lizenz **+ Hosting + FTE-Betrieb + Support**).

---

## Ableitung & Empfehlung

**Antwort auf die berechtigte Kritik:** Die Nischen-Suiten (Tabelle oben) wirken konservativ, weil sie es sind — sie gehören **nicht** in den Kern. Empfohlenes Zielbild ist **geschichtet (Composable / Best-of-Breed):**

1. **Moderner Datenkern / SSoT:** **MS Dynamics 365 + Dataverse + Power Platform** (Empfehlung — MS-Footprint, natives N:M-/Debitor-Modell, Low-Code fürs Junior-Team, EU-Residency). Alternative ohne MS-Bindung: **Salesforce Education Cloud**. Hier liegen Stammdaten, Beziehungen, Debitoren, Golden Record.
2. **Marketing/Controlling:** auf dem Kern (Dynamics Customer Insights) **oder** **HubSpot** als Kampagnen-Layer.
3. **Integration:** iPaaS / Power Automate als entkoppelte Sync-Schicht (Leitplanke L3).
4. **Fach-Satelliten** (anbinden, opportunistisch ersetzen): Hochschule (TraiNex **oder** Eigenbau auf Dataverse/Education Accelerator), Akademie/Seminar (SEMCO/TCmanager), Berufsschule (Modernisierung), Gästehaus-PMS — plus Pflichtsysteme Schild-NRW, Sket, Moodle, WebUntis, Teams.

**Modern Make — Rolle des Java-Dienstleisters, neu eingeordnet:** **nicht** ein Java-Monolith von Grund auf, sondern der DL baut die **Integrations-/Sync-Schicht und kundenspezifische Logik auf dem modernen Stack** (Power Platform / Azure / APIs). So zählt seine Hauskenntnis, ohne neuen Legacy-Lock-in — und das Junior-Team kann per Low-Code mitwachsen.

**Wann doch eine Nischen-Suite (ANTRAGO/cobra)?** Wenn die Lizenzkosten der Plattform-Ebene nicht tragbar sind oder je Domäne eine schnelle, schlanke Fachlösung reicht — dann bleibt der „Buy/Integrate-first"-Pfad aus der Hauptmatrix die pragmatische Sparvariante, allerdings **ohne** den modernen, zukunftssicheren Datenkern.

**Wann Open Source (Ebene unten)?** Wenn Lizenzfreiheit strategisch gewünscht ist **und** der Java-DL den Betrieb dauerhaft trägt: dann **EspoCRM als lizenzkostenfreier Datenkern** + OSS-Satelliten (Indico/Pretix für Veranstaltungen, QloApps für PMS, ILIAS/Moodle für LMS, n8n als iPaaS). **Vorbehalt:** Gesamtkosten (Hosting/Betrieb/Support) und das **OpenEduCat-Lehrstück** (Customizing-Falle) zwingend gegen die SaaS-Variante rechnen — siehe [TCO-Kostenaufstellung](TCO-Kostenaufstellung.md).

**Erste RFI-/PoC-Fragen (an alle):** N:M + Mehr-Debitoren konkret · Web-UX-Reifegrad (L8) · EU-Hosting/DSGVO · Lizenz-Gesamtkosten über 5 Jahre · Schnittstellen zu Teams/M365, Schild-NRW, Sket, Moodle, DATEV/E-Rechnung.

---

## Quellen
- ANTRAGO: [Betrieb/Hosting](https://www.antrago.de/betrieb/) · [SaaS](https://www.antrago.de/saas/) · [smart](https://www.antrago-smart.de/) · [Moodle](https://www.antrago.de/moodle/) · [FAQ](https://www.antrago.de/faq/)
- TraiNex: [trainings-online.de](https://www.trainings-online.de/) · [Lösungen/Ganzheitlichkeit](https://www.trainings-online.de/loesungen/ganzheitlichkeit)
- HISinOne: [his.de/hisinone](https://www.his.de/hisinone) · [HIS-SaaS](https://www.his.de/cloudservice)
- evidenz: [evidenz.de](https://www.evidenz.de/) · [Seminarmanagement](https://www.evidenz.de/seminarmanagement-software/)
- cobra CRM: [Bildungsanbieter](https://www.cobra.de/crm-nach-branchen/bildungsanbieter) · [Cloud](https://cobra.de/loesungen/cobra-crm-cloud/) · [Integrationen/API](https://cobra.de/loesungen/integrationen-und-api/) · [On-Premise](https://cobra.de/loesungen/cobra-crm-classic/)
- TCmanager/SoftDeCC: [LMS/Training Administration](https://www.softdecc.com/en/lms/training-administration.html) · [softguide](https://www.softguide.de/programm/tcmanager-seminarverwaltungsoftware)
- SEMCO: [Funktionen](https://www.semcosoft.com/de/was-benoetigen-sie) · [softguide](https://www.softguide.de/programm/semco-die-smarte-kursverwaltung) · [Capterra](https://www.capterra.com.de/software/1070912/SEMCO)
- OrgaEasy: [private Berufsschule](https://orgaeasy.com/private-berufsschule/) · [Tarife](https://orgaeasy.com/tarife/)
- MS Dynamics 365 / Dataverse: [Education data model](https://learn.microsoft.com/en-us/dynamics365/industry/accelerators/edu-data-model) · [Education Accelerator](https://learn.microsoft.com/en-us/dynamics365/industry/accelerators/edu-overview) · [N:N-Beziehungen in Dataverse](https://learn.microsoft.com/en-us/power-apps/maker/data-platform/create-edit-nn-relationships) · [Data residency](https://learn.microsoft.com/en-us/dynamics365/get-started/availability) · [Power Apps Pricing](https://www.microsoft.com/en-us/power-platform/products/power-apps/pricing/)
- Salesforce Education Cloud: [salesforce.com/eu/education](https://www.salesforce.com/eu/education/cloud/) · [Hyperforce EU](https://www.itmagazine.ch/artikel/79220/Salesforce_startet_seine_EU-Cloud.html)
- HubSpot: [Datenmodell Bildung](https://knowledge.hubspot.com/de/data-management/data-model-templates) · [mehrere Unternehmen je Kontakt](https://community.hubspot.com/t5/Produkt-Updates/FAQ-zu-den-verbesserten-Datensatz-Zuordnungen/ba-p/521851)

### Open-Source-/kostenfreie Ebene
- ERPNext / Frappe Education: [for-education](https://frappe.io/erpnext/for-education) · [frappe/education (GitHub)](https://github.com/frappe/education) · [Frappe Education](https://frappe.io/education)
- Odoo Community / OpenEduCat: [OpenEduCat Doku](https://doc.openeducat.org/) · [Community vs. Enterprise Accounting](https://www.odoo.com/forum/help-1/accounting-community-vs-enterprise-271286) · [Community Edition Guide](https://theledgerlabs.com/odoo-community-edition-guide/)
- EspoCRM: [espocrm.com](https://www.espocrm.com/) · [espocrm/espocrm (GitHub, AGPLv3)](https://github.com/espocrm/espocrm) · [Download/Self-host](https://www.espocrm.com/download/)
- SuiteCRM: [suitecrm.com](https://suitecrm.com/) · Twenty: [twenty.com](https://twenty.com/)
- ILIAS / Moodle: [ILIAS docu.ilias.de](https://docu.ilias.de/) · [Open Source LMS (DE)](https://www.opensourcelms.de/) · [moodle.org](https://moodle.org/)
- Indico (CERN): [getindico.io](https://getindico.io/) · [indico/indico (GitHub, MIT)](https://github.com/indico/indico)
- Pretix: [pretix.eu](https://pretix.eu/)
- QloApps: [qloapps.com](https://qloapps.com/) · [Qloapps/QloApps (GitHub)](https://github.com/Qloapps/QloApps) · HotelDruid: [hoteldruid.com](https://www.hoteldruid.com/)
- Kill Bill: [killbill.io](https://killbill.io/) · [killbill/killbill (GitHub, Apache 2.0)](https://github.com/killbill/killbill) · Invoice Ninja: [invoiceninja.com](https://www.invoiceninja.com/)
- Shopware CE: [shopware.com](https://www.shopware.com/) · Keycloak: [keycloak.org](https://www.keycloak.org/)
- n8n: [n8n.io](https://n8n.io/) · [n8n-io/n8n (GitHub, fair-code/SUL)](https://github.com/n8n-io/n8n) · [Sustainable Use License](https://docs.n8n.io/sustainable-use-license/)
