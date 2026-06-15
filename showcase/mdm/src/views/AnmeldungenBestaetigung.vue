<script setup lang="ts">
// EBZ-Bestätigung (Anmeldung Berufsschule, Schritt I/E): offene Anmeldungen prüfen und bestätigen
// (ANGEFRAGT → BESTAETIGT_EBZ; löst die Benachrichtigung an Azubi + Firma aus). Noch nicht
// abrechenbar — das wird die Anmeldung erst mit der Vertragsbestätigung der Firma (AKTIV).
import { ref, computed } from 'vue';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Select from 'primevue/select';
import Message from 'primevue/message';
import { AnmeldungStatus } from '@/api/model';
import { offeneAnmeldungen, bestaetigeAnmeldung, ApiFehler, type OffeneAnmeldungView } from '@/dubletten';
import { auth, login } from '@/auth';

const qc = useQueryClient();
const status = ref<AnmeldungStatus>(AnmeldungStatus.ANGEFRAGT);

const { data, isFetching, refetch } = useQuery({
  queryKey: ['offeneAnmeldungen', status],
  queryFn: () => offeneAnmeldungen(status.value),
});

const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const aktiv = ref<number | null>(null);

const mut = useMutation({
  mutationFn: bestaetigeAnmeldung,
  onSuccess: () => qc.invalidateQueries({ queryKey: ['offeneAnmeldungen'] }),
});

async function bestaetigen(a: OffeneAnmeldungView) {
  if (!a.anmeldungId) return;
  meldung.value = null;
  aktiv.value = a.anmeldungId;
  try {
    await mut.mutateAsync(a.anmeldungId);
    meldung.value = { text: `Anmeldung von „${a.teilnehmerName}" bestätigt — Azubi & Firma benachrichtigt.`, severity: 'success' };
  } catch (e) {
    if (e instanceof ApiFehler && e.status === 401) return login();
    meldung.value = { text: (e as Error).message, severity: 'error' };
  } finally {
    aktiv.value = null;
  }
}

const statusSchwere: Record<string, 'warn' | 'info'> = { ANGEFRAGT: 'warn', BESTAETIGT_EBZ: 'info' };
const istAngefragt = computed(() => status.value === 'ANGEFRAGT');
</script>

<template>
  <section>
    <div class="kopf">
      <h2>Anmeldungen — EBZ-Bestätigung</h2>
      <div class="filter">
        <Select v-model="status" :options="['ANGEFRAGT', 'BESTAETIGT_EBZ']" />
        <Button text icon="pi pi-refresh" :loading="isFetching" @click="() => refetch()" />
      </div>
    </div>

    <Message v-if="!auth.angemeldet" severity="warn">
      Bitte <a href="#" @click.prevent="login">anmelden</a> (Rolle <code>rechnung-pflege</code>).
    </Message>
    <Message v-if="meldung" :severity="meldung.severity" closable @close="meldung = null">{{ meldung.text }}</Message>

    <DataTable :value="data ?? []" dataKey="anmeldungId" stripedRows paginator :rows="15" size="small">
      <Column field="teilnehmerName" header="Teilnehmer:in" sortable />
      <Column field="schuljahr" header="Schuljahr" sortable />
      <Column field="halbjahr" header="HJ" style="width: 4rem" />
      <Column header="Status" style="width: 10rem">
        <template #body="{ data: a }"><Tag :value="a.status" :severity="statusSchwere[a.status] ?? 'secondary'" /></template>
      </Column>
      <Column header="" style="width: 11rem">
        <template #body="{ data: a }">
          <Button v-if="istAngefragt" label="EBZ bestätigen" size="small" icon="pi pi-check"
            :loading="aktiv === a.anmeldungId" @click="bestaetigen(a)" />
          <span v-else class="erledigt"><i class="pi pi-check-circle" /> bestätigt</span>
        </template>
      </Column>
      <template #empty><div class="leer">Keine Anmeldungen im Status {{ status }}.</div></template>
    </DataTable>
  </section>
</template>

<style scoped>
.kopf {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
.erledigt {
  color: #16a34a;
  font-size: 0.9rem;
}
.leer {
  padding: 1rem;
  color: #888;
}
</style>
