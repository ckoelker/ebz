<script setup lang="ts">
// Öffentliche Ausbildungsbetrieb-Anfrage (kein Login). Firmendaten + Ansprechpartner → der EBZ
// prüft (HITL/KI-Dubletten) und lädt anschließend per E-Mail zum Login ein. `website` ist ein
// verstecktes Honeypot-Feld (Bot-Schutz); echte Nutzer lassen es leer.
import { reactive, ref } from 'vue';
import { anfrageStellen, ApiFehler, type AusbildungsbetriebAnfrage } from '@/portal';

const form = reactive<AusbildungsbetriebAnfrage>({
  name: '',
  strasse: '',
  plz: '',
  ort: '',
  ustId: '',
  ansprechpartnerEmail: '',
  ansprechpartnerName: '',
  website: '', // Honeypot
});

const busy = ref(false);
const erfolg = ref(false);
const fehler = ref<string | null>(null);

const gueltig = () =>
  !!form.name.trim() && !!form.ansprechpartnerName.trim() && /.+@.+\..+/.test(form.ansprechpartnerEmail);

async function absenden() {
  fehler.value = null;
  if (!gueltig()) {
    fehler.value = 'Bitte Firmenname, Ansprechpartner und eine gültige E-Mail angeben.';
    return;
  }
  busy.value = true;
  try {
    await anfrageStellen({ ...form });
    erfolg.value = true;
  } catch (e) {
    const st = e instanceof ApiFehler ? e.status : undefined;
    fehler.value =
      st === 429 ? 'Zu viele Anfragen — bitte später erneut versuchen.'
      : st === 400 ? 'Bitte prüfen Sie Ihre Eingaben.'
      : (e as Error).message;
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <section>
    <h2 class="text-xl font-bold mb-4">Als Ausbildungsbetrieb anmelden</h2>

    <UAlert
      v-if="erfolg"
      color="success"
      variant="soft"
      icon="i-lucide-check-circle"
      title="Vielen Dank!"
      description="Ihre Anfrage ist eingegangen. Der EBZ prüft Ihre Angaben und sendet Ihrem Ansprechpartner eine E-Mail mit dem Zugang zum Portal."
    />

    <template v-else>
      <p class="text-sm text-muted mb-4 max-w-xl">
        Erfassen Sie Ihren Betrieb und einen Ansprechpartner. Nach der Prüfung durch den EBZ erhalten
        Sie eine Einladung zum Login und können anschließend Ihre Auszubildenden anmelden.
      </p>

      <UAlert
        v-if="fehler"
        color="error"
        variant="soft"
        :title="fehler"
        close
        class="mb-4"
        @update:open="fehler = null"
      />

      <form class="flex flex-col gap-4 max-w-xl" @submit.prevent="absenden">
        <h3 class="font-semibold">Betrieb</h3>
        <UFormField label="Firmenname" required>
          <UInput v-model="form.name" class="w-full" />
        </UFormField>
        <div class="flex gap-3">
          <UFormField label="Straße" class="flex-1">
            <UInput v-model="form.strasse" class="w-full" />
          </UFormField>
          <UFormField label="PLZ" class="w-28">
            <UInput v-model="form.plz" class="w-full" />
          </UFormField>
          <UFormField label="Ort" class="flex-1">
            <UInput v-model="form.ort" class="w-full" />
          </UFormField>
        </div>
        <UFormField label="USt-IdNr.">
          <UInput v-model="form.ustId" placeholder="z. B. DE123456789" class="w-full" />
        </UFormField>

        <h3 class="font-semibold mt-2">Ansprechpartner</h3>
        <UFormField label="Name" required>
          <UInput v-model="form.ansprechpartnerName" class="w-full" />
        </UFormField>
        <UFormField label="E-Mail" required>
          <UInput v-model="form.ansprechpartnerEmail" type="email" class="w-full" />
        </UFormField>

        <!-- Honeypot: für Menschen unsichtbar -->
        <input v-model="form.website" class="absolute -left-[9999px] w-px h-px opacity-0" tabindex="-1"
          autocomplete="off" aria-hidden="true" />

        <div class="mt-1">
          <UButton type="submit" icon="i-lucide-send" :loading="busy">Anfrage absenden</UButton>
        </div>
      </form>
    </template>
  </section>
</template>
