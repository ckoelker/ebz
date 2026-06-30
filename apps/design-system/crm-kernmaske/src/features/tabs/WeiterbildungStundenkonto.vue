<script setup lang="ts">
import { computed } from 'vue'
import type { Weiterbildung } from '../../domain/types'

// Weiterbildungspflicht §34c GewO / §15b MaBV: 20 Std. / 3 Jahre. Stundenkonto mit
// Ampel (grün ≥ Soll, gelb ≥ ⅔, rot darunter) + Nachweisliste (EBZ/extern).
const props = defineProps<{ daten: Weiterbildung }>()

const quote = computed(() => Math.min(1, props.daten.istStunden / props.daten.sollStunden))
const ampel = computed<'success' | 'warning' | 'error'>(() =>
  quote.value >= 1 ? 'success' : quote.value >= 2 / 3 ? 'warning' : 'error')
const fehlt = computed(() => Math.max(0, props.daten.sollStunden - props.daten.istStunden))
</script>

<template>
  <UCard :ui="{ header: 'flex items-center gap-2' }">
    <template #header>
      <UIcon name="i-lucide-graduation-cap" class="size-4 text-primary-600" />
      <h3 class="font-semibold text-highlighted">Weiterbildung §34c · {{ daten.zeitraum }}</h3>
      <UBadge :color="ampel" variant="soft" size="sm" class="ml-auto">
        {{ daten.istStunden }} / {{ daten.sollStunden }} Std.
      </UBadge>
    </template>

    <div class="space-y-3">
      <div>
        <UProgress :model-value="daten.istStunden" :max="daten.sollStunden" :color="ampel" />
        <p class="text-sm mt-1" :class="fehlt ? 'text-warning' : 'text-success'">
          <template v-if="fehlt">Es fehlen noch <b>{{ fehlt }} Std.</b> im Zeitraum.</template>
          <template v-else>Pflicht erfüllt.</template>
        </p>
      </div>
      <div class="divide-y divide-default ring-1 ring-default rounded-md">
        <div v-for="n in daten.nachweise" :key="n.titel" class="flex items-center gap-2 px-3 py-2 text-sm">
          <UIcon name="i-lucide-file-check-2" class="size-4 text-muted" />
          <span class="text-default">{{ n.titel }}</span>
          <UBadge color="neutral" variant="subtle" size="sm">{{ n.quelle }}</UBadge>
          <span class="text-dimmed ml-auto">{{ n.jahr }} · {{ n.stunden }} Std.</span>
        </div>
      </div>
    </div>
  </UCard>
</template>
