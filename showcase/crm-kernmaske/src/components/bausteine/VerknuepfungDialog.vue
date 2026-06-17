<script setup lang="ts">
import { ref, computed } from 'vue'
import { PERSONEN, ORGANISATIONEN, personName, initialen, LOOKUPS } from '../../mock/data'

// Beidseitiger Verknüpfungs-Dialog Person↔Firma. Bestandssuche ZUERST (Dublettenschutz):
// erst wenn kein Treffer passt, neu anlegen. Rolle wird beim Verknüpfen gesetzt.
const props = defineProps<{ richtung: 'person-zu-firma' | 'firma-zu-person'; open?: boolean }>()
const emit = defineEmits<{ (e: 'verknuepfen', payload: { id: string; rolle: string }): void; (e: 'close'): void }>()

const open = ref(props.open ?? true)
const q = ref('')
const rolle = ref<string>('')
const gewaehlt = ref<string | null>(null)

const kandidaten = computed(() => {
  const s = q.value.trim().toLowerCase()
  if (props.richtung === 'person-zu-firma') {
    return ORGANISATIONEN.filter(o => !s || o.name.toLowerCase().includes(s))
      .map(o => ({ id: o.id, label: o.name, sub: o.unternehmenstyp ?? '', org: true }))
  }
  return PERSONEN.filter(p => !s || personName(p).toLowerCase().includes(s))
    .map(p => ({ id: p.id, label: personName(p), sub: p.kontaktpunkte[0]?.email ?? p.kontaktpunkte[0]?.nummerAnzeige ?? '', org: false }))
})
const ziel = computed(() => props.richtung === 'person-zu-firma' ? 'Firma' : 'Person')
</script>

<template>
  <div>
    <UButton color="primary" variant="soft" icon="i-lucide-link" @click="open = true">
      {{ ziel }} verknüpfen
    </UButton>

    <UModal v-model:open="open" :title="`${ziel} verknüpfen (Bestand zuerst)`" :ui="{ content: 'max-w-lg' }">
      <template #body>
        <div class="space-y-3">
          <UAlert
            color="info"
            variant="soft"
            icon="i-lucide-info"
            title="Dublettenschutz"
            description="Erst im Bestand suchen. Nur wenn keiner passt, neu anlegen."
          />
          <UInput v-model="q" icon="i-lucide-search" :placeholder="`${ziel} im Bestand suchen …`" autofocus class="w-full" />

          <div class="max-h-56 overflow-auto divide-y divide-default ring-1 ring-default rounded-md">
            <button
              v-for="k in kandidaten"
              :key="k.id"
              class="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-primary-50"
              :class="gewaehlt === k.id ? 'bg-primary-50' : ''"
              @click="gewaehlt = k.id"
            >
              <UAvatar :text="initialen(k.label)" size="2xs" :class="k.org ? 'rounded-md' : ''" />
              <div class="min-w-0">
                <div class="font-medium text-default truncate">{{ k.label }}</div>
                <div class="text-xs text-muted truncate">{{ k.sub }}</div>
              </div>
              <UIcon v-if="gewaehlt === k.id" name="i-lucide-check" class="size-4 text-primary-600 ml-auto" />
            </button>
          </div>

          <USelect v-model="rolle" :items="LOOKUPS.rollen" placeholder="Rolle der Verknüpfung …" class="w-full" />
        </div>
      </template>

      <template #footer>
        <div class="flex items-center w-full gap-2">
          <UButton color="neutral" variant="ghost" icon="i-lucide-plus" size="sm">{{ ziel }} neu anlegen</UButton>
          <div class="ml-auto flex gap-2">
            <UButton color="neutral" variant="ghost" @click="open = false; emit('close')">Abbrechen</UButton>
            <UButton
              color="primary"
              icon="i-lucide-link"
              :disabled="!gewaehlt || !rolle"
              @click="emit('verknuepfen', { id: gewaehlt!, rolle }); open = false"
            >
              Verknüpfen
            </UButton>
          </div>
        </div>
      </template>
    </UModal>
  </div>
</template>
