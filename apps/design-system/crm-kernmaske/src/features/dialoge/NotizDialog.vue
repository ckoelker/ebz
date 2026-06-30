<script setup lang="ts">
import { reactive } from 'vue'
import { LOOKUPS } from '../../data/mock'
import DialogShell from '../../ui/DialogShell.vue'

// Notiz/Aktivität erfassen (Rich-Text als Platzhalter). Wird als Aktivität in der
// Kontakthistorie gespeichert (an Person ODER Firma).
defineProps<{ open?: boolean }>()
const form = reactive({ typ: 'Notiz', betreff: '', text: '' })
const emit = defineEmits<{ (e: 'save', v: typeof form): void }>()
</script>

<template>
  <DialogShell
    title="Notiz / Aktivität erfassen"
    size="lg"
    trigger-label="Notiz erfassen"
    trigger-icon="i-lucide-sticky-note"
    trigger-variant="soft"
    :open="open"
    primary-label="Speichern"
    primary-icon="i-lucide-check"
    :primary-disabled="!form.betreff"
    @primary="emit('save', form)"
  >
    <div class="space-y-4">
      <div class="grid sm:grid-cols-2 gap-4">
        <UFormField label="Typ"><USelect v-model="form.typ" :items="LOOKUPS.aktivitaetTyp" class="w-full" /></UFormField>
        <UFormField label="Betreff" required><UInput v-model="form.betreff" class="w-full" placeholder="Kurzbetreff …" /></UFormField>
      </div>
      <UFormField label="Inhalt" hint="Rich-Text (Demo)">
        <UTextarea v-model="form.text" :rows="5" class="w-full" placeholder="Gesprächsnotiz, Vereinbarungen …" />
      </UFormField>
    </div>
  </DialogShell>
</template>
