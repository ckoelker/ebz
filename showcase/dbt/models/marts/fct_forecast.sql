-- Monats-GuV-Forecast (Grain: month). Die drei Erlös-Buckets sind disjunkt (L1) —
-- jeder € liegt pro Monat in genau einem Bucket — und werden zur Forecast-Erlöslinie
-- summiert; abzüglich der Gemeinkosten-Run-Rate ergibt sich das erwartete Ergebnis.
with months as (
    select month from {{ ref('dim_month') }}
),
actual as (
    select month, amount_net_cents from {{ ref('fct_revenue_actual') }}
),
contracted as (
    select month, amount_net_cents from {{ ref('fct_revenue_contracted') }}
),
pipeline as (
    select month, amount_net_cents from {{ ref('fct_pipeline_weighted') }}
),
overhead as (
    select to_date(period_month, 'YYYY-MM') as month, round(sum(amount_eur) * 100)::bigint as overhead_cost_cents
    from {{ ref('overhead_plan') }}
    group by 1
),
calc as (
select
    m.month,
    coalesce(a.amount_net_cents, 0)                                       as actual_net_cents,
    coalesce(c.amount_net_cents, 0)                                       as contracted_net_cents,
    coalesce(p.amount_net_cents, 0)                                       as pipeline_weighted_net_cents,
    coalesce(o.overhead_cost_cents, 0)                                    as overhead_cost_cents,
    (coalesce(a.amount_net_cents, 0) + coalesce(c.amount_net_cents, 0)
        + coalesce(p.amount_net_cents, 0))                                as revenue_forecast_net_cents,
    (coalesce(a.amount_net_cents, 0) + coalesce(c.amount_net_cents, 0)
        + coalesce(p.amount_net_cents, 0) - coalesce(o.overhead_cost_cents, 0)) as result_forecast_net_cents
from months m
left join actual     a on a.month = m.month
left join contracted c on c.month = m.month
left join pipeline   p on p.month = m.month
left join overhead   o on o.month = m.month
)
-- Euro-Komfortspalten (Cent/100) für die BI-Schicht.
select
    calc.*,
    round(calc.actual_net_cents / 100.0, 2)             as actual_net_eur,
    round(calc.contracted_net_cents / 100.0, 2)         as contracted_net_eur,
    round(calc.pipeline_weighted_net_cents / 100.0, 2)  as pipeline_weighted_net_eur,
    round(calc.overhead_cost_cents / 100.0, 2)          as overhead_cost_eur,
    round(calc.revenue_forecast_net_cents / 100.0, 2)   as revenue_forecast_eur,
    round(calc.result_forecast_net_cents / 100.0, 2)    as result_forecast_eur
from calc
order by month
