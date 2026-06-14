-- Belegkopf aus dem Billing-SoR (Schema `rechnung`), PII-minimiert: KEINE Klartext-Felder
-- (zeitraum_bezeichnung, nummer entfallen — Teilnehmer-/Zeitraumbezug bleibt im operativen System).
-- Nur festschreibungs-/auswertungsrelevante Metadaten für den Erlös-Mart.
select
    id                  as rechnung_id,
    belegart,
    bereich,
    debitor_id,
    status,
    ausstellungsdatum,
    original_rechnung_id
from {{ source('rechnung', 'rechnung') }}
