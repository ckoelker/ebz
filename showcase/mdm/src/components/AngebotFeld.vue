<script setup lang="ts">
// Ein Eingabefeld, getrieben aus der FeldDef-Konfig. Bindet via vee-validate useField an das
// umgebende useForm (in AngebotPflege) → Validierung kommt aus der generierten zod. errorMessage
// zeigt sowohl zod-Feldfehler als auch server-seitig gesetzte (Cross-Field-400) an.
import { computed } from 'vue';
import { useField } from 'vee-validate';
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import InputNumber from 'primevue/inputnumber';
import Checkbox from 'primevue/checkbox';
import Select from 'primevue/select';
import type { FeldDef } from '@/bildung';

const props = defineProps<{ field: FeldDef }>();

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const { value, errorMessage } = useField<any>(() => props.field.name);

// PrimeVue Select erwartet ein veränderbares Array (zod-Enum.options ist readonly).
const optionen = computed(() => (props.field.options ? Array.from(props.field.options) : undefined));
</script>

<template>
  <div class="feld">
    <label :for="field.name">{{ field.label }}</label>

    <Checkbox v-if="field.art === 'checkbox'" v-model="value" :inputId="field.name" binary />
    <InputNumber v-else-if="field.art === 'number'" v-model="value" :inputId="field.name" :useGrouping="false" showButtons />
    <Textarea v-else-if="field.art === 'textarea'" v-model="value" :id="field.name" rows="3" autoResize />
    <Select
      v-else-if="field.art === 'select'"
      v-model="value"
      :inputId="field.name"
      :options="optionen"
      placeholder="– wählen –"
    />
    <input v-else-if="field.art === 'date'" :id="field.name" type="date" v-model="value" class="p-inputtext p-component" />
    <InputText v-else v-model="value" :id="field.name" />

    <small v-if="errorMessage" class="fehler">{{ errorMessage }}</small>
  </div>
</template>

<style scoped>
.feld {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 0.9rem;
}
.feld label {
  font-weight: 600;
  font-size: 0.85rem;
}
.fehler {
  color: var(--p-red-500, #e24c4c);
  font-size: 0.8rem;
}
</style>
