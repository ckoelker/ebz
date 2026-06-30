<script setup lang="ts">
import { computed } from 'vue'
import PartyAvatar from '@crm-ui/ui/PartyAvatar.vue'

// Linke Cockpit-Schiene: Navigation mit Zähler-Badges, Subnav „+ Person/+ Firma"
// (nur bei aktivem Kundenstamm), Quicklinks „Zuletzt aufgerufen", Profil unten.
// Zähler/Quicklinks/Benutzer kommen per Prop (entkoppelt).
const props = withDefaults(defineProps<{
  active?: string
  kundenstammAktiv?: boolean
  eingriffe?: number
  sonderfaelle?: number
  quicklinks?: { id: string; label: string; org: boolean }[]
  benutzer?: { name: string; gruppe: string }
}>(), { eingriffe: 0, sonderfaelle: 0, quicklinks: () => [], benutzer: () => ({ name: '', gruppe: '' }) })

const emit = defineEmits<{ (e: 'nav', key: string): void; (e: 'open', id: string): void; (e: 'neu', typ: 'person' | 'firma'): void }>()

const nav = computed(() => [
  { key: 'dashboard', label: 'Dashboard', icon: 'i-lucide-layout-dashboard' },
  { key: 'eingriffe', label: 'Eingriffe', icon: 'i-lucide-hand', badge: props.eingriffe, badgeColor: 'error' as const },
  { key: 'sonderfaelle', label: 'Sonderfälle', icon: 'i-lucide-clipboard-list', badge: props.sonderfaelle, badgeColor: 'warning' as const },
  { key: 'prozesse', label: 'Automatisierte Prozesse', icon: 'i-lucide-workflow' },
  { key: 'kundenstamm', label: 'Kundenstamm', icon: 'i-lucide-users' },
])
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
      <PartyAvatar :name="ql.label" :org="ql.org" size="2xs" />
      <span class="truncate">{{ ql.label }}</span>
    </button>

    <div class="mt-auto pt-3 flex items-center gap-2 text-sm border-t border-default">
      <PartyAvatar :name="benutzer.name" size="xs" />
      <div class="leading-tight">
        <div class="font-medium">{{ benutzer.name }}</div>
        <div class="text-[11px] text-muted">{{ benutzer.gruppe }}</div>
      </div>
    </div>
  </nav>
</template>
