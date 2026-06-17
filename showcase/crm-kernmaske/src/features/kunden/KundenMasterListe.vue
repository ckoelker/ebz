<script setup lang="ts">
import { ref, computed } from 'vue'
import MasterListItem from './MasterListItem.vue'
import SegmentedControl from '../../ui/SegmentedControl.vue'
import type { Person, Organisation, Mitgliedschaft } from '../../domain/types'
import { buildKundenListe, type KundenFilter } from '../../domain/kundenliste'

// Master-Liste des Kundenstamms: Segment-Filter + Sofortsuche. Daten kommen per Prop
// (entkoppelt); das View-Model baut der Selector buildKundenListe (testbar).
const props = defineProps<{
  personen: Person[]
  organisationen: Organisation[]
  mitgliedschaften: Mitgliedschaft[]
  initialSelected?: string
}>()
const emit = defineEmits<{ (e: 'select', id: string): void }>()

const filter = ref<KundenFilter>('alle')
const q = ref('')
const selected = ref<string>(props.initialSelected ?? '')

const filterOpts = [
  { value: 'alle', label: 'Alle' },
  { value: 'personen', label: 'Personen' },
  { value: 'firmen', label: 'Firmen' },
]
const items = computed(() =>
  buildKundenListe(props.personen, props.organisationen, props.mitgliedschaften, filter.value, q.value))

function pick(id: string) {
  selected.value = id
  emit('select', id)
}
</script>

<template>
  <div class="w-80 shrink-0 bg-default ring-1 ring-default rounded-lg overflow-hidden flex flex-col h-[600px]">
    <div class="p-2 border-b border-default space-y-2 sticky top-0 bg-default">
      <SegmentedControl
        :model-value="filter"
        :options="filterOpts"
        @update:model-value="filter = $event as KundenFilter"
      />
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
