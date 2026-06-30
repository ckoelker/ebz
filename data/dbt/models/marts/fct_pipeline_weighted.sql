-- Bucket `pipeline` (L1): gewichteter, noch unsicherer Erlös aus offenen Inhouse-Deals.
-- Gewicht = Stage-Wahrscheinlichkeit aus dem Seed (L7, nicht hardcoden). Erwarteter
-- Monat = Ankermonat + typische Monate-bis-Abschluss je Stage. closedwon/closedlost
-- sind ausgeschlossen (won → contracted, lost → 0; L2).
with deals as (
    select * from {{ ref('stg_hubspot_deals') }}
    where stage not in ('closedwon', 'closedlost')
),
prob as (
    select * from {{ ref('stage_probabilities') }}
)
select
    (({{ anchor_month() }}) + (prob.months_to_close || ' month')::interval)::date as month,
    round(sum(deals.amount_net_cents * prob.probability))::bigint                 as amount_net_cents,
    'pipeline'                                                                     as bucket
from deals
join prob on prob.stage = deals.stage
where prob.probability > 0
group by 1
