# Zielarchitektur — Capability-Map (Best-of-Breed mit starkem Kern)

> Ergänzung zu [Grobkonzept](Grobkonzept-IT-Konsolidierung.md) und [TCO](TCO-Kostenaufstellung.md).
> Beantwortet die Frage: *„Wenn wir alles auf Power Platform/Azure bauen — ist das nie die günstigste Lösung?"* → **Korrekt. Und genau deshalb baut man nicht alles auf einer Plattform.**

## Leitprinzip
**Commodity kaufen, nur den Differenzierer bauen.**
- **Differenzierer (selbst/Plattform):** der vereinheitlichte **Daten-/Kundenkern** + **domänenübergreifende Orchestrierung** + Controlling. Das ist euer eigentliches Problem und euer Wert.
- **Commodity (fertige SaaS):** Shop, PMS, Billing/Abo, Ticketing, LMS, Prüfungs-Engine — alles Standardmärkte mit reifen, günstigen Produkten. **Niemals nachbauen.**

Der Kern ist nur ~**15–25 %** der Gesamt-Landschaft. Shop/PMS/Billing/LMS/Ticketing zahlt ihr als separate SaaS-Abos **unabhängig davon**, ob der Kern Power Platform, Salesforce oder Eigenbau ist. „Power Platform für alles" ist deshalb ein Strohmann — die richtige Frage ist nur: *Ist Power Platform der günstigste **Kern**?* (Bei 120 Mitarbeitern: wettbewerbsfähig.)

---

## Capability-Map: jede Fähigkeit auf die richtige Ebene

| # | Fähigkeit | Ebene | System-Typ | Beispiel-Produkte | OSS on-prem / günstige Cloud-Alternative | Make/Buy |
|---|---|---|---|---|---|---|
| 1 | **Single Point of Truth / MDM** | **KERN** | Datenplattform | **Dataverse** (alt.: Salesforce, Custom) | **EspoCRM** (OSS, self-host — Custom Entities/N:M) · günstige Cloud: NocoDB/Baserow | Plattform-Kern |
| 3 | **Kundenstamm / CRM** | **KERN** | CRM | **Dynamics/Dataverse** | **EspoCRM / Twenty** (OSS, self-host) · günstige Cloud: Zoho CRM | Plattform-Kern |
| 2 | **Controlling** | **KERN-nah** | BI/Analytics | **Power BI** auf Kern + Feeds | **Metabase / Apache Superset** (OSS, self-host) · gratis Cloud: Looker Studio | Konfiguration |
| 4 | **SSO / Identity** | **QUERSCHNITT** | Identity Provider | **Keycloak** 🔒 *(gesetzt)* | **Authentik** (OSS-Alternative, self-host) | **gesetzt** — anbieter-neutrales OIDC, self-host, im Showcase erprobt (2 Realms) |
| 5 | **Marketing + Sales-Pipeline** | Satellit/Layer | Marketing-Automation + CRM-Pipeline | **HubSpot** 🔒 *(gesetzt 2026-06-12)* | — gesetzt; Mautic/Brevo/CI damit verworfen (Golden Record bleibt im Kern) | Buy |
| 8 | **Produktbuchung (Studium-Einschreibung)** | KERN **oder** Campus | Enrollment/Booking | Kern-App **oder** Campus-System | **ERPNext / Odoo Admission** (OSS, self-host) o. Formular→EspoCRM | Konfig/Buy |
| 12 | **Schulverwaltung** | Satellit **+ Pflicht** | Schul-/Campus-Admin | **Schild-NRW (Pflicht)** + Campus/Berufsbildung-System | **Gibbon / openSIS / ERPNext Education** (OSS, self-host) — **Schild-NRW bleibt Pflicht** | Pflicht + Buy |
| 13 | **Prüfungsverwaltung** | Satellit | Exam-Management | **Sket (bereits vorhanden)** / Campus-Modul | **Moodle-Prüfung (vorhanden)** / ERPNext Examination (OSS) | vorhanden/Buy |
| 6 | **Rechnungsstellung** | Satellit | Billing / ERP / FiBu | **Business Central** / **DATEV**; Abo: **Billwerk/Stripe Billing** | Rechnung: **Invoice Ninja** · Abo: **Kill Bill** (beide OSS, self-host) · günstige DE-Cloud: lexoffice/sevDesk | Buy |
| 9 | **Abo / Dauerzugriff Lehrmaterial** | Satellit | Subscription + Entitlement + LMS | **Moodle (vorhanden)** + Abo-Billing + Entitlement | **Moodle (vorhanden)** + **Kill Bill** (Entitlement/Abo, OSS, self-host) | Buy/integrieren |
| 7 | **Shop (Lehrmaterial-Verkauf)** | Satellit | E-Commerce | **Shopify / Shopware / WooCommerce** | **WooCommerce / Shopware CE** (OSS, self-host) · günstige Cloud: Shopify Basic | Buy — nie selbst |
| 10 | **Ticket-/Veranstaltungsbuchung** | Satellit | Event/Ticketing **oder** Seminartool | **SEMCO/TCmanager** (deckt Buchung+Rechnung) / Ticketing-SaaS | **Pretix** (Seminar/Ticketing) · **Indico** (Kongress/Tagung) — OSS, self-host · günstige Cloud: Pretix Hosted | Buy |
| 11 | **PMS (Gästehaus)** | Satellit | **Conference-House** (PMS + C&C/Bankett + POS), nicht nur Hotel-PMS | SIHOT · OPERA Cloud S&E · Best-of-Breed — siehe [RFI ⏸️ geparkt/inaktiv](PMS-Gaestehaus-RFI.md) | QloApps/HotelDruid (OSS) decken **kein** Bankett/Bestuhlung | Buy — **niemals** selbst |

