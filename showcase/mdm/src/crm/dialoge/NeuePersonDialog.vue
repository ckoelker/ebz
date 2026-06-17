<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText } from '@/crm/fehler';
import { postCrmPersonen, putCrmPersonenId } from '@/api/endpoints/crm-resource/crm-resource';
import type { PersonInput, PersonDetail } from '@/api/model';

// Anlage/Bearbeitung einer Person (gestuft: Pflicht zuerst). Pflicht = Vorname + Nachname (Plan A1);
// alles Übrige optional. Klassifikationen (Sprache/Lead-Quelle) aus den Lookups. Mit `existing` = Edit.
const props = defineProps<{ open: boolean; existing?: PersonDetail | null }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'created', id: number): void; (e: 'saved'): void }>();

const { data: sprachen } = useLookup('sprache');
const { data: leadquellen } = useLookup('leadquelle');

const geschlechtItems = [
  { label: 'Männlich', value: 'MAENNLICH' },
  { label: 'Weiblich', value: 'WEIBLICH' },
  { label: 'Divers', value: 'DIVERS' },
  { label: 'Keine Angabe', value: 'KEINE_ANGABE' },
];

const leer = (): PersonInput => ({
  vorname: '', nachname: '', geschlecht: 'KEINE_ANGABE', titel: '', korrespondenzspracheCode: 'de',
  leadQuelleCode: undefined, werbesperre: false, auskunftssperre: false,
});
const form = reactive<PersonInput>(leer());
const fehler = ref('');
const speichert = ref(false);

watch(() => props.open, (v) => {
  if (!v) return;
  fehler.value = '';
  Object.assign(form, leer());
  if (props.existing) {
    Object.assign(form, {
      vorname: props.existing.vorname ?? '',
      nachname: props.existing.nachname ?? '',
      geschlecht: props.existing.geschlecht ?? 'KEINE_ANGABE',
      titel: props.existing.titel ?? '',
      geburtsdatum: props.existing.geburtsdatum,
      geburtsort: props.existing.geburtsort ?? '',
      korrespondenzspracheCode: props.existing.korrespondenzspracheCode ?? 'de',
      werbesperre: props.existing.werbesperre ?? false,
      auskunftssperre: props.existing.auskunftssperre ?? false,
    });
  }
});

async function speichern() {
  fehler.value = '';
  if (!form.vorname.trim() || !form.nachname.trim()) {
    fehler.value = 'Vor- und Nachname sind Pflicht.';
    return;
  }
  speichert.value = true;
  try {
    const payload = { ...form, titel: form.titel || undefined };
    if (props.existing?.id) {
      await putCrmPersonenId(props.existing.id, payload);
      emit('saved');
    } else {
      const p = (await postCrmPersonen(payload)) as PersonDetail;
      emit('created', p.id!);
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
    title="Neue Person"
    size="lg"
    :open="open"
    primary-label="Anlegen"
    primary-icon="i-lucide-user-plus"
    :primary-loading="speichert"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="fehler" color="error" variant="soft" :title="fehler" class="mb-4" />
    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Vorname" required>
        <UInput v-model="form.vorname" class="w-full" />
      </UFormField>
      <UFormField label="Nachname" required>
        <UInput v-model="form.nachname" class="w-full" />
      </UFormField>
      <UFormField label="Geschlecht">
        <USelect v-model="form.geschlecht" :items="geschlechtItems" class="w-full" />
      </UFormField>
      <UFormField label="Titel" help="DE-Grade voran (Dr./Prof.), internationale nachgestellt">
        <UInput v-model="form.titel" placeholder="z. B. Dr." class="w-full" />
      </UFormField>
      <UFormField label="Geburtsdatum">
        <UInput v-model="form.geburtsdatum" type="date" class="w-full" />
      </UFormField>
      <UFormField label="Korrespondenzsprache">
        <USelect v-model="form.korrespondenzspracheCode" :items="lookupItems(sprachen)" class="w-full" />
      </UFormField>
      <UFormField label="Lead-Quelle" class="col-span-2">
        <USelect
          v-model="form.leadQuelleCode"
          :items="lookupItems(leadquellen)"
          placeholder="— wählen —"
          class="w-full"
        />
      </UFormField>
    </div>
  </DialogShell>
</template>
