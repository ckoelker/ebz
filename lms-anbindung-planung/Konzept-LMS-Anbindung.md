# Konzept — LMS-/WBT-Anbindung (Ablösung Lemon LMS)

> Best-of-Breed-Showcase EBZ. Stand 2026-06-15. **Reine Planung — kein Code.**
> Rechtliche/lizenzrechtliche Aussagen = Recherchestand, vor Realisierung final prüfen.
> Verifizierte Quellen siehe §8.

---

## §0 Auftrag & Kontext
Der EBZ-Shop (Vendure) soll künftig **Zugang zu Web-Based-Trainings (WBT)** verkaufen. Das heute
genutzte **Lemon LMS** (kommerziell, Lemon Systems GmbH) soll **abgelöst** werden. Anforderungen:
- Aufruf **aus dem Portal**, **Login-Übergabe** über das vorhandene **Keycloak** (Realm
  `ebz-customers` für Kunden, `ebz-staff` für Mitarbeitende).
- **Vollständig per API steuerbar** (Provisionierung Einschreibung, Status).
- **Self-hosted, kostenfrei**, anschlussfähig an euren Stack (Quarkus / Vue / Postgres / Keycloak /
  MinIO / Vendure).
- **~100 Bestandskurse aus Lemon** sollen übernommen werden.
- **Offline-Mobile-Lernen** (Lemons Signatur) wurde als **verzichtbar** eingestuft („online reicht").

## §1 Auswahlkriterien (gewichtet)
1. **Verbreitung / Langlebigkeit / Bus-Faktor** — etabliert für einen Bildungsanbieter dieser Größe.
2. **Stack-Fit** — Postgres, Keycloak-OIDC, REST-API, Docker-Compose-tauglich.
3. **Betriebsaufwand** — möglichst leichtgewichtig.
4. **SCORM/WBT-Fähigkeit** — Pflicht wegen der 100 Bestandskurse.
5. **Lizenz** — frei, möglichst ohne starkes Copyleft.
6. **DACH-/Bildungsträger-Fit.**

## §2 Evaluierungs-Trichter (ausgeschlossen)
| Kandidat | Ausschlussgrund |
|---|---|
| Moodle, ILIAS | vom Nutzer ausgeschlossen |
| Sakai | sterbend (~2 % Higher-Ed-Anteil, „von fast allen Instituten verlassen") |
| Wellms (EscolaLMS) | zu geringe Verbreitung (Bus-Faktor) — trotz Postgres/headless |
| LearnHouse | Startup, klein; **SSO nur in Enterprise-Lizenz** |
| Chamilo | MySQL-zentriert, kein DACH-Bezug |
| Opigno | API-first nur in **kostenpflichtiger** Enterprise-Edition |
| Frappe LMS | MariaDB, kein DACH |
| **Selbst machen** | 100 Kurse abspielen braucht **SCORM-Runtime** (= ausgeschlossene Nischen-Lib); Neu-Erstellung unwirtschaftlich |

Verbleibende, ernsthaft verglichene Optionen: **OpenOLAT**, **Open edX** (und als Referenz **Lemon**,
**Selbst machen**).

## §3 Vollmatrix
| Kriterium | **OpenOLAT** | **Open edX** | **Selbst machen** | **Lemon** (Status quo) |
|---|---|---|---|---|
| **SCORM (Migration!)** | ✅ **1.2 nativ** (Import + Tracking) · ❌ kein 2004 | ✅ **1.2 + 2004** (XBlock) | ❌ ohne Nischen-Lib nicht realistisch | ✅ (Kernfeature) |
| **Betriebsaufwand** | ⚠️ **ein** Java/Tomcat + Postgres (moderat) | ❌ **schwer**: LMS+Studio+MySQL+Mongo+Redis+ES, ~16 GB+, k8s ab Skalierung | ⚠️ ihr betreibt eh Multi-Service | ✅ Vendor betreibt |
| **Postgres** | ✅✅ ausdrücklich empfohlen | ❌ MySQL + MongoDB | ✅✅ | n/a |
| **Keycloak-OIDC (Login-Übergabe)** | ✅ **nativ** (OAuth2/OIDC/Shibboleth/LDAP) | ⚠️ via third-party-auth (machbar) | ✅✅ nativ | ⚠️ Vendor-abhängig |
| **Lizenz** | ✅ **Apache 2.0** | ⚠️ **AGPLv3** (Copyleft) | eure | proprietär |
| **DACH-/Bildungs-Fit** | ✅✅ Uni Zürich, RPTU/Virtual Campus RLP, Uni Innsbruck, HWG Ludwigshafen | ⚠️ MOOC/global, Hochschul-/Corporate-Bias | ✅ maßgeschneidert | ✅ DACH-Vendor |
| **Verbreitung / Bus-Faktor** | ✅ solide DACH-institutionell | ✅✅ riesig global (140 Mio. Lernende) | ⚠️ hängt allein an euch | ⚠️ Vendor-abhängig |
| **Autoren-Werkzeug** | ✅ Kurseditor (SCORM nur **Import**) | ✅✅ Studio (stärker) | ❌ selbst bauen | ✅ |
| **API / Provisionierung** | ✅ „für Remote-Mgmt gebaut" (User/Gruppen/Kurse/Einschreibung) | ✅ Enrollment-API (umfangreich, sprawling) | ✅✅ ihr definiert sie | ⚠️ Webshop-API-Modul |
| **Vendure-Anbindung** | gleich: Keycloak-SSO + REST + Outbox | gleich | nativ in eurem Stack | begrenzt |
| **Headless/Vue** | ⚠️ Konsum per SSO-Launch (kein Headless) | ⚠️ teils MFEs | ✅✅ | ❌ |
| **Mobile + Offline** | irrelevant (gestrichen) | irrelevant | irrelevant | ✅✅ (Signatur — entwertet) |
| **Stack/Sprache** | Java/JVM — **kennt ihr (Quarkus)** | Python/Django — fremd | euer Stack | extern |
| **Kostenmodell** | frei + moderater Betrieb | frei + **hoher** Betrieb | Entwicklung + Dauerwartung | Lizenz/Per-Seat |

## §4 Empfehlung
**OpenOLAT** — auf nahezu jeder relevanten Achse besser als Open edX: Postgres-nativ, Keycloak-OIDC
nativ, **Apache-2.0** statt AGPL, DACH-/Bildungs-Fit, JVM das ihr kennt und vor allem **erheblich
leichterer Betrieb**. Der einzige Minuspunkt (Tomcat) ist kleiner als Open edX' Multi-Service-
Schwergewicht — „wegen Tomcat raus, Open edX rein" wäre inkonsistent.

**Entscheidungsregel (gating):**
- Lemon-Kurse als **SCORM 1.2** → **OpenOLAT**.
- Lemon-Kurse als **SCORM 2004** (oder gemischt mit 2004) → **Open edX**.
- **Kein brauchbarer SCORM-Export** aus Lemon → Lock-in-/Re-Authoring-Verhandlung mit Lemon,
  plattformunabhängig.

## §5 Integrationsarchitektur (für OpenOLAT)

### Zielbild
```
                Keycloak (ebz-customers / ebz-staff, OIDC)
                          ▲ Login / SSO
                          │
  ┌─────────┐   kauft   ┌────────────┐  Outbox-Event   ┌────────────────────┐
  │ Portal  │──Zugang──▶│  Vendure   │────────────────▶│ integration        │
  │ (Vue)   │           │ (Commerce) │  (Zahlung ok)   │ (Quarkus)          │
  │ "Meine  │◀──Liste───┤            │                 │  Outbox-Dispatcher │
  │ Trainings"          └────────────┘                 └─────────┬──────────┘
  │  + Start │                                          REST      │ enrol/user
  └────┬─────┘                                                    ▼
       │ SSO-Launch (OIDC)                              ┌────────────────────┐
       └────────────────────────────────────────────────▶ OpenOLAT          │
                                                         │ (Tomcat + JVM)    │
                                                         │  SCORM-1.2-Kurse  │
                                                         └─────────┬─────────┘
                                                                   ▼
                                                          Postgres-DB "openolat"
```
OpenOLAT bleibt **eigenständig** (wie Vendure). Kopplung nur über **Keycloak-SSO** (Login) und
**REST** (Provisionierung/Status). Kein Umbau von Vendure, keine Nischen-Bibliotheken.

### 5.1 Deployment (Docker Compose, Profil `controlling`)
- Service `openolat` (offizielles Image, Tomcat/JVM) + Volume `olatdata` (Kurs-/SCORM-Ablage).
- **DB:** neue Datenbank `openolat` **in der bestehenden Postgres-Instanz**, eigener User — Muster wie
  vendure/controlling/lightdash. ⚠️ Neue **Application-DB** (kein Schema in `mdm`) → **Freigabe nötig**.
- Eigener Hostname (z. B. `lms.localhost`), in nginx wie die übrigen SPAs.
- Footprint: ein App-Server + DB (kein Mongo/ES/Redis-Verbund wie Open edX).

### 5.2 Identität & SSO
- OpenOLAT nativer **OAuth2/OIDC-Adapter** gegen `ebz-customers` (Lernende) und `ebz-staff`
  (Autoren/Admins).
- **Just-in-time-Provisioning** beim ersten OIDC-Login; Schlüssel = Keycloak-`sub` (stabil),
  Name/E-Mail aus Claims.
- **Rollen:** Keycloak-Rollen → OpenOLAT-Rollen (Lernende = user; Kurspflege = author/admin via
  Staff-Rolle, analog `katalog-pflege`).
- Login-Übergabe aus dem Portal = OIDC-Redirect (kein zweites Passwort).

### 5.3 Commerce → Entitlement → Einschreibung (Outbox-Muster)
1. **Vendure**-Produkt „Kurs-Zugang" trägt Custom-Field `openolatRepositoryEntryKey` (Kurs-Referenz),
   analog zu `vendureProductId` der MDM-Shop-Projektion (umgekehrte Richtung).
2. Zahlung erfasst → Vendure-Event → `integration` schreibt **Outbox**-Eintrag `COURSE_ENROLLMENT`.
3. **Dispatcher** `OpenolatEnrollmentDispatcher` ruft OpenOLAT-REST (`/restapi`, Service-Account):
   User sicherstellen + in Kurs/Gruppe einschreiben — **idempotent**, mit Retry/Dead-Letter/HITL wie
   die bestehende WebUntis-Sync, im BPMN als `SERVICE_TASK` sichtbar.
4. Storno → optionales Gegen-Event (Ausschreibung).
- REST-Client: `quarkus-rest-client-jackson` (wie Vendure-Shop-Projektion-Client).

### 5.4 Kurs-Katalog / Mapping
- Quelle der Wahrheit: `openolatRepositoryEntryKey` am Vendure-Produkt.
- Optional später: Veröffentlichung aus **MDM** (wie „Shop-Projektion"), Pflege an einer Stelle.
  Start: manuelles Mapping der 100 Kurse.

### 5.5 Content-Migration (100 Kurse)
- SCORM-**1.2**-Pakete aus Lemon → OpenOLAT-**SCORM-Lernressource** importieren, je in einen Kurs.
- OpenOLAT **importiert** SCORM (erstellt es nicht) — passt, Inhalte kommen aus Lemon.
- Bulk-Import skriptbar; Mapping-Tabelle Lemon-Kurs → RepositoryEntryKey → Vendure-Produkt.
- **Gating:** SCORM-Version + Exportierbarkeit aus Lemon (→ §7).

### 5.6 Portal „Meine Trainings"
- Neue Portal-Seite analog „Meine Rechnungen": `integration` liefert die Einschreibungen des
  eingeloggten Kunden (kontext-skopiert wie die Rechnungen).
- „Starten" → **SSO-Deeplink** in den OpenOLAT-Kurs (OIDC-Auto-Login); Konsum in OpenOLAT
  (kein Headless-Rendering — bewusst, „online reicht").

### 5.7 Fortschritt / Zertifikate
- SCORM-Completion/Score wird in OpenOLAT getrackt.
- Optional: Completion per REST zurück nach `integration` (Reporting/Lightdash) und **Zertifikate**
  (OpenOLAT-Zertifikatsmodul kann Lemons Zertifizierung ersetzen).
- ⚠️ Offen: ob die REST-API Completion **auslesen** kann (Einschreiben bestätigt) → §7.

## §6 Phasen-Roadmap
| Phase | Inhalt |
|---|---|
| **L0** | Deploy: `openolat`-Service + Postgres-DB + Hostname/Proxy; Admin erreichbar |
| **L1** | SSO: Keycloak-OIDC beidseitig (Kunden/Staff), JIT-User, Rollen-Mapping |
| **L2** | 1 Kurs end-to-end: SCORM-1.2-Import → Kurs → manuelles Mapping → Portal-Launch |
| **L3** | Commerce-Kopplung: Vendure-Produkt-Mapping → Outbox `COURSE_ENROLLMENT` → Dispatcher → Einschreibung (idempotent, Retry/HITL) |
| **L4** | Portal „Meine Trainings" + Completion/Zertifikate |
| **L5a** | **Seed-Import** von 3 frei verfügbaren SCORM-1.2-Kursen aus `testdata/` end-to-end (kein Lemon nötig; Fetch via `showcase/lms-fetch-testdata.sh`) |
| **L5b** | Bulk-Migration der echten 100 Kurse (nach Lemon-Export-Klärung) — gegated |

## §7 Offene Punkte / Gating-Fragen
**An Lemon / Fachbereich (blockt die echte Migration L5b, NICHT den Showcase):** der Showcase-
Durchstich nutzt **frei verfügbare SCORM-1.2-Seed-Kurse** aus `testdata/` (siehe L1-Plan §5a), bis
der Lemon-Export geklärt ist.
1. Können die 100 Kurse als **SCORM exportiert** werden — alle, oder nur die importierten WBTs?
2. In welcher **SCORM-Version**: **1.2** (→ OpenOLAT) oder **2004** (→ Open edX)?
3. Für nativ in Lemon erstellte Inhalte: gibt es **irgendeinen** Export (Medien + Struktur), oder
   droht **Re-Authoring**? (Lock-in-Frage)

**Intern:**
- **Neue Postgres-DB `openolat`** — Freigabe nötig (Schema-Regel: kein neues Schema/keine neue DB ohne OK).
- **OpenOLAT-Release + JVM-Linie** festklopfen (Versionskompatibilität, BOM-Disziplin).
- **REST-Completion-Read** in der OpenAPI-Spec verifizieren (für Reporting/Zertifikate).
- **Betrieb/Pflege:** wer pflegt Kurse (Staff-Rolle), wer betreibt den Tomcat-Service.

## §8 Quellen (Recherche 2026-06-15)
- OpenOLAT: [SCORM Learning Content — nur 1.2](https://docs.openolat.org/manual_user/learningresources/Course_Element_SCORM_Learning_Content/) ·
  [REST API](https://docs.openolat.org/manual_admin/administration/REST_API/) ·
  [Installation/DB (Postgres empfohlen)](https://docs.openolat.org/admin-manual/installation/installGuide/) ·
  [OLAT/OpenOLAT — Wikipedia](https://en.wikipedia.org/wiki/OLAT)
- DACH-Verbreitung: [OpenOLAT @ Uni Innsbruck](https://lms.uibk.ac.at/dmz/) ·
  [@ RPTU/ZHDL](https://zhdl.rptu.de/en/ele/services-for-lecturers/openolat) ·
  [@ HWG Ludwigshafen](https://www.hwg-lu.de/en/service/openolat)
- Open edX: [SCORM XBlock (1.2+2004, AGPL)](https://github.com/overhangio/openedx-scorm-xblock) ·
  [SCORM in Open edX](https://openedx.org/blog/leveraging-scorm-in-the-open-edx-platform/) ·
  [Tutor-Deployment/Ressourcen](https://docs.tutor.edly.io/) ·
  [Hosting-Guide (Ressourcen)](https://cubite.io/blogs/open-edx-hosting)
- Sakai (Ausschluss): [Marktanteil — 6sense](https://6sense.com/tech/learning-management-systems/sakai-market-share)
- Lemon: [Lemon Systems GmbH — LMS](https://www.lemon-mobile-learning.com/en/lms/learn-managament-system/) ·
  [Features — eLearning Industry](https://elearningindustry.com/directory/elearning-software/lemon-mobile-learning-system)
