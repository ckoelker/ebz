<script setup lang="ts">
import type { Kontaktpunkt } from '../domain/types'
import { kontaktIcon, kontaktpunktLabel } from '../domain/kontaktpunkt'

// Read-only-Anzeige von Kontaktpunkten (≠ KanalEdit = Bearbeitung). Anzeige-Logik
// vorher mehrfach inline.
withDefaults(defineProps<{ kontaktpunkte: Kontaktpunkt[]; mitKontext?: boolean; cols?: 1 | 2 }>(), { cols: 2 })
</script>

<template>
  <div class="grid gap-x-8 gap-y-2 text-sm" :class="cols === 2 ? 'sm:grid-cols-2' : 'grid-cols-1'">
    <div v-for="(k, i) in kontaktpunkte" :key="i" class="flex items-center gap-2">
      <UIcon :name="kontaktIcon(k.typ)" class="size-4 text-muted shrink-0" />
      <span class="text-default truncate">{{ kontaktpunktLabel(k) }}</span>
      <UBadge v-if="mitKontext && k.kontext" color="neutral" variant="subtle" size="sm">{{ k.kontext }}</UBadge>
    </div>
  </div>
</template>
