<script setup lang="ts">
import { computed } from 'vue'
import type { Person, Organisation } from '../../domain/types'
import { personName } from '../../domain/party'
import { briefanrede } from '../../domain/briefanrede'
import PartyAvatar from '../../ui/PartyAvatar.vue'
import StatusBadges from '../../ui/StatusBadges.vue'

// Detail-Kopf für Person ODER Firma: Avatar, Titelzeile, Status-/Sperr-Badges,
// abgeleitete Briefanrede (Person), Primäraktionen (Slot).
const props = defineProps<{ person?: Person; org?: Organisation }>()

const istFirma = computed(() => !!props.org)
const titel = computed(() => props.org ? props.org.name : props.person ? personName(props.person) : '')
const status = computed(() => (props.org ?? props.person)!.status)
const anrede = computed(() => props.person ? briefanrede(props.person) : '')

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
    <PartyAvatar :name="titel" :org="istFirma" size="lg" />
    <div class="min-w-0">
      <div class="flex items-center gap-2 flex-wrap">
        <h2 class="text-xl font-bold text-highlighted">{{ titel }}</h2>
        <StatusBadges
          :status="status"
          :werbesperre="person?.werbesperre"
          :auskunftssperre="person?.auskunftssperre"
          :unvollstaendig="person?.unvollstaendig"
        />
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
