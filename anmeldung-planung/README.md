# anmeldung-planung — Anmeldung Berufsschule

Realisierungsplan für den **end-to-end-Anmeldeprozess Berufsschule**: vom Ausbildungs­betrieb
(Firma + Ansprechpartner) über die HITL-/KI-gestützte Dublettenprüfung, die Keycloak-Login-
Provisionierung, die Azubi-Anmeldung durch den Firmenansprechpartner, die Vertragsbestätigung
bis zum bestehenden Rechnungslauf. Reine **Planungs-/Konzept-Doku** (analog `party-planung/`,
`rechnungsstellung-planung/`) — **noch kein Code**.

> Dieser Prozess **baut auf vorhandenen Bausteinen auf** und erfindet sie nicht neu:
> Identität & Kontext = **Party-Kern** (`party-planung/`, GEBAUT), Abrechnung = **R1 Berufsschule**
> (`rechnungsstellung-planung/`, GEBAUT). Neu sind v. a. die **Self-Service-Frontends**, die
> **KI-Dublettenprüfung mit Mensch-im-Loop**, die **Keycloak-Provisionierung**, der **E-Mail-Versand**
> und ein **Anmeldungs-Lebenszyklus** (Anfrage → Bestätigung → Vertrag → abrechenbar).

---

## 1. Leitidee

Ein **Ausbildungsbetrieb** meldet seine **Azubis** zur Berufsschule an. Der Prozess ist
*self-service mit Sicherheitsnetz*: die Firma erfasst selbst, aber **jede Identität wird gegen den
Bestand auf Dubletten geprüft** — KI **schlägt vor**, ein Mensch (EBZ-Sachbearbeitung) **entscheidet**
(HITL). So entstehen keine Doppel-Firmen/Doppel-Personen, und die spätere Abrechnung rollt sauber
auf **einen** Kunden/Debitor zusammen.

Zwei Identitäts-Nahtstellen, beide über den Party-Kern:
- **Firma + Ansprechpartner** (Schritt A–C): Organisation anlegen/zuordnen, Ansprechpartner als
  `Person` (provisorisch) + `Mitgliedschaft` (Rolle `ANSPRECHPARTNER`/`AUSBILDER`, `buchungsberechtigt`).
- **Azubi** (Schritt D–F): `Person` (provisorisch) + `Mitgliedschaft` (Rolle `AZUBI`), Anmeldung im
  **Firmenkontext** → zahlungspflichtiger Debitor = Firmen-Debitor (`ermittleDebitor`).

Login & Aktivierung laufen über **Account-Claiming** (vorhandenes `selbstRegistrieren`): die Firma/der
EBZ legt die Person **provisorisch** an; loggt sich der Mensch später mit **derselben E-Mail** ein,
wird genau diese Person geclaimt (Keycloak-`sub` gebunden, Status → AKTIV). „Selbe E-Mail = selbe
Person" bleibt damit über alle Wege konsistent.

---

## 2. Akteure & Realms

| Akteur | Wo | Realm / Rolle | Aufgabe |
|---|---|---|---|
| **Firmen-Ansprechpartner** | Frontend-SPA (Self-Service-Portal) | `ebz-customers` | Firma registrieren, Azubis anmelden, Vertrag bestätigen |
| **Azubi** | Frontend-SPA (Self-Service) | `ebz-customers` | eigenen Account aktivieren (Claim), Anmeldung einsehen |
| **EBZ-Sachbearbeitung** | Backend-SPA (HITL-Cockpit, in `mdm/`) | `ebz-staff` / `rechnung-pflege` | Dubletten-Reviews entscheiden, EBZ-Bestätigung, Rechnungslauf |
| **KI (OpenAI via langchain4j)** | im `integration`-Backend | — (DPA vorhanden) | Dubletten-**Vorschlag** + Begründung (nie Auto-Entscheidung) |

Vorhanden: zwei Realms `ebz-customers` + `ebz-staff` (`showcase/vendure/keycloak/realms/*.json`),
Keycloak `:8088` (admin/admin); `quarkus-oidc` im `integration`-Service; `quarkus-langchain4j-openai`
(JSON-Mode, `temperature=0`) bereits konfiguriert; AI-Anreicherungsmuster existiert (`DealEnricher`).

---

## 3. Prozess & Zustandsmodell

