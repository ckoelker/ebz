<script setup lang="ts">
import { reactive } from 'vue'
import type { Mitgliedschaft } from '../../domain/types'
import { LOOKUPS, orgById } from '../../data/mock'
import LookupCheckboxListe from './LookupCheckboxListe.vue'

// N:M-Zugehörigkeit Person↔Firma inline pflegen: Mehrfachrollen (gefiltert), Position,
// Haupt-Flags, Buchungsberechtigung/Rechnungsempfänger, Gültigkeit. „Ausscheiden"
// historisiert (gueltigBis) statt zu löschen.
const props = defineProps<{ mitgliedschaft: Mitgliedschaft }>()
const emit = defineEmits<{ (e: 'save', v: Mitgliedschaft): void; (e: 'ausscheiden', id: string): void }>()

const m = reactive<Mitgliedschaft>({ ...props.mitgliedschaft, rollen: [...props.mitgliedschaft.rollen] })
const org = orgById(props.mitgliedschaft.orgId)
</script>

<template>
  <UCard :ui="{ header: 'flex items-center gap-2' }">
    <template #header>
      <UIcon name="i-lucide-link" class="size-4 text-primary-600" />
      <h3 class="font-semibold text-highlighted">{{ org?.name }}</h3>
      <UBadge v-if="m.gueltigBis" color="neutral" variant="soft" size="sm">ausgeschieden {{ m.gueltigBis }}</UBadge>
      <UBadge v-else-if="m.hauptzugehoerigkeit" color="primary" variant="soft" size="sm">Hauptzugehörigkeit</UBadge>
    </template>

    <div class="space-y-4">
      <LookupCheckboxListe v-model="m.rollen" :options="LOOKUPS.rollen" label="Rollen" filterable />

      <div class="grid sm:grid-cols-2 gap-4">
        <UFormField label="Position"><UInput v-model="m.position" class="w-full" /></UFormField>
        <UFormField label="Abteilung"><UInput v-model="m.abteilung" class="w-full" /></UFormField>
        <UFormField label="Gültig von"><UInput v-model="m.gueltigVon" type="date" class="w-full" /></UFormField>
        <UFormField label="Gültig bis" hint="leer = aktiv"><UInput v-model="m.gueltigBis as string" type="date" class="w-full" /></UFormField>
      </div>

      <div class="flex flex-wrap gap-x-6 gap-y-2">
        <UCheckbox v-model="m.hauptzugehoerigkeit" label="Hauptzugehörigkeit (Person)" />
        <UCheckbox v-model="m.hauptansprechpartner" label="Hauptansprechpartner (Firma)" />
        <UCheckbox v-model="m.buchungsberechtigt" label="buchungsberechtigt" />
        <UCheckbox v-model="m.rechnungsempfaenger" label="Rechnungsempfänger" />
      </div>
    </div>

    <template #footer>
      <div class="flex items-center gap-2">
        <UButton
          v-if="!m.gueltigBis"
          color="warning"
          variant="soft"
          size="sm"
          icon="i-lucide-log-out"
          @click="emit('ausscheiden', m.id)"
        >
          Ausscheiden (historisieren)
        </UButton>
        <UButton color="primary" size="sm" icon="i-lucide-check" class="ml-auto" @click="emit('save', { ...m })">
          Speichern
        </UButton>
      </div>
    </template>
  </UCard>
</template>
