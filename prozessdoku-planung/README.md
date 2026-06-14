# Prozessdoku-Showcase — BPMN automatisch aus OpenTelemetry-Spans

**Status: GEPLANT (noch kein Code freigegeben).** Dieses Dokument ist der Startpunkt; siehe
[Status & Startpunkt](#status--startpunkt) ganz unten für den genauen Stand der bereits
angefassten Dateien.

## Context / Warum
Der EBZ-Showcase ist agil über drei Systeme gewachsen (Portal-SPA :5175, MDM-Cockpit :5174,
integration-Backend) plus Drittsysteme (Keycloak, Mail, Vendure) und Mocks. Dem Kunden ist schwer zu
erklären, **welcher Prozessschritt in welchem System von welcher Person** läuft und wohin Daten
fließen. Ziel: **BPMN-Dateien automatisch erzeugen**, die sich bei Code-Änderung mitaktualisieren —
ohne handgepflegtes Diagramm.

## Gewählter Ansatz (mit Nutzer iteriert)
Ein Prozess-Schritt *ist* ein **OpenTelemetry-Span**. Wir instrumentieren die fachlichen Schritte als
Business-Spans (Attribute *akteur/system/typ/phase*, Korrelation *fall*). Dieselbe Instrumentierung
dient **zwei Zwecken**:
1. **Prod:** Spans → **Jaeger** (Live-Trace-/Span-Ansicht, systemübergreifend via Context-Propagation).
2. **Doku:** Im E2E-Test werden die Spans abgegriffen → **PM4py** entdeckt das Prozessmodell → **BPMN**
   mit Lanes + Auto-Layout. Mehrere Test-Szenarien = Varianten → Gateways.

Verworfen: CSV/JSONL-Eigenformat und reine Code-Annotationen (decken Front-/Dritt-/Mock-Schritte nicht ab).

### Datenfluss
```
Service-Code + E2E-Test
   └─ Prozessspur.schritt(...) ──► OTel-Spans
                                     ├─► (Prod) OTLP ──► Jaeger UI :16686
                                     └─► (Test) SpanLogExporter ──► spans.jsonl
                                                                       └─► generate.py (PM4py)
                                                                             └─► *.bpmn (+Lanes)
                                                                                   └─► layout.mjs ──► .bpmn/.svg → showcase/docs/bpmn/
```

## Festlegungen
- Trace-Backend **Jaeger all-in-one** (OTLP :4317/:4318, UI :16686).
- Ausgabe **Übersicht (Collaboration + Lanes) UND Subprozess-Dateien je Phase**.
- Refresh **beim E2E-Lauf + CI-Verify**.
- PM4py **2.7.22.4** läuft verifiziert auf dem vorhandenen **Python 3.13.3**.

## Architektur-Kernentscheidungen
- **Case-Id = Business-Korrelation `prozess.fall`** (über OTel-**Baggage**), NICHT die traceId — ein
  Durchlauf erstreckt sich über mehrere HTTP-Requests/Traces (Anfrage → später Cockpit → Portal …).
  In Prod aus der Anmeldung/Org abgeleitet; im Test = Szenario-Name (Header `X-Prozess-Fall`).
- **Subprozesse aus Attribut `prozess.phase`** (nicht aus Span-Nesting, da Requests getrennte Traces sind).
- **Lanes aus `prozess.system` + `prozess.akteur`**.
- **Instrumentierung in den Orchestrierungs-Services** (nicht in den Providern), damit Spans auch bei
  gemockten Drittsystemen (FakeProvisionierung etc.) feuern.

## Komponenten & Dateien

### 1. OTel-Setup (Runtime)
- `showcase/integration/pom.xml`: `quarkus-opentelemetry` (runtime) + `io.opentelemetry:opentelemetry-sdk-testing` (test). Versionen via quarkus-bom.
- `showcase/integration/src/main/resources/application.properties`:
  `quarkus.otel.service.name=ebz-integration`, `quarkus.otel.exporter.otlp.endpoint` (dev: localhost:4317,
  Container: `http://jaeger:4317`); im **Test** OTLP-Export aus (`%test.quarkus.otel.traces.exporter=none`),
  SDK aber an, damit der Test-SpanProcessor (s.u.) Spans erhält.
- `showcase/docker-compose.yml`: Service `jaeger` (jaegertracing/all-in-one), Ports 4317/4318/16686,
  Profil `controlling`; integration bekommt `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317`.

### 2. Instrumentierungs-Helfer + Vokabular (Runtime)
Neu: `showcase/integration/src/main/java/de/netzfactor/ebz/controlling/integration/prozessdoku/`
- `Prozess.java` — Enums `Akteur` (ANONYM, FIRMA, AZUBI, EBZ, SYSTEM), `System` (PORTAL, COCKPIT,
  BACKEND, KEYCLOAK, MAIL, VENDURE, RECHNUNGSLAUF), `Typ` (USER_TASK, SERVICE_TASK, MESSAGE,
  BUSINESS_RULE), `Phase` (ANFRAGE_DUBLETTEN, EINLADUNG, AZUBI_ANMELDUNG, EBZ_BESTAETIGUNG, VERTRAG,
  RECHNUNGSLAUF) — je mit Label.
- `Prozessspur.java` (`@ApplicationScoped`, `@Inject Tracer`): `schritt(aktivitaet, Akteur, System, Typ, Phase)`
  startet/endet einen Span mit Attributen `prozess.akteur/system/typ/phase` und `prozess.fall`
  (aus Baggage; Overload mit explizitem `fall` für Test-Schritte ohne HTTP-Kontext).
- `ProzessFallFilter.java` (JAX-RS `ContainerRequestFilter`): Header `X-Prozess-Fall` → Baggage `prozess.fall`.

### 3. Business-Spans an den Prozess-Schritten (Runtime, dual-use)
`Prozessspur.schritt(...)`-Aufrufe in den bestehenden Services (ein Aufruf je fachlichem Schritt):
- `party/service/PartyHoheitService.java`: „Ausbildungsbetrieb-Anfrage" (ANONYM/PORTAL), „Login & Konto-Claim" (FIRMA/KEYCLOAK).
- `party/service/DublettenReviewService.java`: „KI-Dublettenbewertung" (SYSTEM/BACKEND/BUSINESS_RULE), „Dubletten-Entscheidung" (EBZ/COCKPIT).
- `party/service/EinladungsService.java`: „Login-Einladung" (EBZ/COCKPIT), „Login provisioniert" (SYSTEM/KEYCLOAK), „Einladungsmail" (SYSTEM/MAIL/MESSAGE).
- `party/service/BuchungService.java`: „Azubi-Anmeldung" (FIRMA/PORTAL), „Anmeldung angelegt ANGEFRAGT" (SYSTEM/BACKEND).
- `party/service/AnmeldungWorkflowService.java`: „EBZ bestätigt Anmeldung" (EBZ/COCKPIT) + 2× Mail (MESSAGE); „Firma bestätigt Vertrag" (FIRMA/PORTAL).
- Rechnungslauf-Service (Berufsschule): „Rechnungslauf bucht Anmeldung" (SYSTEM/RECHNUNGSLAUF/BUSINESS_RULE).

### 4. Test-Capture der Spans
Neu (Test): `…/prozessdoku/SpanLogExporter.java` — CDI-`SpanProcessor` (Quarkus-OTel sammelt CDI-
SpanProcessor-Beans); schreibt je beendetem **Business-Span** (Attribut `prozess.phase` vorhanden) eine
JSON-Zeile (`fall, name, startEpochNanos, akteur, system, typ, phase`) nach
`target/prozess-log/spans.jsonl` (Truncate bei Klassen-Init). Fallback, falls CDI-Processor nicht
greift: `InMemorySpanExporter` + Flush in `@AfterAll`.

### 5. E2E-Szenarien (Java, rest-assured + @TestSecurity, MockMailbox, FakeProvisionierung)
Neu: `…/party/AnmeldungBerufsschuleE2ETest.java` — pro Methode ein Szenario; jeder rest-assured-Call
sendet `X-Prozess-Fall: <szenario>` (Helper); Schritte ohne Backend-Pendant (reine SPA-/Mock-Schritte)
via injizierten `Prozessspur` mit explizitem `fall`. Backend-Spans feuern automatisch aus (3).
Szenarien (→ Gateways): `happyPath_neueFirma_neuerAzubi`, `variante_firmaDublette_merge`,
`variante_azubiDublette_merge`, `variante_abbruch`. Wiederverwenden: Muster aus
`VertragBestaetigungTest.java` und `AnmeldungBestaetigungTest.java` (Org via Anfrage→`ANGEFRAGT`,
Aktivierung via `POST /party/reviews/entscheidung` NEUANLAGE_BESTAETIGT, `testIp(n)`-Isolation,
`FakeProvisionierung`, MockMailbox).

### 6. Generator (Python + PM4py)
Neu: `showcase/prozessdoku/` — `generate.py`, `requirements.txt` (pm4py, pandas), `README.md`, venv.
- `spans.jsonl` → DataFrame → `pm4py.format_dataframe(df, case_id="fall", activity_key="name", timestamp_key=<startNanos→ts>)`.
- **Subprozesse:** Log je `phase` filtern → `pm4py.discover_bpmn_inductive` → `pm4py.write_bpmn(out/sub-<phase>.bpmn)`.
- **Übersicht:** Aktivitäten auf `phase` umlabeln → minen → Collaboration mit **Call-Activities** je Phase.
- **Lanes injizieren:** erzeugte BPMN-XML nachbearbeiten → `collaboration` + `laneSet`, jeden flowNode
  der Lane seines dominanten `system`/`akteur` zuordnen (Mapping Aktivität→System aus dem Log).

### 7. Layout & Bilder (Node/pnpm)
Neu: `showcase/prozessdoku/layout.mjs` — `bpmn-auto-layout` (DI-Koordinaten) + `bpmn-to-image` (SVG/PNG).
Ergebnisse nach `showcase/docs/bpmn/` (committed).

### 8. Orchestrierung & Trigger
Neu: `showcase/prozessdoku/build.sh` (bash):
1) `mvn -f showcase/integration/pom.xml test -Dtest='*E2ETest'` → `spans.jsonl`
2) `python generate.py` → `out/*.bpmn`  3) `node layout.mjs` → `.bpmn`+`.svg` nach `showcase/docs/bpmn/`.
**CI-Verify:** `build.sh` + `git diff --exit-code showcase/docs/bpmn/` (rot bei fehlender Regenerierung).

