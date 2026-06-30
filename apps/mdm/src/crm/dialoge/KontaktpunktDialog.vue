<script setup lang="ts">
import { ref, watch } from 'vue';
import { useForm, useField } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { VORWAHLEN, VORWAHL_DEFAULT, baueE164, baueAnzeige, zerlege } from '@/crm/telefon';
import { fehlerText, istUnauth } from '@/crm/fehler';
import { violationsZuFehlern } from '@/bildung';
import { login } from '@/auth';
import { postCrmKontaktpunkte, putCrmKontaktpunkteId } from '@/api/endpoints/crm-resource/crm-resource';
import { PostCrmKontaktpunkteBody } from '@/api/zod/crm-resource/crm-resource.zod';
import type { KontaktpunktView } from '@/api/model';

// Anlage/Bearbeitung eines Kontaktpunkts (Plan A3) — je Typ andere Felder. Stack B (zod→vee-validate);
// die typ-spezifische Pflicht + länderabhängige PLZ prüft der Server (409 → serverFehler).
const props = defineProps<{
  open: boolean; ownerType: 'person' | 'organisation'; ownerId: number; existing?: KontaktpunktView | null;
}>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'saved'): void }>();

const { data: laender } = useLookup('land');
const typItems = [
  { label: 'E-Mail', value: 'EMAIL' },
  { label: 'Telefon', value: 'TELEFON' },
  { label: 'Adresse', value: 'ADRESSE' },
];
const telefonartItems = [
  { label: 'Festnetz', value: 'FESTNETZ' },
  { label: 'Mobil', value: 'MOBIL' },
  { label: 'Fax', value: 'FAX' },
];

const initial = () => ({
  typ: 'EMAIL', label: '', primaer: false, email: '', nummerAnzeige: '', nummerE164: '',
  telefonart: 'FESTNETZ', strasse: '', hausnummer: '', plz: '', ort: '', region: '', landCode: 'DE',
});
const { handleSubmit, setErrors, resetForm, isSubmitting, errors } = useForm({
  validationSchema: toTypedSchema(PostCrmKontaktpunkteBody),
  initialValues: initial(),
});
const { value: typ } = useField<string>('typ');
const { value: label } = useField<string>('label');
const { value: primaer } = useField<boolean>('primaer');
const { value: email } = useField<string>('email');
const { value: nummerAnzeige } = useField<string>('nummerAnzeige');
const { value: nummerE164 } = useField<string>('nummerE164');
const { value: telefonart } = useField<string>('telefonart');
const { value: strasse } = useField<string>('strasse');
const { value: hausnummer } = useField<string>('hausnummer');
const { value: plz } = useField<string>('plz');
const { value: ort } = useField<string>('ort');
const { value: region } = useField<string>('region');
const { value: landCode } = useField<string>('landCode');

const serverFehler = ref('');

// Telefon-E.164: getrennte Eingabe Vorwahl + nationale Nummer; daraus werden nummerE164/nummerAnzeige
// normalisiert gebaut (führende Null wird entfernt). Beide Form-Felder bleiben der Server-Vertrag.
const vorwahl = ref(VORWAHL_DEFAULT);
const telNational = ref('');
watch([vorwahl, telNational, typ], () => {
  if (typ.value === 'TELEFON') {
    nummerE164.value = baueE164(vorwahl.value, telNational.value);
    nummerAnzeige.value = baueAnzeige(vorwahl.value, telNational.value);
  }
});

watch(() => props.open, (v) => {
  if (!v) return;
  serverFehler.value = '';
  const base = initial();
  if (props.existing) {
    Object.assign(base, {
      typ: props.existing.typ ?? 'EMAIL', label: props.existing.label ?? '', primaer: props.existing.primaer ?? false,
      email: props.existing.email ?? '', nummerAnzeige: props.existing.nummerAnzeige ?? '',
      telefonart: props.existing.telefonart ?? 'FESTNETZ', strasse: props.existing.strasse ?? '',
      hausnummer: props.existing.hausnummer ?? '', plz: props.existing.plz ?? '', ort: props.existing.ort ?? '',
      region: props.existing.region ?? '', landCode: props.existing.landCode ?? 'DE',
    });
  }
  const z = zerlege(props.existing?.nummerAnzeige);
  vorwahl.value = z.vorwahl;
  telNational.value = z.national;
  resetForm({ values: base });
});

const speichern = handleSubmit(async (values) => {
  serverFehler.value = '';
  const payload = {
    ...values,
    personId: props.ownerType === 'person' ? props.ownerId : undefined,
    organisationId: props.ownerType === 'organisation' ? props.ownerId : undefined,
  };
  try {
    if (props.existing?.id) await putCrmKontaktpunkteId(props.existing.id, payload);
    else await postCrmKontaktpunkte(payload);
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
    :title="existing ? 'Kontaktpunkt bearbeiten' : 'Kontaktpunkt hinzufügen'"
    :open="open"
    primary-label="Speichern"
    primary-icon="i-lucide-check"
    :primary-loading="isSubmitting"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="serverFehler" color="error" variant="soft" :title="serverFehler" class="mb-4" />
    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Typ" :error="errors.typ">
        <USelect v-model="typ" :items="typItems" class="w-full" />
      </UFormField>
      <UFormField label="Bezeichnung" :error="errors.label">
        <UInput v-model="label" placeholder="z. B. Zentrale, Privat" class="w-full" />
      </UFormField>

      <template v-if="typ === 'EMAIL'">
        <UFormField label="E-Mail" class="col-span-2" :error="errors.email">
          <UInput v-model="email" type="email" class="w-full" />
        </UFormField>
      </template>

      <template v-else-if="typ === 'TELEFON'">
        <UFormField label="Ländervorwahl">
          <USelect v-model="vorwahl" :items="VORWAHLEN" class="w-full" />
        </UFormField>
        <UFormField label="Art" :error="errors.telefonart">
          <USelect v-model="telefonart" :items="telefonartItems" class="w-full" />
        </UFormField>
        <UFormField label="Nummer (national)" class="col-span-2" help="führende Null wird automatisch entfernt"
                    :error="errors.nummerAnzeige">
          <UInput v-model="telNational" placeholder="231 555012" class="w-full" />
        </UFormField>
        <UFormField label="E.164 (normalisiert)" class="col-span-2" help="fürs Anruf-Matching (CTI) – automatisch erzeugt">
          <UInput :model-value="nummerE164" disabled placeholder="+49231555012" class="w-full" />
        </UFormField>
      </template>

      <template v-else>
        <UFormField label="Straße" :error="errors.strasse">
          <UInput v-model="strasse" class="w-full" />
        </UFormField>
        <UFormField label="Hausnummer" :error="errors.hausnummer">
          <UInput v-model="hausnummer" class="w-full" />
        </UFormField>
        <UFormField label="PLZ" :error="errors.plz">
          <UInput v-model="plz" class="w-full" />
        </UFormField>
        <UFormField label="Ort" :error="errors.ort">
          <UInput v-model="ort" class="w-full" />
        </UFormField>
        <UFormField label="Land" :error="errors.landCode">
          <USelect v-model="landCode" :items="lookupItems(laender)" class="w-full" />
        </UFormField>
        <UFormField label="Region" :error="errors.region">
          <UInput v-model="region" class="w-full" />
        </UFormField>
      </template>

      <UFormField label="Primär" class="col-span-2">
        <UCheckbox v-model="primaer" label="Vorrang-Kanal dieses Typs" />
      </UFormField>
    </div>
  </DialogShell>
</template>
