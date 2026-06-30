-- Fakturierter Netto-Erlös aus dem Billing-SoR — je Monat (Ausstellungsdatum) und Bereich, über
-- ALLE Bereiche (Berufsschule/Hochschule/Akademie/Shop). Ergänzt die Vendure-basierten Erlös-Buckets
-- um die in Lightdash bisher unsichtbaren Schul-/Hochschul-Rechnungen.
--   • nur festgeschriebene Belege (AUSGESTELLT/BEZAHLT/STORNIERT) mit Ausstellungsdatum
--   • Storno/Gutschrift sind negativ gespeichert → netten die Originalrechnung automatisch aus
--   • `ausstellungsdatum` ist ein Kalendertag (DATE) → direktes Monats-Bucketing ohne Zeitzone
with belege as (
    select rechnung_id, bereich, ausstellungsdatum
    from {{ ref('stg_rechnung_belege') }}
    where status in ('AUSGESTELLT', 'BEZAHLT', 'STORNIERT')
      and ausstellungsdatum is not null
),
positionen as (
    select rechnung_id, sum(net_cents) as net_cents
    from {{ ref('stg_rechnung_positionen') }}
    group by 1
)
select
    date_trunc('month', b.ausstellungsdatum)::date          as month,
    b.bereich,
    sum(coalesce(p.net_cents, 0))::bigint                   as amount_net_cents,
    (sum(coalesce(p.net_cents, 0)) / 100.0)::numeric(14, 2) as amount_net_eur,
    count(distinct b.rechnung_id)                           as belege
from belege b
left join positionen p on p.rechnung_id = b.rechnung_id
group by 1, 2
