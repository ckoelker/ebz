<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { PERSONEN, LOOKUPS } from '../../data/mock'
import { personName } from '../../domain/party'
import { personPflichtFelder, type FeldDef } from '../../domain/forms'
import FeldRenderer from '../../ui/FeldRenderer.vue'
import DialogShell from '../../ui/DialogShell.vue'
import Stepper from '../../ui/Stepper.vue'

// Gestufte Neuanlage Person (Pflicht → Marketing → Zugehörigkeit → Rest). Erfassung nur
// positiv (keine Sperren beim Anlegen). Live-Dublettenwarnung beim Namen.
defineProps<{ open?: boolean }>()
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

const marketingFelder: FeldDef[] = [
  { key: 'leadQuelle', label: 'Lead-Quelle', typ: 'select', items: LOOKUPS.leadQuelle },
  { key: 'korrespondenzsprache', label: 'Korrespondenzsprache', typ: 'select', items: LOOKUPS.sprachen.map(s => ({ label: s.name, value: s.code })) },
]
</script>

<template>
  <DialogShell title="Neue Person anlegen" size="xl" trigger-label="Person anlegen" trigger-icon="i-lucide-user-plus" :open="open">
    <div class="space-y-5">
      <Stepper :steps="schritte" :model-value="step" />

      <div v-if="step === 0" class="grid sm:grid-cols-2 gap-4">
        <FeldRenderer v-for="f in personPflichtFelder" :key="f.key" :field="f" v-model="form[f.key]" />
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
        <FeldRenderer v-for="f in marketingFelder" :key="f.key" :field="f" v-model="form[f.key]" />
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

    <template #footer="{ close }">
      <div class="flex items-center w-full gap-2">
        <UButton v-if="step > 0" color="neutral" variant="ghost" @click="step--">Zurück</UButton>
        <div class="ml-auto flex gap-2">
          <UButton color="neutral" variant="ghost" @click="close()">Abbrechen</UButton>
          <UButton v-if="step < schritte.length - 1" color="primary" :disabled="step === 0 && !pflichtOk" @click="step++">Weiter</UButton>
          <UButton v-else color="primary" icon="i-lucide-check" @click="close()">Anlegen</UButton>
        </div>
      </div>
    </template>
  </DialogShell>
</template>
