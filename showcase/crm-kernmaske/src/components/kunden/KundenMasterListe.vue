<script setup lang="ts">
import { ref, computed } from 'vue'
import MasterListItem from './MasterListItem.vue'
import {
  PERSONEN, ORGANISATIONEN, personName, mitgliedschaftenVonPerson, mitgliedschaftenVonOrg, orgById,
} from '../../mock/data'

// Master-Liste des Kundenstamms: Segment-Filter Alle/Personen/Firmen + Sofortsuche.
// Personen zeigen Haupt-Firma·Rolle·Ort, Firmen zeigen Typ·Ort·Personenzahl.
const filter = ref<'alle' | 'personen' | 'firmen'>('alle')
const q = ref('')
const selected = ref<string>('p1')
const emit = defineEmits<{ (e: 'select', id: string): void }>()

function hauptInfo(pid: string) {
  const m = mitgliedschaftenVonPerson(pid).find(x => x.hauptzugehoerigkeit && !x.gueltigBis)
  if (!m) return null
  const o = orgById(m.orgId)
  return o ? `${o.name} · ${m.rollen[0] ?? ''}` : null
}

const items = computed(() => {
  const s = q.value.trim().toLowerCase()
  const personen = PERSONEN
    .filter(p => !s || personName(p).toLowerCase().includes(s))
    .map(p => {
      const addr = p.kontaktpunkte.find(k => k.typ === 'ADRESSE')
      return {
        id: p.id, label: personName(p), org: false,
        sub: hauptInfo(p.id) ?? 'Privatkontakt',
        sub2: [addr?.ort, p.geburtsdatum ? '*' + p.geburtsdatum.slice(0, 4) : null].filter(Boolean).join(' · '),
        warn: !!p.unvollstaendig, blocked: !!p.werbesperre,
      }
    })
  const firmen = ORGANISATIONEN
    .filter(o => !s || o.name.toLowerCase().includes(s))
    .map(o => {
      const addr = o.kontaktpunkte.find(k => k.typ === 'ADRESSE')
      const n = mitgliedschaftenVonOrg(o.id).filter(m => !m.gueltigBis).length
      return {
        id: o.id, label: o.name, org: true,
        sub: [o.unternehmenstyp, addr?.ort].filter(Boolean).join(' · '),
        sub2: `${n} Person${n === 1 ? '' : 'en'} verknüpft`,
        warn: false, blocked: false,
      }
    })
  if (filter.value === 'personen') return personen
  if (filter.value === 'firmen') return firmen
  return [...personen, ...firmen]
})

function pick(id: string) {
  selected.value = id
  emit('select', id)
}
</script>

<template>
  <div class="w-80 shrink-0 bg-default ring-1 ring-default rounded-lg overflow-hidden flex flex-col h-[600px]">
    <div class="p-2 border-b border-default space-y-2 sticky top-0 bg-default">
      <div class="flex gap-1">
        <UButton
          v-for="f in (['alle', 'personen', 'firmen'] as const)"
          :key="f"
          :color="filter === f ? 'primary' : 'neutral'"
          :variant="filter === f ? 'solid' : 'soft'"
          size="sm"
          class="flex-1 justify-center capitalize"
          @click="filter = f"
        >
          {{ f }}
        </UButton>
      </div>
      <UInput v-model="q" icon="i-lucide-search" placeholder="In Liste filtern …" size="sm" class="w-full" />
    </div>
    <div class="overflow-auto flex-1">
      <MasterListItem
        v-for="it in items"
        :key="it.id"
        v-bind="it"
        :active="selected === it.id"
        @click="pick(it.id)"
      />
    </div>
  </div>
</template>
