<script setup lang="ts">
import { ref, computed } from 'vue'

// DSGVO Art. 17: „Recht auf Vergessen". Kein Hard-Delete, sondern Sperren +
// geplante Anonymisierung nach Aufbewahrungsfrist (inkl. Envers-Purge). Rolle
// crm-datenschutz. Bewusste Bestätigung erforderlich.
const props = defineProps<{ open?: boolean; name?: string }>()
const open = ref(props.open ?? true)
const verstanden = ref(false)
const emit = defineEmits<{ (e: 'bestaetigen'): void }>()

const beschreibung = computed(() => {
  const wer = props.name ? `Der Datensatz „${props.name}“ ` : 'Der Datensatz '
  return wer + 'wird sofort gesperrt und nach Ablauf der gesetzlichen Aufbewahrungsfrist '
    + '(z. B. §147 AO) automatisch anonymisiert — inkl. Historie (Envers-Purge). Kein sofortiges Hard-Delete.'
})
</script>

<template>
  <div>
    <UButton color="error" variant="soft" icon="i-lucide-shield-x" @click="open = true">Recht auf Vergessen</UButton>

    <UModal v-model:open="open" title="Recht auf Vergessen (DSGVO Art. 17)" :ui="{ content: 'max-w-lg' }">
      <template #body>
        <div class="space-y-4">
          <UAlert
            color="error"
            variant="soft"
            icon="i-lucide-alert-octagon"
            title="Sperren + geplante Anonymisierung"
            :description="beschreibung"
          />
          <ul class="text-sm text-muted list-disc pl-5 space-y-1">
            <li>Sofort: Sperre für Werbung/Verarbeitung, Ausschluss aus Selektionen.</li>
            <li>Nach Frist: Anonymisierung der Stamm- und Bewegungsdaten.</li>
            <li>Aktion ist protokollpflichtig (Rolle <code>crm-datenschutz</code>).</li>
          </ul>
          <UCheckbox v-model="verstanden" label="Ich habe die Konsequenzen verstanden und bin berechtigt." />
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end w-full gap-2">
          <UButton color="neutral" variant="ghost" @click="open = false">Abbrechen</UButton>
          <UButton color="error" icon="i-lucide-shield-x" :disabled="!verstanden" @click="emit('bestaetigen'); open = false">
            Sperren & Anonymisierung planen
          </UButton>
        </div>
      </template>
    </UModal>
  </div>
</template>
