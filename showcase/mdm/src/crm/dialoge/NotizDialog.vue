<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import DialogShell from '@/components/DialogShell.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText } from '@/crm/fehler';
import { postCrmAktivitaeten } from '@/api/endpoints/crm-resource/crm-resource';
import type { AktivitaetInput } from '@/api/model';

// Erfassung einer Aktivität/Notiz (Plan A9, Kontakthistorie) zu einer Person oder Organisation.
const props = defineProps<{ open: boolean; ownerType: 'person' | 'organisation'; ownerId: number }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'saved'): void }>();

const { data: typen } = useLookup('aktivitaetstyp');

const richtungItems = [
  { label: 'Ausgehend', value: 'AUSGEHEND' },
  { label: 'Eingehend', value: 'EINGEHEND' },
  { label: 'Intern', value: 'INTERN' },
];

const leer = (): AktivitaetInput => ({
  typCode: 'NOTIZ', richtung: 'INTERN', betreff: '', inhaltHtml: '', dauerMinuten: undefined,
});
const form = reactive<AktivitaetInput>(leer());
const fehler = ref('');
const speichert = ref(false);

watch(() => props.open, (v) => { if (v) { Object.assign(form, leer()); fehler.value = ''; } });

async function speichern() {
  fehler.value = '';
  if (!form.betreff.trim()) {
    fehler.value = 'Betreff ist Pflicht.';
    return;
  }
  speichert.value = true;
  try {
    await postCrmAktivitaeten({
      ...form,
      personId: props.ownerType === 'person' ? props.ownerId : undefined,
      organisationId: props.ownerType === 'organisation' ? props.ownerId : undefined,
    });
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
    title="Aktivität erfassen"
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
      <UFormField label="Typ">
        <USelect v-model="form.typCode" :items="lookupItems(typen)" class="w-full" />
      </UFormField>
      <UFormField label="Richtung">
        <USelect v-model="form.richtung" :items="richtungItems" class="w-full" />
      </UFormField>
      <UFormField label="Betreff" required class="col-span-2">
        <UInput v-model="form.betreff" class="w-full" />
      </UFormField>
      <UFormField label="Inhalt" class="col-span-2">
        <UTextarea v-model="form.inhaltHtml" :rows="4" autoresize class="w-full" />
      </UFormField>
      <UFormField label="Dauer (Min.)">
        <UInputNumber v-model="form.dauerMinuten" :min="0" class="w-full" />
      </UFormField>
    </div>
  </DialogShell>
</template>
