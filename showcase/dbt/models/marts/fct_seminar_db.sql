-- Seminar-Deckungsbeitragsrechnung + Break-even (Grain: product_variant_id).
-- DB-Stufenrechnung (netto, Cent):
--   DB I  = Umsatz − variable Einzelkosten            (Stückdeckungsbeitrag-Basis)
--   DB II = DB I − fixe Einzelkosten                  (harte Wahrheit vor Umlage, L19)
--   Ergebnis = DB II − umgelegte Gemeinkosten
--   Break-even (Ergebnis=0): (fixe Einzelkosten + var. Pauschale + Umlage) / DB I je TN, aufgerundet.
-- Gemeinkostenumlage (L16): Pool = durchschnittliche Monats-Gemeinkosten (Run-Rate),
-- nach aktivem Schlüssel (allocation_keys, L19) verteilt; div0 über coalesce abgefangen.
with lines as (
    select
        product_variant_id,
        sum(order_placed_quantity) as participants,
        sum(line_net_cents)        as revenue_net_cents
    from {{ ref('stg_vendure_order_lines') }}
    where is_seminar
    group by 1
),
costs as (
    select
        product_variant_id,
        sum(case when not is_variable then amount_net_cents else 0 end)                       as fixed_cost_cents,
        sum(case when is_variable and per_participant then amount_net_cents else 0 end)        as var_per_tn_cents,
        sum(case when is_variable and not per_participant then amount_net_cents else 0 end)    as var_flat_cents
    from {{ ref('stg_vendure_seminar_cost') }}
    group by 1
),
base as (
    select
        c.product_variant_id,
        coalesce(l.participants, 0)       as participants,
        coalesce(l.revenue_net_cents, 0)  as revenue_net_cents,
        c.fixed_cost_cents,
        c.var_per_tn_cents,
        c.var_flat_cents
    from costs c
    left join lines l using (product_variant_id)
),
active_basis as (
    select basis from {{ ref('allocation_keys') }} where active limit 1
),
weights as (
    select
        b.*,
        case when (select basis from active_basis) = 'participant_share'
             then b.participants::numeric
             else b.revenue_net_cents::numeric
        end as alloc_weight
    from base b
),
pool as (
    -- durchschnittliche Monats-Gemeinkosten als umlagefähiger Pool
    select round(avg(amount_eur) * 100)::bigint as pool_cents from {{ ref('overhead_plan') }}
),
total_weight as (
    select sum(alloc_weight) as tw from weights
)
select
    w.product_variant_id,
    w.participants,
    w.revenue_net_cents,
    (w.var_per_tn_cents * w.participants + w.var_flat_cents)                       as variable_cost_cents,
    w.fixed_cost_cents,
    round((select pool_cents from pool) * w.alloc_weight
          / coalesce(nullif((select tw from total_weight), 0), 1))::bigint         as overhead_cents,
    -- DB-Stufen
    (w.revenue_net_cents - (w.var_per_tn_cents * w.participants + w.var_flat_cents)) as db1_cents,
    (w.revenue_net_cents - (w.var_per_tn_cents * w.participants + w.var_flat_cents)
        - w.fixed_cost_cents)                                                       as db2_cents,
    (w.revenue_net_cents - (w.var_per_tn_cents * w.participants + w.var_flat_cents)
        - w.fixed_cost_cents
        - round((select pool_cents from pool) * w.alloc_weight
                / coalesce(nullif((select tw from total_weight), 0), 1)))           as result_cents,
    -- Stückdeckungsbeitrag (DB I je TN) = Netto-Stückpreis − variable Kosten je TN
    case when w.participants > 0
         then round(w.revenue_net_cents::numeric / w.participants) - w.var_per_tn_cents
         else null end                                                             as contribution_per_tn_cents,
    -- Break-even-Teilnehmerzahl (Ergebnis = 0)
    case when w.participants > 0
              and (round(w.revenue_net_cents::numeric / w.participants) - w.var_per_tn_cents) > 0
         then ceil(
                (w.fixed_cost_cents + w.var_flat_cents
                 + round((select pool_cents from pool) * w.alloc_weight
                         / coalesce(nullif((select tw from total_weight), 0), 1)))::numeric
                / (round(w.revenue_net_cents::numeric / w.participants) - w.var_per_tn_cents)
              )::int
         else null end                                                            as break_even_participants
from weights w
