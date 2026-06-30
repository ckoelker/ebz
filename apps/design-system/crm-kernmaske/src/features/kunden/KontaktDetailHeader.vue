<script setup lang="ts">
import { computed } from 'vue'
import type { Person, Organisation } from '../../domain/types'
import { personName } from '../../domain/party'
import { briefanrede } from '../../domain/briefanrede'
import CrmKontaktDetailHeader from '@crm-ui/ui/KontaktDetailHeader.vue'

// Domänen-Adapter: leitet aus Person/Organisation die primitiven Kopf-Props ab und delegiert die
// Darstellung an den geteilten, prop-reinen KontaktDetailHeader (@crm-ui) → EINE Layout-Quelle für
// Storybook + mdm. Die abgeleitete Briefanrede/Meta bleibt hier (app-spezifische Geschäftsregel).
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
  <CrmKontaktDetailHeader
    :title="titel"
    :org="istFirma"
    :status="status"
    :werbesperre="person?.werbesperre"
    :auskunftssperre="person?.auskunftssperre"
    :unvollstaendig="person?.unvollstaendig"
    :anrede="anrede"
    :meta="meta"
  >
    <template #actions>
      <slot name="actions">
        <UButton color="primary" size="sm" icon="i-lucide-pencil">Bearbeiten</UButton>
      </slot>
    </template>
  </CrmKontaktDetailHeader>
</template>
