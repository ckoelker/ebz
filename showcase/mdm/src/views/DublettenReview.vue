<script setup lang="ts">
// HITL-Dubletten-Review (Anmeldung Berufsschule, Schritt I): offene Fälle (Firmen/Personen) mit
// KI-Vorschlag. Mensch entscheidet je Fall: auf einen Vorschlag „Zusammenführen" (Merge) oder
// „Neuanlage bestätigen". KI priorisiert nur — entschieden wird hier.
import { ref } from 'vue';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import { reviewQueue, entscheide, einschaetzungSchwere, ApiFehler, type Fall } from '@/dubletten';
import { auth, login } from '@/auth';

const qc = useQueryClient();

const { data, isFetching, refetch } = useQuery({
  queryKey: ['reviewQueue'],
  queryFn: reviewQueue,
});

const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const aktiv = ref<number | null>(null);

const mut = useMutation({
  mutationFn: entscheide,
  onSuccess: () => qc.invalidateQueries({ queryKey: ['reviewQueue'] }),
});

async function zusammenfuehren(f: Fall, zielId: number, ziel: string) {
  await ausfuehren(f, () =>
    mut.mutateAsync({ art: f.art!, kandidatId: f.kandidatId!, entscheidung: 'GEMERGT', zielId }),
    `„${f.bezeichnung}" wurde mit „${ziel}" zusammengeführt.`);
}

async function neuanlage(f: Fall) {
  await ausfuehren(f, () =>
    mut.mutateAsync({ art: f.art!, kandidatId: f.kandidatId!, entscheidung: 'NEUANLAGE_BESTAETIGT' }),
    `„${f.bezeichnung}" als eigenständige Neuanlage bestätigt.`);
}

async function ausfuehren(f: Fall, aktion: () => Promise<unknown>, erfolg: string) {
  meldung.value = null;
  aktiv.value = f.kandidatId ?? null;
  try {
    await aktion();
    meldung.value = { text: erfolg, severity: 'success' };
  } catch (e) {
    if (e instanceof ApiFehler && e.status === 401) return login();
    meldung.value = { text: (e as Error).message, severity: 'error' };
  } finally {
    aktiv.value = null;
  }
}

const prozent = (a?: number) => `${Math.round((a ?? 0) * 100)} %`;
</script>

<template>
  <section>
    <div class="kopf">
      <h2>Dubletten-Review</h2>
      <Button text icon="pi pi-refresh" :loading="isFetching" @click="() => refetch()" />
    </div>
    <p class="hinweis">
      KI schlägt vor, Sie entscheiden. „Zusammenführen" führt den Kandidaten in das Ziel; „Neuanlage
      bestätigen" lässt ihn als eigenständige Identität bestehen.
    </p>

    <Message v-if="!auth.angemeldet" severity="warn">
      Bitte <a href="#" @click.prevent="login">anmelden</a> (Rolle <code>rechnung-pflege</code>), um Fälle zu sehen und zu entscheiden.
    </Message>
    <Message v-if="meldung" :severity="meldung.severity" closable @close="meldung = null">{{ meldung.text }}</Message>

    <DataTable :value="data ?? []" dataKey="kandidatId" stripedRows size="small">
      <Column header="Art" style="width: 7rem">
        <template #body="{ data: f }"><Tag :value="f.art" :severity="f.art === 'FIRMA' ? 'info' : 'contrast'" /></template>
      </Column>
      <Column header="Kandidat">
        <template #body="{ data: f }">
          <strong>{{ f.bezeichnung }}</strong> <small class="id">#{{ f.kandidatId }}</small>
        </template>
      </Column>
      <Column header="KI-Vorschläge (Merge-Ziel)">
        <template #body="{ data: f }">
          <div v-for="v in f.vorschlaege" :key="v.zielId" class="vorschlag">
            <Tag :value="v.einschaetzung" :severity="einschaetzungSchwere[v.einschaetzung] ?? 'secondary'" />
            <span class="score">{{ prozent(v.aehnlichkeit) }}</span>
            <span class="ziel">{{ v.bezeichnung }} <small class="id">#{{ v.zielId }}</small></span>
            <Button label="Zusammenführen" size="small" severity="danger" outlined icon="pi pi-link"
              :loading="aktiv === f.kandidatId" @click="zusammenfuehren(f, v.zielId, v.bezeichnung)" />
            <small class="begruendung">{{ v.begruendung }}</small>
          </div>
        </template>
      </Column>
      <Column header="" style="width: 12rem">
        <template #body="{ data: f }">
          <Button label="Neuanlage bestätigen" size="small" severity="secondary" icon="pi pi-user-plus"
            :loading="aktiv === f.kandidatId" @click="neuanlage(f)" />
        </template>
      </Column>
      <template #empty><div class="leer">Keine offenen Dubletten-Fälle.</div></template>
    </DataTable>
  </section>
</template>

<style scoped>
.kopf {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.hinweis {
  color: #666;
  font-size: 0.9rem;
  margin: 0.25rem 0 1rem;
}
.vorschlag {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.2rem 0;
}
.score {
  font-variant-numeric: tabular-nums;
  color: #444;
  min-width: 3.5rem;
}
.ziel {
  min-width: 14rem;
}
.begruendung {
  color: #888;
}
.id {
  color: #aaa;
}
.leer {
  padding: 1rem;
  color: #888;
}
</style>
