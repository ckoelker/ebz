<script setup lang="ts">
// Tab-Leiste des Detailbereichs mit optionalen Bubble-Zählern (z. B. offene Loginversuche).
defineProps<{ tabs: { key: string; label: string; bubble?: number }[]; modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
</script>

<template>
  <div class="flex gap-1 border-b-2 border-default flex-wrap">
    <button
      v-for="t in tabs"
      :key="t.key"
      class="px-3 py-2 text-sm font-medium rounded-t-md relative -mb-0.5"
      :class="modelValue === t.key
        ? 'text-primary-700 shadow-[inset_0_-2px_0] shadow-primary-600'
        : 'text-muted hover:text-highlighted hover:bg-elevated'"
      @click="emit('update:modelValue', t.key)"
    >
      {{ t.label }}
      <UBadge v-if="t.bubble" color="error" variant="solid" size="sm" class="ml-1 align-middle">{{ t.bubble }}</UBadge>
    </button>
  </div>
</template>
