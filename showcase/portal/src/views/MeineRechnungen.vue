<script setup lang="ts">
// Self-Service-Rechnungsabruf: der eingeloggte Kunde wählt seinen Kontext (privat als Selbstzahler bzw.
// eine buchungsberechtigte Firma) und sieht dessen festgeschriebene Belege; je Beleg lädt er die
// ZUGFeRD-E-Rechnung als PDF. Der Aufrufer wird serverseitig über den Token aufgelöst (kein Fremdzugriff).
import { ref, onMounted, computed } from 'vue';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Select from 'primevue/select';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import {
  partyLogin, rechnungsKontexte, meineRechnungen, rechnungPdf, ApiFehler,
  type PortalRechnungView,
} from '@/portal';
import { auth, login } from '@/auth';

type KontextOption = { key: string; label: string; organisationId?: number };

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const kontexte = ref<KontextOption[]>([]);
const gewaehlt = ref<string | null>(null);
const belege = ref<PortalRechnungView[]>([]);
const pdfAktiv = ref<number | null>(null);

const angemeldet = computed(() => auth.angemeldet);

onMounted(async () => {
  if (!auth.angemeldet) return;
  laden.value = true;
  try {
    await partyLogin({ email: auth.email, anzeigeName: auth.name || auth.benutzer });
    const ctx = await rechnungsKontexte();
    kontexte.value = ctx.map((k) => ({
      key: k.organisationId == null ? 'privat' : `org-${k.organisationId}`,
      label: k.bezeichnung ?? (k.organisationId == null ? 'Privat' : `Organisation ${k.organisationId}`),
      organisationId: k.organisationId ?? undefined,
    }));
    gewaehlt.value = kontexte.value[0]?.key ?? null;
    await ladeBelege();
  } catch (e) {
    fehler(e);
  } finally {
    laden.value = false;
  }
});

async function ladeBelege() {
  if (!gewaehlt.value) {
    belege.value = [];
    return;
  }
  laden.value = true;
  try {
    const k = kontexte.value.find((o) => o.key === gewaehlt.value);
    belege.value = await meineRechnungen(k?.organisationId);
  } catch (e) {
    fehler(e);
  } finally {
    laden.value = false;
  }
}

async function pdfLaden(z: PortalRechnungView) {
  if (!z.id) return;
  meldung.value = null;
  pdfAktiv.value = z.id;
  try {
    const blob = await rechnungPdf(z.id);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `rechnung-${z.nummer ?? z.id}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    fehler(e);
  } finally {
    pdfAktiv.value = null;
  }
}

function fehler(e: unknown) {
  // Nur neu anmelden, wenn KEINE Session besteht — sonst (Token gültig, aber 401 vom Backend) Fehler
  // zeigen statt in eine Redirect-Schleife zu laufen.
  if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}

function euro(cent: number): string {
  return (cent / 100).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
}

const statusSchwere: Record<string, 'warn' | 'info' | 'success' | 'secondary'> = {
  AUSGESTELLT: 'info',
  BEZAHLT: 'success',
  STORNIERT: 'secondary',
};
</script>

<template>
  <section>
    <h2>Meine Rechnungen</h2>

    <Message v-if="!angemeldet" severity="warn">
      Bitte <a href="#" @click.prevent="login">anmelden</a>, um Ihre Rechnungen zu sehen.
    </Message>

    <template v-else>
      <Message v-if="meldung" :severity="meldung.severity" closable @close="meldung = null">{{ meldung.text }}</Message>

      <Message v-if="!laden && kontexte.length === 0" severity="info">
        Ihrem Login sind noch keine Rechnungskontexte zugeordnet.
      </Message>

      <template v-else>
        <div class="kopf">
          <label class="kontextwahl">
            Kontext
            <Select v-model="gewaehlt" :options="kontexte" optionLabel="label" optionValue="key"
              @change="ladeBelege" />
          </label>
        </div>

        <DataTable :value="belege" dataKey="id" :loading="laden" stripedRows size="small">
          <Column field="nummer" header="Beleg-Nr." sortable />
          <Column field="ausstellungsdatum" header="Datum" sortable style="width: 9rem" />
          <Column header="Betrag" style="width: 9rem">
            <template #body="{ data: z }">{{ euro(z.summeCent) }}</template>
          </Column>
          <Column header="Status" style="width: 9rem">
            <template #body="{ data: z }"><Tag :value="z.status" :severity="statusSchwere[z.status] ?? 'secondary'" /></template>
          </Column>
          <Column header="Zeitraum" field="zeitraumBezeichnung" />
          <Column header="" style="width: 8rem">
            <template #body="{ data: z }">
              <Button label="PDF" size="small" icon="pi pi-file-pdf" text
                :loading="pdfAktiv === z.id" @click="pdfLaden(z)" />
            </template>
          </Column>
          <template #empty><div class="leer">Keine Rechnungen in diesem Kontext.</div></template>
        </DataTable>
      </template>
    </template>
  </section>
</template>

<style scoped>
.kopf {
  margin: 0.5rem 0 0.75rem;
}
.kontextwahl {
  display: inline-flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
  color: #444;
}
.leer {
  padding: 1rem;
  color: #888;
}
</style>
