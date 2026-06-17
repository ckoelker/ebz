<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import FeldRenderer, { type FeldDef } from './FeldRenderer.vue'

// Inline-Stammdaten-Pflege (Person ODER Firma): Ansicht ⇄ Bearbeiten mit
// Speichern/Abbrechen. Die Felddefinition kommt von außen, die Werte sind ein
// flaches Objekt — so deckt EINE Komponente beide Entitätstypen ab.
const props = defineProps<{
  titel: string
  felder: FeldDef[]
  werte: Record<string, unknown>
}>()
const emit = defineEmits<{ (e: 'save', v: Record<string, unknown>): void }>()

const edit = ref(false)
const draft = reactive<Record<string, unknown>>({ ...props.werte })

function start() { Object.assign(draft, props.werte); edit.value = true }
function cancel() { edit.value = false }
function save() { emit('save', { ...draft }); edit.value = false }

const anzeige = computed(() => props.felder.map(f => ({
  label: f.label,
  value: f.typ === 'checkbox'
    ? (props.werte[f.key] ? 'ja' : 'nein')
    : (Array.isArray(props.werte[f.key]) ? (props.werte[f.key] as string[]).join(', ') : (props.werte[f.key] ?? '—')),
})))
</script>

<template>
  <UCard :ui="{ header: 'flex items-center gap-2' }">
    <template #header>
      <UIcon name="i-lucide-id-card" class="size-4 text-primary-600" />
      <h3 class="font-semibold text-highlighted">{{ titel }}</h3>
      <UButton v-if="!edit" color="primary" variant="soft" size="sm" icon="i-lucide-pencil" class="ml-auto" @click="start">
        Bearbeiten
      </UButton>
      <div v-else class="ml-auto flex gap-2">
        <UButton color="neutral" variant="ghost" size="sm" @click="cancel">Abbrechen</UButton>
        <UButton color="primary" size="sm" icon="i-lucide-check" @click="save">Speichern</UButton>
      </div>
    </template>

    <div v-if="!edit" class="grid sm:grid-cols-2 gap-x-8 gap-y-2 text-sm">
      <div v-for="a in anzeige" :key="a.label" class="flex gap-2">
        <span class="text-muted w-40 shrink-0">{{ a.label }}</span>
        <span class="text-default">{{ a.value }}</span>
      </div>
    </div>

    <div v-else class="grid sm:grid-cols-2 gap-4">
      <FeldRenderer
        v-for="f in felder"
        :key="f.key"
        :field="f"
        v-model="draft[f.key]"
      />
    </div>
  </UCard>
</template>
