<script setup lang="ts">
import { ref, computed } from 'vue'
import Topbar from './Topbar.vue'
import Rail from './Rail.vue'
import {
  PERSONEN, ORGANISATIONEN, MITARBEITER, EINGRIFFE, SONDERFAELLE, QUICKLINKS,
  personById, orgById,
} from '../../data/mock'
import { personName } from '../../domain/party'

// Gesamt-Shell des Betriebs-Cockpits (Container): lädt die Mock-Daten und reicht sie an
// die prop-reinen Shell-Komponenten durch. Eine Shell für alle Ansichten — kein View-Bruch.
const active = ref('dashboard')
const titel: Record<string, string> = {
  dashboard: 'Dashboard',
  eingriffe: 'Eingriffe (Human-in-the-loop)',
  sonderfaelle: 'Sonderfälle',
  prozesse: 'Automatisierte Prozesse',
  kundenstamm: 'Kundenstamm',
}

const quicklinks = computed(() => QUICKLINKS.map(q => q.typ === 'person'
  ? { id: q.id, label: personName(personById(q.id)!), org: false }
  : { id: q.id, label: orgById(q.id)!.name, org: true }))
const railBenutzer = { name: MITARBEITER.name, gruppe: MITARBEITER.gruppen[0] }
const topBenutzer = { name: MITARBEITER.name, rolle: MITARBEITER.rolle }
</script>

<template>
  <div class="h-[760px] flex flex-col ring-1 ring-default rounded-lg overflow-hidden bg-muted">
    <Topbar
      :personen="PERSONEN"
      :organisationen="ORGANISATIONEN"
      :benutzer="topBenutzer"
      @select="active = 'kundenstamm'"
    />
    <div class="flex-1 flex min-h-0">
      <Rail
        :active="active"
        :kundenstamm-aktiv="active === 'kundenstamm'"
        :eingriffe="EINGRIFFE.length"
        :sonderfaelle="SONDERFAELLE.length"
        :quicklinks="quicklinks"
        :benutzer="railBenutzer"
        @nav="active = $event"
      />
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
