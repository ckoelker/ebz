<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { PERSONEN, personName, LOOKUPS } from '../../mock/data'
import FeldRenderer from '../bausteine/FeldRenderer.vue'

// Gestufte Neuanlage Person (Pflicht → Marketing → Zugehörigkeit → Rest). Erfassung nur
// positiv (keine Sperren beim Anlegen). Live-Dublettenwarnung beim Namen.
const props = defineProps<{ open?: boolean }>()
const open = ref(props.open ?? true)
const step = ref(0)
const schritte = ['Pflichtangaben', 'Marketing/Quelle', 'Zugehörigkeit', 'Weitere']

const form = reactive<Record<string, unknown>>({
  vorname: '', nachname: '', geschlecht: '', email: '', leadQuelle: '', titel: '', korrespondenzsprache: 'DE',
})

const dubletten = computed(() => {
  const n = String(form.nachname).trim().toLowerCase()
  if (n.length < 2) return []
  return PERSONEN.filter(p => p.nachname.toLowerCase().includes(n)).map(personName)
})
const pflichtOk = computed(() => form.vorname && form.nachname && form.geschlecht && form.email)

const f = {
  vorname: { key: 'vorname', label: 'Vorname', typ: 'text' as const, required: true },
  nachname: { key: 'nachname', label: 'Nachname', typ: 'text' as const, required: true },
  geschlecht: { key: 'geschlecht', label: 'Geschlecht', typ: 'select' as const, required: true, items: LOOKUPS.geschlecht.map(g => ({ label: g.bezeichnung, value: g.code })) },
  titel: { key: 'titel', label: 'Titel', typ: 'text' as const },
  email: { key: 'email', label: 'E-Mail (1 Kontaktpunkt Pflicht)', typ: 'text' as const, required: true },
  leadQuelle: { key: 'leadQuelle', label: 'Lead-Quelle', typ: 'select' as const, items: LOOKUPS.leadQuelle },
  sprache: { key: 'korrespondenzsprache', label: 'Korrespondenzsprache', typ: 'select' as const, items: LOOKUPS.sprachen.map(s => ({ label: s.name, value: s.code })) },
}
</script>

<template>
  <div>
    <UButton color="primary" icon="i-lucide-user-plus" @click="open = true">Person anlegen</UButton>

    <UModal v-model:open="open" title="Neue Person anlegen" :ui="{ content: 'max-w-2xl' }">
      <template #body>
        <div class="space-y-5">
          <ol class="flex items-center gap-2 text-sm">
            <li v-for="(s, i) in schritte" :key="s" class="flex items-center gap-2">
              <span
                class="size-6 rounded-full flex items-center justify-center text-xs font-semibold"
                :class="i <= step ? 'bg-primary-500 text-white' : 'bg-elevated text-muted'"
              >{{ i + 1 }}</span>
              <span :class="i === step ? 'text-highlighted font-medium' : 'text-muted'">{{ s }}</span>
              <UIcon v-if="i < schritte.length - 1" name="i-lucide-chevron-right" class="size-4 text-dimmed" />
            </li>
          </ol>

          <div v-if="step === 0" class="grid sm:grid-cols-2 gap-4">
            <FeldRenderer :field="f.titel" v-model="form.titel" />
            <div />
            <FeldRenderer :field="f.vorname" v-model="form.vorname" />
            <FeldRenderer :field="f.nachname" v-model="form.nachname" :error="dubletten.length ? undefined : undefined" />
            <FeldRenderer :field="f.geschlecht" v-model="form.geschlecht" />
            <FeldRenderer :field="f.email" v-model="form.email" />
            <UAlert
              v-if="dubletten.length"
              class="sm:col-span-2"
              color="warning"
              variant="soft"
              icon="i-lucide-alert-triangle"
              title="Mögliche Dublette"
              :description="`Bereits im Bestand: ${dubletten.join(', ')}. Bitte prüfen, ob es dieselbe Person ist.`"
            />
          </div>

          <div v-else-if="step === 1" class="grid sm:grid-cols-2 gap-4">
            <FeldRenderer :field="f.leadQuelle" v-model="form.leadQuelle" />
            <FeldRenderer :field="f.sprache" v-model="form.korrespondenzsprache" />
            <p class="sm:col-span-2 text-xs text-dimmed">Werbe-/Auskunftssperren werden bei der Erfassung NICHT gesetzt — nur positive Angaben. Einwilligungen laufen über Double-Opt-In.</p>
          </div>

          <div v-else-if="step === 2" class="space-y-2">
            <p class="text-sm text-muted">Optional: Person direkt mit einer Firma verknüpfen (N:M, mit Rolle). Bestand zuerst durchsuchen.</p>
            <UButton color="primary" variant="soft" icon="i-lucide-link">Firma verknüpfen …</UButton>
          </div>

          <div v-else class="text-sm text-muted">
            Weitere Angaben (Geburtsdatum, Adresse, Staatsangehörigkeit) können später inline ergänzt werden — „unvollständig speichern" ist erlaubt.
          </div>
        </div>
      </template>

      <template #footer>
        <div class="flex items-center w-full gap-2">
          <UButton v-if="step > 0" color="neutral" variant="ghost" @click="step--">Zurück</UButton>
          <div class="ml-auto flex gap-2">
            <UButton color="neutral" variant="ghost" @click="open = false">Abbrechen</UButton>
            <UButton v-if="step < schritte.length - 1" color="primary" :disabled="step === 0 && !pflichtOk" @click="step++">Weiter</UButton>
            <UButton v-else color="primary" icon="i-lucide-check" @click="open = false">Anlegen</UButton>
          </div>
        </div>
      </template>
    </UModal>
  </div>
</template>
