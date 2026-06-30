"""Showcase M2 — dlt-Pipeline: Shop-DB `vendure` → Warehouse-DB `controlling`.

Bewusst **dlt statt Camel** (gelebte Leitlinie „Commodity kaufen"): schlankes,
inkrementelles Extract-Load, on-prem, MIT-Lizenz. Der Differenzierer (HubSpot +
KI-Konvertierung) liegt dagegen in der Camel-Quarkus/LangChain4j-Integration (M1).

Geladen werden vier Tabellen ins Ziel-Schema (dataset) `vendure` der DB `controlling`:
    order · order_line · installment · seminar_cost

Leitplanken:
  - L20: Die Quelle wird **nur lesend** über den Rolle `controlling_reader` angefasst;
         geschrieben wird als `controlling` in die Warehouse-DB.
  - L29 (PII-Minimierung/DSGVO): Es werden **nur** die fürs Controlling nötigen Spalten
         repliziert — KEINE Kontakt-PII (Teilnehmer-Name/-E-Mail, Liefer-/Rechnungsadresse,
         Kundentabelle). Nur Beträge, Mengen, Zustände, Datümer, Variant-/Kunden-IDs.
  - L4/L26: inkrementell über `updatedAt` (Watermark) + idempotenter Upsert
         (write_disposition="merge", primary_key="id") → Re-Runs sind unschädlich.

Aufruf (Stack muss laufen):  python vendure_to_warehouse.py
Konfiguration kommt aus Umgebungsvariablen (Defaults passen zu .env, Postgres
auf Host-Port 6543). Siehe README.md.
"""
from __future__ import annotations

import os

import dlt
import psycopg2
import psycopg2.extras

# --- Quelle: Shop-DB `vendure`, NUR lesend über controlling_reader (L20) ------
SRC = dict(
    host=os.getenv("VENDURE_DB_HOST", "localhost"),
    port=int(os.getenv("VENDURE_DB_PORT", "6543")),
    dbname=os.getenv("VENDURE_DB_NAME", "vendure"),
    user=os.getenv("READER_USER", "controlling_reader"),
    password=os.getenv("READER_PASSWORD", "controlling_reader"),
)

# --- Ziel: Warehouse-DB `controlling`, schreibend als controlling --------------
DEST_USER = os.getenv("CONTROLLING_USER", "controlling")
DEST_PASSWORD = os.getenv("CONTROLLING_PASSWORD", "controlling")
DEST_HOST = os.getenv("CONTROLLING_DB_HOST", "localhost")
DEST_PORT = int(os.getenv("CONTROLLING_DB_PORT", "6543"))
DEST_DB = os.getenv("CONTROLLING_DB", "controlling")
DEST_DSN = f"postgresql://{DEST_USER}:{DEST_PASSWORD}@{DEST_HOST}:{DEST_PORT}/{DEST_DB}"

DATASET = os.getenv("DLT_DATASET", "vendure")

_conn = None


def _src():
    """Lazy, wiederverwendete Read-only-Verbindung zur Shop-DB."""
    global _conn
    if _conn is None or _conn.closed:
        _conn = psycopg2.connect(**SRC)
        _conn.set_session(readonly=True)  # Defense-in-depth zusätzlich zur Rolle (L20)
    return _conn


def _rows(select_sql: str, cursor_field: str, last_value):
    """Führt SELECT aus; bei vorhandenem Watermark inkrementell (col > last_value)."""
    with _src().cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        if last_value is not None:
            cur.execute(f'{select_sql} WHERE "{cursor_field}" > %s ORDER BY "{cursor_field}"', (last_value,))
        else:
            cur.execute(f'{select_sql} ORDER BY "{cursor_field}"')
        for row in cur:
            yield dict(row)


# --- Ressourcen (explizite Spaltenauswahl = PII-Minimierung, L29) --------------

@dlt.resource(name="order", write_disposition="merge", primary_key="id",
              columns={"training_company": {"data_type": "text"}})
def orders(updated=dlt.sources.incremental("updatedAt")):
    sql = (
        'SELECT id, code, type, state, active, "orderPlacedAt", "currencyCode", '
        '"customerId", "subTotal", "subTotalWithTax", shipping, "shippingWithTax", '
        '"customFieldsEnrollmenttype" AS enrollment_type, '
        '"customFieldsTrainingcompany" AS training_company, '
        '"createdAt", "updatedAt" '
        'FROM "order"'
    )
    yield from _rows(sql, "updatedAt", updated.last_value)


@dlt.resource(name="order_line", write_disposition="merge", primary_key="id")
def order_lines(updated=dlt.sources.incremental("updatedAt")):
    # KEINE customFieldsParticipantname / -email (PII, L29).
    sql = (
        'SELECT id, "orderId", "productVariantId", quantity, "orderPlacedQuantity", '
        '"listPrice", "listPriceIncludesTax", "initialListPrice", "taxLines", '
        '"createdAt", "updatedAt" '
        'FROM order_line'
    )
    yield from _rows(sql, "updatedAt", updated.last_value)


@dlt.resource(name="installment", write_disposition="merge", primary_key="id")
def installments(updated=dlt.sources.incremental("updatedAt")):
    sql = (
        'SELECT id, "orderId", "orderCode", "variantName", sequence, "totalCount", '
        'amount, "currencyCode", "dueDate", status, "createdAt", "updatedAt" '
        'FROM installment'
    )
    yield from _rows(sql, "updatedAt", updated.last_value)


@dlt.resource(name="seminar_cost", write_disposition="merge", primary_key="id")
def seminar_costs(updated=dlt.sources.incremental("updatedAt")):
    sql = (
        'SELECT id, "productVariantId", "costType", label, amount, "currencyCode", '
        '"isVariable", "perParticipant", "createdAt", "updatedAt" '
        'FROM seminar_cost'
    )
    yield from _rows(sql, "updatedAt", updated.last_value)


def main() -> None:
    pipeline = dlt.pipeline(
        pipeline_name="vendure_to_warehouse",
        destination=dlt.destinations.postgres(credentials=DEST_DSN),
        dataset_name=DATASET,
    )
    info = pipeline.run([orders(), order_lines(), installments(), seminar_costs()])
    print(info)
    # Knappe Row-Count-Bilanz (L28 Observability).
    print("\nGeladen je Tabelle:")
    for name, count in (pipeline.last_trace.last_normalize_info.row_counts or {}).items():
        if not name.startswith("_dlt"):
            print(f"  {name:14s} {count}")


if __name__ == "__main__":
    main()
