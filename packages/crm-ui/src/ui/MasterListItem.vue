<script setup lang="ts">
import PartyAvatar from './PartyAvatar.vue';

// Ein Eintrag der Master-Liste (Person oder Firma) mit Avatar, Identifikations-Infos
// (Firma·Rolle·Ort bzw. Typ·Ort·Personenzahl) und Status-/Sperr-Indikatoren. Prop-rein.
defineProps<{
  label: string;
  sub?: string;
  sub2?: string;
  org?: boolean;
  active?: boolean;
  warn?: boolean;
  blocked?: boolean;
}>();
</script>

<template>
  <button
    class="w-full flex items-center gap-3 px-3 py-2.5 text-left border-b border-default hover:bg-primary-50"
    :class="active ? 'bg-primary-50 shadow-[inset_3px_0_0] shadow-primary-500' : ''"
  >
    <PartyAvatar :name="label" :org="org" size="sm" />
    <div class="min-w-0 flex-1">
      <div class="font-medium text-highlighted truncate flex items-center gap-1.5">
        {{ label }}
        <UIcon v-if="warn" name="i-lucide-alert-triangle" class="size-3.5 text-warning shrink-0" />
        <UIcon v-if="blocked" name="i-lucide-ban" class="size-3.5 text-error shrink-0" />
      </div>
      <div v-if="sub" class="text-xs text-muted truncate">{{ sub }}</div>
      <div v-if="sub2" class="text-[11px] text-dimmed truncate">{{ sub2 }}</div>
    </div>
  </button>
</template>