**Verbindungsschicht (das eigentliche Projekt):** iPaaS / Power Automate (Sync) **+ Keycloak-SSO** (ein Login über alle Satelliten) **+ Event-/API-Bus**. Erst SSO + Integration machen Best-of-Breed komfortabel. **Keycloak** ist als anbieter-neutraler IdP **gesetzt** (self-host, im Showcase mit 2 Realms erprobt); externe Konten (M365 o. ä.) werden bei Bedarf föderiert. **OSS-Variante der Verbindungsschicht:** **n8n** (self-host) statt Power Automate. **JVM-Variante des Layers** (Camel Quarkus + LangChain4j + Quarkus Flow) — siehe Architektur-Option unten.

> **Zur OSS-/Low-Cost-Spalte:** je Fähigkeit ist *eine* lizenzkostenfreie On-Prem- **oder** günstige Cloud-Option genannt — als Sparvariante und Verhandlungsanker. **Achtung (gilt durchgängig):** „lizenzfrei" ≠ „kostenlos" — Hosting, Betrieb, Updates, Sicherheit und Support kommen hinzu, und das **OpenEduCat-Lehrstück** (eure Hochschule auf Odoo-OSS, die ihr gerade ablöst) mahnt zur Vorsicht. Detail-Profile, Lizenzarten und Quellen: siehe Open-Source-Ebene der [Anbieter-Vergleichsmatrix](Anbieter-Vergleichsmatrix.md). Belastbarer Vergleich nur über die [TCO-Kostenaufstellung](TCO-Kostenaufstellung.md) (Lizenz **+ Hosting + FTE-Betrieb + Support**).

---

## Architektur-Option (offen): JVM-Integrations-/Kern-Layer — Quarkus + Camel + LangChain4j + Flow

> **Nicht gesetzt — Option zur Bewertung im RFI/PoC.** Schärft die in [TCO §6](TCO-Kostenaufstellung.md) gelistete „Eigenbau Java/Quarkus"-Variante: **nicht** als handgebauter Monolith, sondern als **standardbasierter Integrations-/Kern-Layer** — und stellt sie damit als ernsthafte Alternative **neben Power Platform/Dataverse** für die Fähigkeiten **#1 (MDM)** und **#6 (Integration)**.

Statt „Quarkus = noch ein CRUD-Backend" bündelt eine **einzige nativ-kompilierte JVM-Runtime** drei Schichten on-prem:

