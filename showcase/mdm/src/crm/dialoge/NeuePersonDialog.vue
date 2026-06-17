<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { useForm, useField } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import DialogShell from '@/components/DialogShell.vue';
import DublettenWarnung from '@/crm/DublettenWarnung.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText, istUnauth } from '@/crm/fehler';
import { violationsZuFehlern } from '@/bildung';
import { login } from '@/auth';
import { postCrmPersonen, putCrmPersonenId } from '@/api/endpoints/crm-resource/crm-resource';
import { PostCrmPersonenBody } from '@/api/zod/crm-resource/crm-resource.zod';
import type { PersonDetail } from '@/api/model';

// Anlage/Bearbeitung einer Person (Plan A1). Stack B: Validierung aus der generierten zod
// (toTypedSchema) über vee-validate; Server-400 (Bean-Validation-Violations) wird via setErrors an
// die Felder gehängt. Mit `existing` = Edit. Beim Anlegen: Live-Dublettenwarnung (A16).
const props = defineProps<{ open: boolean; existing?: PersonDetail | null }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'created', id: number): void;
  (e: 'verwenden', id: number): void; (e: 'saved'): void }>();

function aufVerwenden(id: number) {
  emit('verwenden', id);
  emit('update:open', false);
}

const { data: sprachen } = useLookup('sprache');
const { data: leadquellen } = useLookup('leadquelle');

const geschlechtItems = [
  { label: 'Männlich', value: 'MAENNLICH' },
  { label: 'Weiblich', value: 'WEIBLICH' },
  { label: 'Divers', value: 'DIVERS' },
  { label: 'Keine Angabe', value: 'KEINE_ANGABE' },
];

const initial = () => ({
  vorname: '', nachname: '', geschlecht: 'KEINE_ANGABE', titel: '', geburtsdatum: undefined,
  korrespondenzspracheCode: 'de', leadQuelleCode: undefined, werbesperre: false, auskunftssperre: false,
});
const { handleSubmit, setErrors, resetForm, isSubmitting, errors } = useForm({
  validationSchema: toTypedSchema(PostCrmPersonenBody),
  initialValues: initial(),
});
const { value: vorname } = useField<string>('vorname');
const { value: nachname } = useField<string>('nachname');
const { value: geschlecht } = useField<string>('geschlecht');
const { value: titel } = useField<string>('titel');
const { value: geburtsdatum } = useField<string | undefined>('geburtsdatum');
// Optionales Datum: das native date-Input liefert beim Leeren "" — das verletzt zod
// (string().date().optional() akzeptiert nur undefined, nicht ""). Daher "" ⇄ undefined mappen.
const geburtsdatumInput = computed({
  get: () => geburtsdatum.value ?? '',
  set: (v: string) => { geburtsdatum.value = v === '' ? undefined : v; },
});
const { value: korrespondenzspracheCode } = useField<string>('korrespondenzspracheCode');
const { value: leadQuelleCode } = useField<string>('leadQuelleCode');

const serverFehler = ref('');

watch(() => props.open, (v) => {
  if (!v) return;
  serverFehler.value = '';
  if (props.existing) {
    resetForm({ values: {
      vorname: props.existing.vorname ?? '', nachname: props.existing.nachname ?? '',
      geschlecht: props.existing.geschlecht ?? 'KEINE_ANGABE', titel: props.existing.titel ?? '',
      geburtsdatum: props.existing.geburtsdatum, korrespondenzspracheCode: props.existing.korrespondenzspracheCode ?? 'de',
      leadQuelleCode: undefined, werbesperre: props.existing.werbesperre ?? false,
      auskunftssperre: props.existing.auskunftssperre ?? false,
    } });
  } else {
    resetForm({ values: initial() });
  }
});

const speichern = handleSubmit(async (values) => {
  serverFehler.value = '';
  try {
    if (props.existing?.id) {
      await putCrmPersonenId(props.existing.id, values);
      emit('saved');
    } else {
      const p = (await postCrmPersonen(values)) as PersonDetail;
      emit('created', p.id!);
    }
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
    title="Neue Person"
    size="lg"
    :open="open"
    primary-label="Speichern"
    primary-icon="i-lucide-user-plus"
    :primary-loading="isSubmitting"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="serverFehler" color="error" variant="soft" :title="serverFehler" class="mb-4" />
    <DublettenWarnung
      v-if="!existing"
      art="person"
      :vorname="vorname"
      :nachname="nachname"
      :titel="titel"
      @verwenden="aufVerwenden"
    />
    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Vorname" required :error="errors.vorname">
        <UInput v-model="vorname" class="w-full" />
      </UFormField>
      <UFormField label="Nachname" required :error="errors.nachname">
        <UInput v-model="nachname" class="w-full" />
      </UFormField>
      <UFormField label="Geschlecht" :error="errors.geschlecht">
        <USelect v-model="geschlecht" :items="geschlechtItems" class="w-full" />
      </UFormField>
      <UFormField label="Titel" help="DE-Grade voran (Dr./Prof.), internationale nachgestellt" :error="errors.titel">
        <UInput v-model="titel" placeholder="z. B. Dr." class="w-full" />
      </UFormField>
      <UFormField label="Geburtsdatum" :error="errors.geburtsdatum">
        <UInput v-model="geburtsdatumInput" type="date" class="w-full" />
      </UFormField>
      <UFormField label="Korrespondenzsprache" :error="errors.korrespondenzspracheCode">
        <USelect v-model="korrespondenzspracheCode" :items="lookupItems(sprachen)" class="w-full" />
      </UFormField>
      <UFormField label="Lead-Quelle" class="col-span-2" :error="errors.leadQuelleCode">
        <USelect v-model="leadQuelleCode" :items="lookupItems(leadquellen)" placeholder="— wählen —" class="w-full" />
      </UFormField>
    </div>
  </DialogShell>
</template>
