<script setup lang="ts">
// Rechnungs-Cockpit (Liste). Macht den fertigen Beleg-Lebenszyklus sichtbar: filterbare Belegliste
// (Status/Bereich), Anlegen einer freien Sonderrechnung, Sprung ins Detail. Routine-Rechnungen
// entstehen automatisch aus den Prozessen (Läufe/Shop) — hier nur Sicht + menschliche Einzelfälle.
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import {
  rechnungen, debitoren, sonderrechnungAnlegen, zugferdHerunterladen, euro,
  RechnungStatus, Bereich, ApiFehler,
  type RechnungDto, type DebitorDto, type SonderrechnungDto,
} from '@/rechnung';
import { login } from '@/auth';

const router = useRouter();
const ALLE = 'ALLE';
const fStatus = ref<string>(ALLE);
const fBereich = ref<string>(ALLE);

const { data: belege, isFetching, refetch, error } = useQuery({
  queryKey: ['rechnungen'],
  queryFn: async (): Promise<RechnungDto[]> => await rechnungen(),
});
const { data: debis } = useQuery({
  queryKey: ['debitoren'],
  queryFn: async (): Promise<DebitorDto[]> => await debitoren(),
});

const debitorName = (id?: number | null): string => {
  const d = (debis.value ?? []).find((x) => x.id === id);
  return d ? `${d.debitorNr} · ${d.name}` : id ? `Debitor ${id}` : '—';
};

const statusItems = [{ label: 'Alle Status', value: ALLE }, ...Object.values(RechnungStatus).map((s) => ({ label: s, value: s }))];
const bereichItems = [{ label: 'Alle Bereiche', value: ALLE }, ...Object.values(Bereich).map((b) => ({ label: b, value: b }))];

const gefiltert = computed(() =>
  (belege.value ?? [])
    .filter((r) => (fStatus.value === ALLE || r.status === fStatus.value)
      && (fBereich.value === ALLE || r.bereich === fBereich.value))
    .sort((a, b) => (b.id ?? 0) - (a.id ?? 0)),
);

const statusFarbe: Record<string, 'neutral' | 'info' | 'success' | 'error'> = {
  ENTWURF: 'neutral', AUSGESTELLT: 'info', BEZAHLT: 'success', STORNIERT: 'error',
};
const versandFarbe: Record<string, 'neutral' | 'success' | 'error'> = {
  NICHT_VERSENDET: 'neutral', VERSENDET: 'success', FEHLGESCHLAGEN: 'error',
};
const festgeschrieben = (r: RechnungDto) => r.status !== 'ENTWURF';

// ── Meldungen / Aktionen ──
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
function fehler(e: unknown) {
  if (e instanceof ApiFehler && e.status === 401) return login();
  meldung.value = { text: e instanceof Error ? e.message : 'Unbekannter Fehler.', severity: 'error' };
}

const ladendId = ref<number | null>(null);
async function pdf(r: RechnungDto) {
  if (!r.id) return;
  ladendId.value = r.id;
  meldung.value = null;
  try {
    await zugferdHerunterladen(r.id, r.nummer);
  } catch (e) {
    fehler(e);
  } finally {
    ladendId.value = null;
  }
}

// ── Sonderrechnung anlegen ──
const modalOffen = ref(false);
const neu = ref<{ debitorId?: number; bereich?: string; zeitraumBezeichnung?: string }>({});
const anlegend = ref(false);
const debitorItems = computed(() =>
  (debis.value ?? []).map((d) => ({ label: `${d.debitorNr} · ${d.name}`, value: d.id })));
const bereichOptItems = [{ label: 'Aus Debitor übernehmen', value: ALLE }, ...Object.values(Bereich).map((b) => ({ label: b, value: b }))];

function oeffneNeu() {
  neu.value = { bereich: ALLE };
  meldung.value = null;
  modalOffen.value = true;
}
async function anlegen() {
  if (!neu.value.debitorId) return;
  anlegend.value = true;
  try {
    const body: SonderrechnungDto = {
      debitorId: neu.value.debitorId,
      bereich: neu.value.bereich && neu.value.bereich !== ALLE ? (neu.value.bereich as Bereich) : undefined,
      zeitraumBezeichnung: neu.value.zeitraumBezeichnung || undefined,
    };
    const r = (await sonderrechnungAnlegen(body)) as RechnungDto;
    modalOffen.value = false;
    await refetch();
    if (r?.id) router.push(`/rechnungen/${r.id}`);
  } catch (e) {
    fehler(e);
  } finally {
    anlegend.value = false;
  }
}
</script>

