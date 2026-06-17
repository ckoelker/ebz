<script setup lang="ts">
import { ref, watch } from 'vue';
import { useForm, useField } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText, istUnauth } from '@/crm/fehler';
import { violationsZuFehlern } from '@/bildung';
import { login } from '@/auth';
import { postCrmOrganisationen, putCrmOrganisationenId } from '@/api/endpoints/crm-resource/crm-resource';
import { PostCrmOrganisationenBody } from '@/api/zod/crm-resource/crm-resource.zod';
import type { OrgDetail } from '@/api/model';

// Anlage/Bearbeitung einer Organisation (Plan A2), Stack B (zod→vee-validate, Server-400→setErrors).
const props = defineProps<{ open: boolean; existing?: OrgDetail | null }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'created', id: number): void; (e: 'saved'): void }>();

const { data: branchen } = useLookup('branche');
const { data: unternehmenstypen } = useLookup('unternehmenstyp');
const { data: schwerpunkte } = useLookup('schwerpunkt');
const { data: verbaende } = useLookup('verband');
const { data: kammern } = useLookup('ihk');

const initial = () => ({
  name: '', rechtsform: '', brancheCode: undefined, website: '', ustId: '', bestandsgroesse: undefined,
  ausbildungsbetrieb: false, ihkKammerCode: undefined, gewerbeerlaubnis: 'KEINE',
  unternehmenstypCodes: [] as string[], schwerpunktCodes: [] as string[], verbandCodes: [] as string[],
});
const { handleSubmit, setErrors, resetForm, isSubmitting, errors } = useForm({
  validationSchema: toTypedSchema(PostCrmOrganisationenBody),
  initialValues: initial(),
});
const { value: name } = useField<string>('name');
const { value: rechtsform } = useField<string>('rechtsform');
const { value: brancheCode } = useField<string>('brancheCode');
const { value: website } = useField<string>('website');
const { value: ustId } = useField<string>('ustId');
const { value: bestandsgroesse } = useField<number | undefined>('bestandsgroesse');
const { value: ausbildungsbetrieb } = useField<boolean>('ausbildungsbetrieb');
const { value: ihkKammerCode } = useField<string>('ihkKammerCode');
const { value: unternehmenstypCodes } = useField<string[]>('unternehmenstypCodes');
const { value: schwerpunktCodes } = useField<string[]>('schwerpunktCodes');
const { value: verbandCodes } = useField<string[]>('verbandCodes');

const serverFehler = ref('');

watch(() => props.open, (v) => {
  if (!v) return;
  serverFehler.value = '';
  if (props.existing) {
    resetForm({ values: {
      name: props.existing.name ?? '', rechtsform: props.existing.rechtsform ?? '',
      brancheCode: props.existing.brancheCode ?? undefined, website: props.existing.website ?? '',
      ustId: props.existing.ustId ?? '', bestandsgroesse: props.existing.bestandsgroesse,
      ausbildungsbetrieb: props.existing.ausbildungsbetrieb ?? false,
      ihkKammerCode: props.existing.ihkKammerCode ?? undefined,
      gewerbeerlaubnis: props.existing.gewerbeerlaubnis ?? 'KEINE',
      unternehmenstypCodes: props.existing.unternehmenstypen ?? [], schwerpunktCodes: props.existing.taetigkeitsschwerpunkte ?? [],
      verbandCodes: props.existing.verbaende ?? [],
    } });
  } else {
    resetForm({ values: initial() });
  }
});

const speichern = handleSubmit(async (values) => {
  serverFehler.value = '';
  try {
    if (props.existing?.id) {
      await putCrmOrganisationenId(props.existing.id, values);
      emit('saved');
    } else {
      const o = (await postCrmOrganisationen(values)) as OrgDetail;
      emit('created', o.id!);
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
    title="Neue Organisation"
    size="xl"
    :open="open"
    primary-label="Speichern"
    primary-icon="i-lucide-building-2"
    :primary-loading="isSubmitting"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="serverFehler" color="error" variant="soft" :title="serverFehler" class="mb-4" />
    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Name" required class="col-span-2" :error="errors.name">
        <UInput v-model="name" class="w-full" />
      </UFormField>
      <UFormField label="Rechtsform" :error="errors.rechtsform">
        <UInput v-model="rechtsform" placeholder="z. B. GmbH" class="w-full" />
      </UFormField>
      <UFormField label="USt-IdNr." :error="errors.ustId">
        <UInput v-model="ustId" placeholder="DE…" class="w-full" />
      </UFormField>
      <UFormField label="Branche (WZ/NACE)" :error="errors.brancheCode">
        <USelect v-model="brancheCode" :items="lookupItems(branchen)" placeholder="— wählen —" class="w-full" />
      </UFormField>
      <UFormField label="Website" :error="errors.website">
        <UInput v-model="website" class="w-full" />
      </UFormField>
      <UFormField label="Unternehmenstyp(en)" class="col-span-2">
        <USelect v-model="unternehmenstypCodes" :items="lookupItems(unternehmenstypen)" multiple class="w-full" />
      </UFormField>
      <UFormField label="Tätigkeitsschwerpunkte" class="col-span-2">
        <USelect v-model="schwerpunktCodes" :items="lookupItems(schwerpunkte)" multiple class="w-full" />
      </UFormField>
      <UFormField label="Verbände" class="col-span-2">
        <USelect v-model="verbandCodes" :items="lookupItems(verbaende)" multiple class="w-full" />
      </UFormField>
      <UFormField label="IHK / Kammer" :error="errors.ihkKammerCode">
        <USelect v-model="ihkKammerCode" :items="lookupItems(kammern)" placeholder="— wählen —" class="w-full" />
      </UFormField>
      <UFormField label="Bestandsgröße (Einheiten)" :error="errors.bestandsgroesse">
        <UInputNumber v-model="bestandsgroesse" :min="0" class="w-full" />
      </UFormField>
      <UFormField label="Ausbildungsbetrieb" class="col-span-2">
        <UCheckbox v-model="ausbildungsbetrieb" label="Ist Ausbildungsbetrieb (Berufsschul-/Azubi-Prozesse)" />
      </UFormField>
    </div>
  </DialogShell>
</template>
