# L1 — Code-Plan LMS-Anbindung (OpenOLAT, im `integration`-Service + Showcase)

> Konkreter Implementierungsplan auf Basis von [Konzept-LMS-Anbindung.md](Konzept-LMS-Anbindung.md).
> **Entscheidungen (User, 2026-06-15):** Plattform **OpenOLAT** (SCORM **1.2**) · eigene Postgres-DB
> **`openolat`** in der bestehenden Instanz **freigegeben** · Katalog **sofort aus MDM veröffentlichen**
> (Shop-Projektion-Muster) · dieses Dokument = **detaillierter Plan, kein Code**.
>
> **STATUS: PLANUNG — nichts gebaut.** An die Lemon-Kurse kommen wir **vorerst nicht** heran; der
> Showcase-Durchstich nutzt deshalb **frei verfügbare SCORM-1.2-Seed-Kurse** aus `testdata/`
> (§5a). Die echte Lemon-Migration der 100 Kurse bleibt gegated (L5b), blockiert aber den
> Durchstich **nicht** mehr. Slice-Reihenfolge L0→L5 (§11).

## §1 Architektur-Rollen
- **OpenOLAT** = LMS/Delivery-SoR (Kurse, SCORM-1.2-Inhalte, Lern-Fortschritt). Eigenständig wie Vendure.
- **Vendure** = Commerce/Zahlungs-SoR (verkauft „Kurs-Zugang").
- **MDM / `integration` (Quarkus)** = Katalog-SoR (welcher Kurs = welches Produkt = welche OpenOLAT-
  Ressource) + **Einschreibungs-Orchestrierung** (Outbox→OpenOLAT) + Portal-Backend.
- **Keycloak** = Identität/SSO (`ebz-customers` Lernende, `ebz-staff` Autoren/Admins).
- **Naht:** bezahlte Vendure-Order → **Einschreibungs-Anforderung** → Outbox → OpenOLAT-REST.

## §2 Verortung
- Package `de.netzfactor.ebz.controlling.integration.lms` (Unterpakete `model`/`web`/`service`).
- **DB-Schema `mdm`** (DB `controlling`) für die integration-seitigen Entities (echte `@ManyToOne`-FKs,
  Integer-Cent, Enums als String-Spalten wie `bildung`).
- **OpenOLAT eigene DB `openolat`** in derselben Postgres-Instanz (eigener User) — Muster
  vendure/controlling/lightdash; **kein** Schema in `mdm`.
- Reuse: bestehendes **Outbox**-Framework (WebUntis-Sync-Muster), `quarkus-rest-client-jackson`
  (wie Vendure-Shop-Projektion-Client), `quarkus-oidc` RBAC, Panache, Stack-B-Codegen (orval).

## §3 Deployment (Docker Compose, Profil `controlling`)
- Service **`openolat`** (offizielles Image, Tomcat/JVM) + Volume `olatdata` (Kurs-/SCORM-Ablage).
- **DB-Init:** `openolat`-DB + User im Postgres-`initdb` ergänzen (wie die übrigen DBs).
- **Reverse-Proxy:** Host `lms.localhost` in nginx (wie die SPAs).
- **Env/Config** (`olat.properties` via Env): `db.*` = pgsql/host/port/`openolat`/user; Instanz-ID;
  SMTP optional; Suchindex intern (kein ES nötig).
- ⚠️ **Versionsdisziplin:** OpenOLAT-Release + JVM-Linie fixieren (Image-Tag pinnen), wie bei euch üblich.

## §4 Identität & SSO (Keycloak OIDC)
- OpenOLAT **generischer OpenID-Connect-Provider** gegen **`ebz-customers`** (Lernende):
  Issuer/Client/Secret als Config; **Just-in-time-User** beim ersten Login; Schlüssel = Keycloak
  **`sub`** (stabil), `username`/`email`/Name aus Claims.
- **Staff/Autoren (L1 pragmatisch):** lokale OpenOLAT-Admin/Author-Accounts für den Kurs-Import;
  **`ebz-staff`-OIDC** als zweiter Provider = Folgeschritt (Config-Entscheidung, §10).
- **Wichtig für die Naht:** OpenOLAT-Identity-Key ↔ Keycloak-`sub` ↔ Party-Identität müssen
  zusammenpassen, damit Einschreibung (REST) und späterer SSO-Launch **denselben** User treffen.

## §5 Datenmodell (Schema `mdm`)
Geldbeträge **Integer Cent**. Enums als String (kein DB-Check, vgl. `bildung`). Reale `@ManyToOne`-FKs.

**`WbtKurs`** — Katalog-Eintrag (SoR für „verkaufbarer Kurs"); je Lemon-Kurs einer.
`id · version · titel · beschreibung · openolatKey (RepositoryEntry-Key der importierten SCORM-Ressource) · vendureProductId? (null bis Projektion) · preisCent · aktiv · status (ENTWURF|VEROEFFENTLICHT) · scormVersion (Default "1.2")`

**`Kurseinschreibung`** — Einschreibung + Status-Cache für „Meine Trainings" & idempotente Provisionierung.
`id · version · wbtKurs (→ @ManyToOne WbtKurs) · person (→ @ManyToOne Party-Identität) · bestellkontext? (→ @ManyToOne Party-Kontext) · vendureOrderId? · status (ANGEFORDERT|EINGESCHRIEBEN|FEHLER|AUSGESCHRIEBEN) · openolatIdentityKey? · eingeschriebenAm? · completionStatus? (PASSED|COMPLETED|FAILED|…, optional L4) · completionAm?`
- **Unique** (`person`,`wbtKurs`) → kein Doppel-Einschreiben.

**Outbox-Event-Typ** `KURS_EINSCHREIBUNG` (im bestehenden Outbox-Schema): Payload
`{ kurseinschreibungId, openolatKey, keycloakSub, email, name }`.

## §5a Seed-/Testdaten (Ersatz für den Lemon-Export)
Da der Lemon-Export vorerst nicht verfügbar ist, kommt der Kurs-Content aus **frei verfügbaren
SCORM-1.2-Paketen** aus GitHub. Ablage **`testdata/scorm/`** (gitignored). Reproduzierbar über das
**committete** Fetch-Skript **`showcase/lms-fetch-testdata.sh`** (lädt + verifiziert `imsmanifest.xml`
auf SCORM 1.2):
| Ordner | Kurs | SCORM | Quelle |
|---|---|---|---|
| `golf-scorm12/` | Golf Examples „Completes On Passing Quiz" (umfänglich, mehrseitig + Quiz) | 1.2 | [`jbroadway/scorm`](https://github.com/jbroadway/scorm) `samples/` |
| `minimal-smoke/` | RuntimeMinimumCalls (kanonisches SCORM-1.2-Minimalpaket, für schnelle Import-/Dispatcher-Tests) | 1.2 | [`jbroadway/scorm`](https://github.com/jbroadway/scorm) `samples/AllGolfExamples/` |
| `learn-git-branching/` | Learn Git Branching (echter interaktiver Kurs) | 1.2 | [`andre-wojtowicz/learn-git-branching-scorm`](https://github.com/andre-wojtowicz/learn-git-branching-scorm) (Release) |

> **Lizenz/Provenienz:** Es handelt sich um öffentlich verteilte Beispiel-/Test-Pakete (Golf-Inhalt
> aus Wikipedia/Wikihow; learnGitBranching = MIT). Die Quell-Repos deklarieren **keine** explizite
> Repo-Lizenz → Nutzung **nur lokal zum Testen**, `testdata/` ist **gitignored** (keine
> Redistribution). Für eine produktive Auslieferung gelten die echten Lemon-Kurse (L5b).

## §6 Services
- **`WbtKatalogService`** — CRUD `WbtKurs`; `veroeffentliche(id)` = **MDM-Projektion** (s. §7).
- **`KurseinschreibungService`** — `anfordern(personRef, wbtKursId, vendureOrderId, kontextRef?)`:
  legt/aktualisiert `Kurseinschreibung(ANGEFORDERT)` **+ Outbox-Event** in **einer** Transaktion;
  idempotent (Unique person×Kurs). `ausschreiben(...)` = Gegen-Event (Storno).
- **`OpenolatEnrollmentDispatcher`** (Outbox-Consumer für `KURS_EINSCHREIBUNG`):
  ruft **OpenOLAT-REST** (Service-Account-Auth):
  1. **User sicherstellen** (anhand `keycloakSub`/`email`) → `openolatIdentityKey`.
  2. **Einschreiben** in Kurs/Lerngruppe der `openolatKey`-Ressource.
  → setzt `Kurseinschreibung.status=EINGESCHRIEBEN`/`FEHLER`; **Retry/Dead-Letter/HITL** wie WebUntis-
  Sync; im BPMN als `SERVICE_TASK` sichtbar. **Idempotent** (Re-Enrol = no-op).
- **`OpenolatRestClient`** (`quarkus-rest-client-jackson`) — Auth (`/restapi/auth` Token bzw. Basic),
  `ensureUser`, `enrol`, `unenrol`, *(L4)* `readCompletion`. ⚠️ Exakte Endpunkt-Pfade/Payloads gegen
  die **OpenOLAT-REST-OpenAPI** verifizieren (Doku bestätigt User-/Gruppen-/Kurs-Management +
  „für Remote-Management gebaut"; Completion-Read = offener Verifikationspunkt, §10).
- **`PortalLmsService`** — liefert die `Kurseinschreibung`en des eingeloggten Kunden (kontext-skopiert
  wie die Rechnungen) für „Meine Trainings" + baut den **SSO-Launch-Deeplink** zur `openolatKey`.

## §7 Katalog-Projektion aus MDM (Shop-Projektion-Muster)
„Veröffentlichen" eines `WbtKurs` (analog bestehender Shop-Projektion `vendureProductId`):
1. **Vendure** create/update Product (Admin-API, REST-Client + Superadmin-Login) → `vendureProductId`
   zurückschreiben; Custom-Field am Produkt: `openolatKey` (Delivery-Referenz).
2. **OpenOLAT-Key** referenzieren = der beim Import erzeugte RepositoryEntry-Key (manuell/Skript erfasst).
3. Status `VEROEFFENTLICHT`. Damit: **MDM = Katalog-SoR**, Vendure = Commerce, OpenOLAT = Delivery.
- Cockpit: „Veröffentlichen"-Button in `showcase/mdm` (RBAC `katalog-pflege`), generierte zod/Forms
  (orval — **neuen Tag `LMS Resource` in `orval.config.ts` `input.filters.tags` ergänzen**, sonst wird
  der Client nicht generiert; danach regen→typecheck→build→Image).

## §8 Provisionierungs-Naht (bezahlte Vendure-Order → Einschreibung)
- **Trigger:** bezahlte Order mit Kurs-Produkt. Umsetzung wie der Rechnungs-/Order-Pfad (R7-Muster):
  Vendure-Event/Fulfillment → interner Endpoint `POST /lms/einschreibungen` (Service-Auth) mit
  `{ keycloakSub|email, vendureOrderId, openolatKeys[] }` → `KurseinschreibungService.anfordern(...)`.
- Mapping Produkt→Kurs über `WbtKurs.vendureProductId`/`openolatKey`.
- **Storno/Refund** → `ausschreiben(...)` (Gegen-Event), optional konfigurierbar (Zugang sofort entziehen ja/nein).

## §9 REST-Endpunkte
**Katalog/Pflege `/lms` (RBAC `katalog-pflege`):**
- `GET/POST /lms/kurse`, `GET/PUT/DELETE /lms/kurse/{id}`
- `POST /lms/kurse/{id}/veroeffentlichen` → Vendure-Projektion (vendureProductId zurück)

**Provisionierung (intern, Service-Auth):**
- `POST /lms/einschreibungen` `{keycloakSub|email, vendureOrderId, openolatKeys[]}`
- `POST /lms/einschreibungen/{id}/ausschreiben`

**Portal (Realm `ebz-customers`, kontext-skopiert wie Rechnung-Portal):**
- `GET /lms/portal/meine-trainings` → Liste `{kurs, status, completion?, launchUrl}`
- `GET /lms/portal/kurse/{id}/launch` → 302 SSO-Deeplink in OpenOLAT
- DTO + Bean-Validation als Single Source (Stack B) → erscheint im `/q/openapi` (Tag `LMS Resource`).

## §10 Offene Verifikations-/Config-Punkte (nicht L0-blockierend)
- **OpenOLAT-REST-Completion-Read** (für L4 Reporting/Zertifikate) in der OpenAPI-Spec bestätigen.
- **Exakte REST-Pfade/Payloads** (auth/user/enrol) gegen die OpenOLAT-Version verifizieren.
- **Staff-SSO** (`ebz-staff` als zweiter OIDC-Provider) vs. lokale Author-Accounts — Config-Entscheidung.
- **Lemon-Export** der 100 Kurse als SCORM 1.2 (alle vs. nur WBTs) — fachliches Gating.
- **Zugang-bei-Storno** (sofort entziehen?) — Produktentscheidung.

## §11 Phasen / Abgrenzung
| Phase | Inhalt | Abgrenzung |
|---|---|---|
| **L0** | `openolat`-Service + DB `openolat` + Host/Proxy; Admin erreichbar | kein Fachcode |
| **L1** | OIDC-Lernende (JIT), 1 SCORM-1.2-Kurs importiert, `WbtKurs` + Projektion nach Vendure, Portal-Launch | Provisionierung noch manuell |
| **L2** | `KurseinschreibungService` + Outbox `KURS_EINSCHREIBUNG` + `OpenolatEnrollmentDispatcher` (idempotent, Retry/HITL) | Completion noch nicht |
| **L3** | Provisionierungs-Naht Vendure-Order→Einschreibung; „Meine Trainings" produktiv | — |
| **L4** | Completion-Read + Zertifikate (OpenOLAT-Zertifikatsmodul) | — |
| **L5a** | **Seed-Import** der 3 SCORM-1.2-Kurse aus `testdata/` end-to-end (Import → `WbtKurs` → Projektion → Kauf → Einschreibung → Launch) | **jetzt machbar, kein Lemon nötig** |
| **L5b** | Bulk-Migration der echten 100 Kurse (nach Lemon-Export) | **gegated** (Lemon-Zugang/Export) |

## §12 Teststrategie (rest-assured + Unit, Muster wie `bildung`/`rechnung`)
- **Katalog:** `WbtKurs` anlegen → `veroeffentlichen` → `vendureProductId` gesetzt (Live-Vendure/Mock).
- **Provisionierung:** `anfordern` legt `Kurseinschreibung(ANGEFORDERT)` + genau **ein** Outbox-Event;
  Dispatcher gegen **OpenOLAT-Mock** → `EINGESCHRIEBEN`; **idempotent** (zweimal = ein Enrol).
- **Fehlerpfad:** OpenOLAT-Down → `FEHLER` + Retry/Dead-Letter (wie WebUntis).
- **Portal:** `meine-trainings` nur eigene/kontext-skopierte Einschreibungen (Fremdzugriff → leer/403);
  `launch` liefert gültigen Deeplink.
- **RBAC:** Pflege-Ops ohne `katalog-pflege` → 403; Portal-Ops nur `ebz-customers`.
- **Live (nach L1):** echter SCORM-1.2-Kurs, Login `customer`, Kurs starten, Fortschritt in OpenOLAT.
