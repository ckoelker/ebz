<script setup lang="ts">
import { ref, watch } from 'vue';
import { useForm, useField } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import DialogShell from '@/components/DialogShell.vue';
import { fehlerText, istUnauth } from '@/crm/fehler';
import { violationsZuFehlern } from '@/bildung';
import { login } from '@/auth';
import { postCrmWeiterbildung } from '@/api/endpoints/crm-resource/crm-resource';
import { PostCrmWeiterbildungBody } from '@/api/zod/crm-resource/crm-resource.zod';

// Erfassung eines Weiterbildungsnachweises (Plan A19, §34c GewO / §15b MaBV), Stack B.
// Auch fremde (externe) Nachweise werden auf das Stundenkonto angerechnet.
const props = defineProps<{ open: boolean; personId: number }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'saved'): void }>();

const heute = () => new Date().toISOString().slice(0, 10);
const initial = () => ({
  personId: props.personId, titel: '', anbieter: '', stunden: undefined as number | undefined,
  datum: heute(), extern: false,
});
const { handleSubmit, setErrors, resetForm, isSubmitting, errors } = useForm({
  validationSchema: toTypedSchema(PostCrmWeiterbildungBody),
  initialValues: initial(),
});
const { value: titel } = useField<string>('titel');
const { value: anbieter } = useField<string>('anbieter');
const { value: stunden } = useField<number | undefined>('stunden');
const { value: datum } = useField<string>('datum');
const { value: extern } = useField<boolean>('extern');

const serverFehler = ref('');
watch(() => props.open, (v) => { if (v) { resetForm({ values: initial() }); serverFehler.value = ''; } });

const speichern = handleSubmit(async (values) => {
  serverFehler.value = '';
  try {
    await postCrmWeiterbildung({ ...values, personId: props.personId });
    emit('saved');
    emit('update:open', false);
  } catch (e) {
    if (istUnauth(e)) return login();
    const fehler = violationsZuFehlern((e as { response?: { data?: unknown } })?.response?.data);
    if (Object.keys(fehler).length) setErrors(fehler);
    else serverFehler.value = fehlerText(e);
  }
});
</script>

<template>
  <DialogShell
    title="Weiterbildungsnachweis erfassen"
    size="lg"
    :open="open"
    primary-label="Speichern"
    primary-icon="i-lucide-check"
    :primary-loading="isSubmitting"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="serverFehler" color="error" variant="soft" :title="serverFehler" class="mb-4" />
    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Titel der Maßnahme" required class="col-span-2" :error="errors.titel">
        <UInput v-model="titel" class="w-full" />
      </UFormField>
      <UFormField label="Anbieter" :error="errors.anbieter">
        <UInput v-model="anbieter" class="w-full" />
      </UFormField>
      <UFormField label="Datum" required :error="errors.datum">
        <UInput v-model="datum" type="date" class="w-full" />
      </UFormField>
      <UFormField label="Stunden" required :error="errors.stunden">
        <UInputNumber v-model="stunden" :min="0" :step="0.5" class="w-full" />
      </UFormField>
      <UFormField label="Externer Nachweis" :error="errors.extern" class="self-end">
        <USwitch v-model="extern" label="außerhalb des EBZ erworben" />
      </UFormField>
    </div>
  </DialogShell>
</template>