<template>
  <section>
    <div class="flex justify-between items-center">
      <h2 class="text-xl font-bold">Rechnungen</h2>
      <UButton icon="i-lucide-file-plus" @click="oeffneNeu">Sonderrechnung</UButton>
    </div>

    <div class="flex gap-2 items-center my-4">
      <USelect v-model="fStatus" :items="statusItems" class="w-44" />
      <USelect v-model="fBereich" :items="bereichItems" class="w-44" />
      <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="() => { refetch(); }" />
      <span class="text-muted text-sm">{{ gefiltert.length }} Belege</span>
    </div>

    <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
            :title="meldung.text" close class="mb-4" @update:open="meldung = null" />
    <UAlert v-if="error" color="error" variant="soft" class="mb-4"
            :title="(error as Error).message" />

    <div class="overflow-x-auto border border-default rounded-lg">
      <table class="w-full text-sm">
        <thead class="bg-elevated/50 text-muted text-left">
          <tr>
            <th class="px-3 py-2 font-medium">Beleg</th>
            <th class="px-3 py-2 font-medium">Debitor</th>
            <th class="px-3 py-2 font-medium">Zeitraum</th>
            <th class="px-3 py-2 font-medium text-right">Betrag</th>
            <th class="px-3 py-2 font-medium">Status</th>
            <th class="px-3 py-2 font-medium">Versand</th>
            <th class="px-3 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in gefiltert" :key="r.id"
              class="border-t border-default hover:bg-elevated/40 cursor-pointer"
              @click="router.push(`/rechnungen/${r.id}`)">
            <td class="px-3 py-2">
              <div class="font-medium">{{ r.nummer ?? '— Entwurf —' }}</div>
              <div class="text-muted text-xs">{{ r.belegart }} · {{ r.bereich }}</div>
            </td>
            <td class="px-3 py-2">{{ debitorName(r.debitorId) }}</td>
            <td class="px-3 py-2 text-muted">{{ r.zeitraumBezeichnung ?? '—' }}</td>
            <td class="px-3 py-2 text-right tabular-nums">{{ euro(r.summeCent) }}</td>
            <td class="px-3 py-2">
              <UBadge :color="statusFarbe[r.status ?? ''] ?? 'neutral'" variant="soft" size="sm">{{ r.status }}</UBadge>
            </td>
            <td class="px-3 py-2">
              <UBadge :color="versandFarbe[r.versandStatus ?? 'NICHT_VERSENDET'] ?? 'neutral'" variant="soft" size="sm">
                {{ r.versandStatus ?? 'NICHT_VERSENDET' }}
              </UBadge>
            </td>
            <td class="px-3 py-2 text-right" @click.stop>
              <div class="flex gap-1 justify-end">
                <UTooltip v-if="festgeschrieben(r)" text="ZUGFeRD-PDF">
                  <UButton color="neutral" variant="ghost" size="sm" icon="i-lucide-download"
                           :loading="ladendId === r.id" @click="pdf(r)" />
                </UTooltip>
                <UButton color="neutral" variant="ghost" size="sm" icon="i-lucide-chevron-right"
                         @click="router.push(`/rechnungen/${r.id}`)" />
              </div>
            </td>
          </tr>
          <tr v-if="gefiltert.length === 0">
            <td colspan="7" class="px-3 py-8 text-center text-muted">Keine Belege.</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Sonderrechnung anlegen -->
    <UModal v-model:open="modalOffen" title="Sonderrechnung anlegen">
      <template #body>
        <div class="space-y-4">
          <p class="text-sm text-muted">
            Ad-hoc-Beleg außerhalb der Standard-Läufe. Legt einen leeren Entwurf an — Positionen und
            Festschreibung erfolgen anschließend im Detail.
          </p>
          <UFormField label="Debitor" required>
            <USelect v-model="neu.debitorId" :items="debitorItems" placeholder="Debitor wählen…" class="w-full"
                     searchable />
          </UFormField>
          <UFormField label="Bereich" help="Standard = Bereich des Debitors">
            <USelect v-model="neu.bereich" :items="bereichOptItems" class="w-full" />
          </UFormField>
          <UFormField label="Zeitraum / Bezeichnung">
            <UInput v-model="neu.zeitraumBezeichnung" placeholder="z. B. Einmalleistung Beratung 06/2026" class="w-full" />
          </UFormField>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="modalOffen = false">Abbrechen</UButton>
          <UButton :disabled="!neu.debitorId" :loading="anlegend" @click="anlegen">Anlegen</UButton>
        </div>
      </template>
    </UModal>
  </section>
</template>
