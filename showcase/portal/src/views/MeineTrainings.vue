<script setup lang="ts">
// Self-Service-WBT-Zugang: der eingeloggte Lernende sieht seine eigenen Kurs-Einschreibungen und
// startet einen freigeschalteten Kurs per SSO-Deeplink in OpenOLAT (neuer Tab → Keycloak-SSO → Kurs).
// Der Aufrufer wird serverseitig über den Token-sub aufgelöst; launchUrl liefert das Backend nur für
// EINGESCHRIEBENe Trainings.
import { ref, onMounted, computed } from 'vue';
import type { TableColumn } from '@nuxt/ui';
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

const statusFarbe: Record<string, 'warning' | 'success' | 'error' | 'neutral'> = {
  ANGEFORDERT: 'warning',
  EINGESCHRIEBEN: 'success',
  FEHLGESCHLAGEN: 'error',
};
const statusText: Record<string, string> = {
  ANGEFORDERT: 'wird bereitgestellt …',
  EINGESCHRIEBEN: 'verfügbar',
  FEHLGESCHLAGEN: 'Problem — bitte EBZ kontaktieren',
};

const columns: TableColumn<MeinTrainingView>[] = [
  { accessorKey: 'kursTitel', header: 'Training' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'aktion', header: '' },
];
</script>

<template>
  <section>
    <h2 class="text-xl font-bold mb-4">Meine Trainings</h2>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a>, um Ihre Trainings zu sehen.
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
        :title="meldung.text" close class="mb-4" @update:open="meldung = null" />

      <UTable :data="trainings" :columns="columns" :loading="laden"
        :empty="'Sie haben noch keine Trainings gebucht.'">
        <template #status-cell="{ row }">
          <UBadge :color="statusFarbe[row.original.status ?? ''] ?? 'neutral'" variant="soft" size="sm">
            {{ statusText[row.original.status ?? ''] ?? row.original.status }}
          </UBadge>
        </template>
        <template #aktion-cell="{ row }">
          <UButton v-if="row.original.launchUrl" size="sm" icon="i-lucide-external-link"
            @click="starten(row.original)">Kurs starten</UButton>
          <span v-else class="text-dimmed">—</span>
        </template>
      </UTable>
    </template>
  </section>
</template>
