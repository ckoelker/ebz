# Kommunikations-System auf dem EBZ-Party-Kern

## Context
Kommunikation ist heute zersplittert: MS Teams (Berufsschüler), E-Mail (Seminar-TN), OpenEduCat (Studierende), dazu verstreute, einseitige `quarkus-mailer`-Aufrufe (Einladung, Anmeldebestätigung, Rechnungsversand). Es fehlt **ein** identitäts-verankerter, DSGVO-konformer Kommunikationskanal. Ziel: **ein** hauseigenes Kommunikations-System auf dem bestehenden **Party-Kern** (Person/Mitgliedschaft/Kontaktpunkt/Einwilligung), das vier Richtungen abdeckt:
1. **System → Person** — Benachrichtigungen aus Domänen-Events (Anmeldung bestätigt, Rechnung, Einschreibung, Note).
2. **Person ↔ Person** — Direktnachrichten (Community: Studierende/TN untereinander, TN↔Dozent).
3. **Administration ↔ Person** — zweiseitige Vorgangs-Kommunikation (Prüfungsamt/Support ↔ Person).
4. **Person → Personengruppe** — Broadcast/Verteiler (Kohorte/Berufsschulklasse/Seminar-TN).

**Entscheidung: nativ in Quarkus/Postgres bauen** (eure „selber machen"-Linie). Nur so greifen Party-Identität (`keycloakSub`), Kanäle (`Kontaktpunkt`) und Einwilligung/`werbesperre` durchgängig und docken an Domänen-Events an. *Alternative verworfen:* Mattermost/Discourse = eigenes Identitäts-/Consent-Silo, keine Event-/DSGVO-Kopplung.

## Getroffene Entscheidungen (Dialog 2026-06-19)
- **Reifegrad:** Showcase-Demonstrator (lauffähig/demonstrierbar, schlank, keine Hochlast-/Skalierungsthemen).
- **Scope:** **alle 4 Richtungen** (System→Person, Admin↔Person, Person→Gruppe, Person↔Person).
- **AI-Agenten = strategischer Kern** → Agenten als erstklassige Teilnehmer von Anfang an (Typ AGENT, EU-AI-Act-Kennzeichnung, A2A/MCP/HITL prägen das Modell sofort).
- **Audio/Video:** später, eigene Stufe.
- **Notification-Engine:** Eigenbau auf Quarkus/Postgres, Novus Muster (Preference/Template/Digest/Inbox-Widget) nachbauen statt Produkt adoptieren (Matrix 20:0 → 13:7 für Eigenbau; Killer: Novu deckt nur 1/4 Richtungen ab + Node/Mongo/Redis-Silo).
- **Echtzeit-Transport:** **SSE** für System→Person-Benachrichtigungen + **WebSocket** (`quarkus-websockets-next`) für interaktive Chats (Admin/Direkt/Gruppe/Agent).
- **Modell F28 = JA:** SYSTEM-Benachrichtigungen sind **nur `PersonEreignis`** (+opt. `Zustellung`); `Konversation` nur für echte Threads.
- **Agenten-Reichweite:** intern via `quarkus-langchain4j`; **MCP nur als kleines Zukunfts-Beispiel** (Log/Inbox-Tool, demonstrativ), **kein A2A** jetzt.
- **HITL:** kategorieabhängig — Pflicht-Freigabe nur bei sensiblen Kategorien (Prüfung/Vertrag/Geld), sonst autonome Agenten-Antwort (mit KI-Kennzeichnung).
- **Agenten-Use-Cases zuerst:** (1) Co-Pilot/Antwortvorschläge für Sachbearbeiter (HITL), (2) FAQ-/Studienberatungs-Bot (autonom + KI-Kennzeichnung), (3) MCP-Demo (Log/Inbox-Tool, RBAC-gefiltert, Zukunfts-Showcase).
- **Komfort:** Preference **basic** (Kanal an/aus) in K1; Preference-Center (Kanal×Kategorie), Digest-Bündelung, Quiet-Hours/Rate-Limit in **K1b** (s. Reihenfolge-Verfeinerung).
- **Kanäle:** Portal-Inbox + **E-Mail + SMS + MS Teams + generischer Webhook** (KanalVersand-Adapter-Muster wie WebUntis/Suite8). Teams/Webhook = Brücke zur heutigen Berufsschüler-/Drittsystem-Kommunikation.
- **Quittierung:** Felder in K1 (`bestaetigungErforderlich`/`bestaetigtAm`+Nachweis + Button), Durchsetzungs-Workflow in **K5**.
- **Bestands-Mails:** schrittweise auf die Event-Spine migrieren (je Schritt grüne Tests, kein Big-Bang).
- **Demo/Seed:** auf Bestandsdaten aufsetzen (Carla Kundin / Berufsschul-Anmeldung) — System-Benachrichtigung + Admin↔Carla-Thread + Kohorte-Broadcast.
- **UI-Verortung:** in Bestand integrieren — Person-Seite ins Portal (neben MeineRechnungen/MeineAzubis), Admin ins mdm-Cockpit (kein neues SPA).
- **Reihenfolge:** **K0 gemeinsames Fundament zuerst** (Event-Spine + Datenmodell + KanalVersand-Adapter + RBAC), dann **System→Person** als erste Richtung, danach Admin↔Person → Gruppe → Person↔Person → K5.
- **Reihenfolge-Verfeinerung (Plan-Review 2026-06-19):** **K0 schlank** (nur PORTAL+E-Mail, keine Agenten-Funktion/WS/SMS/Teams/Webhook/MCP); **Kanäle inkrementell** (PORTAL+E-Mail zuerst, Rest via Port nachziehen); **SSE in K1, WebSocket erst K2** (erster Chat); **Komfort gesplittet**: Preference basic in K1, **Digest/Quiet-Hours → K1b**. **Event-Quellen vorerst nur Bestand** (Anmeldung/Rechnung/Einschreibung); Prüfungs-/Note-Events erst mit dem (pausierten) Prüfungsmodul. **Template-Registry** gehört in K1.

## Wiederverwendung (Bestand)
- `party/model/Person.java` (`keycloakSub`, `werbesperre`/`auskunftssperre`, `briefanrede()`), `Mitarbeiter` (Staff), `Mitgliedschaft` (Person×Organisation N:M), `Kontaktpunkt` (EMAIL/TELEFON, `primaer`, E.164), `Einwilligung` (Kanal EMAIL/TELEFON/POST/SMS, Zweck, Status, Rechtsgrundlage), `Aktivitaet`(+`Anhang` in MinIO) als CRM-Kontakthistorie, `service/PartyHoheitService.primaerEmail`.
- `quarkus-mailer` → Mailpit/MockMailbox (Muster: `EinladungsService`, `AnmeldungWorkflowService`, `RechnungVersandService`).
- `outbox/service/OutboxService` (transaktionale Outbox) für zuverlässigen async Kanal-Versand.
- Keycloak-Realms `ebz-staff`/`ebz-customers`; `@Authenticated`/`@RolesAllowed`; **kontext-skopierte Autorisierung** (`PortalResource`: sub→Person, 403 cross-tenant).
- Portal-SPA (Vue, oidc-client-ts, **orval**) für Person-Seite; mdm-SPA (**Stack B**: vee-validate/zod/PrimeVue) für Admin-Cockpit.
- `bildung.Bildungsangebot` + `rechnung.Anmeldung` (Einschreibung) → abgeleitete Gruppen (Kohorte/Klasse/TN-Liste). MinIO für Anhänge.

## Datenmodell — neues Package `kommunikation`, Tabellen im `mdm`-Schema, echte @ManyToOne-FKs
- **Konversation** (Thread): `typ` (ADMIN/DIREKT/GRUPPE/**VIDEO**), `betreff`, optionale Kontext-FKs (`bildungsangebot`/`anmeldung`/`rechnung` — „betrifft Ihre Prüfung/Rechnung"), `status` (OFFEN/GESCHLOSSEN), `erstelltAm`. **Entscheidung (F28): SYSTEM-Benachrichtigungen sind KEINE Konversation**, sondern nur `PersonEreignis` (+opt. `Zustellung`); `Konversation` nur für echte Threads.
- **KonversationsTeilnehmer**: `konversation` FK + (`person` XOR `mitarbeiter` XOR `agent`), `rolle` (ABSENDER/EMPFAENGER/ADMIN), `gelesenBis`, `stumm`. *Teilnehmer-Typ **AGENT** (C11) für KI-Assistenten.*
- **Nachricht**: `konversation` FK, Absender (`person` XOR `mitarbeiter` XOR `agent`), `inhaltHtml`, `zeitpunkt`, **`kiGeneriert`** (boolean → EU-AI-Act-Art.-50-Kennzeichnung, sichtbar in der UI); **NachrichtAnhang** (MinIO-Metadaten, static nested wie `Aktivitaet.Anhang`; auch Meeting-Recording/Transkript).
- **Personengruppe**: `name`, `beschreibung`, `quelle` (MANUELL/BILDUNGSANGEBOT/ORGANISATION), optional `bildungsangebot`/`organisation` FK. **GruppenMitglied** (nur MANUELL): `gruppe`+`person` FK. Abgeleitete Gruppen lösen Mitglieder **zum Sendezeitpunkt** auf (Query auf Anmeldung/Mitgliedschaft).
- **PersonEreignis** (personenseitiger **Aktivitätslog**, Read-Model): `empfaengerPerson` FK, `kategorie`, `betreff`, optional `nachricht` FK, Kontext-FKs (bildungsangebot/anmeldung/rechnung), `zeitpunkt`, `prozessFall`, **`sichtbar`** (pro Event-Typ deklariert → interne Vermerke bleiben unsichtbar). Basis-Projektion **jedes** person-relevanten Domänen-Events (append-only, Pull-Zeitstrahl im Portal). *Benachrichtigung = PersonEreignis + Zustellung.*
  - **Quittierung/Pflicht-Bestätigung — Felder JETZT (K1), Workflow später:** `bestaetigungErforderlich` (boolean, pro Event-Typ) + `bestaetigtAm` nullable + **Nachweis-Trio** `bestaetigtVon`/`nachweisIp`/`nachweisZeit` (analog Double-Opt-In auf `Einwilligung`). Endpunkt `bestaetige(ereignisId)` + UI-Button „zur Kenntnis genommen". *Nachgelagert (eigene Stufe):* Fristen, Erinnerungen/Eskalation, blockierende Gates, Bestätigungs-Reporting (Scheduler/Outbox).
- **Zustellung** (je Empfänger × Kanal, nur die **Push**-Teilmenge): `personEreignis` FK, `kanal` (PORTAL/EMAIL/SMS), `status` (NEU/ZUGESTELLT/GELESEN/FEHLER), `zeitpunkt`, **`gelesenAm`** (Lese-Zeitstempel, PORTAL-Ansicht setzt ihn → Kerndatum, daher direkt in K1). **PORTAL-Badge immer** bei Push; EMAIL/SMS nur bei Consent/Präferenz, Versand über Outbox. Reine Log-Einträge (kein Push) haben kein `Zustellung`.
- **DSGVO**: transaktional/Vertrag (`Rechtsgrundlage VERTRAG_6_1_B`) umgeht Marketing-Opt-In; Marketing/Broadcast prüft `Einwilligung` + `Person.werbesperre`; `auskunftssperre` gewinnt immer (Schlanke Bündelung der Enums/DTOs).

## Backend (`de.netzfactor.ebz.controlling.integration.kommunikation`)
- `model/` — obige Entities (lean, nested Enums; MinIO-Anhang als static nested).
- `service/KommunikationService` — `sendeDirekt`, `sendeAnGruppe`, `eroeffneVorgang`/`antworte`, `markiereGelesen`; Fan-out → `Zustellung` je Empfänger (PORTAL + optional EMAIL via `Mailer` / SMS via Outbox).
- `service/BenachrichtigungService` — Domänen-Event → **`PersonEreignis`** (Log) + optional `Zustellung` (Push; **keine** SYSTEM-Konversation, F28); **CDI-`@Observes`** auf bestehende Ereignisse; die heutigen Ad-hoc-Mailer-Aufrufe (Einladung/Anmeldung/Rechnung) **hier durchleiten** → erscheinen künftig in Portal-Inbox/Zeitstrahl **und** E-Mail.
- `service/GruppenService` — CRUD manuelle Gruppen; Auflösung abgeleiteter Gruppen (Anmeldung/Mitgliedschaft).
- `web/KommunikationResource` (Person, `@Authenticated`, sub→Person, kontext-skopiert wie `PortalResource`): Konversationen listen/lesen, senden/antworten, gelesen-markieren, postbare Gruppen.
- `web/AdminKommunikationResource` (`ebz-staff`, `@RolesAllowed`): Admin↔Person-Vorgänge, Gruppen-Pflege, Broadcast.
- Kanal-Versand (EMAIL/SMS) über **Outbox** (Retry/Dead-Letter wiederverwenden).

## Frontend
- **Portal** (Vue): **K1** „Meine Aktivitäten"-Zeitstrahl (`PersonEreignis`) + Ungelesen-Badge + Quittierungs-Button (noch keine Threads); **ab K2** „Nachrichten"-Inbox (Konversationsliste, Thread, Verfassen) via WebSocket; orval-Client (neue Tags in `input.filters.tags` ergänzen).
- **mdm** (Stack-B-Cockpit): Admin-Inbox + Personengruppen-Pflege + Broadcast-Maske (vee-validate/generierte zod). Lösch-Aktionen mit Bestätigung.

## Tracing vs. System→Person — gemeinsame Event-Spine (Best Practice)
Beobachtung: System→Person-Benachrichtigung überschneidet sich mit dem Tracing — aber nur im **Auslöser**, nicht in der Verantwortung. Lösung: **drei parallele Projektionen aus EINEM Domänen-Event**, korreliert über die Fall-/Trace-Id, nicht gekoppelt.

| Ebene | SoR? | Haltbarkeit | Bei euch |
|---|---|---|---|
| Observability (Span) | nein | gesampelt/TTL | `prozessdoku/Prozessspur.schritt()` → OTel/Jaeger/Phoenix, BPMN via PM4py |
| **Domänen-Event** (Geschäftsfakt) | **ja** | dauerhaft | heute imperativ → **explizit machen** (CDI-Event) |
| **Aktivitätslog (personenseitig)** | Read-Model-Projektion | dauerhaft, append-only | neu `PersonEreignis` (Pull-Zeitstrahl im Portal) — die *offengelegte* Schwester des Traces |
| Benachrichtigung (System→Person) | Push-Teilmenge des Logs + Zustellnachweis | darf nicht verloren gehen | neu `Zustellung` + Outbox |
| Audit | ja | unveränderlich | Hibernate Envers (`@Audited`) |

**Prinzipien:**
1. **Domänen-Event als einziger Auslöser.** Heutige Inline-Paare `mailer.send(...)` + `prozess.schritt(...MESSAGE...)` (z. B. `EinladungsService`) ersetzen durch **ein** in-Transaktion CDI-Event (`AnmeldungBestaetigt`/`EinladungVersendet`/`RechnungVersandt`/`NoteErfasst`).
2. **Fan-out via `@Observes`:** Observer A → `Prozessspur` (Span); Observer B → `Nachricht`+`Zustellung(PORTAL)` in **derselben Tx** (BEFORE_COMPLETION) + **Outbox-Enqueue** für EMAIL/SMS. Verallgemeinert das vorhandene Outbox-Muster (Drittsystem-Provisionierung → Benachrichtigungskanäle).
3. **Haltbarkeitsgrenze = Transactional Outbox, nicht der Trace.** „Zugestellt?" in `Zustellung.status` + `OutboxAuftrag` (Retry/Backoff/Dead-Letter/HITL). PORTAL-Inbox-Kopie in der Geschäfts-Tx → unverlierbar.
4. **Korrelieren statt koppeln.** Gleiche `prozess.fall`/trace_id auf `Nachricht`/`Zustellung` mitführen (wie `OutboxAuftrag.prozessFall` aus dem W3C-Baggage) → Support springt Benachrichtigung↔Trace/BPMN.
5. **Nie Geschäfts-Benachrichtigungen im Tracing-Backend speichern** (Jaeger/Phoenix = kein SoR; Sampling/Retention). Inbox = Postgres `mdm.zustellung`.
6. **Ein Event-Katalog, zwei Projektionen:** `Prozess.Phase/Typ/Verfahren` (Observability) **und** Benachrichtigungs-Template+Publikum+Rechtsgrundlage aus demselben Event ableiten — keine divergierenden Listen.
7. **Idempotenz** wie in der Outbox (`idempotenzSchluessel`) → kein Doppelversand bei Retry.
8. **Log (Pull) vs. Benachrichtigung (Push) aus derselben Quelle:** jedes person-relevante Event → `PersonEreignis` (Aktivitätslog, immer wenn `sichtbar`); nur die Push-Teilmenge bekommt zusätzlich `Zustellung`(Kanal). `Aktivitaet` bleibt staff-internes CRM-Log; `PersonEreignis` ist die person-offengelegte Projektion (Art.-15-Transparenz, kuratiert).

→ Konsequenz fürs Backend: `BenachrichtigungService` ist ein **`@Observes`-Consumer** der Domänen-Events, kein zweiter imperativer Pfad; er schreibt `PersonEreignis` (Log) und entscheidet je Event-Typ über `Zustellung` (Push); Kanal-Versand reused `OutboxService`.

## Trends 2026 (recherchiert) & offene Entscheidungspunkte
**Echtzeit-Transport:** **SSE ab K1** (System→Person-Feed), **WebSocket** (`quarkus-websockets-next`) **ab K2** (erster echter Chat; Presence/„tippt gerade" optional ab K4). **Gotcha Auth:** Browser-`EventSource` kann keine Auth-Header → Token via Cookie/Query; WS-Handshake-Token separat lösen. WS skaliert „sticky" → bei >1 Instanz Backplane (`LISTEN/NOTIFY`/Redis); Read-Receipts batchen (1× letzte gelesene).
**Notification-Infra:** Fan-out-Kern selbst (Party/Consent-Kopplung), aber Muster von **Novu** (OSS, self-hosted) / Knock übernehmen: **Preference-Center** (Kanal×Kategorie je Person), **Digest/Bündelung**, **Quiet Hours/Rate-Limit**, versionierte **Template-Registry** (Cockpit-Vorschau) statt hartkodierter Strings.
**AI-Agenten:** Teilnehmer-Typ **AGENT** (intern via `quarkus-langchain4j`; optional **A2A**-fähig — Google→Linux Foundation, SSE/JSON-RPC/Agent-Cards; **MCP-Server** fürs Aktivitätslog mit RBAC/DSGVO-Filter). **HITL**-Freigabe offizieller Agenten-Antworten. Agent = eigener Keycloak-Service-Account, im Audit markiert; LLM-Spans (Phoenix) mit Konversation korreliert.
**EU AI Act Art. 50 (ab 2.8.2026):** Pflicht-**Kennzeichnung „KI-generiert"** sobald Agenten antworten (Modell-Flag + sichtbare UI; Bußgeld bis 15 Mio €/3 %).
**Audio/Video/Meetings (OFFENE Entscheidung — beide Apache-2.0, self-hostbar):**
- **Jitsi Meet** = fertige UI + **iframe-Embed** = schnellster Weg für *menschliche* Meetings; Preis: Keycloak-SSO braucht **Middleware-Adapter** (`jitsi-keycloak`/Nordeck). → vernünftiger Default, wenn nur menschliche Meetings.
- **LiveKit** = **SDK-first (UI selbst bauen)**; Mehrwert = **AI-Agent-Framework** (`livekit/agents`, Voice/Video); JWT mappt sauber auf Keycloak. → nur wenn AI-AV-Agenten Scope; ggf. gesetzt, da hausweit bereits im Einsatz.
- Meeting als Konversations-Typ **VIDEO** (Einladung/Link in Inbox + Log-Eintrag); **Recording/Transkript** (Whisper) → `NachrichtAnhang`/Log **nur mit expliziter Einwilligung**. Abgrenzung zu OpenOLAT/Teams klären.
**Datenmodell-Optimierungen:** (a) **`EreignisTyp`-Registry** als Single Source (sichtbar/push-default/Rechtsgrundlage/Template/`bestaetigungErforderlich`) treibt Trace **und** Benachrichtigung; (b) SYSTEM = **nur `PersonEreignis`**; (c) **Dedupe-Schlüssel** je Event; (d) `Zielsystemexport`→**`KanalVersand`**-Adapter.
**DSGVO-Fallstricke:** Event→Rechtsgrundlage-Matrix auditieren; Retention/Anonymisierung von Nachrichten/Log; `sichtbar` als **Allowlist** (Default false); **HTML-Sanitizing** von `inhaltHtml` (XSS), Anhang-Limit+Virenscan.

## Modularisierung & Schnittstellen (Best Practice — gegen den „Moloch")
Logisch in **Bounded Sub-Contexts** schneiden, physisch In-Process-Monolith (Showcase). Vorbild: Quarkus/Spring-Modulith + Ports&Adapters + ACL; durchgesetzt per ArchUnit-Fitness-Functions.

**Teilbereiche (je 1 Verantwortung + Port):** 1 Event-Ingest (ACL, `@Observes` fremde Events→`EreignisTyp`) · 2 `EreignisTyp`-Registry (Contract) · 3 Aktivitätslog (`PersonEreignis`) · 4 Orchestrierung/Routing (Consent+Preference+Template+Fan-out+Dedupe+Digest/Quiet-Hours) · 5 Präferenzen+Consent (`ErreichbarkeitPort`, ACL→party) · 6 Templates (`TemplatePort`, Qute) · 7 Kanäle/Zustellung (**`KanalVersand`-SPI** + Adapter PORTAL/E-Mail/SMS/Teams/Webhook) · 8 Konversation/Threads · 9 Personengruppen (`EmpfaengerAufloesung`, ACL→bildung/party) · 10 Echtzeit-Gateway (SSE+WS, `RealtimePort`) · 11 Agenten (`AgentPort`, langchain4j/HITL/MCP) · 12 Identität (`IdentitaetsPort`, ACL→party/keycloak).

**Schnittstellen, jetzt setzen:**
- **Eine published Fassade** `KommunikationApi` = das Einzige, was andere Module/Web aufrufen; Rest `internal`.
- **Inbound nur über Domänen-Events (ACL):** Module feuern eigene CDI-Events; Mapping-Adapter liegt in `kommunikation` → kein Compile-Coupling auf `rechnung`/`bildung`-Interna.
- **Outbound-Ports (SPI):** `KanalVersand`, `TemplatePort`, `ErreichbarkeitPort`, `EmpfaengerAufloesung`, `IdentitaetsPort`, `RealtimePort`, `AgentPort`, `DispatchPort` → neue Kanäle/Agenten = neuer Adapter ohne Kern-Eingriff (euer `Zielsystemexport`-Muster).

**Kapselung/Durchsetzung:** Package-by-feature `kommunikation/{api,domain,application,adapter/{ingest,kanal,realtime,agent},infra}` (nur `api` exportiert); **ArchUnit-Fitness-Functions neu einführen** (kein Import von `*.internal`, party nur via `*.api`/Event-DTO, keine Zyklen, Adapter nur am Port; läuft in `mvn test`); DTOs an Grenzen (keine Entities); Tabellen in `mdm`, aber nur von `kommunikation` beschrieben. **Split-Readiness:** Ports erlauben spätere Herauslösung als Dienst, ohne es jetzt zu tun.

**Bewusst NICHT im Showcase:** kein Microservice-Split, kein Kafka, keine getrennte DB, Ports = In-Process-Interfaces.

### Produktions-Schnitt (Microservices) & Split-Readiness
Produktions-Zielbild (Standardmuster: API-GW → Core (Outbox+CDC) → Broker → Channel-Worker → Vendoren; separater Realtime-Tier). **4–5 echte Services** (nicht 12 → keine Nano-Service-Falle):
- **1 Communication-Core (SoR):** Konversation/Threads + Aktivitätslog + Orchestrierung + EreignisTyp + Gruppen + **Präferenzen/Consent + Templates bleiben hier**. Eigene Postgres.
- **2 Channel-Dispatch (+ Worker je Kanal):** unabhängige Skalierung + **Vendor-Isolation**; stateless, idempotent, Topic-konsumierend.
- **3 Realtime-Gateway:** SSE/WS + Presence; sticky LB, Connection-Registry, eigene Skalierungskurve.
- **4 AI/Agent-Service:** langchain4j + HITL + **MCP-Server**; LLM-Latenz/Secrets/Footprint isoliert.
- **5 Video/Meeting (später):** LiveKit/Jitsi-SFU = eigene Infra; Recording→S3.

**Geteilte Prod-Infra:** DB-per-Service; API-GW+Keycloak am Edge; MinIO/S3.

**Kafka/Redis/CDC sind NICHT zwingend** — Default für Hyperscale, je mit Postgres-nativer Alternative; Einsatz nur ab Schwellen:
- **Broker:** nötig erst bei echtem Split + Replay/sehr hohem Durchsatz. Sonst **Outbox-als-Queue** (`SELECT … FOR UPDATE SKIP LOCKED`, ggf. `pgmq`) + `LISTEN/NOTIFY`. Konsistent mit „KEIN Kafka"-Entscheidung beim Outbox. Leichter als Kafka: NATS/RabbitMQ.
- **Redis:** nur wenn **Realtime-Tier mehrinstanzig** (≥2 WS-Instanzen). Eine Instanz trägt 100k+ Verbindungen → bei EBZ-Größe genügt eine; sonst Backplane via `LISTEN/NOTIFY`.
- **CDC (Debezium):** nur als Begleiter von Kafka; ohne Kafka gegenstandslos — der vorhandene **Polling-Publisher** reicht.
- **Was Produktion wirklich braucht:** durable async (Outbox ✓), HA (≥2 stateless App-Instanzen hinter LB), horizontale Skalierung wo Last entsteht — alles mit **Quarkus+Postgres** erreichbar.

**Jetzt im Monolith bauen, damit der Split billig wird:** (1) die 8 Ports konsequent; (2) **Domänen-Events als serialisierbare DTOs** (keine Entity-Refs; Gegenbeispiel: heutige `Anmeldung`-gebundene `OutboxAuftrag`); (3) `KanalVersand`-SPI = spätere Channel-Worker (stateless+idempotent); (4) `RealtimePort` kapselt SSE → später Gateway+Redis; (5) Idempotenz-/Dedupe-Keys überall; (6) logisch DB-per-Context schon jetzt; (7) Agent/MCP als Prozessgrenze denken. → Strangler-Split = Adapter-Austausch, kein Rewrite. Broker/Redis/CDC **nur ab Schwelle** einschieben.

## Phasen (festgelegt — optimierte Reihenfolge)
- **K0 — Fundament (schlank):** Package `kommunikation`; Domänen-Events als **serialisierbare DTOs** (CDI), `EreignisTyp`-Registry; Kern-Entities (`PersonEreignis`/`Zustellung`/`Konversation`/`Nachricht`(+Anhang)/`Personengruppe`) inkl. **Agenten-Modellfelder** (Teilnehmer-Typ AGENT, `kiGeneriert`) — **aber keine Agenten-Funktion**; die **8 Ports** als Interfaces; **nur PORTAL+E-Mail**-`KanalVersand`-Adapter; eigene **`ZustellAuftrag`**-Outbox-Abstraktion (Dispatcher-Mechanik aus `OutboxService` herausgelöst); RBAC (sub→Person); **ArchUnit-Fitness-Functions**; Demo-Seed auf Bestandsdaten. **Kein** WS/SMS/Teams/Webhook/MCP.
- **K1 — System→Person (Log + Benachrichtigung):** `BenachrichtigungService` (`@Observes`) auf **Bestands-Events** (Anmeldung/Rechnung/Einschreibung; Prüfungs-/Note-Events erst wenn das Prüfungsmodul existiert) → `PersonEreignis` (Log) + push-fähige `Zustellung` (PORTAL/E-Mail); **Template-Registry** (Qute); **Preference basic** (Kanal an/aus); **SSE**-Feed; Quittierungs-Felder + Button; Bestands-Mails schrittweise durchleiten. Portal: **„Meine Aktivitäten"-Zeitstrahl** + Ungelesen-Badge (noch **keine** Thread-Inbox).
- **K1b — Komfort:** Digest-Bündelung + Quiet-Hours/Rate-Limit (Scheduler/Deferred-Send); Preference verfeinern (Kanal×Kategorie).
- **K2 — Admin↔Person (Vorgänge) + Co-Pilot-Agent:** erste echte Threads → **WebSocket** (`quarkus-websockets-next`) + **Cross-Realm** (ebz-staff↔ebz-customers, OIDC-Multi-Tenant); Admin-Cockpit + Portal-**„Nachrichten"-Inbox**; Kontext-FK auf Anmeldung/Rechnung; Admin-Antwort schreibt zusätzlich `Aktivitaet` (kein Doppelsystem). **Agent-Use-Case 1:** Co-Pilot entwirft Antworten, Mitarbeiter gibt frei (HITL).
- **K3 — Person→Gruppe (Broadcast):** Personengruppe (manuell + abgeleitet), Consent/`werbesperre`-Durchsetzung, Fan-out; **weitere Kanäle (SMS/Teams/Webhook)** über den `KanalVersand`-Port dort nachziehen, wo gebraucht.
- **K4 — Person↔Person + FAQ-Agent:** Peer-Messaging (WS); **Agent-Use-Case 2:** FAQ-/Studienberatungs-Bot (autonom, KI-Kennzeichnung); **MCP-Demo**-Tool. Moderation optional.
- **K5 — Pflicht-Bestätigungs-Workflow:** auf den K1-Quittierungs-Feldern — Fristen, Erinnerungen/Eskalation (Scheduler), blockierende Gates, Cockpit-Reporting. *Video als spätere eigene Stufe.*

## Verifikation
- rest-assured: senden/antworten/gelesen; kontext-skopierte Authz (403 cross-tenant); Consent/`werbesperre` (Marketing blockiert, transaktional zugestellt); Gruppen-Fan-out löst abgeleitete Mitglieder auf; `MockMailbox` prüft E-Mail-Fan-out; Outbox-Dispatch + Retry.
- Portal-Playwright: Login `customer/customer` (Carla Kundin) → „Nachrichten" zeigt geseedete System-Benachrichtigung + Admin-Thread; antworten; Ungelesen-Badge.
- Idempotenter Demo-Seeder (Muster `PartyDemoSeeder`): eine SYSTEM-Benachrichtigung, ein Admin↔Carla-Thread, eine Studiengang-Kohorte-Gruppe + Broadcast.

## Risiken / Gotchas
- DSGVO: Marketing vs. transaktional (Rechtsgrundlage); `werbesperre`/`auskunftssperre` gewinnen immer.
- Neue Tabellen **nur** im `mdm`-Schema, kein neues Schema; echte FKs.
- JPA-Enum-CHECK-Constraints auf bestehender DB bei neuen Enum-Werten.
- Doppelsystem vermeiden: `Aktivitaet` bleibt CRM-Kontaktlog, `Konversation` ist die Live-Kommunikation; K2 verknüpft beide.
- Quarkus-Extension zuerst prüfen; Mailer/Outbox bereits vorhanden.
- **Outbox-Naht (wichtig):** `OutboxAuftrag` ist **hart an `Anmeldung` gekoppelt** → **nicht** 1:1 für Kanal-Versand nutzbar. Dispatcher-Mechanik in wiederverwendbare Abstraktion ziehen; `kommunikation` bekommt eigene `ZustellAuftrag`-Tabelle mit echter FK auf `Zustellung`/`Nachricht`.
- Heute **kein ArchUnit/Modulith-Enforcement** → Modulgrenzen nur Konvention; mit Fitness-Functions absichern.
- **SSE/WS-Auth-Gotcha:** Browser-`EventSource` sendet keine Auth-Header (Token via Cookie/Query lösen); WS-Handshake braucht eigenes Token-Handling mit Keycloak.
- **Cross-Realm (K2):** Mitarbeiter (`ebz-staff`) und Person (`ebz-customers`) in *einer* Konversation → OIDC-Multi-Tenant einplanen.
- **Event-Produzenten:** „NoteErfasst"/Prüfungs-Events haben noch keinen Produzenten (Prüfungsmodul pausiert) → K1 nur Bestands-Events; Kontext-FK `prüfung` erst mit dem Modul.
