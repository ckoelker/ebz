-- Ratenplan (F4–F6). Quelle speichert Bruttobetrag (unitPriceWithTax); für die
-- einheitlich netto geführte Erlös-Recognition (L17) auf netto umrechnen (eine
-- Steuerklasse im Showcase, var vat_rate). status='scheduled' = gesicherter Zukunfts-
-- erlös (`contracted`), 'invoiced' = fakturiert (`actual`).
select
    id                                                         as installment_id,
    order_id,
    order_code,
    variant_name,
    sequence,
    total_count,
    amount                                                     as amount_gross_cents,
    round(amount / (1 + {{ var('vat_rate') }}))::bigint        as amount_net_cents,
    currency_code,
    status,
    due_date,
    {{ month_berlin('due_date') }}                             as due_month
from {{ source('vendure', 'installment') }}