```
A. Firma registriert sich (Frontend, ebz-customers nach Login ODER öffentliche Anfrage)
       Organisation(ANGEFRAGT) + Ansprechpartner(Person PROVISORISCH, Mitgliedschaft)
                                   │
B. HITL+KI Dubletten-Review (Backend-SPA, ebz-staff)
       KI: Firmen-Kandidaten + Personen-Kandidaten + Score + Begründung
       Mensch: [Neuanlage bestätigen] | [auf Bestand mergen]
                                   │  (bestätigt)
C. Provisionierung: Keycloak-Login an Ansprechpartner-E-Mail (ebz-customers),
       Verknüpfung Person↔Organisation steht; Einladungs-E-Mail (Set-Password / Magic-Link)
                                   │  Ansprechpartner bestätigt Login (Claim → Person AKTIV)
D. Ansprechpartner meldet Azubis an (Frontend, Firmenkontext)
       je Azubi: Person(PROVISORISCH) + Mitgliedschaft(AZUBI) + Anmeldung(ANGEFRAGT)
                                   │
E. HITL+KI Azubi-Dubletten-Review (Backend-SPA)  →  EBZ bestätigt Anmeldung
       Anmeldung ANGEFRAGT → BESTAETIGT_EBZ ; E-Mail an Azubi + Firma
                                   │
F. Firma bestätigt den Vertrag        Anmeldung BESTAETIGT_EBZ → AKTIV (= abrechenbar)
   Azubi aktiviert seinen Account     Person(Azubi) PROVISORISCH → AKTIV (Claim, eigener Login)
                                   │
G. Zeitpunkt x: Rechnungslauf (R1, bestehend)  bucht alle AKTIV-Anmeldungen je Schuljahr/Halbjahr
       Anmeldung AKTIV → ABGESCHLOSSEN (nach Festschreibung)
```

