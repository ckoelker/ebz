<script setup lang="ts">
import { ref, watch } from 'vue';
import { useForm, useField } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText, istUnauth } from '@/crm/fehler';
import { violationsZuFehlern } from '@/bildung';
import { login } from '@/auth';
import { postCrmEinwilligungen } from '@/api/endpoints/crm-resource/crm-resource';
import { PostCrmEinwilligungenBody } from '@/api/zod/crm-resource/crm-resource.zod';

// Erfassung einer Marketing-Einwilligung/Opt-In (Plan A6), Stack B (zod→vee-validate, Server-400→setErrors).
// Startet AUSSTEHEND; das Erteilen (Double-Opt-In) und der Widerruf laufen über die Tabellen-Aktionen.
const props = defineProps<{ open: boolean; personId: number }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'saved'): void }>();

const { data: quellen } = useLookup('leadquelle');
const kanalItems = [
  { label: 'E-Mail', value: 'EMAIL' },
  { label: 'Telefon', value: 'TELEFON' },
  { label: 'Post', value: 'POST' },
  { label: 'SMS', value: 'SMS' },
];
const zweckItems = [
  { label: 'Newsletter', value: 'NEWSLETTER' },
  { label: 'Telefonwerbung', value: 'TELEFONWERBUNG' },
  { label: 'Postwerbung', value: 'POSTWERBUNG' },
  { label: 'Befragung', value: 'BEFRAGUNG' },
  { label: 'Veranstaltungseinladung', value: 'VERANSTALTUNGSEINLADUNG' },
];
const rechtsgrundlageItems = [
  { label: 'Einwilligung (Art. 6 Abs. 1 a)', value: 'EINWILLIGUNG_6_1_A' },
  { label: 'Vertrag (Art. 6 Abs. 1 b)', value: 'VERTRAG_6_1_B' },
  { label: 'Berechtigtes Interesse (Art. 6 Abs. 1 f)', value: 'BERECHTIGTES_INTERESSE_6_1_F' },
];

const initial = () => ({
  personId: props.personId, kanal: 'EMAIL', zweck: 'NEWSLETTER',
  rechtsgrundlage: 'EINWILLIGUNG_6_1_A', quelleCode: undefined as string | undefined,
});
const { handleSubmit, setErrors, resetForm, isSubmitting, errors } = useForm({
  validationSchema: toTypedSchema(PostCrmEinwilligungenBody),
  initialValues: initial(),
});
const { value: kanal } = useField<string>('kanal');
const { value: zweck } = useField<string>('zweck');
const { value: rechtsgrundlage } = useField<string>('rechtsgrundlage');
const { value: quelleCode } = useField<string | undefined>('quelleCode');

const serverFehler = ref('');
watch(() => props.open, (v) => { if (v) { resetForm({ values: initial() }); serverFehler.value = ''; } });

const speichern = handleSubmit(async (values) => {
  serverFehler.value = '';
  try {
    await postCrmEinwilligungen({ ...values, personId: props.personId });
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
    title="Einwilligung erfassen"
    size="lg"
    :open="open"
    primary-label="Speichern (ausstehend)"
    primary-icon="i-lucide-check"
    :primary-loading="isSubmitting"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="serverFehler" color="error" variant="soft" :title="serverFehler" class="mb-4" />
    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Kanal" required :error="errors.kanal">
        <USelect v-model="kanal" :items="kanalItems" class="w-full" />
      </UFormField>
      <UFormField label="Zweck" required :error="errors.zweck">
        <USelect v-model="zweck" :items="zweckItems" class="w-full" />
      </UFormField>
      <UFormField label="Rechtsgrundlage (Art. 6)" class="col-span-2" :error="errors.rechtsgrundlage">
        <USelect v-model="rechtsgrundlage" :items="rechtsgrundlageItems" class="w-full" />
      </UFormField>
      <UFormField label="Quelle" class="col-span-2" :error="errors.quelleCode">
        <USelect v-model="quelleCode" :items="lookupItems(quellen)" class="w-full" />
      </UFormField>
    </div>
    <p class="text-xs text-dimmed mt-4">
      Neue Einwilligungen starten <strong>AUSSTEHEND</strong>; nach dem Double-Opt-In wird in der Liste
      „Erteilen" bestätigt. Werbe-/Auskunftssperre überstimmen jedes Opt-In (Plan A1/A6).
    </p>
  </DialogShell>
</template>
