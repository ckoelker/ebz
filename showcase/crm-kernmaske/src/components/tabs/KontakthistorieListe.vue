<script setup lang="ts">
import type { Aktivitaet } from '../../types'
import { personById, personName } from '../../mock/data'

// Kontakthistorie (Aktivitäten) als Timeline. Im Firmen-Tab „Kommunikation" wird
// zusätzlich die Person je Aktivität als Chip gezeigt (→ Sprung möglich).
defineProps<{ aktivitaeten: Aktivitaet[]; mitPerson?: boolean }>()
const emit = defineEmits<{ (e: 'person', id: string): void }>()
const icon: Record<string, string> = {
  'Telefon eingehend': 'i-lucide-phone-incoming',
  'Telefon ausgehend': 'i-lucide-phone-outgoing',
  'E-Mail': 'i-lucide-mail',
  'Notiz': 'i-lucide-sticky-note',
  'Brief': 'i-lucide-file-text',
  'Meeting': 'i-lucide-users',
}
</script>

<template>
  <div class="space-y-3">
    <div v-for="a in aktivitaeten" :key="a.id" class="flex gap-3">
      <div class="flex flex-col items-center">
        <div class="size-8 rounded-full bg-primary-50 text-primary-700 flex items-center justify-center shrink-0">
          <UIcon :name="icon[a.typ] ?? 'i-lucide-circle'" class="size-4" />
        </div>
        <div class="w-px flex-1 bg-default mt-1" />
      </div>
      <div class="pb-3 min-w-0 flex-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium text-highlighted">{{ a.betreff }}</span>
          <UBadge color="neutral" variant="subtle" size="sm">{{ a.typ }}</UBadge>
          <UBadge
            v-if="mitPerson && a.personId"
            color="primary"
            variant="soft"
            size="sm"
            class="cursor-pointer"
            @click="emit('person', a.personId!)"
          >
            {{ personName(personById(a.personId)!) }}
          </UBadge>
          <span class="text-xs text-dimmed ml-auto">{{ a.zeitpunkt }} · {{ a.bearbeiter }}</span>
        </div>
        <div class="text-sm text-muted mt-0.5 prose-sm" v-html="a.inhaltHtml" />
        <div v-if="a.anhaenge.length" class="flex gap-1.5 mt-1">
          <UBadge v-for="an in a.anhaenge" :key="an" color="neutral" variant="outline" size="sm" icon="i-lucide-paperclip">{{ an }}</UBadge>
        </div>
      </div>
    </div>
  </div>
</template>
