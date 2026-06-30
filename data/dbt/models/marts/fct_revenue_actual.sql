-- Bucket `actual` (L1): bereits realisierter Erlös je Monat (Europe/Berlin), netto.
--   • platzierte Bestellungen (Einmalkäufe) im Monat des orderPlacedAt
--   • fakturierte Raten (status='invoiced') im Fälligkeitsmonat
-- Abo-Aufträge selbst sind in stg_vendure_orders ausgeschlossen → keine Doppelzählung.
with orders as (
    select placed_month as month, sum(total_net_cents) as amount_net_cents
    from {{ ref('stg_vendure_orders') }}
    group by 1
),
invoiced as (
    select due_month as month, sum(amount_net_cents) as amount_net_cents
    from {{ ref('stg_vendure_installments') }}
    where status = 'invoiced'
    group by 1
),
unioned as (
    select * from orders
    union all
    select * from invoiced
)
select
    month,
    sum(amount_net_cents)::bigint as amount_net_cents,
    'actual'                       as bucket
from unioned
group by 1
