<script setup lang="ts">
import { ref, computed } from 'vue'
import { PERSONEN, ORGANISATIONEN, personName, initialen, MITARBEITER } from '../../mock/data'

// Globale Sofortsuche mit „/"-Shortcut + Ergebnis-Dropdown (Personen & Firmen),
// Anruf-Trigger (CTI-Demo) und User-Badge mit CRM-Rolle.
const q = ref('')
const open = ref(false)

const treffer = computed(() => {
  const s = q.value.trim().toLowerCase()
  if (!s) return [] as { id: string; label: string; sub: string; org: boolean }[]
  const ps = PERSONEN.filter(p => personName(p).toLowerCase().includes(s))
    .map(p => ({ id: p.id, label: personName(p), sub: 'Person', org: false }))
  const os = ORGANISATIONEN.filter(o => o.name.toLowerCase().includes(s))
    .map(o => ({ id: o.id, label: o.name, sub: o.unternehmenstyp ?? 'Firma', org: true }))
  return [...ps, ...os].slice(0, 8)
})

const emit = defineEmits<{ (e: 'select', id: string): void; (e: 'anruf'): void }>()
function pick(id: string) {
  open.value = false
  q.value = ''
  emit('select', id)
}
</script>

<template>
  <header class="h-14 flex items-center gap-4 px-4 bg-primary-500 text-white">
    <div class="font-extrabold tracking-wide leading-tight">
      EBZ <span class="font-normal opacity-80">CRM</span>
      <div class="text-[10px] font-normal opacity-70 -mt-1">Akademie der Immobilienwirtschaft</div>
    </div>

    <div class="relative flex-1 max-w-xl">
      <UInput
        v-model="q"
        icon="i-lucide-search"
        placeholder="Personen & Firmen suchen …  ( / )"
        class="w-full"
        :ui="{ base: 'rounded-full' }"
        @focus="open = true"
        @update:model-value="open = true"
      />
      <div
        v-if="open && treffer.length"
        class="absolute top-11 inset-x-0 z-50 bg-default text-default rounded-lg shadow-lg ring ring-default max-h-80 overflow-auto"
      >
        <button
          v-for="t in treffer"
          :key="t.id"
          class="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-elevated border-b border-default last:border-0"
          @click="pick(t.id)"
        >
          <UAvatar :text="initialen(t.label)" size="2xs" :class="t.org ? 'rounded-md' : ''" />
          <span class="font-medium">{{ t.label }}</span>
          <span class="text-xs text-muted ml-auto">{{ t.sub }}</span>
        </button>
      </div>
    </div>

    <div class="flex-1" />

    <UButton
      color="neutral"
      variant="ghost"
      icon="i-lucide-phone-incoming"
      class="text-white hover:bg-white/10"
      @click="emit('anruf')"
    >
      Anruf simulieren
    </UButton>

    <div class="flex items-center gap-2 text-sm">
      <UAvatar :text="MITARBEITER.kuerzel" size="xs" />
      <div class="leading-tight">
        <div>{{ MITARBEITER.name }}</div>
        <div class="text-[10px] opacity-80">{{ MITARBEITER.rolle }}</div>
      </div>
    </div>
  </header>
</template>