**Kniff am Lebenszyklus (wichtig):** Der bestehende `RechnungslaufService` bucht heute genau
`status = AKTIV` (Berufsschule, je `schuljahr`/`halbjahr`). Wir nutzen das, statt es zu ändern:
neue Vorstufen **ANGEFRAGT** und **BESTAETIGT_EBZ** sind *nicht* abrechenbar; erst die
**Vertragsbestätigung der Firma** setzt → **AKTIV**. Damit bleibt der Rechnungslauf unverändert und
es kann nichts versehentlich vor Vertragsabschluss fakturiert werden.
→ `AnmeldungStatus` erweitern: heute `AKTIV, ABGEBROCHEN, ABGESCHLOSSEN` ⇒ ergänzen um
`ANGEFRAGT, BESTAETIGT_EBZ` (AKTIV behält die Bedeutung „vertraglich bestätigt, abrechenbar").

---

## 4. Was existiert / was fehlt (Gap-Analyse)

| Baustein | Status | Quelle / Lücke |
|---|---|---|
| Organisation anlegen | ✅ vorhanden | `POST /party/organisationen` |
| Ansprechpartner/Azubi vor-anlegen (provisorisch + Mitgliedschaft) | ✅ vorhanden | `POST /party/organisationen/{id}/teilnehmer` (`registriereTeilnehmer`) |
| Personen-Dubletten-**Kandidaten** | ✅ vorhanden | `kandidaten(personId)` (Namensschlüssel) |
| Personen-**Merge** (HITL-Aktion) | ✅ vorhanden | `POST /party/personen/merge` |
| Berufsschul-Anmeldung im Kontext → Debitor | ✅ vorhanden | `POST /party/buchungen/berufsschule` (`bucheBerufsschule`) |
| Account-Claiming / Selbst-Aktivierung | ✅ vorhanden | `selbstRegistrieren` / `POST /party/personen/login` (`sub` aus Token) |
| Rechnungslauf Berufsschule | ✅ vorhanden | `RechnungslaufService` (bucht `AKTIV`) |
| **Organisations-Dubletten** (Kandidaten + Merge) | ❌ Lücke | `Organisation` hat keinen `matchSchluessel`, keine `kandidaten`/`merge` |
| **KI-Dubletten-Scoring** (Firma & Person) | ❌ Lücke | neuer `DublettenBerater` (langchain4j), liefert Score+Begründung als JSON |
| **HITL-Review-Workflow** (Status, Queue) | ❌ Lücke | Anmeldung-Status-Erweiterung + Review-Endpunkte + Backend-SPA-Sicht |
| **Keycloak-Login-Provisionierung** | ❌ Lücke | `keycloak-admin-client` fehlt; Service „lege Customer-User + Einladung an" |
| **E-Mail-Versand** (Einladung, Anmeldebestätigung) | ❌ Lücke | `quarkus-mailer` fehlt; Mailpit als Dev-Service in `docker-compose` fehlt |
| **Customer-Portal-Endpunkte** (Firmenkontext-Auth statt `rechnung-pflege`) | ❌ Lücke | heutige Buchungs-Endpunkte verlangen `rechnung-pflege` (Staff) |
| **Vertragsbestätigung** durch die Firma | ❌ Lücke | Endpunkt + Status-Übergang BESTAETIGT_EBZ → AKTIV (+ Audit) |
| **Frontend-SPA Firmenportal** | ❌ Lücke | weder `frontend/` (Shop) noch `mdm/` deckt das Self-Service-Portal ab |
| **Backend-SPA HITL-Cockpit** | 🟡 teilweise | `mdm/` (Cockpit, `ebz-staff`) existiert → Review-Modul ergänzen |

---

## 5. Realisierungsschritte

Reihenfolge so gewählt, dass jeder Schritt **für sich testbar** ist (Backend zuerst, dann SPA);
jeder Schritt endet mit `-Dtest=…`-gezielten Tests, nicht der ganzen Suite.

### A0 — Infrastruktur-Nähte (Voraussetzung)
- **Mail**: `quarkus-mailer` ins `integration`-POM; **Mailpit** als Dev-Service in
  `showcase/docker-compose.yml` (`:8025` UI / `:1025` SMTP); `%dev`/`%test`-Mailer-Config.
- **Keycloak-Admin**: `keycloak-admin-client`-Dependency; Service-Account-Client im Realm
  `ebz-customers` mit `manage-users` (für User-Anlage); Config (URL/Realm/Client/Secret).
- **KI-Provider**: Abstraktion OpenAI (Default, vorhanden) ⇄ **Ollama** (optionaler Dev-Service im
  `docker-compose`, lokales JSON-fähiges Modell), per Config umschaltbar (§9.3).
- **DoD**: Smoke-Test „Mail an Mailpit zugestellt" + „Admin-Client listet Realm-User".

### A — Firma + Ansprechpartner erfassen (Backend)
- **Öffentlicher** Endpunkt „Ausbildungsbetrieb-Anfrage" (unauthentifiziert, §9.1) mit **Bot-/Spam-
  Schutz** (Rate-Limit + Honeypot/Captcha): Organisation (`ANGEFRAGT`) + Ansprechpartner
  (`registriereTeilnehmer`, Rolle `ANSPRECHPARTNER`/`AUSBILDER`, `buchungsberechtigt=true`). **Noch
  kein Login** — der entsteht erst nach HITL (Schritt C).
- **Org-Dubletten fähig machen**: `Organisation.matchSchluessel` (USt-Id bzw. `name|plz`,
  gleiche Normalisierung wie Debitor/Person), `organisationKandidaten(orgId)` + `mergeOrganisation`.
- **DoD**: rest-assured: Anfrage legt Org+Person+Mitgliedschaft an; Kandidaten finden bestehende Firma.

### B — KI-Dubletten-Berater + HITL-Review (Backend)
- `DublettenBerater` hinter einer **Provider-Abstraktion** (OpenAI ⇄ Ollama, per Config, §9.3):
  Eingang = Kandidatenpaar(e) **minimiert** (normalisierte Tokens statt Rohdaten, soweit möglich —
  DSGVO/Datensparsamkeit); Ausgang = `{score, sicher: MATCH|UNSICHER|KEIN_MATCH, begruendung}`.
  KI **entscheidet nie**.
- Review-Endpunkte: Queue offener Fälle (Firma & Person), Entscheidung „Neuanlage bestätigen" |
  „auf Ziel mergen" (nutzt vorhandenes `merge` / neues `mergeOrganisation`); jede Entscheidung
  mit Audit (wer/wann/KI-Score).
- **DoD**: KI-Antwort wird geparst; HITL-Merge führt Dublette zusammen; Audit geschrieben. Test mit
  gemocktem/aufgezeichnetem LLM-Result (kein Live-Key in CI).

### C — Keycloak-Provisionierung + Einladung (Backend)
- Nach bestätigter Neuanlage/Zuordnung: Customer-User im Realm `ebz-customers` anlegen
  (E-Mail = Username, `emailVerified=false`), **Einladungs-E-Mail** (Set-Password / Action-Link)
  via Mailer. Person↔Org-Verknüpfung steht bereits.
- **Claim**: beim ersten Login bindet `selbstRegistrieren` den `sub` an die provisorische Person → AKTIV.
- **DoD**: Provisionierung legt KC-User an + Mail in Mailpit; simulierter Login claimt die Person.

### D — Azubi-Anmeldung durch den Ansprechpartner (Backend, Customer-Auth)
- **Customer-Portal-Endpunkte** mit Firmenkontext-Autorisierung (statt `rechnung-pflege`): Aufrufer
  über Token-`sub` → Person; muss **buchungsberechtigtes Mitglied** der Organisation sein (sonst 403),
  Muster wie `firmensicht`.
- Je Azubi: `registriereTeilnehmer` (Rolle `AZUBI`) + `bucheBerufsschule` mit `kontextOrganisationId`
  = Firma, **Status `ANGEFRAGT`** (nicht abrechenbar). Debitor = Firmen-Debitor via `ermittleDebitor`.
- **DoD**: Ansprechpartner bucht im eigenen Org-Kontext (201); fremder Kontext → 403; Anmeldung
  landet als `ANGEFRAGT`.

### E — Azubi-Dubletten-Review + EBZ-Bestätigung + E-Mail (Backend)
- HITL+KI auf den Azubi (wie B). Danach EBZ-Bestätigung: `ANGEFRAGT → BESTAETIGT_EBZ`;
  **Anmeldebestätigungs-E-Mail** an Azubi **und** Firma (Mailer-Templates).
- **DoD**: Statusübergang + zwei Mails in Mailpit; Doppel-Azubi wird vor Bestätigung erkannt.

### F — Vertragsbestätigung (Firma) + Azubi-Selbst-Aktivierung (Backend)
- Customer-Endpunkt „Vertrag bestätigen" (Ansprechpartner, Org-Scope): `BESTAETIGT_EBZ → AKTIV`
  (abrechenbar), mit Audit (Zeitpunkt/Person).
- Azubi-Account: optional Provisionierung (C) + Claim (`login`) → Azubi-Person AKTIV, eigener Login.
- **DoD**: nur nach Vertragsbestätigung ist die Anmeldung `AKTIV`; Azubi-Claim funktioniert.

### G — Rechnungslauf (bestehend, nur verifizieren)
- Zum Zeitpunkt x: `RechnungslaufService` (Berufsschule, `schuljahr`/`halbjahr`) bucht die `AKTIV`-
  Anmeldungen → Belege → (R2 ZUGFeRD / R4 DATEV bleiben separate Milestones). **Kein neuer Code**,
  nur End-to-End-Verifikation, dass ausschließlich vertraglich bestätigte Anmeldungen fakturiert werden.
- **DoD**: Lauf erzeugt Belege nur für `AKTIV`; `ANGEFRAGT`/`BESTAETIGT_EBZ` bleiben unberührt.

### H — Frontend-SPA „Firmenportal" (`ebz-customers`)
- Neue SPA (Vue, Stack wie `mdm/`: keycloak-js-SSO, generierte Typen/Validierung) oder Modul:
  Firma registrieren → Status verfolgen → nach Login Azubis erfassen (Liste/Maske, Cross-Field-
  Validierung) → Vertrag bestätigen. Realm `ebz-customers`.
- **DoD**: Playwright-SSO-Flow: Login → Azubi anlegen → Vertrag bestätigen.

### I — Backend-SPA „HITL-Cockpit" (Modul in `mdm/`, `ebz-staff`)
- Review-Queues (Firmen-/Personen-/Azubi-Dubletten) mit KI-Score+Begründung; Aktionen
  Neuanlage/Merge; EBZ-Bestätigung. Realm `ebz-staff`, Rolle `rechnung-pflege`.
- **DoD**: Playwright: offenen Fall entscheiden (Merge) → verschwindet aus der Queue; Audit sichtbar.

---

## 6. Datenmodell-Änderungen (Überblick)

- `AnmeldungStatus` += `ANGEFRAGT`, `BESTAETIGT_EBZ` (vor `AKTIV`).
- `Organisation` += `matchSchluessel`, `status` (`ANGEFRAGT`/`AKTIV`/`ZUSAMMENGEFUEHRT`),
  `goldenOrganisationId` (Merge analog `Person`).
- `Mitgliedschaft.Rolle` ggf. += `ANSPRECHPARTNER` (falls nicht über `AUSBILDER` abgedeckt).
- **Audit** (leichtgewichtig): wer/wann hat Review entschieden, Vertrag bestätigt, mit KI-Score
  (Nachvollziehbarkeit der HITL-Entscheidung).
- Hibernate `schema-management.strategy=update` legt **Tabellen** an; **Schema** `party` existiert
  bereits (initdb). Keine neuen Schemata nötig.

---

## 7. HITL + KI — Leitplanken

- **KI schlägt vor, Mensch entscheidet.** Kein automatischer Merge/keine automatische Anlage; der
  KI-`score` priorisiert nur die Queue und liefert eine Begründung.
- **Datensparsamkeit** trotz OpenAI-DPA: dem Modell möglichst **normalisierte/abstrahierte Merkmale**
  statt voller PII übergeben; On-Prem-Ollama als Alternative bleibt offen (wie im Controlling-Showcase).
- **Determinismus**: `temperature=0`, `response-format=json_object` (bereits gesetzt) → robustes Parsen,
  Fallback bei Parsefehler = „UNSICHER" (immer in die menschliche Queue).
- **Auditierbarkeit**: Score + Begründung + menschliche Entscheidung werden gespeichert.

---

## 8. Sicherheit & DSGVO

- **Zwei Realms strikt getrennt**: Self-Service-Endpunkte = `ebz-customers` + **Org-Mitgliedschafts-
  Scope** (Aufrufer per Token-`sub`, muss buchungsberechtigtes Mitglied sein, sonst 403); HITL/Lauf =
  `ebz-staff`/`rechnung-pflege`.
- **DSGVO-Sichten** wie im Party-Kern: Firmenportal sieht **nur** den eigenen Org-Kontext, **nie**
  Privatbuchungen der Personen.
- **Login-Provisionierung** nur nach bestätigter Identität (HITL), nie blind aus der Self-Service-Anfrage.
- **Mass-Assignment-Schutz**: nie `id/version/status` aus dem Body (Muster der bestehenden Resource).

---

## 9. Entscheidungen (geklärt 2026-06-14)

1. **Firmen-Erstkontakt = öffentliche Anfrage + Einladung.** Die Firma stellt eine öffentliche,
   unauthentifizierte Self-Service-Anfrage (Lead) — mit **Bot-/Spam-Schutz** (Rate-Limit + Honeypot/
   Captcha am öffentlichen Endpunkt, kein `ebz-customers`-Login nötig). Der **Login entsteht erst nach
   der HITL-Dublettenprüfung** per Admin-provisionierter **Einladung** (Set-Password/Action-Mail).
   ⇒ Identität wird **vor** dem Login geprüft; keine Keycloak-Self-Registration.
2. **Vertrag = Status-Flag + Audit.** Bestätigung ist der Statusübergang `BESTAETIGT_EBZ → AKTIV`
   mit Audit (wer/wann). Kein PDF/keine E-Signatur im Showcase (PDF = möglicher späterer Ausbau).
3. **KI-Modell = umschaltbar (OpenAI ⇄ Ollama).** `DublettenBerater` hinter einer **Provider-
   Abstraktion**, per Config wählbar: Default **OpenAI** (DPA, gpt-4o-mini, JSON-Mode, `temperature=0`,
   bereits konfiguriert) mit **Datensparsamkeit**; **On-Prem-Ollama** als datenschutz-strengste
   Alternative (eigener Dev-Service, lokales JSON-fähiges Modell). Beide liefern dasselbe
   `{score, sicher, begruendung}`-Schema; Fallback bei Parsefehler = „UNSICHER".
4. **Azubi-Login = optional/claimbar.** Azubis werden über die Firma angemeldet; ein eigener Login
   ist **optional** über Account-Claiming (`selbstRegistrieren`), wenn der Azubi seine Anmeldung selbst
   einsehen will. Die Gültigkeit der Anmeldung hängt **nicht** am Azubi-Login (sie wird über die
   Firmen-Vertragsbestätigung `AKTIV`). Keine erzwungene Azubi-Aktivierung.

### Noch offen (unkritisch, mit Default)
- **Rollen-Feinschnitt** im `ebz-customers`-Realm: Default = dedizierte Rolle
  `firma-ansprechpartner` zusätzlich zur Org-Mitgliedschafts-Prüfung (kann beim Bau von H/D
  festgezurrt werden).

---

## 10. Teststrategie

- **Backend**: rest-assured je Schritt (A–G), `@TestSecurity` für Rollen/Realms; LLM in Tests
  gemockt/aufgezeichnet (kein Live-Key in CI); Mail gegen Mailpit verifiziert; gezielt `-Dtest=…`.
- **Frontend**: vitest (Komponenten/Validierung) + Playwright-SSO (Firmenportal-Flow, HITL-Cockpit-Flow).
- **End-to-End**: ein Durchstich A→G gegen den laufenden Stack (eine Firma, ein Azubi, ein Lauf).

## Status — Backend A–G GEBAUT + VERIFIZIERT (2026-06-14)

Alle Backend-Schritte sind im `integration`-Service gebaut und getestet (8 Party-Testklassen grün;
Camel im Test aus). Commits: A0 `f616619`, A `555c9d0`, B `dd4ff64`, C `d6984be`, D `8c6e697`,
E `228315a`, F `f04b19a`.

- **A0** Mail-Naht (quarkus-mailer + Mailpit). *KI-Provider-Abstraktion: OpenAI default, Ollama-
  Umschaltung via langchain4j-Config; Keycloak-Admin-Client kam in C.*
- **A** öffentlicher Lead `POST /party/anfragen/ausbildungsbetrieb` (Honeypot + Rate-Limit) +
  Firmen-Dubletten (`Organisation` status/matchSchluessel/golden, Kandidaten/Merge).
- **B** KI-Dubletten-Berater (`DublettenKlassifikator`/`DublettenBerater`, Fallback) + HITL-Review
  (`/party/reviews/queue`, `/entscheidung`, Audit `DublettenReview`).
- **C** Keycloak-Provisionierung (`LoginProvisionierung`) + Einladungsmail
  (`POST /party/personen/{id}/einladung`); Claim aktiviert die Person.
- **D** Customer-Portal `POST /party/portal/azubi-anmeldung` (Org-Scope) → Anmeldung `ANGEFRAGT`;
  `AnmeldungStatus += ANGEFRAGT, BESTAETIGT_EBZ`.
- **E** EBZ-Bestätigung `POST /party/anmeldungen/{id}/bestaetigung` (`→ BESTAETIGT_EBZ`) + Mails an
  Azubi/Firma.
- **F** Vertragsbestätigung `POST /party/portal/anmeldungen/{id}/vertrag-bestaetigen` (`→ AKTIV`,
  Audit); Azubi-Selbst-Aktivierung = Claim.
- **G** Rechnungslauf unverändert — verifiziert: bucht `AKTIV`, ignoriert `ANGEFRAGT`/`BESTAETIGT_EBZ`.

## Frontends

- **I — HITL-Cockpit: GEBAUT (2026-06-14, Commit `123404b`)** als Modul im bestehenden
  `showcase/mdm/` (`ebz-staff`, Rolle `rechnung-pflege`). Views `DublettenReview.vue` (Queue + KI-
  Vorschlag → Merge/Neuanlage) + `AnmeldungenBestaetigung.vue` (offene Anmeldungen → EBZ-Bestätigung);
  Routen `/reviews` + `/anmeldungen`; Client aus `/q/openapi` neu generiert (inkl. `/party`). Backend-
  Zusatz: `GET /party/anmeldungen?status=…`. Verifiziert: `pnpm build` (vue-tsc) + Backend-Regression grün.
  *Live-Betrieb: das laufende `showcase-integration`-Image ist älter (ohne `/party`) → Container neu
  bauen: `docker compose --profile controlling up -d --build integration`.*
- **H — Außenportal: OFFEN.** Eigene neue SPA `showcase/portal/` (`ebz-customers`) = allgemeines
  Self-Service-Portal für **Firmen UND Teilnehmer** (künftig alle selbst implementierten Außen-
  funktionen, nicht nur BS-Anmeldung): Anfrage → Login → Azubis erfassen → Vertrag bestätigen. Der
  Shop (`showcase/frontend/`) bleibt eigenständig. Backend-Endpunkte stehen bereit.
