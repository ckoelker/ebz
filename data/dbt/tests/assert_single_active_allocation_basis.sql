-- L19: Der Umlageschlüssel ist umschaltbar, aber zu jedem Zeitpunkt darf genau EIN
-- basis aktiv sein — sonst ist die Gemeinkostenverteilung mehrdeutig.
select count(*) as active_count
from {{ ref('allocation_keys') }}
where active
having count(*) <> 1
