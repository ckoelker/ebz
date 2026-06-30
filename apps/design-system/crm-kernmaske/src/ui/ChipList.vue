<script setup lang="ts">
import type { UiColor } from '../domain/severity'

// Badge-/Chip-Liste (Rollen, Verbände, Schwerpunkte …). Vorher mehrfach inline.
withDefaults(defineProps<{
  items: string[]
  color?: UiColor
  variant?: 'solid' | 'outline' | 'soft' | 'subtle'
  size?: 'sm' | 'md'
  empty?: string
}>(), { color: 'primary', variant: 'subtle', size: 'sm', empty: '' })
</script>

<template>
  <div class="flex flex-wrap gap-1">
    <UBadge v-for="i in items" :key="i" :color="color" :variant="variant" :size="size">{{ i }}</UBadge>
    <span v-if="!items.length && empty" class="text-dimmed text-sm">{{ empty }}</span>
  </div>
</template>
