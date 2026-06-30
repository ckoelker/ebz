<script setup lang="ts">
// HubSpot-Sync-Cockpit. Macht den Outbound-Sync MDM→HubSpot sichtbar/steuerbar: Auftrags-Queue mit
// Status, Backfill aller aktiven Parteien, sofortiges Verarbeiten fälliger Aufträge, Retry von
// Dead-Lettern und das Anstoßen des Rechts auf Vergessen. Routine-Sync läuft automatisch über den
// Dispatcher — hier nur Sicht + menschliche Einzelfälle.
import { ref, computed } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import {
  auftraege, backfill, verarbeiten, retry, syncContact, syncCompany, erasure, zeit,
  ApiFehler, type AuftragDto,
} from '@/hubspot';
import { login } from '@/auth';

const { data: liste, isFetching, refetch, error } = useQuery({
  queryKey: ['hubspot-auftraege'],
  queryFn: async (): Promise<AuftragDto[]> => await auftraege(),
});

const statusFarbe: Record<string, 'neutral' | 'info' | 'success' | 'warning' | 'error'> = {
  NEU: 'neutral', IN_ARBEIT: 'info', ERLEDIGT: 'success', MANUELL: 'warning', FEHLER: 'warning', TOT: 'error',
};
const wiederholbar = (s: string) => s === 'FEHLER' || s === 'TOT' || s === 'MANUELL';

const offen = computed(() =>
  (liste.value ?? []).filter((a) => a.status === 'NEU' || a.status === 'FEHLER').length);

// ── Meldungen ──
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const busy = ref<string | null>(null);
function fehler(e: unknown) {
  if (e instanceof ApiFehler && e.status === 401) return login();
  meldung.value = { text: e instanceof Error ? e.message : 'Unbekannter Fehler.', severity: 'error' };
}
function ok(text: string) {
  meldung.value = { text, severity: 'success' };
}
async function fuehreAus(key: string, fn: () => Promise<void>) {
  busy.value = key;
  meldung.value = null;
  try {
    await fn();
    await refetch();
  } catch (e) {
    fehler(e);
  } finally {
    busy.value = null;
  }
}

const doBackfill = () => fuehreAus('backfill', async () => {
  const r = await backfill();
  ok(`Backfill eingereiht: ${r.kontakte} Kontakte, ${r.firmen} Firmen.`);
});
const doRun = () => fuehreAus('run', async () => {
  const r = await verarbeiten();
  ok(`${r.verarbeitet} Auftrag/Aufträge verarbeitet.`);
});
const doRetry = (id: number) => fuehreAus(`retry-${id}`, async () => {
  await retry(id);
  ok(`Auftrag ${id} erneut eingereiht.`);
});

// ── Einzel-Trigger ──
const personId = ref<number | undefined>();
const orgId = ref<number | undefined>();
function doSyncContact() {
  if (!personId.value) return;
  fuehreAus('contact', async () => {
    await syncContact(personId.value!);
    ok(`Kontakt-Sync für Person ${personId.value} eingereiht.`);
  });
}
function doSyncCompany() {
  if (!orgId.value) return;
  fuehreAus('company', async () => {
    await syncCompany(orgId.value!);
    ok(`Firmen-Sync für Organisation ${orgId.value} eingereiht.`);
  });
}

// ── Recht auf Vergessen (mit Bestätigung) ──
const erasureModal = ref(false);
const erasurePersonId = ref<number | undefined>();
function oeffneErasure() {
  erasurePersonId.value = undefined;
  meldung.value = null;
  erasureModal.value = true;
}
function bestaetigeErasure() {
  if (!erasurePersonId.value) return;
  fuehreAus('erasure', async () => {
    await erasure(erasurePersonId.value!);
    erasureModal.value = false;
    ok(`Recht auf Vergessen für Person ${erasurePersonId.value} eingereiht.`);
  });
}
</script>

