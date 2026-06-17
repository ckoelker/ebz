<script setup lang="ts">
import { ref } from 'vue'
import { EINGRIFFE, SONDERFAELLE, QUICKLINKS, personById, orgById, personName, initialen, MITARBEITER } from '../../mock/data'

// Linke Cockpit-Schiene: Navigation mit Zähler-Badges, Subnav „+ Person/+ Firma"
// (nur bei aktivem Kundenstamm), Quicklinks „Zuletzt aufgerufen", Profil unten.
const props = defineProps<{ active?: string; kundenstammAktiv?: boolean }>()
const emit = defineEmits<{ (e: 'nav', key: string): void; (e: 'open', id: string): void; (e: 'neu', typ: 'person' | 'firma'): void }>()

const nav = ref([
  { key: 'dashboard', label: 'Dashboard', icon: 'i-lucide-layout-dashboard' },
  { key: 'eingriffe', label: 'Eingriffe', icon: 'i-lucide-hand', badge: EINGRIFFE.length, badgeColor: 'error' as const },
  { key: 'sonderfaelle', label: 'Sonderfälle', icon: 'i-lucide-clipboard-list', badge: SONDERFAELLE.length, badgeColor: 'warning' as const },
  { key: 'prozesse', label: 'Automatisierte Prozesse', icon: 'i-lucide-workflow' },
  { key: 'kundenstamm', label: 'Kundenstamm', icon: 'i-lucide-users' },
])

const quicklinks = QUICKLINKS.map(q => {
  if (q.typ === 'person') {
    const p = personById(q.id)!
    return { id: q.id, label: personName(p), org: false }
  }
  const o = orgById(q.id)!
  return { id: q.id, label: o.name, org: true }
})
</script>

<template>
  <nav class="w-60 shrink-0 h-full bg-default ring-1 ring-default p-3 flex flex-col gap-1 overflow-auto">
    <template v-for="n in nav" :key="n.key">
      <button
        class="w-full flex items-center gap-2.5 px-2.5 py-2 rounded-md text-sm font-medium hover:bg-elevated"
        :class="props.active === n.key ? 'bg-primary-50 text-primary-700 ring-1 ring-primary-200' : 'text-default'"
        @click="emit('nav', n.key)"
      >
        <UIcon :name="n.icon" class="size-4" />
        <span>{{ n.label }}</span>
        <UBadge v-if="n.badge" :color="n.badgeColor" variant="soft" size="sm" class="ml-auto">{{ n.badge }}</UBadge>
      </button>

      <div v-if="n.key === 'kundenstamm' && props.kundenstammAktiv" class="ml-7 flex flex-col gap-0.5 mb-1">
        <button class="flex items-center gap-2 px-2 py-1.5 rounded-md text-sm text-muted hover:bg-elevated" @click="emit('neu', 'person')">
          <UIcon name="i-lucide-user-plus" class="size-3.5" /> Person anlegen
        </button>
        <button class="flex items-center gap-2 px-2 py-1.5 rounded-md text-sm text-muted hover:bg-elevated" @click="emit('neu', 'firma')">
          <UIcon name="i-lucide-building-2" class="size-3.5" /> Firma anlegen
        </button>
      </div>
    </template>

    <div class="text-[11px] uppercase tracking-wide text-dimmed mt-4 mb-1 px-1">Zuletzt aufgerufen</div>
    <button
      v-for="ql in quicklinks"
      :key="ql.id"
      class="flex items-center gap-2 px-2 py-1.5 rounded-md text-sm hover:bg-primary-50"
      @click="emit('open', ql.id)"
    >
      <UAvatar :text="initialen(ql.label)" size="2xs" :class="ql.org ? 'rounded-md' : ''" />
      <span class="truncate">{{ ql.label }}</span>
    </button>

    <div class="mt-auto pt-3 flex items-center gap-2 text-sm border-t border-default">
      <UAvatar :text="MITARBEITER.kuerzel" size="xs" />
      <div class="leading-tight">
        <div class="font-medium">{{ MITARBEITER.name }}</div>
        <div class="text-[11px] text-muted">{{ MITARBEITER.gruppen[0] }}</div>
      </div>
    </div>
  </nav>
</template>
