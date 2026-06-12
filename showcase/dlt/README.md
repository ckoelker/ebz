# dlt — Vendure → Warehouse (Showcase M2)

Schlanke **Commodity-EL**-Pipeline (Extract-Load): kopiert die fürs Controlling
nötigen Tabellen aus der Shop-DB `vendure` in die Warehouse-DB `controlling`
(beide im selben Postgres-Container). Bewusst **dlt** statt Camel — der
KI-/Integrations-Differenzierer liegt in `../integration` (M1).

Geladene Tabellen (Ziel-Schema `vendure` in DB `controlling`):
`order` · `order_line` · `installment` · `seminar_cost`.

## Leitplanken
- **L20** — Quelle nur lesend über die Rolle `controlling_reader`; geschrieben wird
  als `controlling`. Zusätzlich `SET SESSION READ ONLY` als Defense-in-depth.
- **L29 (DSGVO)** — nur Beträge/Mengen/Zustände/Datümer/IDs; **keine Kontakt-PII**
  (Teilnehmer-Name/-E-Mail, Liefer-/Rechnungsadresse, Kundentabelle) wird repliziert.
- **L4/L26** — inkrementell über `updatedAt` + idempotenter `merge`-Upsert auf `id`.

## Voraussetzungen
- Laufender Stack (`cd showcase && docker compose up -d`), Postgres auf Host-Port **6543**.
- Vendure geseedet **inkl. Bewegungsdaten**:
  ```
  cd ../vendure
  pnpm run seed              # Katalog + Beispiel-Seminarkosten
  node scripts/seed-demo-orders.mjs   # platzierte Orders + Ratenplan
  ```
- Python (Plan-Pin 3.12; auf dieser Maschine 3.13 — dlt 1.27.2 läuft real auf 3.13).

## Ausführen
```bash
cd showcase/dlt
python -m venv .venv && .venv/Scripts/activate   # Windows; Linux/mac: source .venv/bin/activate
pip install -r requirements.txt
python vendure_to_warehouse.py
```

Konfiguration via Umgebungsvariablen (Defaults passen zu `showcase/.env`):
`VENDURE_DB_HOST/PORT/NAME`, `READER_USER/READER_PASSWORD`,
`CONTROLLING_DB_HOST/PORT`, `CONTROLLING_DB/USER/PASSWORD`, `DLT_DATASET` (Default `vendure`).

## Verifikation
```sql
-- in Adminer (DB controlling) oder psql:
SELECT count(*) FROM vendure."order";
SELECT count(*) FROM vendure.order_line;
SELECT count(*) FROM vendure.installment;
SELECT count(*) FROM vendure.seminar_cost;
```
Re-Run lädt dank Watermark + Merge nur Deltas (Row-Counts bleiben stabil).