<template>
  <section>
    <div class="flex justify-between items-center">
      <h2 class="text-xl font-bold">HubSpot-Sync</h2>
      <div class="flex gap-2">
        <UButton icon="i-lucide-users" :loading="busy === 'backfill'" @click="doBackfill">Backfill</UButton>
        <UButton color="neutral" icon="i-lucide-play" :loading="busy === 'run'" @click="doRun">
          Fällige verarbeiten
        </UButton>
        <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching"
                 @click="() => { refetch(); }" />
      </div>
    </div>

    <p class="text-sm text-muted my-2">
      Outbound MDM→HubSpot (Marketing-SoR). Consent-Entscheidungen, Marketing-Merkmale (Branche/Verband)
      und das Recht auf Vergessen werden mitsynchronisiert. {{ offen }} offen.
    </p>

    <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
            :title="meldung.text" close class="my-3" @update:open="meldung = null" />
    <UAlert v-if="error" color="error" variant="soft" class="my-3" :title="(error as Error).message" />

    <!-- Einzelfälle -->
    <div class="flex flex-wrap gap-3 items-end my-4 p-3 border border-default rounded-lg bg-elevated/30">
      <UFormField label="Person-ID">
        <div class="flex gap-1">
          <UInput v-model.number="personId" type="number" placeholder="z. B. 42" class="w-28" />
          <UButton color="neutral" variant="subtle" icon="i-lucide-user-round-check"
                   :loading="busy === 'contact'" :disabled="!personId" @click="doSyncContact">Kontakt</UButton>
        </div>
      </UFormField>
      <UFormField label="Organisation-ID">
        <div class="flex gap-1">
          <UInput v-model.number="orgId" type="number" placeholder="z. B. 7" class="w-28" />
          <UButton color="neutral" variant="subtle" icon="i-lucide-building-2"
                   :loading="busy === 'company'" :disabled="!orgId" @click="doSyncCompany">Firma</UButton>
        </div>
      </UFormField>
      <span class="flex-1" />
      <UButton color="error" variant="subtle" icon="i-lucide-user-x" @click="oeffneErasure">
        Recht auf Vergessen
      </UButton>
    </div>

    <div class="overflow-x-auto border border-default rounded-lg">
      <table class="w-full text-sm">
        <thead class="bg-elevated/50 text-muted text-left">
          <tr>
            <th class="px-3 py-2 font-medium">#</th>
            <th class="px-3 py-2 font-medium">Objekt</th>
            <th class="px-3 py-2 font-medium">Operation</th>
            <th class="px-3 py-2 font-medium">Partei</th>
            <th class="px-3 py-2 font-medium">Status</th>
            <th class="px-3 py-2 font-medium text-center">Vers.</th>
            <th class="px-3 py-2 font-medium">Letzter Fehler</th>
            <th class="px-3 py-2 font-medium">Erstellt</th>
            <th class="px-3 py-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in liste ?? []" :key="a.id" class="border-t border-default hover:bg-elevated/40">
            <td class="px-3 py-2 tabular-nums text-muted">{{ a.id }}</td>
            <td class="px-3 py-2">{{ a.objektTyp }}</td>
            <td class="px-3 py-2">{{ a.operation }}</td>
            <td class="px-3 py-2">{{ a.partei }}</td>
            <td class="px-3 py-2">
              <UBadge :color="statusFarbe[a.status] ?? 'neutral'" variant="soft" size="sm">{{ a.status }}</UBadge>
            </td>
            <td class="px-3 py-2 text-center tabular-nums">{{ a.versuche }}</td>
            <td class="px-3 py-2 text-muted text-xs max-w-xs truncate" :title="a.letzterFehler ?? ''">
              {{ a.letzterFehler ?? '—' }}
            </td>
            <td class="px-3 py-2 text-muted text-xs">{{ zeit(a.erstelltAm) }}</td>
            <td class="px-3 py-2 text-right">
              <UTooltip v-if="wiederholbar(a.status)" text="Erneut einreihen">
                <UButton color="neutral" variant="ghost" size="sm" icon="i-lucide-rotate-cw"
                         :loading="busy === `retry-${a.id}`" @click="doRetry(a.id)" />
              </UTooltip>
            </td>
          </tr>
          <tr v-if="(liste ?? []).length === 0">
            <td colspan="9" class="px-3 py-8 text-center text-muted">
              Keine Sync-Aufträge. „Backfill" reiht alle aktiven Parteien ein.
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Recht auf Vergessen (Bestätigung) -->
    <UModal v-model:open="erasureModal" title="Recht auf Vergessen">
      <template #body>
        <div class="space-y-3">
          <p class="text-sm text-muted">
            Stößt die <strong>permanente Löschung</strong> des Kontakts in HubSpot an (GDPR-Delete) und
            entfernt das Mapping. Ist der GDPR-Delete per Config abgeschaltet, wird stattdessen archiviert
            und der Auftrag auf <strong>MANUELL</strong> gestellt. Diese Aktion ist nicht umkehrbar.
          </p>
          <UFormField label="Person-ID" required>
            <UInput v-model.number="erasurePersonId" type="number" placeholder="z. B. 42" class="w-40" />
          </UFormField>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="erasureModal = false">Abbrechen</UButton>
          <UButton color="error" :disabled="!erasurePersonId" :loading="busy === 'erasure'"
                   @click="bestaetigeErasure">Löschen anstoßen</UButton>
        </div>
      </template>
    </UModal>
  </section>
</template>
