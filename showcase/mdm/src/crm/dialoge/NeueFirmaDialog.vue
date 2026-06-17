<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText } from '@/crm/fehler';
import { postCrmOrganisationen, putCrmOrganisationenId } from '@/api/endpoints/crm-resource/crm-resource';
import type { OrganisationInput, OrgDetail } from '@/api/model';

// Anlage/Bearbeitung einer Organisation. Pflicht = Name (Plan A2); Immobilien-Spezifika (Unternehmenstyp/
// Schwerpunkte/Verbände/Branche/IHK) als Mehrfach-Lookups. Mit `existing` = Edit.
const props = defineProps<{ open: boolean; existing?: OrgDetail | null }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'created', id: number): void; (e: 'saved'): void }>();

const { data: branchen } = useLookup('branche');
const { data: unternehmenstypen } = useLookup('unternehmenstyp');
const { data: schwerpunkte } = useLookup('schwerpunkt');
const { data: verbaende } = useLookup('verband');
const { data: kammern } = useLookup('ihk');

const leer = (): OrganisationInput => ({
  name: '', rechtsform: '', brancheCode: undefined, website: '', ustId: '', bestandsgroesse: undefined,
  ausbildungsbetrieb: false, ihkKammerCode: undefined, gewerbeerlaubnis: 'KEINE',
  unternehmenstypCodes: [], schwerpunktCodes: [], verbandCodes: [],
});
const form = reactive<OrganisationInput>(leer());
const fehler = ref('');
const speichert = ref(false);

watch(() => props.open, (v) => {
  if (!v) return;
  fehler.value = '';
  Object.assign(form, leer());
  if (props.existing) {
    Object.assign(form, {
      name: props.existing.name ?? '',
      rechtsform: props.existing.rechtsform ?? '',
      brancheCode: props.existing.brancheCode ?? undefined,
      website: props.existing.website ?? '',
      ustId: props.existing.ustId ?? '',
      bestandsgroesse: props.existing.bestandsgroesse,
      ausbildungsbetrieb: props.existing.ausbildungsbetrieb ?? false,
      ihkKammerCode: props.existing.ihkKammerCode ?? undefined,
      gewerbeerlaubnis: props.existing.gewerbeerlaubnis ?? 'KEINE',
      unternehmenstypCodes: props.existing.unternehmenstypen ?? [],
      schwerpunktCodes: props.existing.taetigkeitsschwerpunkte ?? [],
      verbandCodes: props.existing.verbaende ?? [],
    });
  }
});

async function speichern() {
  fehler.value = '';
  if (!form.name.trim()) {
    fehler.value = 'Name ist Pflicht.';
    return;
  }
  speichert.value = true;
  try {
    if (props.existing?.id) {
      await putCrmOrganisationenId(props.existing.id, { ...form });
      emit('saved');
    } else {
      const o = (await postCrmOrganisationen({ ...form })) as OrgDetail;
      emit('created', o.id!);
    }
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
    title="Neue Organisation"
    size="xl"
    :open="open"
    primary-label="Anlegen"
    primary-icon="i-lucide-building-2"
    :primary-loading="speichert"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="fehler" color="error" variant="soft" :title="fehler" class="mb-4" />
    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Name" required class="col-span-2">
        <UInput v-model="form.name" class="w-full" />
      </UFormField>
      <UFormField label="Rechtsform">
        <UInput v-model="form.rechtsform" placeholder="z. B. GmbH" class="w-full" />
      </UFormField>
      <UFormField label="USt-IdNr.">
        <UInput v-model="form.ustId" placeholder="DE…" class="w-full" />
      </UFormField>
      <UFormField label="Branche (WZ/NACE)">
        <USelect v-model="form.brancheCode" :items="lookupItems(branchen)" placeholder="— wählen —" class="w-full" />
      </UFormField>
      <UFormField label="Website">
        <UInput v-model="form.website" class="w-full" />
      </UFormField>
      <UFormField label="Unternehmenstyp(en)" class="col-span-2">
        <USelect v-model="form.unternehmenstypCodes" :items="lookupItems(unternehmenstypen)" multiple class="w-full" />
      </UFormField>
      <UFormField label="Tätigkeitsschwerpunkte" class="col-span-2">
        <USelect v-model="form.schwerpunktCodes" :items="lookupItems(schwerpunkte)" multiple class="w-full" />
      </UFormField>
      <UFormField label="Verbände" class="col-span-2">
        <USelect v-model="form.verbandCodes" :items="lookupItems(verbaende)" multiple class="w-full" />
      </UFormField>
      <UFormField label="IHK / Kammer">
        <USelect v-model="form.ihkKammerCode" :items="lookupItems(kammern)" placeholder="— wählen —" class="w-full" />
      </UFormField>
      <UFormField label="Bestandsgröße (Einheiten)">
        <UInputNumber v-model="form.bestandsgroesse" :min="0" class="w-full" />
      </UFormField>
      <UFormField label="Ausbildungsbetrieb" class="col-span-2">
        <UCheckbox v-model="form.ausbildungsbetrieb" label="Ist Ausbildungsbetrieb (Berufsschul-/Azubi-Prozesse)" />
      </UFormField>
    </div>
  </DialogShell>
</template>
