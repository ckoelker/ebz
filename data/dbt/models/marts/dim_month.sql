-- Lückenlose Monatsachse (L14): von frühestem bis spätestem Monat über alle Buckets
-- + Gemeinkosten-Plan, damit der Forecast auch Monate ohne Aktivität zeigt.
with months as (
    select month from {{ ref('fct_revenue_actual') }}
    union
    select month from {{ ref('fct_revenue_contracted') }}
    union
    select month from {{ ref('fct_pipeline_weighted') }}
    union
    select to_date(period_month, 'YYYY-MM') from {{ ref('overhead_plan') }}
),
bounds as (
    select min(month) as lo, max(month) as hi from months
)
select gs::date as month
from bounds, generate_series(bounds.lo, bounds.hi, interval '1 month') as gs
