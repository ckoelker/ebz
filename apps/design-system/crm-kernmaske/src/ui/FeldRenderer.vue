<script setup lang="ts">
// Port von AngebotFeld.vue (MDM-Cockpit): rendert EIN Feld typabhängig
// (text/number/date/select/checkbox/textarea) inkl. Label, Pflicht-Stern und Fehler.
// FeldDef (Single Source of Truth) liegt in domain/forms.
import type { FeldDef } from '../domain/forms'
const props = defineProps<{ field: FeldDef; modelValue: unknown; error?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: unknown): void }>()
function set(v: unknown) { emit('update:modelValue', v) }
</script>

<template>
  <UFormField
    :label="field.label"
    :required="field.required"
    :error="error"
    :hint="field.hint"
    :name="field.key"
  >
    <UInput
      v-if="field.typ === 'text'"
      :model-value="modelValue as string"
      :placeholder="field.placeholder"
      class="w-full"
      @update:model-value="set($event)"
    />
    <UInputNumber
      v-else-if="field.typ === 'number'"
      :model-value="modelValue as number"
      class="w-full"
      @update:model-value="set($event)"
    />
    <UInput
      v-else-if="field.typ === 'date'"
      type="date"
      :model-value="modelValue as string"
      class="w-full"
      @update:model-value="set($event)"
    />
    <USelect
      v-else-if="field.typ === 'select'"
      :model-value="modelValue as string"
      :items="field.items"
      :placeholder="field.placeholder ?? 'Bitte wählen …'"
      class="w-full"
      @update:model-value="set($event)"
    />
    <UTextarea
      v-else-if="field.typ === 'textarea'"
      :model-value="modelValue as string"
      :placeholder="field.placeholder"
      :rows="3"
      class="w-full"
      @update:model-value="set($event)"
    />
    <UCheckbox
      v-else-if="field.typ === 'checkbox'"
      :model-value="modelValue as boolean"
      :label="field.placeholder"
      @update:model-value="set($event)"
    />
  </UFormField>
</template>
