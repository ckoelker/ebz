# Fuxam — Einordnung (Bildungs-Suite-Konsolidierer?)

> Notiz zur Bildungs-Planung (Stand 2026-06-30). **Gehört NICHT zum Gästehaus/PMS-Thema** — Fuxam ist reine
> Bildungs-Software. Hier nur eingeordnet, weil es bestehende EBZ-Entscheidungen (OpenOLAT/Sket) berührt.
> **Status: beobachten, noch nicht entscheidungsreif.**

## Was Fuxam ist
**FuxamOne** — KI-gestützte **All-in-One-Bildungsplattform**: **Campus-Management + LMS + digitale Prüfungen +
KI-Planung + Mobile-App**, vermarktet für **Hochschulen, Berufsschulen, Akademien**.

## Harte Fakten (jenseits Marketing, recherchiert)
- **Fuxam GmbH**, Berlin, **gegründet 2021**. Gründer: Julian Schröder, Oliver Grübnau, Leo van den Brandt.
- **~21 Mitarbeiter.** Funding: **Seed ~$635K (Nov 2023)**, Investor **Pioneer Ventures** (zuvor ~€330k).
- Erste Kunden **seit Anfang 2023**. Bekannte Referenzen: **CODE University of Applied Sciences (Berlin)**,
  **Freie Hochschule Stuttgart** (seit Okt 2023); etwas Südamerika.
- **Befund:** sehr **frühe Phase, leicht finanziert, kleines Team**; wenige, kleine, **private** Hochschulen als
  Referenz — **keine großen/öffentlichen, keine Berufsschul-Referenz** gefunden (trotz Berufsschul-Marketing).

## Vorgänger / Basis: „smarTest" → „Future of Exam"
- Fuxam ist die **Umbenennung/Fortführung von „smarTest"** — kein gestandenes Vorgängerunternehmen, sondern die
  **Frühform desselben jungen Vorhabens** (Studentenprojekt an der **CODE University Berlin**, 2021; Julian
  Schröder war mit dem LMS/Papierprüfungen der Hochschule unzufrieden).
- Register: **Fuxam GmbH** eingetragen **08.02.2022** (HRB 238572 B, AG Charlottenburg; erste Registerspur
  29.12.2021). Name **Fuxam = „FUture of eXAM"**.
- **Kern-Erkenntnis:** Die **technische Basis ist ein Prüfungs-/Klausur-Tool** (smarTest/FuxamExams). **Campus-
  Management + LMS sind später angebaute Breite** (ab 2023). → **Reife-Gefälle:** das **Exam-Modul** ist am
  ältesten/tiefsten, **LMS und Campus-Management am jüngsten/dünnsten**.
- **Folge für EBZ:** Fuxam würde euch ausgerechnet bei seinen **jüngsten Modulen** herausfordern — **LMS gegen
  euer integriertes OpenOLAT** und **Campus-Mgmt als OpenEduCat-Ersatz** —, während die eigentliche Kernkompetenz
  (digitale Prüfungen) nur einen **Teilbereich** abdeckt. smarTest selbst hat **keine eigenständigen
  Referenzen/Historie** (kein Reifenachweis aus der Vorgänger-Phase).

