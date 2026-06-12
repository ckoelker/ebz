-- Seminar-Kostenpositionen (M2-Plugin). Beträge netto in Cent. Die Aufteilung
-- isVariable/perParticipant ist die Grundlage des Break-even (L18).
select
    id                                  as cost_id,
    product_variant_id::text            as product_variant_id,
    cost_type,
    label,
    amount                              as amount_net_cents,
    currency_code,
    is_variable,
    per_participant
from {{ source('vendure', 'seminar_cost') }}