## Risiken (ehrlich)
- **CDI-SpanProcessor-Discovery** (Quarkus-OTel): falls die Test-Erfassung nicht automatisch greift →
  Fallback `InMemorySpanExporter` + `@AfterAll`-Flush. Früh verifizieren.
- **Lane-Auto-Layout:** `bpmn-auto-layout` unterstützt Lanes/Collaboration nur teilweise → Fallback:
  Lanes ohne perfekte Koordinaten ausgeben, in Camunda Modeler re-layouten; oder Übersicht ohne Lanes.
- **Span-Granularität in Prod:** Auto-Spans (HTTP/DB) verrauschen die Generierung → Generator filtert
  strikt auf Business-Spans (`prozess.phase` gesetzt); Jaeger zeigt weiterhin alles.
- **Aufwand:** deutlich größer als ein Datei-Log (Runtime-Dep, Service-Instrumentierung, compose, Capture). Daher staged.

## Verifikation
1. `docker compose --profile controlling up -d jaeger integration` → Live-Trace in Jaeger UI :16686 nach einem Durchlauf.
2. `bash showcase/prozessdoku/build.sh` grün → `showcase/docs/bpmn/uebersicht.bpmn` + `sub-*.bpmn` + `.svg` entstehen.
3. `uebersicht.bpmn` in Camunda Modeler: Lanes = Akteur/System, Phasen als Call-Activities, Gateways aus Varianten.
4. Drift-Test: einen `Prozessspur.schritt`-Aufruf entfernen → erneut bauen → BPMN ändert sich; CI-`git diff` schlägt an.