## Technik (Fingerprint-Recherche 2026-06-30 — da keine offizielle Doku)
Aus HTTP-Headern/HTML-Signaturen (fuxam.app, app.fuxam.com, fuxam.com) + Personio-Stellenausschreibung
(„Fullstack Engineer"):
- **Stack:** **TypeScript · Next.js / React · Node.js · Tailwind**; **Prisma ORM** (→ sehr wahrscheinlich
  PostgreSQL); KI via **Vercel AI SDK** + Vektor-DB (**Pinecone/Weaviate**); **Sentry** (Error-Monitoring).
- **Hosting:** App läuft auf **Vercel** (`Server: Vercel`, `X-Powered-By: Next.js`); Marketing-Site hinter
  **Cloudflare**. → **Vercel = US-PaaS (auf AWS)** ⇒ **DSGVO/Hosting-Region ist ein harter Prüfpunkt** für
  Studierenden-/Prüfungsdaten (Auftragsverarbeiter + EU-Region klären).
- **Team/Kultur:** klein, **„AI-native"** (tägliche Nutzung von Cursor/Claude Code/Copilot erwartet), aktuell
  **Junior-Fullstack** gesucht → junges, schlankes, KI-gestützt entwickelndes Team.
- **Multi-Tenancy (jetzt belegt):** der Sign-in (fuxam.app/app.fuxam.com → `/sign-in`) fragt **„Institution /
  Workspace / Subdomain – Select your …"** → Mandanten-Konzept bestätigt. Geratene Tenant-Subdomains
  (code/stuttgart/…`.fuxam.app`) liefern aber **404 (Vercel)**, Wildcard-DNS existiert → **zentrale Vercel-App +
  Workspace-/Slug-Auswahl**, **keine** physische Pro-Mandant-Isolation auffindbar ⇒ **shared (logische)
  Multi-Tenancy** wahrscheinlich. `api.fuxam.com` löst nicht auf → API = Next.js-Routes unter der App-Domain.
- **AVV/DSGVO (aus fuxam.com-Datenschutz — = Marketing-Site!):** Website-Hosting **GoDaddy NL (EU)**; Marketing-
  Verarbeiter Intercom (EU), SendGrid (US), HubSpot, **Stripe (Irland)**, Google Analytics/Tag Manager, Meta/
  TikTok/LinkedIn; US-Transfers via **EU-US DPF/Standardvertragsklauseln**. **Wichtig:** Das ist die Marketing-
  Site — die **Produkt-/Plattform-Datenverarbeitung** (Studierenden-/Prüfungsdaten auf **Vercel/AWS**) ist
  **dort nicht beschrieben**; der **Produkt-AVV/Serverstandort ist nicht öffentlich** → muss angefordert werden.
- **Integrations-Sicht (positiv):** moderner Node/Next-Stack → **REST/JSON-Anbindung an euren Kern grundsätzlich
  gut machbar** — *sofern* APIs bereitgestellt werden (Vertrag/Doku weiterhin nicht öffentlich).

**Einordnung:** Stack ist **modern & sauber (kein Legacy)** — Pluspunkt für Integration. **Risiken:** US-PaaS
(Vercel)/DSGVO-Region · vermutlich **shared Multi-Tenant** (Isolation prüfen) · **kleines Junior-/AI-vibe-Team**
(Code-Reife + Security-Review nötig).

## Substanz-Lücke (weiterhin offen)
Trotz Stack-Fingerprint öffentlich **nicht** dokumentiert: **API-Vertrag/Interop** (LTI/SCORM/xAPI, SSO/OIDC,
Webhooks), **Daten-Isolations-/Mandanten-Modell** im Detail, DSGVO-Auftragsverarbeiter + EU-Region,
Skalierung/SLA, unabhängige (nicht referenz-gestützte) Reviews. → bleibt **Due-Diligence-Pflicht** vor jeder
Setzung.

## Wo es in die EBZ-Bildungslandschaft passt
| EBZ-Fähigkeit | Heute / geplant | Fuxam |
|---|---|---|
| Hochschule Campus-Mgmt | OpenEduCat (🔴 EOL) | **Ersatz-Kandidat** |
| Akademie-Verwaltung | UniTop/NAV (🔴 EOL) | Kandidat |
| LMS / Lerninhalte | **OpenOLAT** (gebaut+tief integriert) | **überschneidet/konkurriert** |
| Prüfungsverwaltung | Sket (+ Moodle-Prüfung) | **Ersatz-Kandidat** |
| Berufsschul-Verwaltung | **Schild-NRW (Pflicht)** + Java-App | daneben, **ersetzt Pflicht nicht** |

**Nicht betroffen:** Gästehaus/PMS, Shop/Vendure, Rechnung/Debitoren, Controlling, Marketing/HubSpot.

## Strategische Spannung
Fuxam ist ein **Bildungs-Konsolidierer** (Campus+LMS+Prüfung in EINEM) — derselbe „Suite vs. Best-of-Breed"-
Konflikt wie beim Gästehaus, nur in der Bildungsdomäne. **Kollidiert** mit dem bewusst gewählten **OpenOLAT-
Weg** (LMS, bereits L0–L6 integriert + Mandanten-Vermarktung) und wäre bei **Sket** ein Ersatz, kein Add-on.

## Risiko-Einordnung
- **Vendor-/Survival-Risiko:** 21-Personen-Seed-Startup (2021, ~$635K) als Träger eines **mehrjährigen,
  geschäftskritischen Bildungs-Kerns** — Startup-Mortalität ist real. Ironischerweise dasselbe Risikomuster wie
  die EOL-/Lock-in-Sorgen (nur von der anderen Seite). Genau die **Versionsneutralitäts-/Abhängigkeits-Lektion
  aus OpenEduCat** mahnt zur Vorsicht.
- **Reife:** Funktionsbreite hoch beworben, Referenztiefe (große/öffentliche, Berufsschule) dünn.

## Ist Fuxam eine Option für EBZ? (Fazit)
**Fuxam konkurriert mit eurem Stack — und zwar mit der Strategie selbst, nicht nur mit Einzelsystemen.** Euer
Leitprinzip ist „**eigener Daten-/Kundenkern (SoR) + Best-of-Breed integrieren**". Fuxam ist eine **konkurrierende
All-in-one-Suite** (Campus+LMS+Prüfung), die Studierende/Kurse/Prozesse **selbst** besitzen will — das läuft eurem
Kern-als-SoR-Modell und dem bereits Gebauten (Party-Kern, `bildung`-Domäne, OpenOLAT-Integration, Anmeldung,
Rechnung/Controlling) **entgegen**.

**Verdict:**
- **Als Bildungs-Kern/LMS-Ersatz: NEIN (jedenfalls nicht jetzt).** Es widerspricht eurer Kern-Strategie, ersetzt
  bewusst gewählte/integrierte Bausteine (OpenOLAT), kommt von einem **frühen Anbieter** (Seed, ~21 Personen,
  Basis = Exam-Tool), mit **US-PaaS/Shared-Multi-Tenant** und **nicht öffentlichem Produkt-AVV**. Reife- und
  Vendor-Risiko zu hoch für einen geschäftskritischen, mehrjährigen Kern.
- **Als schmales Punkt-Tool „digitale Prüfungen": VIELLEICHT (beobachten).** Das ist Fuxams **älteste/stärkste**
  Kompetenz → möglicher **Sket-/„Bochum-Prüfung"-Kandidat**, als Satellit an euren Kern angebunden — **nicht**
  als Campus/LMS-Kern. Nur nach harter DD (s. u.).

**Kurz:** Fuxam ist **keine Option für den Bildungs-Kern**, weil es gegen eure eigene (schon umgesetzte)
Architektur-Entscheidung läuft; bestenfalls ein **beobachteter Prüfungs-Punktbaustein**.

## Empfehlung
**Nicht als Kern-Setzung, sondern „beobachten / höchstens schmaler Pilot".** Falls inhaltlich interessant:
1. **Harte Due Diligence:** API/Standards (LTI/SCORM/xAPI/SSO-OIDC), DSGVO/Hosting (EU), Datenexport/-Eigentum,
   **Source-Code-Escrow + Exit-Klauseln**, Finanzlage/Runway, echte vergleichbare Referenz (Berufskolleg/öffentl.).
2. **Schmaler Pilot** an *einem* Use-Case (z. B. eine Akademie-Strecke) — **nicht** OpenOLAT/Sket vorab ablösen.
3. Anbindung wie jeder Satellit an den **Kern** (Teilnehmer→Party, Gebühren→Debitoren, KPIs→Controlling).

**Fazit:** ernster *Kandidat* zur Bildungs-Konsolidierung, aber **früh + dünn belegt** → kein Ersatz für den
OpenOLAT-Weg auf Basis der heutigen Faktenlage. Entscheidung getrennt vom Gästehaus/PMS-RFI.

---
## Quellen (2026-06-30)
fuxam.com / fuxam.de (Produkt); Crunchbase/PitchBook/Tracxn/Dealroom (Firma/Funding); Handelsblatt-Firmenprofil;
case studies blog.fuxam.com (CODE University, Freie Hochschule Stuttgart); OMR Reviews; starting-up.de;
startupvalley.news. Technische/Interop-Details: **nicht öffentlich auffindbar** (Due-Diligence-Lücke).
