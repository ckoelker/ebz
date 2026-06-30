<script setup lang="ts">
import { ref, computed } from 'vue'
import ChipList from '../../ui/ChipList.vue'

// Tabellengetriebene Mehrfachauswahl (Rollen/Verbände/Schwerpunkte) mit Echtzeit-Filter.
// Spiegelt das „Lookups als eigene Tabellen je Kategorie"-Konzept (im Backend pflegbar).
const props = defineProps<{ options: string[]; modelValue: string[]; label?: string; filterable?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string[]): void }>()

const q = ref('')
const sichtbar = computed(() =>
  !props.filterable || !q.value ? props.options : props.options.filter(o => o.toLowerCase().includes(q.value.toLowerCase())))

function toggle(opt: string) {
  const set = new Set(props.modelValue)
  set.has(opt) ? set.delete(opt) : set.add(opt)
  emit('update:modelValue', props.options.filter(o => set.has(o)))
}
</script>

<template>
  <div>
    <div v-if="label" class="text-sm font-medium text-default mb-1.5">{{ label }}</div>
    <UInput v-if="filterable" v-model="q" icon="i-lucide-search" placeholder="Filtern …" size="sm" class="w-full mb-2" />
    <div class="grid grid-cols-2 gap-x-4 gap-y-1.5 max-h-56 overflow-auto pr-1">
      <UCheckbox
        v-for="opt in sichtbar"
        :key="opt"
        :model-value="modelValue.includes(opt)"
        :label="opt"
        @update:model-value="toggle(opt)"
      />
    </div>
    <ChipList v-if="modelValue.length" :items="modelValue" class="mt-2" />
  </div>
</template>
