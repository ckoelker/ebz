-- HubSpot-Deals (M1, KI-angereichert). Nur Inhouse-Pipeline + EUR (L6/L17).
-- Beträge bleiben deterministisch aus der Quelle (das LLM liefert nie Zahlen, L8).
select
    deal_id,
    deal_name,
    amount_cents                        as amount_net_cents,
    currency,
    delivery_type,
    pipeline,
    stage,
    seminar_kategorie,
    konfidenz,
    review_required,
    source_modified_at
from {{ source('hubspot', 'stg_hubspot_deal') }}
where currency = '{{ var('currency') }}'
  and delivery_type = 'INHOUSE'
