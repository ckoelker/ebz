<script setup lang="ts">
import { EINGRIFFE } from '../../mock/data'
import { healthColor } from '../../ui'
import type { Eingriff } from '../../types'

// Human-in-the-loop: hier hält die Automatik an. Jede Karte zeigt Prozess, Schwere,
// Alter und kontextspezifische Aktionen (Retry/Übernehmen/Mergen/…).
defineProps<{ eingriffe?: Eingriff[] }>()
const emit = defineEmits<{ (e: 'aktion', payload: { id: string; key: string }): void }>()
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2">
    <UCard
      v-for="e in (eingriffe ?? EINGRIFFE)"
      :key="e.id"
      :ui="{ body: 'space-y-2' }"
      class="border-l-4"
      :class="{ 'border-l-error': e.schwere === 'err', 'border-l-warning': e.schwere === 'warn', 'border-l-success': e.schwere === 'ok' }"
    >
      <div class="flex items-center gap-2">
        <UBadge :color="healthColor(e.schwere)" variant="soft" size="sm">{{ e.prozess }}</UBadge>
        <span class="text-xs text-dimmed ml-auto">{{ e.alter }}</span>
      </div>
      <div class="font-medium text-highlighted">{{ e.titel }}</div>
      <p class="text-sm text-muted">{{ e.detail }}</p>
      <div class="flex flex-wrap gap-2 pt-1">
        <UButton
          v-for="a in e.aktionen"
          :key="a.key"
          :color="a.key === 'retry' || a.key === 'merge' || a.key === 'erneut' ? 'primary' : 'neutral'"
          :variant="a.key === 'kontakt' ? 'outline' : 'soft'"
          size="sm"
          @click="emit('aktion', { id: e.id, key: a.key })"
        >
          {{ a.label }}
        </UButton>
      </div>
    </UCard>
  </div>
</template>
