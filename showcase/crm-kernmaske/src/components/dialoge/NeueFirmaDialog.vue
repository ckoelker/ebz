<script setup lang="ts">
import { ref, reactive } from 'vue'
import { LOOKUPS } from '../../mock/data'
import FeldRenderer from '../bausteine/FeldRenderer.vue'

// Neuanlage Firma mit „Daten ziehen" (VIES / Impressum / LLM-Anreicherung — hier Mock):
// USt-IdNr. eingeben → Felder vorbefüllt. Pflicht minimal: Name + (1 Kanal/Adresse/Person).
const props = defineProps<{ open?: boolean }>()
const open = ref(props.open ?? true)
const ziehe = ref(false)

const form = reactive<Record<string, unknown>>({
  name: '', ustId: '', rechtsform: '', unternehmenstyp: '', website: '', telefon: '',
})

const fName = { key: 'name', label: 'Name', typ: 'text' as const, required: true }
const fUst = { key: 'ustId', label: 'USt-IdNr.', typ: 'text' as const, hint: 'für „Daten ziehen"' }
const fRf = { key: 'rechtsform', label: 'Rechtsform', typ: 'text' as const }
const fTyp = { key: 'unternehmenstyp', label: 'Unternehmenstyp', typ: 'select' as const, items: LOOKUPS.unternehmenstyp }
const fWeb = { key: 'website', label: 'Website', typ: 'text' as const }
const fTel = { key: 'telefon', label: 'Telefon (1 Kanal Pflicht)', typ: 'text' as const }

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
  <div>
    <UButton color="primary" icon="i-lucide-building-2" @click="open = true">Firma anlegen</UButton>

    <UModal v-model:open="open" title="Neue Firma anlegen" :ui="{ content: 'max-w-2xl' }">
      <template #body>
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
            <FeldRenderer :field="fName" v-model="form.name" />
            <FeldRenderer :field="fRf" v-model="form.rechtsform" />
            <FeldRenderer :field="fTyp" v-model="form.unternehmenstyp" />
            <FeldRenderer :field="fWeb" v-model="form.website" />
            <FeldRenderer :field="fTel" v-model="form.telefon" />
          </div>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end w-full gap-2">
          <UButton color="neutral" variant="ghost" @click="open = false">Abbrechen</UButton>
          <UButton color="primary" icon="i-lucide-check" :disabled="!form.name" @click="open = false">Anlegen</UButton>
        </div>
      </template>
    </UModal>
  </div>
</template>
