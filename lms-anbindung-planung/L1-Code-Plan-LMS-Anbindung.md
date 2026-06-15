# L1 — Code-Plan LMS-Anbindung (OpenOLAT, im `integration`-Service + Showcase)

> Konkreter Implementierungsplan auf Basis von [Konzept-LMS-Anbindung.md](Konzept-LMS-Anbindung.md).
> **Entscheidungen (User, 2026-06-15):** Plattform **OpenOLAT** (SCORM **1.2**) · eigene Postgres-DB
> **`openolat`** in der bestehenden Instanz **freigegeben** · Katalog **sofort aus MDM veröffentlichen**
> (Shop-Projektion-Muster) · dieses Dokument = **detaillierter Plan, kein Code**.
>
> **STATUS (2026-06-15):** **L0 + L1 + L2 GEBAUT & VERIFIZIERT.** OpenOLAT (Tomcat 10.1/JDK17) + DB
> `openolat` laufen; Keycloak-OIDC-Login (JIT) live; REST-API aktiv; SCORM-1.2-Seed **importiert +
> published** (`FileResource.SCORMCP`, idempotentes `lms-import-seed.sh`, §5a.1). **L1 Backend-Katalog**
> (Package `…/integration/lms`): `WbtKurs` (Schema `mdm`) + `WbtKursDto` (Stack B) + `LmsResource`
> (`/lms/kurse` CRUD, RBAC, Tag `LMS Resource`) + `WbtVendureProjektion` (`fulfillmentType=digital`,
> Vendure live verifiziert). **L2 Einschreibungs-Naht**: `Kurseinschreibung` als **eigener Outbox-
> Datensatz** (Begründung §6a) + `KurseinschreibungService` (anfordern/ausschreiben, Backoff/Dead-Letter)
> + `EnrollmentDispatcher` (@Scheduled) + `OpenolatProvisioning`/`OpenolatApi` (ensureUser **per
> `authProvider=KEYCLOAK`+sub**, enrol/unenrol — alle Endpunkte live gegen `/restapi/openapi.json`
> verifiziert) + `EinschreibungResource` (`/lms/einschreibungen`). **L3 Order-Naht** ✅:
> `POST /lms/einschreibungen/bestellung` (+ `/storno`) löst Produkt→`WbtKurs` auf (§8). **16/16
> rest-assured/Unit grün** (OpenOLAT per `@io.quarkus.test.Mock`). **Offen:** Vendure-Emitter-Plugin
> (PaymentSettled→Push), Portal „Meine Trainings" + SSO-Launch, orval-Client regen (Tag ergänzt).
> Lemon-Migration gegated (L5b). Slices §11.

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

### §5a.1 Import-Pfad — VERIFIZIERT (REST, OpenOLAT 20.1)
Der Content-Pfad ist gegen die laufende Instanz bewiesen. Reproduzierbar über das committete Skript
**`showcase/openolat/lms-import-seed.sh`** (idempotent: bereits importierte Kurse werden per
`displayname` erkannt und übersprungen):

| Endpunkt | Zweck | Hinweis |
|---|---|---|
| `PUT /restapi/repo/entries` (multipart: `file`,`filename`,`resourcename`,`displayname`) | Lernressource importieren → liefert `key` (= `openolatKey`) | ⚠️ curl `-F` erzwingt POST → **`-X PUT`** zwingend, sonst **405** |
| `POST /restapi/repo/entries/{key}/status` (`newStatus=published`) | Kurs freigeben | sonst `entryStatus=preparation` |
| `GET /restapi/repo/entries` | Liste (Idempotenz-Check) | — |

SCORM 1.2 wird korrekt als **`olatResourceTypeName = FileResource.SCORMCP`** erkannt. Beim Erstlauf
vergebene Keys (Verweis für `WbtKurs.openolatKey`): `golf-scorm12=884736`, `minimal-smoke=884737`,
`learn-git-branching=884738`. Auth = HTTP Basic `administrator` (Service-Account später, §6).
Die OpenAPI-Spec der Instanz liegt unter **`/restapi/openapi.json`** (422 Pfade — maßgebliche Quelle
für alle weiteren Pfade/Payloads statt Raten).

### §5a.2 Einschreibungs-Pfad — VERIFIZIERT (REST, L2)
Die User-/Enrol-Endpunkte sind live gegen die Instanz bewiesen (ensureUser-by-OAuth + Enrol):

| Endpunkt | Zweck | Hinweis |
|---|---|---|
| `GET /restapi/users?authProvider=KEYCLOAK&authUsername={sub}` | OpenOLAT-Identität nach Keycloak-Sub finden (idempotenter ensureUser) | Match-Schlüssel ist der **OIDC-`sub`**, nicht E-Mail/Login |
| `PUT /restapi/users` (UserVO) | User anlegen → `key` | + danach Auth anlegen |
| `PUT /restapi/users/{key}/authentications` (`provider=KEYCLOAK`, `authUsername=<sub>`) | OAuth-Auth setzen → **späterer SSO-Login verschmilzt dublettenfrei** | kritisch bei Pre-Provisioning vor Erstlogin |
| `PUT /restapi/repo/entries/{key}/participants/{identityKey}` | Einschreiben (idempotent, Re-PUT = no-op) | DELETE = Ausschreiben |

> Der SSO-JIT-User trägt genau `provider=KEYCLOAK, authUsername=<sub>` — daraus abgeleitet, dass der
> Dispatcher beim Vor-Provisionieren dieselbe Auth setzen muss (sonst Dublett beim ersten SSO-Login).

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

