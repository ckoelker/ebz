<script setup lang="ts">
// Ein Eingabefeld, getrieben aus der FeldDef-Konfig. Bindet via vee-validate useField an das
// umgebende useForm (in AngebotPflege) → Validierung kommt aus der generierten zod. errorMessage
// zeigt sowohl zod-Feldfehler als auch server-seitig gesetzte (Cross-Field-400) an.
// UFormField rendert Label + Fehlermeldung; das eigentliche Control ist ein Nuxt-UI-Input.
import { computed } from 'vue';
import { useField } from 'vee-validate';
import type { FeldDef } from '@/bildung';

const props = defineProps<{ field: FeldDef }>();

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const { value, errorMessage } = useField<any>(() => props.field.name);

// USelect erwartet ein veränderbares Array (zod-Enum.options ist readonly).
const optionen = computed(() => (props.field.options ? Array.from(props.field.options) : undefined));
</script>

<template>
  <UFormField :label="field.label" :error="errorMessage" class="mb-3">
    <UCheckbox v-if="field.art === 'checkbox'" v-model="value" :name="field.name" />
    <UInputNumber
      v-else-if="field.art === 'number'"
      v-model="value"
      :name="field.name"
      class="w-full"
      :format-options="{ useGrouping: false }"
    />
    <UTextarea v-else-if="field.art === 'textarea'" v-model="value" :name="field.name" :rows="3" autoresize class="w-full" />
    <USelect
      v-else-if="field.art === 'select'"
      v-model="value"
      :name="field.name"
      :items="optionen"
      placeholder="– wählen –"
      class="w-full"
    />
    <UInput v-else-if="field.art === 'date'" v-model="value" :name="field.name" type="date" class="w-full" />
    <UInput v-else v-model="value" :name="field.name" class="w-full" />
  </UFormField>
</template>
