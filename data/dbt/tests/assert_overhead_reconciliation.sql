-- L16: Reconciliation — die auf die Seminare umgelegten Gemeinkosten müssen in Summe
-- dem umlagefähigen Pool (Ø Monats-Gemeinkosten) entsprechen (bis auf Rundung ≤ 1 Cent
-- je Seminar). Schlägt an, wenn der Umlageschlüssel „Kosten verliert" oder erzeugt.
with allocated as (
    select coalesce(sum(overhead_cents), 0) as s,
           count(*)                          as n
    from {{ ref('fct_seminar_db') }}
),
pool as (
    select round(avg(amount_eur) * 100)::bigint as p
    from {{ ref('overhead_plan') }}
)
select allocated.s, pool.p
from allocated, pool
where abs(allocated.s - pool.p) > allocated.n