## Reihenfolge der Umsetzung (staged)
1. OTel-Setup (pom/properties/compose Jaeger) + `Prozess`/`Prozessspur`/`ProzessFallFilter`.
2. Test-Capture (`SpanLogExporter`) + Happy-Path-E2E (eine Methode) → `spans.jsonl` entsteht; Jaeger zeigt Trace.
3. `generate.py` Subprozesse (ohne Lanes) → erste .bpmn.
4. Übersicht/Call-Activities + Lane-Injektion + Node-Layout/SVG.
5. Varianten-E2E (Gateways) + `build.sh` + CI-Verify.

## Status & Startpunkt
**Sauberer Start:** Es ist noch **kein** Produktiv-/Testcode angelegt. Frühere Experiment-Reste
(eine `quarkus-opentelemetry`-Zeile in der pom + eine veraltete CSV-Variante `Prozessprotokoll.java`)
wurden bewusst **zurückgerollt**, damit `main` „geplant, nicht gestartet" bleibt.

Erster Schritt beim Wiederaufnehmen: **Stage 1** (siehe oben) — OTel-Setup (pom: `quarkus-opentelemetry`
+ test-scope `opentelemetry-sdk-testing`; properties; Jaeger in docker-compose) und die Helfer
`Prozess`/`Prozessspur`/`ProzessFallFilter`.