### §6a Umsetzungsentscheidung (L2): `Kurseinschreibung` = eigener Outbox-Datensatz
Statt den bestehenden `OutboxAuftrag` zu nutzen, **trägt `Kurseinschreibung` selbst den Outbox-Zustand**
(`status`/`versuche`/`naechsterVersuchAm`/`letzterFehler`/`erledigtAm`). Grund: `OutboxAuftrag` hat eine
**Pflicht-FK auf `mdm.anmeldung`** (`anmeldung_id NOT NULL`); ihn für WBT zu öffnen hieße, ihn polymorph
aufzuweichen — das widerspricht der FK-Disziplin (echte `@ManyToOne` bevorzugt). Die Zuverlässigkeit ist
identisch (Anforderung in **einer** TX mit dem Outbox-Zustand geschrieben; `EnrollmentDispatcher`
@Scheduled zieht fällige Zeilen mit Pessimistic-Lock, REQUIRES_NEW je Zeile, Backoff 30s→max 1h,
Dead-Letter nach 5 Versuchen → HITL). `wbtKurs` bleibt echte FK; `keycloakSub` = externe IdP-Identität
(bewusst Spalte, keine FK). Idempotenz: Unique `(wbt_kurs_id, keycloak_sub)`.

## §7 Katalog-Projektion aus MDM (Shop-Projektion-Muster)
„Veröffentlichen" eines `WbtKurs` (analog bestehender Shop-Projektion `vendureProductId`):
1. **Vendure** create/update Product (Admin-API, REST-Client + Superadmin-Login) → `vendureProductId`
   zurückschreiben; Custom-Field am Produkt: `openolatKey` (Delivery-Referenz).
2. **OpenOLAT-Key** referenzieren = der beim Import erzeugte RepositoryEntry-Key (manuell/Skript erfasst).
3. Status `VEROEFFENTLICHT`. Damit: **MDM = Katalog-SoR**, Vendure = Commerce, OpenOLAT = Delivery.
- Cockpit: „Veröffentlichen"-Button in `showcase/mdm` (RBAC `katalog-pflege`), generierte zod/Forms
  (orval — **neuen Tag `LMS Resource` in `orval.config.ts` `input.filters.tags` ergänzen**, sonst wird
  der Client nicht generiert; danach regen→typecheck→build→Image).

## §8 Provisionierungs-Naht (bezahlte Vendure-Order → Einschreibung) — ✅ GEBAUT (L3)
- **Push wie R7** (`/rechnung/quellen/bestellung`): bezahlte Order → **`POST /lms/einschreibungen/bestellung`**
  `{ vendureOrderId, keycloakSub, email?, anzeigeName?, vendureProductIds[] }`. Das Backend
  (`KurseinschreibungService.ausBestellung`) löst je `vendureProductId` den `WbtKurs` auf
  (**Nicht-WBT-Positionen + nicht importierte Kurse werden still übersprungen**) und ruft `anfordern(...)`
  → idempotent über Unique Kurs×Sub (erneuter Push = keine Dublette).
- **Storno/Refund** → **`POST /lms/einschreibungen/bestellung/{vendureOrderId}/storno`**
  (`stornoBestellung` setzt alle offenen/eingeschriebenen Zeilen der Order auf `STORNO_ANGEFORDERT`).
- **Offen:** der Vendure-seitige Emitter (Plugin auf `PaymentSettled` ruft diesen Endpunkt) — wie bei R7
  ein dünner Push (im Showcase per Skript/Test getrieben); `keycloakSub` trägt der Storefront-Kontext.

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
- ~~**Import-Endpunkt**~~ ✅ **erledigt** — `PUT /restapi/repo/entries` (multipart), SCORM-1.2-Seed
  importiert+published (§5a.1); OpenAPI unter `/restapi/openapi.json`.
- ~~**REST-Pfade/Payloads auth/user/enrol**~~ ✅ **erledigt** (L2, §5a.2): ensureUser-by-
  `authProvider=KEYCLOAK`+sub, `PUT /users`, `PUT /users/{key}/authentications`, `PUT/DELETE
  …/participants/{identityKey}` live verifiziert (Auth = HTTP Basic Admin).
- **OpenOLAT-REST-Completion-Read** (für L4 Reporting/Zertifikate) in der OpenAPI-Spec bestätigen.
- **Staff-SSO** (`ebz-staff` als zweiter OIDC-Provider) vs. lokale Author-Accounts — Config-Entscheidung.
- **Lemon-Export** der 100 Kurse als SCORM 1.2 (alle vs. nur WBTs) — fachliches Gating.
- **Zugang-bei-Storno** (sofort entziehen?) — Produktentscheidung.

## §11 Phasen / Abgrenzung
| Phase | Inhalt | Abgrenzung |
|---|---|---|
| **L0** ✅ | `openolat`-Service + DB `openolat` + Host/Proxy; Admin erreichbar | kein Fachcode |
| **L1** ✅ | OIDC-Lernende (JIT), SCORM-1.2-Kurse importiert, `WbtKurs` + Projektion nach Vendure | Portal-Launch offen; Provisionierung manuell |
| **L2** ✅ | `KurseinschreibungService` + Outbox (Entity-as-Outbox, §6a) + `EnrollmentDispatcher` + `OpenolatProvisioning` (idempotent, Retry/HITL) | Completion noch nicht |
| **L3** 🟡 | Order-Naht `POST /lms/einschreibungen/bestellung` (+Storno) ✅; Vendure-Emitter-Plugin + „Meine Trainings" offen | Portal-Teil offen |
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
