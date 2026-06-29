<script setup lang="ts">
// Self-Service-Rechnungsabruf: der eingeloggte Kunde wählt seinen Kontext (privat als Selbstzahler bzw.
// eine buchungsberechtigte Firma) und sieht dessen festgeschriebene Belege; je Beleg lädt er die
// ZUGFeRD-E-Rechnung als PDF. Der Aufrufer wird serverseitig über den Token aufgelöst (kein Fremdzugriff).
import { ref, onMounted, computed } from 'vue';
import type { TableColumn } from '@nuxt/ui';
import {
  partyLogin, rechnungsKontexte, meineRechnungen, rechnungPdf, ApiFehler,
  type PortalRechnungView,
} from '@/portal';
import { auth, login } from '@/auth';
import { euro } from '@crm-ui/domain/format';
import { rechnungStatusColor } from '@crm-ui/domain/severity';

type KontextOption = { label: string; value: string; organisationId?: number };

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
      value: k.organisationId == null ? 'privat' : `org-${k.organisationId}`,
      label: k.bezeichnung ?? (k.organisationId == null ? 'Privat' : `Organisation ${k.organisationId}`),
      organisationId: k.organisationId ?? undefined,
    }));
    gewaehlt.value = kontexte.value[0]?.value ?? null;
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
    const k = kontexte.value.find((o) => o.value === gewaehlt.value);
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
  if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}

const columns: TableColumn<PortalRechnungView>[] = [
  { accessorKey: 'nummer', header: 'Beleg-Nr.' },
  { accessorKey: 'ausstellungsdatum', header: 'Datum' },
  { id: 'betrag', header: 'Betrag' },
  { accessorKey: 'status', header: 'Status' },
  { accessorKey: 'zeitraumBezeichnung', header: 'Zeitraum' },
  { id: 'pdf', header: '' },
];
</script>

<template>
  <section>
    <h2 class="text-xl font-bold mb-4">Meine Rechnungen</h2>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a>, um Ihre Rechnungen zu sehen.
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
        :title="meldung.text" close class="mb-4" @update:open="meldung = null" />

      <UAlert v-if="!laden && kontexte.length === 0" color="info" variant="soft"
        title="Ihrem Login sind noch keine Rechnungskontexte zugeordnet." />

      <template v-else>
        <div class="flex items-end gap-3 mb-4">
          <UFormField label="Kontext">
            <USelect v-model="gewaehlt" :items="kontexte" class="w-64" @update:model-value="ladeBelege" />
          </UFormField>
        </div>

        <UTable :data="belege" :columns="columns" :loading="laden" :empty="'Keine Rechnungen in diesem Kontext.'">
          <template #betrag-cell="{ row }">{{ euro(row.original.summeCent) }}</template>
          <template #status-cell="{ row }">
            <UBadge :color="rechnungStatusColor(row.original.status)" variant="soft" size="sm">
              {{ row.original.status }}
            </UBadge>
          </template>
          <template #pdf-cell="{ row }">
            <UButton color="neutral" variant="ghost" size="sm" icon="i-lucide-file-text"
              :loading="pdfAktiv === row.original.id" @click="pdfLaden(row.original)">PDF</UButton>
          </template>
        </UTable>
      </template>
    </template>
  </section>
</template>
