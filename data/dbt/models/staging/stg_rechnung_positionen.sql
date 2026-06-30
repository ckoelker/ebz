-- Rechnungspositionen aus dem Billing-SoR, PII-minimiert: KEINE `beschreibung`/`teilnehmer_name`
-- (enthalten Teilnehmer-Klartext). Beträge netto in Cent; Storno/Gutschrift sind bereits negativ
-- gespeichert (Umkehr im Service) → Summen netten die Originalrechnung automatisch aus.
select
    id                                  as position_id,
    rechnung_id,
    menge,
    einzelbetrag_cent,
    (menge * einzelbetrag_cent)         as net_cents,
    steuerfall,
    steuersatz,
    leistungsart
from {{ source('mdm', 'rechnung_position') }}
