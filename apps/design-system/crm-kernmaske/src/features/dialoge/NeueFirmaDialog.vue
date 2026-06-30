<script setup lang="ts">
import { ref, reactive } from 'vue'
import { LOOKUPS } from '../../data/mock'
import type { FeldDef } from '../../domain/forms'
import FeldRenderer from '../../ui/FeldRenderer.vue'
import DialogShell from '../../ui/DialogShell.vue'

// Neuanlage Firma mit „Daten ziehen" (VIES / Impressum / LLM-Anreicherung — hier Mock):
// USt-IdNr. eingeben → Felder vorbefüllt. Pflicht minimal: Name + (1 Kanal/Adresse/Person).
defineProps<{ open?: boolean }>()
const ziehe = ref(false)

const form = reactive<Record<string, unknown>>({
  name: '', ustId: '', rechtsform: '', unternehmenstyp: '', website: '', telefon: '',
})

const fUst: FeldDef = { key: 'ustId', label: 'USt-IdNr.', typ: 'text', hint: 'für „Daten ziehen"' }
const restFelder: FeldDef[] = [
  { key: 'name', label: 'Name', typ: 'text', required: true },
  { key: 'rechtsform', label: 'Rechtsform', typ: 'text' },
  { key: 'unternehmenstyp', label: 'Unternehmenstyp', typ: 'select', items: LOOKUPS.unternehmenstyp },
  { key: 'website', label: 'Website', typ: 'text' },
  { key: 'telefon', label: 'Telefon (1 Kanal Pflicht)', typ: 'text' },
]

function datenZiehen() {
  ziehe.value = true
  setTimeout(() => {
    Object.assign(form, {
      name: 'Wohnbau Rhein-Ruhr eG', rechtsform: 'eG', unternehmenstyp: 'Genossenschaft (eG)',
      website: 'https://wohnbau-rhein-ruhr.de', telefon: '+49 201 123450',
    })
    ziehe.value = false
  }, 700)
}
</script>

<template>
  <DialogShell
    title="Neue Firma anlegen"
    size="xl"
    trigger-label="Firma anlegen"
    trigger-icon="i-lucide-building-2"
    :open="open"
    primary-label="Anlegen"
    primary-icon="i-lucide-check"
    :primary-disabled="!form.name"
  >
    <div class="space-y-4">
      <div class="flex items-end gap-2">
        <FeldRenderer :field="fUst" v-model="form.ustId" class="flex-1" />
        <UButton color="primary" variant="soft" icon="i-lucide-download" :loading="ziehe" @click="datenZiehen">
          Daten ziehen
        </UButton>
      </div>
      <UAlert
        color="info"
        variant="soft"
        icon="i-lucide-sparkles"
        title="Anreicherung (Mock)"
        description="VIES-Prüfung, Impressums-Scrape und LLM-Vorschläge füllen Stammdaten vor — in der echten SPA hinter einem Interface."
      />
      <div class="grid sm:grid-cols-2 gap-4">
        <FeldRenderer v-for="f in restFelder" :key="f.key" :field="f" v-model="form[f.key]" />
      </div>
    </div>
  </DialogShell>
</template>
