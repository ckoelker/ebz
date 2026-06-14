<script setup lang="ts">
// Öffentliche Ausbildungsbetrieb-Anfrage (kein Login). Firmendaten + Ansprechpartner → der EBZ
// prüft (HITL/KI-Dubletten) und lädt anschließend per E-Mail zum Login ein. `website` ist ein
// verstecktes Honeypot-Feld (Bot-Schutz); echte Nutzer lassen es leer.
import { reactive, ref } from 'vue';
import InputText from 'primevue/inputtext';
import Button from 'primevue/button';
import Message from 'primevue/message';
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
    <h2>Als Ausbildungsbetrieb anmelden</h2>

    <Message v-if="erfolg" severity="success">
      Vielen Dank! Ihre Anfrage ist eingegangen. Der EBZ prüft Ihre Angaben und sendet Ihrem
      Ansprechpartner eine E-Mail mit dem Zugang zum Portal.
    </Message>

    <template v-else>
      <p class="hinweis">
        Erfassen Sie Ihren Betrieb und einen Ansprechpartner. Nach der Prüfung durch den EBZ erhalten
        Sie eine Einladung zum Login und können anschließend Ihre Auszubildenden anmelden.
      </p>
      <Message v-if="fehler" severity="error" closable @close="fehler = null">{{ fehler }}</Message>

      <form class="formular" @submit.prevent="absenden">
        <h3>Betrieb</h3>
        <label>Firmenname *<InputText v-model="form.name" /></label>
        <div class="zeile">
          <label>Straße<InputText v-model="form.strasse" /></label>
          <label class="plz">PLZ<InputText v-model="form.plz" /></label>
          <label>Ort<InputText v-model="form.ort" /></label>
        </div>
        <label>USt-IdNr.<InputText v-model="form.ustId" placeholder="z. B. DE123456789" /></label>

        <h3>Ansprechpartner</h3>
        <label>Name *<InputText v-model="form.ansprechpartnerName" /></label>
        <label>E-Mail *<InputText v-model="form.ansprechpartnerEmail" type="email" /></label>

        <!-- Honeypot: für Menschen unsichtbar -->
        <input v-model="form.website" class="honeypot" tabindex="-1" autocomplete="off" aria-hidden="true" />

        <div class="aktionen">
          <Button type="submit" label="Anfrage absenden" icon="pi pi-send" :loading="busy" />
        </div>
      </form>
    </template>
  </section>
</template>

<style scoped>
.hinweis {
  color: #555;
  font-size: 0.95rem;
}
.formular {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-width: 560px;
}
.formular h3 {
  margin: 0.75rem 0 0;
}
.formular label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
  color: #444;
}
.zeile {
  display: flex;
  gap: 0.75rem;
}
.zeile label {
  flex: 1;
}
.zeile .plz {
  max-width: 7rem;
}
.aktionen {
  margin-top: 0.5rem;
}
.honeypot {
  position: absolute;
  left: -9999px;
  width: 1px;
  height: 1px;
  opacity: 0;
}
</style>
