<script setup lang="ts">
import { reactive, ref, watch, computed } from 'vue';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText } from '@/crm/fehler';
import {
  getCrmOrganisationen,
  postCrmPersonenPersonIdOrganisationenOrgIdMitgliedschaften,
  putCrmMitgliedschaftenId,
} from '@/api/endpoints/crm-resource/crm-resource';
import type { MitgliedschaftInput, MitgliedschaftView, OrgListItem } from '@/api/model';

// N:M-Pflege (Plan A4): verknüpft eine Person mit einer Organisation in einer Rolle. Anlage =
// Bestandssuche zuerst (Dublettenschutz); Bearbeitung lässt die Organisation fest. Die Invarianten
// (höchstens eine aktive Hauptzugehörigkeit/ein Hauptansprechpartner) erzwingt das Backend.
const props = defineProps<{ open: boolean; personId: number; existing?: MitgliedschaftView | null }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'saved'): void }>();

const { data: rollen } = useLookup('rolle');

const orgItems = ref<{ label: string; value: number }[]>([]);
const selectedOrg = ref<number | undefined>(undefined);

const leer = (): MitgliedschaftInput => ({
  rolleCode: '', position: '', abteilung: '', hauptzugehoerigkeit: false, hauptansprechpartner: false,
  buchungsberechtigt: false, rechnungsempfaenger: false,
});
const form = reactive<MitgliedschaftInput>(leer());
const fehler = ref('');
const speichert = ref(false);
const istEdit = computed(() => !!props.existing?.id);

watch(() => props.open, async (v) => {
  if (!v) return;
  fehler.value = '';
  Object.assign(form, leer());
  if (props.existing) {
    Object.assign(form, {
      rolleCode: props.existing.rolleCode ?? '',
      position: props.existing.position ?? '',
      abteilung: props.existing.abteilung ?? '',
      hauptzugehoerigkeit: props.existing.hauptzugehoerigkeit ?? false,
      hauptansprechpartner: props.existing.hauptansprechpartner ?? false,
      buchungsberechtigt: props.existing.buchungsberechtigt ?? false,
      rechnungsempfaenger: props.existing.rechnungsempfaenger ?? false,
    });
    selectedOrg.value = props.existing.organisationId;
  } else {
    selectedOrg.value = undefined;
    const page = await getCrmOrganisationen({ size: 200 });
    orgItems.value = (page.items ?? []).map((o: OrgListItem) => ({
      label: o.name ?? `Organisation ${o.id}`, value: o.id!,
    }));
  }
});

async function speichern() {
  fehler.value = '';
  if (!form.rolleCode) {
    fehler.value = 'Bitte eine Rolle wählen.';
    return;
  }
  if (!istEdit.value && !selectedOrg.value) {
    fehler.value = 'Bitte eine Organisation wählen.';
    return;
  }
  speichert.value = true;
  try {
    if (istEdit.value) {
      await putCrmMitgliedschaftenId(props.existing!.id!, { ...form });
    } else {
      await postCrmPersonenPersonIdOrganisationenOrgIdMitgliedschaften(props.personId, selectedOrg.value!, { ...form });
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
    :title="istEdit ? 'Zugehörigkeit bearbeiten' : 'Zugehörigkeit hinzufügen'"
    size="lg"
    :open="open"
    primary-label="Speichern"
    primary-icon="i-lucide-check"
    :primary-loading="speichert"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="fehler" color="error" variant="soft" :title="fehler" class="mb-4" />
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
      <UFormField label="Rolle" required>
        <USelect v-model="form.rolleCode" :items="lookupItems(rollen)" placeholder="— wählen —" class="w-full" />
      </UFormField>
      <UFormField label="Position">
        <UInput v-model="form.position" placeholder="z. B. Leiter Bestand" class="w-full" />
      </UFormField>
      <UFormField label="Abteilung" class="col-span-2">
        <UInput v-model="form.abteilung" class="w-full" />
      </UFormField>
      <div class="col-span-2 grid grid-cols-2 gap-2">
        <UCheckbox v-model="form.hauptzugehoerigkeit" label="Hauptzugehörigkeit (Default-Kanal)" />
        <UCheckbox v-model="form.hauptansprechpartner" label="Hauptansprechpartner der Firma" />
        <UCheckbox v-model="form.buchungsberechtigt" label="Buchungsberechtigt" />
        <UCheckbox v-model="form.rechnungsempfaenger" label="Rechnungsempfänger" />
      </div>
    </div>
  </DialogShell>
</template>
