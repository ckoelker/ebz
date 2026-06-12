# dbt — Transform + Forecast (Showcase M3)

„Das Gehirn": rechnet aus dem Warehouse (DB `controlling`) die **Seminar-Deckungs-
beitragsrechnung/Break-even** und den **treiberbasierten Monats-Forecast**. Liest die
von dlt (M2, Schema `vendure`) und der Integration (M1, Schema `public`) geladenen
Quellen; schreibt die Modelle nach Schema `analytics`.

## Modelle
**staging/** (Views) — säubern, Cent/netto, Typisierung, Monat (Europe/Berlin):
`stg_vendure_orders` · `stg_vendure_order_lines` · `stg_vendure_installments` ·
`stg_vendure_seminar_cost` · `stg_hubspot_deals`.

**seeds/** (Controlling-Setzungen): `overhead_plan.csv` (Gemeinkosten je Monat) ·
`allocation_keys.csv` (Umlageschlüssel, umschaltbar) · `stage_probabilities.csv`
(Stage → Wahrscheinlichkeit + Monate bis Abschluss).

**marts/** (Tabellen):
- `fct_seminar_db` — DB I/II/Ergebnis + **Break-even-TN** je Seminar.
  `DB I = Umsatz − var. Einzelkosten` · `DB II = DB I − fixe Einzelkosten` ·
  `Ergebnis = DB II − umgelegte Gemeinkosten`.
- `fct_revenue_actual` · `fct_revenue_contracted` · `fct_pipeline_weighted` —
  die **drei disjunkten Erlös-Buckets** (L1).
- `dim_month` — lückenlose Monatsachse (Date-Spine, L14).
- `fct_forecast` — Monats-GuV = actual + contracted + gewichtete Pipeline − Gemeinkosten.

## Leitplanken (Tests erzwingen sie)
- **L1** Drei-Bucket-Recognition, jeder € pro Monat in genau einem Bucket;
  `assert_actual_installment_disjoint` verhindert Order/Raten-Doppelzählung.
- **L7** Stage-Wahrscheinlichkeit aus Seed (nicht hardcoden) · **L15** Monat in
  Europe/Berlin (UTC-Quelle) · **L16** `assert_overhead_reconciliation`
  (Σ Umlage = Pool) · **L17** eine Währung · **L19** genau ein aktiver Umlageschlüssel.
- **Unit-Test** `seminar_break_even_worked_example`: Plan-Rechenbeispiel
  (600 €/TN, Fix 1.800 €, var. 25 €/TN, Umlage 800 €) → **Break-even 5 TN**.

## Ausführen
Voraussetzung: Stack läuft, M1+M2 geladen (HubSpot-Deals + dlt-Tabellen im Warehouse).
Reihenfolge (L26): **dlt-Load → dbt → BI**.
```bash
cd showcase/dbt
python -m venv .venv && .venv/Scripts/python -m pip install -r requirements.txt
.venv/Scripts/dbt build --profiles-dir .     # seeds + models + tests + unit test
```
Konfiguration via Umgebung (Defaults = `showcase/.env`): `CONTROLLING_DB_HOST/PORT`,
`CONTROLLING_DB/USER/PASSWORD`. Reproduzierbare Demos: `--vars '{forecast_anchor: "2026-06-01"}'`.

## Verifikation
```sql
SELECT * FROM analytics.fct_seminar_db;   -- DB-Stufen + Break-even
SELECT * FROM analytics.fct_forecast WHERE month >= date_trunc('month', current_date);
```
