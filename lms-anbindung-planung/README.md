# lms-anbindung-planung

Planung für die **Anbindung eines LMS / Web-Based-Trainings** an den EBZ-Best-of-Breed-Showcase —
Ablösung des bestehenden, kommerziellen **Lemon LMS**. Analog zu `rechnungsstellung-planung/`,
`shop-planung/`, `prozessdoku-planung/` — reine Planungs-/Konzept-Doku, **kein Code**.

## Dokumente
- **[Konzept-LMS-Anbindung.md](Konzept-LMS-Anbindung.md)** — Auftrag, Auswahlkriterien, der
  Evaluierungs-Trichter (was wurde warum ausgeschlossen), die Vollmatrix
  **OpenOLAT · Open edX · Selbst machen · Lemon**, Empfehlung **OpenOLAT** (Fallback Open edX,
  Entscheidungsregel = SCORM-Version), die Integrationsarchitektur (SSO + Outbox→Einschreibung),
  Phasen-Roadmap L0–L5, offene Gating-Punkte und Quellen.
- **[L1-Code-Plan-LMS-Anbindung.md](L1-Code-Plan-LMS-Anbindung.md)** — detaillierter
  Realisierungsplan (kein Code). Entscheidungen festgezurrt: **OpenOLAT (SCORM 1.2)**, eigene
  Postgres-DB **`openolat`**, Katalog **aus MDM projizieren**. Enthält Verortung, Deployment,
  Datenmodell (`WbtKurs`/`Kurseinschreibung` in `mdm`), SSO-Config, Services (inkl.
  `OpenolatEnrollmentDispatcher` über das Outbox-Muster), REST-Endpunkte, Provisionierungs-Naht,
  Phasen L0–L5, Teststrategie und offene Verifikationspunkte.

## Kurzfassung
- **Auftrag:** Zugang zu **Web-Based-Trainings** aus dem Shop verkaufen; Aufruf aus dem Portal,
  Login-Übergabe (Keycloak), API-steuerbar; **~100 Bestandskurse aus Lemon** sollen übernommen
  werden. **Offline-Mobile** wurde als verzichtbar eingestuft („online reicht").
- **Empfehlung:** **OpenOLAT** als eigenständiges System neben Vendure — bester Fit für einen
  DACH-Bildungsanbieter eurer Größe: **Postgres-nativ, Keycloak-OIDC nativ, Apache-2.0** (kein
  Copyleft), **deutlich leichterer Betrieb** als Open edX (ein Tomcat-Server statt MySQL+Mongo+
  Redis+ES-Verbund), DACH-/Bildungs-Verbreitung. SCORM **1.2** nativ.
- **Fallback:** **Open edX**, falls die Lemon-Kurse als **SCORM 2004** vorliegen (OpenOLAT kann nur
  1.2). Open edX ist betrieblich schwer und nicht Postgres — nur dann gerechtfertigt.
- **Integration:** euer bewährtes Muster — Vendure verkauft → **Outbox**-Provisionierung ruft die
  LMS-REST-API (Einschreibung) → Portal „Meine Trainings" startet den Kurs per **Keycloak-SSO**.
  Vendure wird **nicht** umgebaut; keine Nischen-Bibliotheken.

> **Status: KONZEPT / PLANUNG — kein Code, nichts gebaut.** Zwei **Gating-Punkte** vor jeder
> Realisierung: (1) Können die 100 Lemon-Kurse exportiert werden (alle vs. nur WBTs)? (2) In welcher
> **SCORM-Version** (1.2 → OpenOLAT, 2004 → Open edX)? Beides ist mit **Lemon/Fachbereich** zu klären.
> Außerdem braucht eine eigene Postgres-DB `openolat` die ausdrückliche Freigabe (Schema-Regel).

## Entscheidungs-Historie (Trichter)
Ausgeschlossen wurden im Verlauf: **Moodle/ILIAS** (Nutzer-Ausschluss), **Sakai** (sterbend, ~2 %),
**Wellms/LearnHouse** (zu geringe Verbreitung / SSO-Paywall), **Chamilo** (MySQL, kein DACH),
**Opigno** (API-first nur in der kostenpflichtigen Enterprise-Edition), **OpenOLAT zunächst „wegen
Tomcat"** — später **wieder aufgenommen**, weil Open edX betrieblich schwerer ist als OpenOLAT.
**Selbst machen** scheidet praktisch aus, weil 100 Kurse abspielen eine SCORM-Runtime (ausgeschlossene
Bibliothek) erfordert und Neu-Erstellung unwirtschaftlich ist.
