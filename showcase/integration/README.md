# Integration — HubSpot-Ingestion (Showcase M1)

Quarkus + Camel Quarkus + LangChain4j: zieht HubSpot-Deals (CRM v3), reichert sie per
KI an (Klassifikation/Normalisierung — **nie** Zahlen, L8) und schreibt sie idempotent
ins Warehouse (DB `controlling`, Tabelle `stg_hubspot_deal`). Der bewusste Differenzierer
gegenüber der Commodity-EL-Strecke (dlt, M2).

Start nur mit Profil `controlling`:
```bash
cd showcase
docker compose --profile controlling up -d --build integration
# REST: POST :8090/ingest/run · GET :8090/ingest/review · GET :8090/ingest/stats
```

## Zwei Betriebsmodi
| `INGESTION_SOURCE` | Verhalten |
|---|---|
| `fixture` (Default) | Mock aus `resources/fixtures/hubspot-deals.sample.json` — läuft **ohne** HubSpot-Zugang |
| `hubspot` | Realer Pull gegen `api.hubapi.com/crm/v3/objects/deals` (Bearer-Token nötig) |

KI-Anreicherung: `ingestion.llm.enabled=true` + gültiger `OPENAI_API_KEY` → OpenAI
(AVV/DPA vorhanden); sonst **regelbasierter Fallback** (`enrichedBy=FALLBACK`).

## HubSpot-Token (`HUBSPOT_TOKEN`) — Real-Modus
Der Client sendet `Authorization: Bearer <token>` ([HubSpotClient.java](src/main/java/de/netzfactor/ebz/controlling/integration/source/HubSpotClient.java)).
Es ist **kein** Entwickler-API-Key, sondern ein Account-Token mit Lese-Scope auf Deals.
Drei Wege, vom empfohlenen zum Ausbaupfad:

### 1. Account Service Key — **empfohlen** (Developer-Plattform 2026.03, Public Beta)
Moderner Server-zu-Server-Weg, statisches `pat-…`-Token → **drop-in, kein Code-Eingriff**.
1. HubSpot-Portal (mit den Deals) → **Development → Keys → Service keys**
2. **Create service key** → Name → **Add new scope** → `crm.objects.deals.read`
3. **Create** → Token (`pat-na1-…`) kopieren
4. `showcase/.env`: `HUBSPOT_TOKEN=pat-na1-…` und `INGESTION_SOURCE=hubspot`

Eigenschaften: langlebig mit Rotations-Governance (rotate & expire now/later, 7-Tage-Fenster;
~halbjährlich rotieren), Single-Account, nur REST (keine Webhooks/UI-Extensions).

### 2. Legacy Private App — Fallback (weiterhin supported)
HubSpot **Development → Legacy apps → Create legacy app → Private** → Tab **Scopes**
`crm.objects.deals.read` → Tab **Auth** → Access-Token kopieren. Ebenfalls statisches
`pat-…`-Bearer → identische Verwendung. Nur als „legacy" markiert (kein neues App-Feature-Set).

### 3. OAuth-App — designierte Mehr-Account-Ausbaustufe (hier NICHT umgesetzt)
Für **verteilte/Marketplace-Apps** auf *fremden* Accounts: Authorization-Code-Flow gegen
`/oauth/v3/token` (`grant_type=authorization_code` → `access_token` + `refresh_token`,
Access-Token läuft ~30 Min, `grant_type=refresh_token` zum Erneuern). Bräuchte zusätzliche
Refresh-Logik im Client — für den Single-Account-Lesezugriff dieses Showcase überflüssig.

> Scope bewusst minimal (`crm.objects.deals.read`, Prinzip minimaler Rechte → L29).
> `HUBSPOT_TOKEN` ist ein Secret; `showcase/.env` ist gitignored. EU/NA-Region egal —
> Basis-URL `https://api.hubapi.com` gilt global (über `HUBSPOT_BASE_URL` übersteuerbar).

## Weitere Schalter
`HUBSPOT_INHOUSE_PIPELINE` (Default `inhouse`, L6-Filter) · `HUBSPOT_BASE_URL` ·
`INGESTION_DELAY`/`INGESTION_PERIOD`/`INGESTION_TIMER_ENABLED` (Camel-Timer) ·
`ingestion.review-threshold` (Konfidenz-Schwelle → Review-Queue, L10).
