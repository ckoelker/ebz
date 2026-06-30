<script setup lang="ts">
// Design-Token-Galerie der EBZ-Kundenmarke. Navy-Scale über CSS-Variablen (robust), semantische
// Farben über <UBadge :color> (Nuxt UI löst sie korrekt auf), Radien über literale Klassen.
const navy = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '950']

const semantic = [
  { name: 'primary', desc: 'EBZ-Navy — Hauptaktionen, Links' },
  { name: 'success', desc: 'verfügbar / bezahlt' },
  { name: 'info', desc: 'ausgestellt / neutral-positiv' },
  { name: 'warning', desc: 'in Arbeit / Frist läuft' },
  { name: 'error', desc: 'Problem / fehlgeschlagen' },
  { name: 'neutral', desc: 'Sekundärtext, Rahmen' },
] as const

const radii = ['rounded-sm', 'rounded-md', 'rounded-lg', 'rounded-xl', 'rounded-2xl', 'rounded-full']
</script>

<template>
  <div class="space-y-8 max-w-3xl">
    <section>
      <h3 class="text-lg font-bold mb-3">Primärfarbe — EBZ-Navy</h3>
      <div class="flex flex-wrap gap-2">
        <div v-for="s in navy" :key="s" class="text-center">
          <div class="w-16 h-12 rounded-md ring-1 ring-default" :style="{ background: `var(--color-navy-${s})` }" />
          <div class="text-xs mt-1 text-muted">{{ s }}</div>
        </div>
      </div>
    </section>

    <section>
      <h3 class="text-lg font-bold mb-3">Semantische Farben (Nuxt UI)</h3>
      <div class="space-y-2">
        <div v-for="c in semantic" :key="c.name" class="flex items-center gap-3">
          <UBadge :color="c.name" variant="solid" size="lg" class="w-28 justify-center">{{ c.name }}</UBadge>
          <span class="text-sm text-muted">{{ c.desc }}</span>
        </div>
      </div>
    </section>

    <section>
      <h3 class="text-lg font-bold mb-3">Radien</h3>
      <div class="flex flex-wrap gap-4">
        <div v-for="r in radii" :key="r" class="text-center">
          <div class="w-16 h-16 bg-primary" :class="r" />
          <div class="text-xs mt-1 text-muted">{{ r }}</div>
        </div>
      </div>
    </section>
  </div>
</template>
