<script setup lang="ts">
import { ref, computed } from 'vue'
import DialogShell from '../../ui/DialogShell.vue'

// DSGVO Art. 17: „Recht auf Vergessen". Kein Hard-Delete, sondern Sperren +
// geplante Anonymisierung nach Aufbewahrungsfrist (inkl. Envers-Purge). Rolle
// crm-datenschutz. Bewusste Bestätigung erforderlich.
const props = defineProps<{ open?: boolean; name?: string }>()
const verstanden = ref(false)
const emit = defineEmits<{ (e: 'bestaetigen'): void }>()

const beschreibung = computed(() => {
  const wer = props.name ? `Der Datensatz „${props.name}“ ` : 'Der Datensatz '
  return wer + 'wird sofort gesperrt und nach Ablauf der gesetzlichen Aufbewahrungsfrist '
    + '(z. B. §147 AO) automatisch anonymisiert — inkl. Historie (Envers-Purge). Kein sofortiges Hard-Delete.'
})
</script>

<template>
  <DialogShell
    title="Recht auf Vergessen (DSGVO Art. 17)"
    size="lg"
    trigger-label="Recht auf Vergessen"
    trigger-icon="i-lucide-shield-x"
    trigger-color="error"
    trigger-variant="soft"
    :open="open"
    primary-label="Sperren & Anonymisierung planen"
    primary-icon="i-lucide-shield-x"
    primary-color="error"
    :primary-disabled="!verstanden"
    @primary="emit('bestaetigen')"
  >
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
  </DialogShell>
</template>
