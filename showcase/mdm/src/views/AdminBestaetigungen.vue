<script setup lang="ts">
// Pflicht-Bestätigungs-Report (K5, Cockpit-Sicht): das Backoffice sieht alle kenntnisnahmepflichtigen
// Benachrichtigungen mit ihrem Stand (offen/bestätigt/überfällig/eskaliert), Frist und Anzahl der
// autonom versendeten Erinnerungen. Reine Lese-Sicht — die Quittierung erfolgt durch die Person im
// Portal; der BestaetigungService erinnert/eskaliert serverseitig. Rolle crm-pflege.
import { ref, onMounted, computed } from 'vue';
import { bestaetigungsReport, ApiFehler, type BestaetigungReportView } from '@/kommunikation';
import { auth, login } from '@/auth';
import NichtAbgestimmtBanner from '@/components/NichtAbgestimmtBanner.vue';
import { datumZeit } from '@crm-ui/domain/format';

const laden = ref(false);
const meldung = ref<string | null>(null);
const liste = ref<BestaetigungReportView[]>([]);
const filter = ref<'ALLE' | 'OFFEN' | 'UEBERFAELLIG' | 'ESKALIERT' | 'BESTAETIGT'>('ALLE');

const angemeldet = computed(() => auth.angemeldet);

const filterItems = [
  { label: 'Alle', value: 'ALLE' },
  { label: 'Offen', value: 'OFFEN' },
  { label: 'Überfällig', value: 'UEBERFAELLIG' },
  { label: 'Eskaliert', value: 'ESKALIERT' },
  { label: 'Bestätigt', value: 'BESTAETIGT' },
];

onMounted(async () => { if (auth.angemeldet) await neuLaden(); });

async function neuLaden() {
  laden.value = true;
  try {
    liste.value = await bestaetigungsReport();
  } catch (e) {
    if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
    meldung.value = (e as Error).message;
  } finally {
    laden.value = false;
  }
}

const zaehler = computed(() => {
  const z = { OFFEN: 0, UEBERFAELLIG: 0, ESKALIERT: 0, BESTAETIGT: 0 } as Record<string, number>;
  for (const r of liste.value) if (r.status && r.status in z) z[r.status]++;
  return z;
});

const gefiltert = computed(() =>
  filter.value === 'ALLE' ? liste.value : liste.value.filter((r) => r.status === filter.value));

function statusFarbe(s?: string): 'warning' | 'error' | 'success' | 'neutral' {
  return s === 'BESTAETIGT' ? 'success' : s === 'ESKALIERT' || s === 'UEBERFAELLIG' ? 'error'
    : s === 'OFFEN' ? 'warning' : 'neutral';
}
function statusLabel(s?: string): string {
  return s === 'BESTAETIGT' ? 'bestätigt' : s === 'ESKALIERT' ? 'eskaliert'
    : s === 'UEBERFAELLIG' ? 'überfällig' : s === 'OFFEN' ? 'offen' : (s ?? '');
}

const zeit = (iso?: string | null): string => datumZeit(iso) || '—';
</script>

<template>
  <section>
    <NichtAbgestimmtBanner />
    <div class="flex items-center justify-between mb-4">
      <h2 class="text-xl font-bold">Pflicht-Kenntnisnahmen</h2>
      <UButton v-if="angemeldet" color="neutral" variant="ghost" size="sm" icon="i-lucide-refresh-cw"
        :loading="laden" @click="neuLaden">Aktualisieren</UButton>
    </div>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a> (Rolle crm-pflege).
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" color="error" variant="soft" :title="meldung" close class="mb-4"
        @update:open="meldung = null" />

      <!-- Status-Übersicht -->
      <div class="flex flex-wrap items-center gap-2 mb-4">
        <UBadge color="warning" variant="soft" size="lg">{{ zaehler.OFFEN }} offen</UBadge>
        <UBadge color="error" variant="soft" size="lg">{{ zaehler.UEBERFAELLIG }} überfällig</UBadge>
        <UBadge color="error" variant="solid" size="lg">{{ zaehler.ESKALIERT }} eskaliert</UBadge>
        <UBadge color="success" variant="soft" size="lg">{{ zaehler.BESTAETIGT }} bestätigt</UBadge>
        <USelect v-model="filter" :items="filterItems" size="sm" class="ml-auto w-44" />
      </div>

      <UAlert v-if="!laden && gefiltert.length === 0" color="info" variant="soft"
        title="Keine Einträge für diesen Filter." />

      <div v-else class="border border-default rounded-lg overflow-auto">
        <table class="w-full text-sm">
          <thead class="bg-elevated text-left">
            <tr>
              <th class="px-3 py-2 font-semibold">Person</th>
              <th class="px-3 py-2 font-semibold">Betreff</th>
              <th class="px-3 py-2 font-semibold">Eingang</th>
              <th class="px-3 py-2 font-semibold">Frist</th>
              <th class="px-3 py-2 font-semibold text-center">Erinn.</th>
              <th class="px-3 py-2 font-semibold">Status</th>
              <th class="px-3 py-2 font-semibold">Bestätigt</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-default">
            <tr v-for="r in gefiltert" :key="r.ereignisId" class="hover:bg-elevated/50">
              <td class="px-3 py-2">
                <span class="font-medium">{{ r.personName }}</span>
                <span class="text-dimmed text-xs"> #{{ r.personId }}</span>
              </td>
              <td class="px-3 py-2">{{ r.betreff }}</td>
              <td class="px-3 py-2 text-dimmed">{{ zeit(r.zeitpunkt) }}</td>
              <td class="px-3 py-2" :class="r.status === 'UEBERFAELLIG' || r.status === 'ESKALIERT'
                ? 'text-error font-medium' : 'text-dimmed'">{{ zeit(r.bestaetigenBis) }}</td>
              <td class="px-3 py-2 text-center">{{ r.erinnerungen }}</td>
              <td class="px-3 py-2">
                <UBadge :color="statusFarbe(r.status)"
                  :variant="r.status === 'ESKALIERT' ? 'solid' : 'soft'" size="sm">
                  {{ statusLabel(r.status) }}
                </UBadge>
              </td>
              <td class="px-3 py-2 text-dimmed">{{ zeit(r.bestaetigtAm) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
