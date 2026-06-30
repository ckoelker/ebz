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

## Substanz-Lücke (Due-Diligence offen)
Öffentlich **nicht** auffindbar: **API-/Interop-Doku** (LTI/SCORM/xAPI für LMS-Interop), DSGVO/Hosting-Details,
Datenmodell, Skalierungs-/Verfügbarkeitsangaben, unabhängige (nicht referenz-gestützte) Reviews. → Eindruck
„viel Marketing, wenig belastbare Technik" bestätigt sich.

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
