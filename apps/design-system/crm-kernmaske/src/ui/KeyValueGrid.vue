<script setup lang="ts">
// Label/Wert-Raster (Stammdaten-Ansicht u. a.). Vorher 2× als .kv-Markup inline.
withDefaults(defineProps<{
  items: { label: string; value: unknown }[]
  cols?: 1 | 2
  labelWidth?: string
}>(), { cols: 2, labelWidth: 'w-40' })
const fmt = (v: unknown) => Array.isArray(v) ? v.join(', ') : (v ?? '—')
</script>

<template>
  <dl class="grid gap-x-8 gap-y-2 text-sm" :class="cols === 2 ? 'sm:grid-cols-2' : 'grid-cols-1'">
    <div v-for="it in items" :key="it.label" class="flex gap-2">
      <dt class="text-muted shrink-0" :class="labelWidth">{{ it.label }}</dt>
      <dd class="text-default m-0">{{ fmt(it.value) }}</dd>
    </div>
  </dl>
</template>
