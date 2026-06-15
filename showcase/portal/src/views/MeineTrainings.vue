<script setup lang="ts">
// Self-Service-WBT-Zugang: der eingeloggte Lernende sieht seine eigenen Kurs-Einschreibungen und
// startet einen freigeschalteten Kurs per SSO-Deeplink in OpenOLAT (neuer Tab → Keycloak-SSO → Kurs).
// Der Aufrufer wird serverseitig über den Token-sub aufgelöst (kein Fremdzugriff); launchUrl liefert
// das Backend nur für EINGESCHRIEBENe Trainings.
import { ref, onMounted, computed } from 'vue';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import { partyLogin, meineTrainings, ApiFehler, type MeinTrainingView } from '@/portal';
import { auth, login } from '@/auth';

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const trainings = ref<MeinTrainingView[]>([]);

const angemeldet = computed(() => auth.angemeldet);

onMounted(async () => {
  if (!auth.angemeldet) return;
  laden.value = true;
  try {
    // Person zum Token claimen (wie beim Rechnungsabruf) — stellt sicher, dass eine Identität existiert.
    await partyLogin({ email: auth.email, anzeigeName: auth.name || auth.benutzer });
    trainings.value = await meineTrainings();
  } catch (e) {
    fehler(e);
  } finally {
    laden.value = false;
  }
});

function starten(t: MeinTrainingView) {
  if (t.launchUrl) {
    window.open(t.launchUrl, '_blank', 'noopener');
  }
}

function fehler(e: unknown) {
  if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}

// Status → Anzeige (Tag-Schwere) + erklärender Hinweis.
const statusSchwere: Record<string, 'warn' | 'info' | 'success' | 'danger' | 'secondary'> = {
  ANGEFORDERT: 'warn',
  EINGESCHRIEBEN: 'success',
  FEHLGESCHLAGEN: 'danger',
};
const statusText: Record<string, string> = {
  ANGEFORDERT: 'wird bereitgestellt …',
  EINGESCHRIEBEN: 'verfügbar',
  FEHLGESCHLAGEN: 'Problem — bitte EBZ kontaktieren',
};
</script>

<template>
  <section>
    <h2>Meine Trainings</h2>

    <Message v-if="!angemeldet" severity="warn">
      Bitte <a href="#" @click.prevent="login">anmelden</a>, um Ihre Trainings zu sehen.
    </Message>

    <template v-else>
      <Message v-if="meldung" :severity="meldung.severity" closable @close="meldung = null">{{ meldung.text }}</Message>

      <DataTable :value="trainings" dataKey="einschreibungId" :loading="laden" stripedRows size="small">
        <Column field="kursTitel" header="Training" sortable />
        <Column header="Status" style="width: 16rem">
          <template #body="{ data: t }">
            <Tag :value="statusText[t.status] ?? t.status" :severity="statusSchwere[t.status] ?? 'secondary'" />
          </template>
        </Column>
        <Column header="" style="width: 11rem">
          <template #body="{ data: t }">
            <Button v-if="t.launchUrl" label="Kurs starten" size="small" icon="pi pi-external-link"
              @click="starten(t)" />
            <span v-else class="wartet">—</span>
          </template>
        </Column>
        <template #empty><div class="leer">Sie haben noch keine Trainings gebucht.</div></template>
      </DataTable>
    </template>
  </section>
</template>

<style scoped>
.leer {
  padding: 1rem;
  color: #888;
}
.wartet {
  color: #aaa;
}
</style>
