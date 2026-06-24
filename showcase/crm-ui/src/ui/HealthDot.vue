<script setup lang="ts">
import type { Health } from '../domain/types';

// Health-Indikator (grün/gelb/rot). Optional mit Label.
defineProps<{ health: Health; label?: string }>();
const cls: Record<Health, string> = { ok: 'bg-success', warn: 'bg-warning', err: 'bg-error' };
</script>

<template>
  <span class="inline-flex items-center gap-1.5">
    <span class="size-2 rounded-full shrink-0" :class="cls[health]" />
    <span v-if="label" class="text-sm text-muted">{{ label }}</span>
  </span>
</template>
