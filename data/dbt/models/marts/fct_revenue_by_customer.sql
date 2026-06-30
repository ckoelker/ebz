-- Fakturierter Netto-Erlös je KUNDE (Debitor/Golden-Record) — die „Top-Kunden nach Umsatz"-Auswertung
-- für Lightdash. Quelle ist der Billing-SoR (alle Bereiche), Logik analog {@code fct_revenue_billed},
-- aber aggregiert auf den Rechnungsempfänger statt auf den Monat.
--   • nur festgeschriebene Belege (AUSGESTELLT/BEZAHLT/STORNIERT)
--   • Storno/Gutschrift sind negativ gespeichert → netten die Originalrechnung automatisch aus
--   • Dublette (ZUSAMMENGEFUEHRT) wird über `golden_debitor_id` auf den Golden-Record gefaltet, damit
--     ein Kunde nur EINMAL und mit der Summe ALLER seiner (auch gemergten) Belege im Ranking steht
with belege as (
    select rechnung_id, debitor_id
    from {{ ref('stg_rechnung_belege') }}
    where status in ('AUSGESTELLT', 'BEZAHLT', 'STORNIERT')
      and ausstellungsdatum is not null
),
positionen as (
    select rechnung_id, sum(net_cents) as net_cents
    from {{ ref('stg_rechnung_positionen') }}
    group by 1
),
-- jeden Beleg-Debitor auf seinen Golden-Record auflösen
beleg_kunde as (
    select
        b.rechnung_id,
        d.golden_debitor_id as kunde_id,
        coalesce(p.net_cents, 0) as net_cents
    from belege b
    join {{ ref('stg_rechnung_debitoren') }} d on d.debitor_id = b.debitor_id
    left join positionen p on p.rechnung_id = b.rechnung_id
),
-- Stammdaten des Golden-Records (Name/Nr/Bereich) für die Kunden-Dimension
golden as (
    select debitor_id, debitor_nr, debitor_name, bereich
    from {{ ref('stg_rechnung_debitoren') }}
)
select
    g.debitor_id                                       as kunde_id,
    g.debitor_nr,
    g.debitor_name,
    g.bereich,
    sum(bk.net_cents)::bigint                          as amount_net_cents,
    (sum(bk.net_cents) / 100.0)::numeric(14, 2)        as amount_net_eur,
    count(distinct bk.rechnung_id)                     as belege
from beleg_kunde bk
join golden g on g.debitor_id = bk.kunde_id
group by 1, 2, 3, 4
