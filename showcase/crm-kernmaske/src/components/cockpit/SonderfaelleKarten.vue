<script setup lang="ts">
import { SONDERFAELLE } from '../../mock/data'
import { prioColor } from '../../ui'
import type { Sonderfall } from '../../types'

// Nicht automatisierte Vorgänge, die manuell abgearbeitet werden (inkl. Fälligkeit/Priorität).
defineProps<{ faelle?: Sonderfall[] }>()
const emit = defineEmits<{ (e: 'erledigt', id: string): void; (e: 'oeffnen', id: string): void }>()
</script>

<template>
  <div class="flex flex-col gap-2">
    <div
      v-for="s in (faelle ?? SONDERFAELLE)"
      :key="s.id"
      class="flex items-center gap-3 px-4 py-3 rounded-lg ring-1 ring-default bg-default"
    >
      <div class="text-lg w-6 text-center">{{ s.icon }}</div>
      <div class="min-w-0 flex-1">
        <div class="font-medium text-highlighted">{{ s.titel }}</div>
        <div class="text-sm text-muted">{{ s.detail }}</div>
      </div>
      <div class="text-right shrink-0">
        <UBadge :color="prioColor(s.prioritaet)" variant="soft" size="sm">{{ s.prioritaet }}</UBadge>
        <div class="text-[11px] text-dimmed mt-1">fällig {{ s.faellig }}</div>
      </div>
      <UButton color="primary" variant="soft" size="sm" icon="i-lucide-check" @click="emit('erledigt', s.id)" />
    </div>
  </div>
</template>
