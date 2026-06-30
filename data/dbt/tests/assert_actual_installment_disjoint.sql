-- L1/L3: Kein Auftrag darf gleichzeitig als Einmalumsatz (actual) UND über seine
-- Raten gezählt werden. Abo-Aufträge werden ausschließlich über Installments
-- erfasst. Findet dieser Test Zeilen, droht Doppelzählung.
select order_code
from {{ ref('stg_vendure_installments') }}
where order_code in (select order_code from {{ ref('stg_vendure_orders') }})
