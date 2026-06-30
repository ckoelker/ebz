<script setup lang="ts">
import DialogShell from '@/components/DialogShell.vue';

// Generische Bestätigung für destruktive Aktionen (Löschen/Ausscheiden/Anonymisieren) — projektweite
// Regel: jede Lösch-/irreversible Aktion fragt vorher nach (siehe Memory „Löschen immer mit Bestätigung").
withDefaults(defineProps<{
  open: boolean;
  title?: string;
  message?: string;
  detail?: string;
  confirmLabel?: string;
  confirmColor?: string;
  confirmIcon?: string;
  loading?: boolean;
}>(), {
  title: 'Wirklich ausführen?',
  confirmLabel: 'Bestätigen',
  confirmColor: 'error',
  confirmIcon: 'i-lucide-trash-2',
});
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'confirm'): void }>();
</script>

<template>
  <DialogShell
    :title="title"
    size="sm"
    :open="open"
    :primary-label="confirmLabel"
    :primary-color="confirmColor"
    :primary-icon="confirmIcon"
    :primary-loading="loading"
    @update:open="emit('update:open', $event)"
    @primary="emit('confirm')"
  >
    <p v-if="message" class="text-sm text-default">{{ message }}</p>
    <p v-if="detail" class="text-xs text-muted mt-2">{{ detail }}</p>
  </DialogShell>
</template>
