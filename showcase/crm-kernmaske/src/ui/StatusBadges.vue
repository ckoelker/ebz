<script setup lang="ts">
import type { Status } from '../domain/types'
import { statusColor } from '../domain/severity'

// Status + DSGVO-Sperren/Vollständigkeit als Badge-Reihe. Wiederverwendbar in
// Detailkopf UND Listen.
defineProps<{
  status?: Status
  werbesperre?: boolean
  auskunftssperre?: boolean
  unvollstaendig?: boolean
}>()
</script>

<template>
  <span class="inline-flex items-center gap-2 flex-wrap">
    <UBadge v-if="status" :color="statusColor(status)" variant="soft" size="sm">{{ status }}</UBadge>
    <UBadge v-if="werbesperre" color="error" variant="soft" size="sm" icon="i-lucide-ban">Werbesperre</UBadge>
    <UBadge v-if="auskunftssperre" color="error" variant="soft" size="sm">Auskunftssperre</UBadge>
    <UBadge v-if="unvollstaendig" color="warning" variant="soft" size="sm">unvollständig</UBadge>
  </span>
</template>
