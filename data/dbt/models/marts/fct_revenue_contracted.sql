-- Bucket `contracted` (L1): vertraglich gesicherter, noch nicht fakturierter Erlös, netto.
--   • geplante Raten (status='scheduled') im Fälligkeitsmonat
--   • gewonnene HubSpot-Deals (closedwon) im Ankermonat — gesichert, aber noch nicht
--     als Vendure-Auftrag konvertiert (Konvertierung ist außerhalb des Showcase-Scopes,
--     §7). Offene Deals zählen NICHT hier, sondern gewichtet in der Pipeline (L2).
with scheduled as (
    select due_month as month, sum(amount_net_cents) as amount_net_cents
    from {{ ref('stg_vendure_installments') }}
    where status = 'scheduled'
    group by 1
),
won as (
    select {{ anchor_month() }} as month, sum(amount_net_cents) as amount_net_cents
    from {{ ref('stg_hubspot_deals') }}
    where stage = 'closedwon'
    group by 1
),
unioned as (
    select * from scheduled
    union all
    select * from won
)
select
    month,
    sum(amount_net_cents)::bigint as amount_net_cents,
    'contracted'                   as bucket
from unioned
group by 1
