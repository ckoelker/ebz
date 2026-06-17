<script setup lang="ts">
import { computed } from 'vue'
import type { Person, Organisation } from '../../types'
import { personName, initialen } from '../../mock/data'
import { statusColor } from '../../ui'

// Detail-Kopf für Person ODER Firma: Avatar, Titelzeile, Status-/Sperr-Badges,
// abgeleitete Briefanrede (Person), Primäraktionen (In-Tab-Edit, Anlegen, …).
const props = defineProps<{ person?: Person; org?: Organisation }>()

const istFirma = computed(() => !!props.org)
const titel = computed(() => props.org ? props.org.name : props.person ? personName(props.person) : '')
const status = computed(() => (props.org ?? props.person)!.status)

const anrede = computed(() => {
  if (!props.person) return ''
  const p = props.person
  const g = p.geschlecht
  const titelTeil = p.titel ? p.titel + ' ' : ''
  if (g === 'W') return `Sehr geehrte Frau ${titelTeil}${p.nachname}`
  if (g === 'M') return `Sehr geehrter Herr ${titelTeil}${p.nachname}`
  return `Hallo ${p.vorname} ${p.nachname}` // divers / o.A. → neutraler Fallback
})

const meta = computed<string[]>(() => {
  const roh = props.org
    ? [props.org.rechtsform, props.org.unternehmenstyp, props.org.ustId]
    : props.person
      ? [
          props.person.leadQuelle ? 'Quelle: ' + props.person.leadQuelle : null,
          props.person.minderjaehrig ? 'minderjährig' : null,
        ]
      : []
  return roh.filter((x): x is string => !!x)
})
</script>

<template>
  <div class="flex items-start gap-4">
    <UAvatar :text="initialen(titel)" size="lg" :class="istFirma ? 'rounded-lg' : ''" />
    <div class="min-w-0">
      <div class="flex items-center gap-2 flex-wrap">
        <h2 class="text-xl font-bold text-highlighted">{{ titel }}</h2>
        <UBadge :color="statusColor(status)" variant="soft" size="sm">{{ status }}</UBadge>
        <UBadge v-if="person?.werbesperre" color="error" variant="soft" size="sm" icon="i-lucide-ban">Werbesperre</UBadge>
        <UBadge v-if="person?.auskunftssperre" color="error" variant="soft" size="sm">Auskunftssperre</UBadge>
        <UBadge v-if="person?.unvollstaendig" color="warning" variant="soft" size="sm">unvollständig</UBadge>
      </div>
      <div class="text-sm text-muted mt-1 flex gap-2 flex-wrap">
        <span v-for="m in meta" :key="m">{{ m }}</span>
      </div>
      <div v-if="anrede" class="text-sm text-dimmed mt-1 italic">Briefanrede: „{{ anrede }} …"</div>
    </div>
    <div class="ml-auto flex gap-2 shrink-0">
      <slot name="actions">
        <UButton color="primary" size="sm" icon="i-lucide-pencil">Bearbeiten</UButton>
      </slot>
    </div>
  </div>
</template>
