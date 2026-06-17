<script setup lang="ts">
import { ref, reactive } from 'vue'
import { LOOKUPS } from '../../mock/data'

// Notiz/Aktivität erfassen (Rich-Text als Platzhalter). Wird als Aktivität in der
// Kontakthistorie gespeichert (an Person ODER Firma).
const props = defineProps<{ open?: boolean }>()
const open = ref(props.open ?? true)
const form = reactive({ typ: 'Notiz', betreff: '', text: '' })
const emit = defineEmits<{ (e: 'save', v: typeof form): void }>()
</script>

<template>
  <div>
    <UButton color="primary" variant="soft" icon="i-lucide-sticky-note" @click="open = true">Notiz erfassen</UButton>

    <UModal v-model:open="open" title="Notiz / Aktivität erfassen" :ui="{ content: 'max-w-xl' }">
      <template #body>
        <div class="space-y-4">
          <div class="grid sm:grid-cols-2 gap-4">
            <UFormField label="Typ"><USelect v-model="form.typ" :items="LOOKUPS.aktivitaetTyp" class="w-full" /></UFormField>
            <UFormField label="Betreff" required><UInput v-model="form.betreff" class="w-full" placeholder="Kurzbetreff …" /></UFormField>
          </div>
          <UFormField label="Inhalt" hint="Rich-Text (Demo)">
            <UTextarea v-model="form.text" :rows="5" class="w-full" placeholder="Gesprächsnotiz, Vereinbarungen …" />
          </UFormField>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end w-full gap-2">
          <UButton color="neutral" variant="ghost" @click="open = false">Abbrechen</UButton>
          <UButton color="primary" icon="i-lucide-check" :disabled="!form.betreff" @click="emit('save', form); open = false">Speichern</UButton>
        </div>
      </template>
    </UModal>
  </div>
</template>
