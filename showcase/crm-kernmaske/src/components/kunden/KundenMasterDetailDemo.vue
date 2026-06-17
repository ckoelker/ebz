<script setup lang="ts">
import { ref, computed } from 'vue'
import KundenMasterListe from './KundenMasterListe.vue'
import KontaktDetailHeader from './KontaktDetailHeader.vue'
import TabBar from './TabBar.vue'
import { personById, orgById, LOGINVERSUCHE } from '../../mock/data'

// Zusammengesetzter Master-Detail-Flow (das zentrale Abnahme-Artefakt des Kundenstamms):
// links Liste → rechts Detailkopf + Tabs. Demonstriert Person- UND Firmen-Auswahl.
const sel = ref('p1')
const person = computed(() => personById(sel.value))
const org = computed(() => orgById(sel.value))
const tab = ref('stammdaten')

const tabs = computed(() => org.value
  ? [
      { key: 'stammdaten', label: 'Stammdaten' },
      { key: 'personen', label: 'Personen' },
      { key: 'kommunikation', label: 'Kommunikation' },
      { key: 'hierarchie', label: 'Hierarchie' },
    ]
  : [
      { key: 'stammdaten', label: 'Stammdaten' },
      { key: 'zugehoerigkeiten', label: 'Zugehörigkeiten' },
      { key: 'kommunikation', label: 'Kommunikation' },
      { key: 'dsgvo', label: 'Einwilligung/DSGVO' },
      { key: 'login', label: 'Loginversuche', bubble: person.value ? LOGINVERSUCHE[person.value.id]?.filter(l => l.ergebnis !== 'erfolgreich').length : 0 },
    ])

function select(id: string) {
  sel.value = id
  tab.value = 'stammdaten'
}
const kp = computed(() => (org.value ?? person.value)!.kontaktpunkte)
</script>

<template>
  <div class="flex gap-4">
    <KundenMasterListe @select="select" />
    <div class="flex-1 min-w-0 space-y-4">
      <KontaktDetailHeader :person="person" :org="org" />
      <TabBar v-model="tab" :tabs="tabs" />
      <UCard>
        <div class="grid sm:grid-cols-2 gap-x-8 gap-y-2 text-sm">
          <div v-for="k in kp" :key="(k.email ?? k.nummerAnzeige ?? k.strasse) + k.typ" class="flex gap-2">
            <span class="text-muted w-24 shrink-0">{{ k.typ }}</span>
            <span class="text-default">
              {{ k.email ?? k.nummerAnzeige ?? [k.strasse, k.hausnummer, '·', k.plz, k.ort].filter(Boolean).join(' ') }}
              <UBadge v-if="k.kontext" color="neutral" variant="subtle" size="sm" class="ml-1">{{ k.kontext }}</UBadge>
            </span>
          </div>
        </div>
        <p class="text-xs text-dimmed mt-3">Aktiver Tab: <b>{{ tab }}</b> — die fachlichen Inhalte je Tab folgen als eigene Bausteine.</p>
      </UCard>
    </div>
  </div>
</template>