| Baustein | Rolle | Macht … |
|---|---|---|
| **Camel Quarkus** (300+ Komponenten, EIP) | Clientanbindung + Konvertierung/Routing | das funktionale Pendant zur Sync-/Mediations-Schicht (low-level: Routen selbst gebaut; HubSpot & Co. via HTTP/REST + JSON-Dataformat, kein fertiger inkrementeller Connector) |
| **Quarkus LangChain4j** | KI-Konvertierung: Text→strukturierte POJOs, Klassifikation, **Entity Resolution/Dedup** | genau die **Golden-Record-Matching-Aufgabe** (#1 MDM) — das kann ein klassisches iPaaS/EL-Tool nicht |
| **Quarkus Flow** (0.9, CNCF Serverless Workflow, Java-DSL, Agentic-AI) | durable, transaktionale Orchestrierung | code-/spec-getriebenes Pendant zu n8n / Power Automate |

**Wo es trägt (Differenzierer):** semantische Konvertierung + Dubletten-/Entity-Resolution in den Golden Record, NL-Datenzugriff — der eigentliche MDM-Kern-Wert.
**Wo es *nicht* trägt (Commodity):** plain EL (eine Quelle inkrementell nach Postgres) bleibt billiger mit **dlt / Airbyte / n8n** — nicht in Camel nachbauen (eigene Leitlinie „Commodity nicht selbst bauen").

**Ehrliche Spannungen:**
- **Zweites Ökosystem** — JVM neben dem Node/TS-Showcase-Stack (Vendure). Als Polyglott-Best-of-Breed legitim (Commerce = Node-Satellit, Integration/Kern = JVM), aber zwei Stacks zu besetzen und zu betreiben.
- **n8n vs. Quarkus Flow** folgt dem Team-Profil: Citizen-Integrator/Tempo → n8n; Java-Engineering-Org/durable + KI → Quarkus Flow.
- **TCO:** Java war in [§6](TCO-Kostenaufstellung.md) am teuersten (Senior-Sätze, DL-Lock-in); KI-gestützte Entwicklung + **Standard-Specs** (Camel-EIP, CNCF Serverless Workflow) statt Handarbeit verkleinern diesen Abstand (vgl. [§7](TCO-Kostenaufstellung.md)) — bewerten, nicht annehmen.

**Einordnung:** ernsthafte **Kern-/Integrations-Alternative**, falls bewusst raus aus MS-Lock-in **und** JVM als Integrations-/Kern-Ökosystem gewollt ist; im RFI/PoC gegen Power Platform/Dataverse (#1/#6) zu prüfen. **Wird hier nicht gesetzt.**

---

## Wichtige Stellhebel

- **Satelliten zusammenlegen, nicht vervielfachen:** Ein gutes **Akademie/Seminar-System** deckt oft Produktbuchung **+ Ticketing + Seminar-Rechnung** in einem ab; ein **Campus-System** deckt Einschreibung **+ Prüfung**. So sinkt die Zahl der Satelliten (und Schnittstellen) deutlich. Das ist der größte Kostenhebel auf der Satellitenseite.
- **Vorhandenes weiternutzen:** Moodle (LMS/Content), Sket (Prüfung), Teams/M365 sind da — anbinden, nicht ersetzen. **SSO = Keycloak** (gesetzt, anbieter-neutral) als zentraler Login darüber.
- **Abo-Logik ist kein CRM-Job:** „dauerhafter Zugriff auf Lehrmaterial" = **Entitlement + Recurring Billing + Content-Delivery (LMS)** — dafür gibt es spezialisierte Abo-Billing-Tools; der Kern hält nur die Kundenbeziehung und den Status.
- **Shop ≠ Buchung ≠ Billing:** drei getrennte Dinge. Shop (Bezahlung digitaler Inhalte) ist E-Commerce; Studienbuchung ist Enrollment; Rechnung/Abo ist Billing. Nicht in ein System pressen.

---

## Antwort auf die Ausgangsfrage (Kosten)

- **Alles auf Power Platform = teuerste UND schlechteste Variante** (Commodity nachbauen). Niemand sollte Shop/PMS/Billing/Ticketing dort bauen.
- **Günstigste *robuste* Variante = Best-of-Breed-Mesh:** schlanker moderner **Kern** (Daten/CRM/Controlling/Automation) + **fertige SaaS je Commodity** + **Keycloak-SSO** + **Integration**.
- Die Kern-Entscheidung (Power Platform vs. Salesforce vs. KI-gestützter Eigenbau) betrifft nur diese **15–25 %** — die übrigen Satellitenkosten fallen in jeder Variante an.
- **Konsequenz:** Die „Plattform ist zu teuer"-Sorge löst sich auf, sobald man Plattform **nur als Kern** versteht und nicht als Monolith für alles. Genauso gilt aber: **auch ein Eigenbau soll nicht alles selbst bauen** — die Make/Buy-Grenze verläuft entlang dieser Capability-Map, nicht entlang der Stack-Wahl.

---

## Nächster sinnvoller Schritt
Capability-Map mit dem **Ist-Bestand abgleichen** (was deckt heute welche Fähigkeit?) und je Satellit „behalten / ersetzen / neu" markieren → daraus wird die belastbare **Soll-Bebauungsplanung** + die Schnittstellenliste für den Integrations-Layer.
