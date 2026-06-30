# Controlling-Showcase

On-prem Best-of-Breed-Showcase für Controlling: Seminar-Profitabilität (DB/Break-even),
Inhouse-Pipeline (HubSpot) und unternehmensweiter GuV-Forecast.

> Dieser Ordner (`controlling-planung/`) enthält **nur die Doku**. Der **Code** ist Teil des
> bestehenden Showcase-Monorepos **`../showcase/`** und liegt **flach** dort (`integration/`,
> `dlt/`, `dbt/`, `lightdash/` — neben `vendure/`/`frontend/`, **kein `controlling/`-Unterordner**).
> Eine Compose, **ein** Postgres. Shop-Doku: [`../shop-planung/`](../shop-planung/), Enterprise-Planung:
> [`../enterprise-stack-planung/`](../enterprise-stack-planung/).

> **Plan & verbindliche Versionen:** [Showcase-Realisierungsplan-Controlling.md](Showcase-Realisierungsplan-Controlling.md)
> (Architektur §2, Milestones §5, Versionsmatrix §0, Leitplanken §9).

## Stack (Kurz)

| Schicht | Technologie | Ort im Repo |
|---|---|---|
| Warehouse | DB `controlling` im **gemeinsamen** Postgres 16 (eigener User) | `showcase/docker-compose.yml` (Service `postgres`) |
| Ingestion (Differenzierer) | Quarkus 3.33 LTS + Camel + LangChain4j 1.10 (Ollama `qwen2.5:7b-instruct`) | `showcase/integration/` |
| Ingestion (Commodity) | dlt 1.27 (liest `vendure` als read-only-User `controlling_reader`) | `showcase/dlt/` |
| Transform / Forecast | dbt-core 1.11 + dbt-postgres 1.10 | `showcase/dbt/` |
| BI | Lightdash 0.3059 — Metadaten in DB `lightdash` (eigener User), Keycloak `ebz-staff` OIDC | `showcase/lightdash/` |
| DB-UI / IdP | Adminer (8082) + Keycloak (8088) — **vom Shop mitgenutzt** | `showcase/docker-compose.yml` |

## Single Source of Truth: ein Postgres, drei DBs

| DB | Zweck | Owner-User |
|---|---|---|
| `vendure` | Shop (operativ) | `vendure` (Superuser) |
| `controlling` | Warehouse (dlt/dbt) | `controlling` |
| `lightdash` | BI-Metadaten | `lightdash` |

dlt liest `vendure` über den read-only-User **`controlling_reader`** (L20). DBs/User legt das
initdb-Script `showcase/postgres/initdb/` an (frisches Volume); Variablen in **`showcase/.env`**.

## Schnellstart (M0)

```bash
cd showcase
docker compose up -d postgres adminer    # gemeinsamer Postgres + DB-UI
# Adminer: http://localhost:8082  (Server: postgres, DB: controlling | vendure | lightdash)
```

## Reihenfolge

M0 (Fundament) → **M1** (HubSpot/Camel/LC4j, zuerst) → M2 (Vendure + dlt) →
M3 (dbt) → M4 (Lightdash, Keycloak-gesichert) → M5 (Auswertung).
