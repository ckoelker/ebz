<script setup lang="ts">
import { ref, computed } from 'vue'
import KundenMasterListe from './KundenMasterListe.vue'
import KontaktDetailHeader from './KontaktDetailHeader.vue'
import TabBar from './TabBar.vue'
import KontaktpunktList from '../../ui/KontaktpunktList.vue'
import { PERSONEN, ORGANISATIONEN, MITGLIEDSCHAFTEN, personById, orgById, LOGINVERSUCHE } from '../../data/mock'

// Container/Demo des Master-Detail-Flows (zentrales Abnahme-Artefakt): lädt die Mock-Daten
// und reicht sie an die prop-reinen Komponenten durch. Person- UND Firmen-Auswahl.
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
    <KundenMasterListe
      :personen="PERSONEN"
      :organisationen="ORGANISATIONEN"
      :mitgliedschaften="MITGLIEDSCHAFTEN"
      initial-selected="p1"
      @select="select"
    />
    <div class="flex-1 min-w-0 space-y-4">
      <KontaktDetailHeader :person="person" :org="org" />
      <TabBar v-model="tab" :tabs="tabs" />
      <UCard>
        <KontaktpunktList :kontaktpunkte="kp" :mit-kontext="true" />
        <p class="text-xs text-dimmed mt-3">Aktiver Tab: <b>{{ tab }}</b> — die fachlichen Inhalte je Tab folgen als eigene Bausteine.</p>
      </UCard>
    </div>
  </div>
</template>
