<script setup lang="ts">
import { healthColor, healthLabel } from '../../domain/severity'
import type { Prozess } from '../../domain/types'
import HealthDot from '@crm-ui/ui/HealthDot.vue'

// Überblick laufender automatisierter Prozesse (Showcase-Strecken) mit Health-Dot
// und „→ Eingriff"-Sprung, wenn die Automatik einen HITL braucht.
defineProps<{ prozesse: Prozess[] }>()
const emit = defineEmits<{ (e: 'eingriff', id: string): void }>()
</script>

<template>
  <div class="divide-y divide-default rounded-lg ring-1 ring-default bg-default overflow-hidden">
    <div
      v-for="p in prozesse"
      :key="p.id"
      class="flex items-center gap-4 px-4 py-3"
    >
      <div class="text-xl w-7 text-center">{{ p.icon }}</div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <span class="font-medium text-highlighted truncate">{{ p.name }}</span>
          <HealthDot :health="p.health" />
        </div>
        <div class="text-sm text-muted truncate">{{ p.detail }}</div>
      </div>
      <div class="text-right shrink-0">
        <UBadge :color="healthColor(p.health)" variant="soft" size="sm">{{ p.kennzahl }}</UBadge>
        <div class="text-[11px] text-dimmed mt-1">{{ p.last }}</div>
      </div>
      <UButton
        v-if="p.health !== 'ok'"
        color="primary"
        variant="soft"
        size="sm"
        trailing-icon="i-lucide-arrow-right"
        @click="emit('eingriff', p.id)"
      >
        Eingriff
      </UButton>
      <UBadge v-else color="success" variant="subtle" size="sm">{{ healthLabel(p.health) }}</UBadge>
    </div>
  </div>
</template>
