<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText } from '@/crm/fehler';
import { postCrmKontaktpunkte, putCrmKontaktpunkteId } from '@/api/endpoints/crm-resource/crm-resource';
import type { KontaktpunktInput, KontaktpunktView } from '@/api/model';

// Anlage/Bearbeitung eines Kontaktpunkts (Plan A3) — je Typ andere Felder. Besitzer = Person ODER
// Organisation (über die Props gesetzt). PLZ wird server-seitig länderabhängig geprüft.
const props = defineProps<{
  open: boolean;
  ownerType: 'person' | 'organisation';
  ownerId: number;
  existing?: KontaktpunktView | null;
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

const leer = (): KontaktpunktInput => ({
  typ: 'EMAIL', label: '', primaer: false, email: '', nummerAnzeige: '', nummerE164: '',
  telefonart: 'FESTNETZ', strasse: '', hausnummer: '', plz: '', ort: '', region: '', landCode: 'DE',
});
const form = reactive<KontaktpunktInput>(leer());
const fehler = ref('');
const speichert = ref(false);

watch(() => props.open, (v) => {
  if (!v) return;
  fehler.value = '';
  Object.assign(form, leer());
  if (props.existing) {
    Object.assign(form, {
      typ: props.existing.typ ?? 'EMAIL',
      label: props.existing.label ?? '',
      primaer: props.existing.primaer ?? false,
      email: props.existing.email ?? '',
      nummerAnzeige: props.existing.nummerAnzeige ?? '',
      telefonart: props.existing.telefonart ?? 'FESTNETZ',
      strasse: props.existing.strasse ?? '',
      hausnummer: props.existing.hausnummer ?? '',
      plz: props.existing.plz ?? '',
      ort: props.existing.ort ?? '',
      region: props.existing.region ?? '',
      landCode: props.existing.landCode ?? 'DE',
    });
  }
});

async function speichern() {
  fehler.value = '';
  speichert.value = true;
  const payload: KontaktpunktInput = {
    ...form,
    personId: props.ownerType === 'person' ? props.ownerId : undefined,
    organisationId: props.ownerType === 'organisation' ? props.ownerId : undefined,
  };
  try {
    if (props.existing?.id) {
      await putCrmKontaktpunkteId(props.existing.id, payload);
    } else {
      await postCrmKontaktpunkte(payload);
    }
    emit('saved');
    emit('update:open', false);
  } catch (e) {
    fehler.value = fehlerText(e);
  } finally {
    speichert.value = false;
  }
}
</script>

<template>
  <DialogShell
    :title="existing ? 'Kontaktpunkt bearbeiten' : 'Kontaktpunkt hinzufügen'"
    :open="open"
    primary-label="Speichern"
    primary-icon="i-lucide-check"
    :primary-loading="speichert"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="fehler" color="error" variant="soft" :title="fehler" class="mb-4" />
    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Typ">
        <USelect v-model="form.typ" :items="typItems" class="w-full" />
      </UFormField>
      <UFormField label="Bezeichnung">
        <UInput v-model="form.label" placeholder="z. B. Zentrale, Privat" class="w-full" />
      </UFormField>

      <template v-if="form.typ === 'EMAIL'">
        <UFormField label="E-Mail" class="col-span-2">
          <UInput v-model="form.email" type="email" class="w-full" />
        </UFormField>
      </template>

      <template v-else-if="form.typ === 'TELEFON'">
        <UFormField label="Nummer (Anzeige)">
          <UInput v-model="form.nummerAnzeige" placeholder="0231 123456" class="w-full" />
        </UFormField>
        <UFormField label="Art">
          <USelect v-model="form.telefonart" :items="telefonartItems" class="w-full" />
        </UFormField>
        <UFormField label="E.164 (normalisiert)" class="col-span-2" help="fürs Anruf-Matching (CTI)">
          <UInput v-model="form.nummerE164" placeholder="+49231123456" class="w-full" />
        </UFormField>
      </template>

      <template v-else>
        <UFormField label="Straße">
          <UInput v-model="form.strasse" class="w-full" />
        </UFormField>
        <UFormField label="Hausnummer">
          <UInput v-model="form.hausnummer" class="w-full" />
        </UFormField>
        <UFormField label="PLZ">
          <UInput v-model="form.plz" class="w-full" />
        </UFormField>
        <UFormField label="Ort">
          <UInput v-model="form.ort" class="w-full" />
        </UFormField>
        <UFormField label="Land">
          <USelect v-model="form.landCode" :items="lookupItems(laender)" class="w-full" />
        </UFormField>
        <UFormField label="Region">
          <UInput v-model="form.region" class="w-full" />
        </UFormField>
      </template>

      <UFormField label="Primär" class="col-span-2">
        <UCheckbox v-model="form.primaer" label="Vorrang-Kanal dieses Typs" />
      </UFormField>
    </div>
  </DialogShell>
</template>
