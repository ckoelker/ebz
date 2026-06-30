<script setup lang="ts">
// Schritt-Anzeige für Wizards (gestufte Neuanlage u. ä.). modelValue = aktiver Index.
defineProps<{ steps: string[]; modelValue: number }>()
</script>

<template>
  <ol class="flex items-center gap-2 text-sm flex-wrap">
    <li v-for="(s, i) in steps" :key="s" class="flex items-center gap-2">
      <span
        class="size-6 rounded-full flex items-center justify-center text-xs font-semibold"
        :class="i <= modelValue ? 'bg-primary-500 text-white' : 'bg-elevated text-muted'"
      >{{ i + 1 }}</span>
      <span :class="i === modelValue ? 'text-highlighted font-medium' : 'text-muted'">{{ s }}</span>
      <UIcon v-if="i < steps.length - 1" name="i-lucide-chevron-right" class="size-4 text-dimmed" />
    </li>
  </ol>
</template>
