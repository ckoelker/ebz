<script setup lang="ts">
// Design-Tokens der EBZ-CRM-Kernmaske: EBZ-Navy als Primary auf dem Nuxt-Default-Theme,
// plus die fachliche Severity-Skala (ok/warn/err) und Badges/Chips.
const navy = [
  ['50', '#e9eff7'], ['100', '#c9d9ec'], ['200', '#94b3d6'], ['300', '#5e8cbf'],
  ['400', '#2f66a3'], ['500', '#0b3a6f'], ['600', '#093460'], ['700', '#082c54'],
  ['800', '#06223f'], ['900', '#05192f'], ['950', '#030f1d'],
] as const
const severities = [
  { label: 'OK / aktiv', color: 'success' as const },
  { label: 'Achtung / ausstehend', color: 'warning' as const },
  { label: 'Fehler / gesperrt', color: 'error' as const },
  { label: 'Info', color: 'info' as const },
  { label: 'Neutral', color: 'neutral' as const },
]
</script>

<template>
  <div class="space-y-8 max-w-3xl">
    <section>
      <h2 class="text-lg font-semibold text-highlighted mb-1">Primärfarbe — EBZ-Navy</h2>
      <p class="text-sm text-muted mb-3">Brand-Navy <code>#0b3a6f</code> als Primary-500 auf dem Nuxt-Default-Theme.</p>
      <div class="grid grid-cols-11 gap-1 rounded-lg overflow-hidden">
        <div v-for="[step, hex] in navy" :key="step" class="text-center">
          <div class="h-12" :style="{ background: hex }" />
          <div class="text-[10px] text-muted mt-1">{{ step }}</div>
        </div>
      </div>
    </section>

    <section>
      <h2 class="text-lg font-semibold text-highlighted mb-3">Buttons</h2>
      <div class="flex flex-wrap gap-2 items-center">
        <UButton color="primary">Speichern</UButton>
        <UButton color="primary" variant="soft">Bearbeiten</UButton>
        <UButton color="primary" variant="outline">Abbrechen</UButton>
        <UButton color="neutral" variant="ghost">Mehr</UButton>
        <UButton color="error" variant="soft" icon="i-lucide-trash-2">Löschen</UButton>
        <UButton color="primary" size="sm" icon="i-lucide-plus">Neu</UButton>
      </div>
    </section>

    <section>
      <h2 class="text-lg font-semibold text-highlighted mb-3">Severity / Badges</h2>
      <div class="flex flex-wrap gap-2">
        <UBadge v-for="s in severities" :key="s.label" :color="s.color" variant="soft">{{ s.label }}</UBadge>
      </div>
    </section>

    <section>
      <h2 class="text-lg font-semibold text-highlighted mb-3">Rollen-Chips (N:M)</h2>
      <div class="flex flex-wrap gap-1.5">
        <UBadge v-for="r in ['Vorstand', 'Seminar-Bucher', 'WEG-Verwalter', 'Aufsichtsrat']" :key="r" color="primary" variant="subtle">{{ r }}</UBadge>
      </div>
    </section>
  </div>
</template>
