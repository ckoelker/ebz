<script setup lang="ts">
import type { Einwilligung } from '../../types'
import { einwilligungColor } from '../../ui'

// Einwilligungen (DSGVO): Kanal/Zweck/Kontext, Status (erteilt/ausstehend/widerrufen),
// Rechtsgrundlage + Datum. Double-Opt-In: Default AUSSTEHEND bis Bestätigung.
defineProps<{ einwilligungen: Einwilligung[] }>()
</script>

<template>
  <div class="overflow-x-auto ring-1 ring-default rounded-lg">
    <table class="w-full text-sm">
      <thead>
        <tr class="text-left text-xs uppercase text-muted bg-elevated">
          <th class="px-3 py-2 font-medium">Kanal</th>
          <th class="px-3 py-2 font-medium">Zweck</th>
          <th class="px-3 py-2 font-medium">Kontext</th>
          <th class="px-3 py-2 font-medium">Status</th>
          <th class="px-3 py-2 font-medium">Rechtsgrundlage</th>
          <th class="px-3 py-2 font-medium">Datum</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-default">
        <tr v-for="(e, i) in einwilligungen" :key="i" class="bg-default">
          <td class="px-3 py-2 text-default">{{ e.kanal }}</td>
          <td class="px-3 py-2 text-default">{{ e.zweck }}</td>
          <td class="px-3 py-2 text-muted">{{ e.kontext }}</td>
          <td class="px-3 py-2"><UBadge :color="einwilligungColor(e.status)" variant="soft" size="sm">{{ e.status }}</UBadge></td>
          <td class="px-3 py-2 text-muted">{{ e.rechtsgrundlage }}</td>
          <td class="px-3 py-2 text-dimmed whitespace-nowrap">{{ e.datum }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
