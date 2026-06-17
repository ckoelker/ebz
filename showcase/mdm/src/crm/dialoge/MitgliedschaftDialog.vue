<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { useForm, useField } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText, istUnauth } from '@/crm/fehler';
import { violationsZuFehlern } from '@/bildung';
import { login } from '@/auth';
import {
  getCrmOrganisationen,
  postCrmPersonenPersonIdOrganisationenOrgIdMitgliedschaften,
  putCrmMitgliedschaftenId,
} from '@/api/endpoints/crm-resource/crm-resource';
import {
  PostCrmPersonenPersonIdOrganisationenOrgIdMitgliedschaftenBody as MitgliedschaftBody,
} from '@/api/zod/crm-resource/crm-resource.zod';
import type { MitgliedschaftView, OrgListItem } from '@/api/model';

// N:M-Pflege (Plan A4), Stack B. Anlage = Bestandssuche der Organisation zuerst (Dublettenschutz);
// Bearbeitung lässt die Organisation fest. Haupt-Flag-Invarianten erzwingt das Backend.
const props = defineProps<{ open: boolean; personId: number; existing?: MitgliedschaftView | null }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'saved'): void }>();

const { data: rollen } = useLookup('rolle');
const orgItems = ref<{ label: string; value: number }[]>([]);
const selectedOrg = ref<number | undefined>(undefined);
const istEdit = computed(() => !!props.existing?.id);

const initial = () => ({
  rolleCode: '', position: '', abteilung: '', hauptzugehoerigkeit: false, hauptansprechpartner: false,
  buchungsberechtigt: false, rechnungsempfaenger: false,
});
const { handleSubmit, setErrors, resetForm, isSubmitting, errors } = useForm({
  validationSchema: toTypedSchema(MitgliedschaftBody),
  initialValues: initial(),
});
const { value: rolleCode } = useField<string>('rolleCode');
const { value: position } = useField<string>('position');
const { value: abteilung } = useField<string>('abteilung');
const { value: hauptzugehoerigkeit } = useField<boolean>('hauptzugehoerigkeit');
const { value: hauptansprechpartner } = useField<boolean>('hauptansprechpartner');
const { value: buchungsberechtigt } = useField<boolean>('buchungsberechtigt');
const { value: rechnungsempfaenger } = useField<boolean>('rechnungsempfaenger');

const serverFehler = ref('');

watch(() => props.open, async (v) => {
  if (!v) return;
  serverFehler.value = '';
  if (props.existing) {
    resetForm({ values: {
      rolleCode: props.existing.rolleCode ?? '', position: props.existing.position ?? '',
      abteilung: props.existing.abteilung ?? '', hauptzugehoerigkeit: props.existing.hauptzugehoerigkeit ?? false,
      hauptansprechpartner: props.existing.hauptansprechpartner ?? false,
      buchungsberechtigt: props.existing.buchungsberechtigt ?? false,
      rechnungsempfaenger: props.existing.rechnungsempfaenger ?? false,
    } });
    selectedOrg.value = props.existing.organisationId;
  } else {
    resetForm({ values: initial() });
    selectedOrg.value = undefined;
    const page = await getCrmOrganisationen({ size: 200 });
    orgItems.value = (page.items ?? []).map((o: OrgListItem) => ({ label: o.name ?? `Organisation ${o.id}`, value: o.id! }));
  }
});

const speichern = handleSubmit(async (values) => {
  serverFehler.value = '';
  if (!istEdit.value && !selectedOrg.value) {
    serverFehler.value = 'Bitte eine Organisation wählen.';
    return;
  }
  try {
    if (istEdit.value) await putCrmMitgliedschaftenId(props.existing!.id!, values);
    else await postCrmPersonenPersonIdOrganisationenOrgIdMitgliedschaften(props.personId, selectedOrg.value!, values);
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
    :title="istEdit ? 'Zugehörigkeit bearbeiten' : 'Zugehörigkeit hinzufügen'"
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
      <UFormField label="Organisation" class="col-span-2" required>
        <span v-if="istEdit" class="font-medium">{{ existing?.organisation }}</span>
        <USelectMenu
          v-else
          v-model="selectedOrg"
          :items="orgItems"
          value-key="value"
          searchable
          placeholder="Organisation suchen…"
          class="w-full"
        />
      </UFormField>
      <UFormField label="Rolle" required :error="errors.rolleCode">
        <USelect v-model="rolleCode" :items="lookupItems(rollen)" placeholder="— wählen —" class="w-full" />
      </UFormField>
      <UFormField label="Position" :error="errors.position">
        <UInput v-model="position" placeholder="z. B. Leiter Bestand" class="w-full" />
      </UFormField>
      <UFormField label="Abteilung" class="col-span-2" :error="errors.abteilung">
        <UInput v-model="abteilung" class="w-full" />
      </UFormField>
      <div class="col-span-2 grid grid-cols-2 gap-2">
        <UCheckbox v-model="hauptzugehoerigkeit" label="Hauptzugehörigkeit (Default-Kanal)" />
        <UCheckbox v-model="hauptansprechpartner" label="Hauptansprechpartner der Firma" />
        <UCheckbox v-model="buchungsberechtigt" label="Buchungsberechtigt" />
        <UCheckbox v-model="rechnungsempfaenger" label="Rechnungsempfänger" />
      </div>
    </div>
  </DialogShell>
</template>
