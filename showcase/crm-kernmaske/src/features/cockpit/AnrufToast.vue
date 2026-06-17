<script setup lang="ts">
import type { Person, Organisation } from '../../domain/types'
import { personName } from '../../domain/party'
import PartyAvatar from '../../ui/PartyAvatar.vue'

// CTI-Demo (anbieter-neutral, später WebSocket): eingehender Anruf unten rechts.
// Die Auflösung Nummer→Person/Firma macht der Container; diese Komponente zeigt nur an.
const props = defineProps<{ nummerE164: string; person?: Person | null; firma?: Organisation | null }>()
const emit = defineEmits<{ (e: 'oeffnen', id: string): void; (e: 'neu'): void; (e: 'schliessen'): void }>()
</script>

<template>
  <div class="w-80 rounded-xl bg-default ring-1 ring-default shadow-xl overflow-hidden">
    <div class="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white text-sm">
      <UIcon name="i-lucide-phone-incoming" class="size-4 animate-pulse" />
      Eingehender Anruf
      <span class="ml-auto font-mono text-xs opacity-80">{{ nummerE164 }}</span>
    </div>
    <div class="p-4 space-y-3">
      <template v-if="person">
        <div class="flex items-center gap-3">
          <PartyAvatar :name="personName(person)" size="md" />
          <div>
            <div class="font-semibold text-highlighted">{{ personName(person) }}</div>
            <div class="text-sm text-muted">{{ firma?.name ?? 'Privatkontakt' }}</div>
          </div>
        </div>
        <div class="flex gap-2">
          <UButton color="primary" block icon="i-lucide-user" @click="emit('oeffnen', person.id)">Kontakt öffnen</UButton>
          <UButton color="neutral" variant="ghost" icon="i-lucide-x" @click="emit('schliessen')" />
        </div>
      </template>
      <template v-else>
        <div class="flex items-center gap-3">
          <UAvatar icon="i-lucide-user-x" size="md" />
          <div class="text-sm text-muted">Unbekannte Nummer — kein Kontakt gefunden.</div>
        </div>
        <div class="flex gap-2">
          <UButton color="primary" block icon="i-lucide-user-plus" @click="emit('neu')">Neu anlegen</UButton>
          <UButton color="neutral" variant="ghost" icon="i-lucide-x" @click="emit('schliessen')" />
        </div>
      </template>
    </div>
  </div>
</template>
