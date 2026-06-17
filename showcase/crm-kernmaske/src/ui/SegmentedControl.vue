<script setup lang="ts">
import { computed } from 'vue'

// Segment-Umschalter (Alle/Personen/Firmen u. ä.). Generisch, v-model-fähig.
const props = defineProps<{
  modelValue: string
  options: { value: string; label: string }[] | string[]
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
const norm = computed(() => props.options.map(o => typeof o === 'string' ? { value: o, label: o } : o))
</script>

<template>
  <div class="flex gap-1">
    <UButton
      v-for="o in norm"
      :key="o.value"
      :color="modelValue === o.value ? 'primary' : 'neutral'"
      :variant="modelValue === o.value ? 'solid' : 'soft'"
      size="sm"
      class="flex-1 justify-center capitalize"
      @click="emit('update:modelValue', o.value)"
    >
      {{ o.label }}
    </UButton>
  </div>
</template>
