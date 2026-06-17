<script setup lang="ts">
import { reactive, computed } from 'vue'
import { MITARBEITER, GRUPPEN } from '../../data/mock'
import DialogShell from '../../ui/DialogShell.vue'

// Wiedervorlage anlegen: Betreff, Fälligkeit, Zuweisung an Mitarbeiter ODER Gruppe,
// Priorität. Erscheint im Cockpit unter „Sonderfälle / offene Wiedervorlagen".
defineProps<{ open?: boolean }>()
const form = reactive({ betreff: '', faelligAm: '2026-06-20', typAn: 'mitarbeiter' as 'mitarbeiter' | 'gruppe', zugewiesenAn: MITARBEITER.name, prioritaet: 'mittel' as 'hoch' | 'mittel' | 'niedrig' })
const emit = defineEmits<{ (e: 'save', v: typeof form): void }>()

const zuweisungItems = computed(() => form.typAn === 'mitarbeiter' ? [MITARBEITER.name] : GRUPPEN)
const typAnItems = [{ label: 'Mitarbeiter', value: 'mitarbeiter' }, { label: 'Gruppe', value: 'gruppe' }]
const prioItems = [{ label: 'hoch', value: 'hoch' }, { label: 'mittel', value: 'mittel' }, { label: 'niedrig', value: 'niedrig' }]
</script>

<template>
  <DialogShell
    title="Wiedervorlage anlegen"
    size="lg"
    trigger-label="Wiedervorlage"
    trigger-icon="i-lucide-calendar-clock"
    trigger-variant="soft"
    :open="open"
    primary-label="Anlegen"
    primary-icon="i-lucide-check"
    :primary-disabled="!form.betreff"
    @primary="emit('save', form)"
  >
    <div class="space-y-4">
      <UFormField label="Betreff" required><UInput v-model="form.betreff" class="w-full" placeholder="z. B. Inhouse-Angebot nachfassen" /></UFormField>
      <div class="grid sm:grid-cols-2 gap-4">
        <UFormField label="Fällig am"><UInput v-model="form.faelligAm" type="date" class="w-full" /></UFormField>
        <UFormField label="Priorität"><USelect v-model="form.prioritaet" :items="prioItems" class="w-full" /></UFormField>
        <UFormField label="Zuweisen an"><USelect v-model="form.typAn" :items="typAnItems" class="w-full" /></UFormField>
        <UFormField :label="form.typAn === 'gruppe' ? 'Gruppe' : 'Mitarbeiter'">
          <USelect v-model="form.zugewiesenAn" :items="zuweisungItems" class="w-full" />
        </UFormField>
      </div>
    </div>
  </DialogShell>
</template>
