# DATEV-Sandbox — Onboarding & verifizierte API-Fakten

> Stand 2026-06-30. **Live gegen die DATEV-Sandbox verifiziert** (headless Bootstrap + echter API-Call HTTP 200).
> Grundlage für D1–D3 (siehe Plan/Konzept). Credentials + Refresh-Token liegen **nur in `.env` (gitignored)**.

## Endpunkte (Sandbox)
- **Authorize:** `https://login.datev.de/openidsandbox/authorize` (leitet nach Methodenwahl auf `https://secure8.datev.de/openidsandbox/authorize`)
- **Token:** `https://sandbox-api.datev.de/token`
- **API-Hosts:** je Produkt, z. B. `https://accounting-clients.api.datev.de/platform-sandbox/v2/...`,
  `https://accounting-extf-files.api.datev.de/platform-sandbox/v3/clients/{clientId}/extf-files/import`,
  `https://accounting-documents.api.datev.de/platform-sandbox/v2/...`
- OIDC-Discovery: `https://login.datev.de/openidsandbox/.well-known/openid-configuration`

## Authentifizierung (verifiziert)
- **Flow:** Authorization Code **+ PKCE (S256, Pflicht)**. `client_credentials` wird **nicht** unterstützt
  (`unsupported_grant_type`). Client-Type **confidential**, Redirect-URI **`http://localhost`**.
- **Pflicht-Parameter sonst Fehler:** `state` lang genug (sonst „state too short"), `code_challenge` (sonst
  „code_challenge missing").
- **Refresh-Token rotiert bei JEDEM Refresh** → der laufende Dienst muss den **neuen** Refresh-Token persistieren
  (deshalb **kein** statischer `quarkus-oidc-client`-Config-Token, sondern eigener Token-Service).
- **Pflicht-Header auf JEDEM API-Call:** `X-DATEV-Client-Id: <OAuth-client_id>` **zusätzlich** zum
  `Authorization: Bearer …` (ohne → 401 „Invalid client id or secret"). Access-Token ist **opaque** (kein JWT).

## Scopes (Prefix `datev:`!)
Beim Consent anzufragen: `openid` + die produkt-/service-genauen Scopes. **Exakte Scopes** (aus dem
`accounting:clients`-Response abgelesen):
| Service | Scope |
|---|---|
| Buchungsdatenservice (EXTF-Import, **D1**) | `datev:accounting:extf-files-import` |
| Belegbilder (**D2**) | `datev:accounting:documents` (Produkt-Scope; final am Produkt prüfen) |
| Mandanten-/Stammdaten | `datev:accounting:clients` |
| Rechnungsdatenservice 2.0 | `datev:file:import` |
| Export Rechnungswesen | `datev:accounting:exchange` |

## Sandbox-Mandanten (clientId = `{Beraternummer}-{Mandant}`)
- **`455148-2`** „C.o.d…" → **Buchungsdatenservice** (`datev:accounting:extf-files-import`) ← **D1-Ziel-Mandant**
- `455148-3` „Danger…" → Rechnungsdatenservice 2.0 (`datev:file:import`)

→ D1-Import-URL: `POST …/extf-files/import` mit **clientId `455148-2`**.

## Token bootstrappen / erneuern
Refresh-Token läuft ab → neu minten mit dem Helfer (headless Consent über Sandbox-Test-User):
```
cd tests/e2e
DATEV_SCOPE='openid datev:accounting:extf-files-import datev:accounting:documents datev:accounting:clients' \
  node datev-token-bootstrap.mjs        # schreibt DATEV_REFRESH_TOKEN nach .env
node datev-clients.mjs                  # Smoke: refresh→access→GET clients (200)
```
Nötige `.env`-Variablen: `DATEV_CLIENT_ID`, `DATEV_CLIENT_SECRET`, `DATEV_SANDBOX_USER`, `DATEV_SANDBOX_PASSWORD`
→ erzeugt `DATEV_REFRESH_TOKEN`. (Optional `DATEV_SANDBOX_CLIENT_ID=455148-2`.)

## Umsetzungsstand D1–D5 (2026-06-30, Branch `feat/datev-cloud`)
Gebaut + `mvn test-compile` grün (Laufzeit-Tests laufen über `tools/stack.sh`; Default `datev.modus=extf` → bestehende Tests unberührt):
- **D1 Buchungsdatenservice** — `DatevCloudApi` (Token + Extf), `DatevTokenService` (Access-Cache + Refresh-Rotation), `DatevCloudUebergabe` (`@Identifier("cloud")`: EXTF-Upload → Job-Poll, Pflicht-Header), Weg-Auswahl in `DatevService` (`extf|cloud-mock|cloud`). Test: `DatevCloudUebergabeTest` gegen `DatevWireMockResource`.
- **D2 Belegbilder** — `accounting:documents` Multipart-PUT mit selbst erzeugter GUID (`DatevBelegbildService`), Endpoint `POST /rechnung/datev/belegbild/{id}` (ZUGFeRD erzeugen+validieren→hochladen). Test: `DatevBelegbildTest`.
- **D3 OPOS-Regelkreis** — Port `OposQuelle` (+ `OposQuelleMock`, real = DATEVconnect/on-prem), `OposRegelkreisService` (Match über Belegnummer → `RechnungService.bezahlen` → BEZAHLT, idempotent), `OposDispatcher` (`@Scheduled`, `opos.dispatcher.*`). Test: `DatevOposRegelkreisTest`.
- **D5 SmartTransfer/Peppol** — Port `PeppolVersand` (+ `PeppolVersandMock`), `RechnungPeppolVersandService` (ZUGFeRD-Validierung als Tor, Empfänger aus USt-IdNr. → Peppol-ID 9930), Endpoint `POST /rechnung/rechnungen/{id}/versenden/peppol`. Test: `PeppolVersandTest`.
- **Offen/„gegen Sandbox final verifizieren":** EXTF-Import-Content-Type + Job-Status-Felder (D1), Belegbild-Metadaten-Schema (D2); D3 real = DATEVconnect (Kanzlei), D5 real = SmartTransfer-Transport-API. Opt-in echter Sandbox-Smoke via `tests/e2e/datev-clients.mjs`.

## Konsequenzen für den Bau (D1)
1. **Eigener `DatevTokenService`** (CDI): hält/rotiert Refresh-Token, cached Access-Token bis `expires_in`,
   POSTet Basic-Auth `client_id:secret` an den Token-Endpoint. Kein `oidc-client`-Extension nötig.
2. **REST-Client** (`quarkus-rest-client-jackson`, vorhanden) mit Pflicht-Headern `Authorization` +
   `X-DATEV-Client-Id`; Base-URLs/clientId aus Config (`datev.*`).
3. Tests weiter **WireMock-first**; der reale Sandbox-Smoke ist mit den obigen Helfern reproduzierbar.
