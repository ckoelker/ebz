<script setup lang="ts">
import { ref, watch } from 'vue';
import { useForm, useField } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText, istUnauth } from '@/crm/fehler';
import { violationsZuFehlern } from '@/bildung';
import { login } from '@/auth';
import { postCrmAktivitaeten } from '@/api/endpoints/crm-resource/crm-resource';
import { PostCrmAktivitaetenBody } from '@/api/zod/crm-resource/crm-resource.zod';

// Erfassung einer Aktivität/Notiz (Plan A9), Stack B (zod→vee-validate, Server-400→setErrors).
const props = defineProps<{ open: boolean; ownerType: 'person' | 'organisation'; ownerId: number }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'saved'): void }>();

const { data: typen } = useLookup('aktivitaetstyp');
const richtungItems = [
  { label: 'Ausgehend', value: 'AUSGEHEND' },
  { label: 'Eingehend', value: 'EINGEHEND' },
  { label: 'Intern', value: 'INTERN' },
];

const initial = () => ({ typCode: 'NOTIZ', richtung: 'INTERN', betreff: '', inhaltHtml: '', dauerMinuten: undefined });
const { handleSubmit, setErrors, resetForm, isSubmitting, errors } = useForm({
  validationSchema: toTypedSchema(PostCrmAktivitaetenBody),
  initialValues: initial(),
});
const { value: typCode } = useField<string>('typCode');
const { value: richtung } = useField<string>('richtung');
const { value: betreff } = useField<string>('betreff');
const { value: inhaltHtml } = useField<string>('inhaltHtml');
const { value: dauerMinuten } = useField<number | undefined>('dauerMinuten');

const serverFehler = ref('');
watch(() => props.open, (v) => { if (v) { resetForm({ values: initial() }); serverFehler.value = ''; } });

const speichern = handleSubmit(async (values) => {
  serverFehler.value = '';
  try {
    await postCrmAktivitaeten({
      ...values,
      personId: props.ownerType === 'person' ? props.ownerId : undefined,
      organisationId: props.ownerType === 'organisation' ? props.ownerId : undefined,
    });
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
    title="Aktivität erfassen"
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
      <UFormField label="Typ" :error="errors.typCode">
        <USelect v-model="typCode" :items="lookupItems(typen)" class="w-full" />
      </UFormField>
      <UFormField label="Richtung" :error="errors.richtung">
        <USelect v-model="richtung" :items="richtungItems" class="w-full" />
      </UFormField>
      <UFormField label="Betreff" required class="col-span-2" :error="errors.betreff">
        <UInput v-model="betreff" class="w-full" />
      </UFormField>
      <UFormField label="Inhalt" class="col-span-2" :error="errors.inhaltHtml">
        <UTextarea v-model="inhaltHtml" :rows="4" autoresize class="w-full" />
      </UFormField>
      <UFormField label="Dauer (Min.)" :error="errors.dauerMinuten">
        <UInputNumber v-model="dauerMinuten" :min="0" class="w-full" />
      </UFormField>
    </div>
  </DialogShell>
</template>
