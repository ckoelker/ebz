-- Debitorenstamm (Rechnungsempfänger) aus dem Billing-SoR (Schema `mdm`) — die Kunden-Dimension der
-- Erlös-Marts. PII-minimiert: nur auswertungsrelevante Identitäts-/Steuerungsfelder, KEINE Anschrift,
-- USt-IdNr., IBAN oder E-Mail (bleiben im operativen System).
--   • `name` ist der Debitorenname (Firma/Person als Rechnungsempfänger) = die Kunden-Bezeichnung.
--   • Golden-Record-Auflösung: eine als ZUSAMMENGEFUEHRT markierte Dublette zeigt per
--     `golden_debitor_id` auf den überlebenden Debitor → `golden_debitor_id` faltet Umsätze gemergter
--     Debitoren auf den Golden-Record (kein Doppelzählen, keine Karteileichen im Ranking).
select
    id                                       as debitor_id,
    debitor_nr,
    name                                     as debitor_name,
    bereich,
    status,
    coalesce(golden_debitor_id, id)          as golden_debitor_id
from {{ source('mdm', 'debitor') }}
