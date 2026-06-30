-- Bestellpositionen platzierter Orders, netto in Cent. `is_seminar` = die Variante
-- führt Seminarkosten (Join über seminar_cost.product_variant_id; type-cast wegen
-- Schema-Kopplung: order_line.product_variant_id ist bigint, seminar_cost.* varchar — L20).
-- Preise sind netto (Channel pricesIncludeTax=false).
with l as (
    select * from {{ source('vendure', 'order_line') }}
),
o as (
    select id, code, state, order_placed_at from {{ source('vendure', 'order') }}
),
sem as (
    select distinct product_variant_id::text as variant_id from {{ source('vendure', 'seminar_cost') }}
)
select
    l.id                                                  as order_line_id,
    l.order_id,
    o.code                                                as order_code,
    l.product_variant_id::text                            as product_variant_id,
    l.quantity,
    l.order_placed_quantity,
    l.list_price                                          as unit_price_net_cents,
    (l.list_price * l.order_placed_quantity)              as line_net_cents,
    (l.product_variant_id::text in (select variant_id from sem)) as is_seminar,
    o.order_placed_at,
    {{ month_berlin('o.order_placed_at') }}               as placed_month
from l
join o on o.id = l.order_id
where o.order_placed_at is not null
  and o.state <> 'Cancelled'
