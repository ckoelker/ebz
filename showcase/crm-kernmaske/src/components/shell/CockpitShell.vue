<script setup lang="ts">
import { ref } from 'vue'
import Topbar from './Topbar.vue'
import Rail from './Rail.vue'

// Gesamt-Shell des Betriebs-Cockpits: Topbar + linke Schiene (konstant) + Main.
// Eine Shell für alle Ansichten (Dashboard/Eingriffe/Sonderfälle/Prozesse/Kundenstamm) —
// kein View-Bruch; der Inhalt wird über den #default-Slot bzw. das `active`-Modell gesteuert.
const active = ref('dashboard')
const titel: Record<string, string> = {
  dashboard: 'Dashboard',
  eingriffe: 'Eingriffe (Human-in-the-loop)',
  sonderfaelle: 'Sonderfälle',
  prozesse: 'Automatisierte Prozesse',
  kundenstamm: 'Kundenstamm',
}
</script>

<template>
  <div class="h-[760px] flex flex-col ring-1 ring-default rounded-lg overflow-hidden bg-muted">
    <Topbar @select="active = 'kundenstamm'" />
    <div class="flex-1 flex min-h-0">
      <Rail :active="active" :kundenstamm-aktiv="active === 'kundenstamm'" @nav="active = $event" />
      <main class="flex-1 min-w-0 overflow-auto">
        <div class="px-6 py-4 border-b border-default bg-default flex items-center gap-3">
          <h1 class="text-lg font-semibold text-highlighted">{{ titel[active] }}</h1>
          <slot name="toolbar" :active="active" />
        </div>
        <div class="p-6">
          <slot :active="active">
            <p class="text-muted">Inhalt für „{{ titel[active] }}".</p>
          </slot>
        </div>
      </main>
    </div>
  </div>
</template>
