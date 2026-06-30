-- Platzierte Bestellungen = Ist-Erlös (`actual`-Bucket). Beträge netto in Cent.
-- Abo-/Ratenaufträge werden NICHT hier als Erlös erfasst, sondern über die
-- Installments (sonst Doppelzählung; L1/L3) — daher Order-Codes mit Raten ausschließen.
with src as (
    select * from {{ source('vendure', 'order') }}
),
with_installments as (
    select distinct order_code from {{ source('vendure', 'installment') }}
)
select
    src.id                                              as order_id,
    src.code                                            as order_code,
    src.state,
    src.currency_code,
    src.customer_id,
    src.enrollment_type,
    src.sub_total                                       as sub_total_net_cents,
    src.sub_total_with_tax                              as sub_total_gross_cents,
    src.shipping                                        as shipping_net_cents,
    src.shipping_with_tax                               as shipping_gross_cents,
    (src.sub_total + src.shipping)                      as total_net_cents,
    (src.sub_total_with_tax + src.shipping_with_tax)    as total_gross_cents,
    src.order_placed_at,
    {{ month_berlin('src.order_placed_at') }}           as placed_month
from src
where src.order_placed_at is not null
  and src.state <> 'Cancelled'
  and src.code not in (select order_code from with_installments)
