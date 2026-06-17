<script setup lang="ts">
// HITL-Dubletten-Review (Anmeldung Berufsschule, Schritt I): offene Fälle (Firmen/Personen) mit
// KI-Vorschlag. Mensch entscheidet je Fall: auf einen Vorschlag „Zusammenführen" (Merge) oder
// „Neuanlage bestätigen". KI priorisiert nur — entschieden wird hier.
import { ref } from 'vue';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import type { TableColumn } from '@nuxt/ui';
import { reviewQueue, entscheide, einschaetzungSchwere, ApiFehler, type Fall } from '@/dubletten';
import { Entscheidung } from '@/api/model';
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
    mut.mutateAsync({ art: f.art!, kandidatId: f.kandidatId!, entscheidung: Entscheidung.GEMERGT, zielId }),
    `„${f.bezeichnung}" wurde mit „${ziel}" zusammengeführt.`);
}

async function neuanlage(f: Fall) {
  await ausfuehren(f, () =>
    mut.mutateAsync({ art: f.art!, kandidatId: f.kandidatId!, entscheidung: Entscheidung.NEUANLAGE_BESTAETIGT }),
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

const columns: TableColumn<Fall>[] = [
  { id: 'art', header: 'Art' },
  { id: 'kandidat', header: 'Kandidat' },
  { id: 'vorschlaege', header: 'KI-Vorschläge (Merge-Ziel)' },
  { id: 'aktionen', header: '' },
];
</script>

<template>
  <section>
    <div class="flex justify-between items-center">
      <h2 class="text-xl font-bold">Dubletten-Review</h2>
      <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="() => { refetch(); }" />
    </div>
    <p class="text-muted text-sm mt-1 mb-4">
      KI schlägt vor, Sie entscheiden. „Zusammenführen" führt den Kandidaten in das Ziel; „Neuanlage
      bestätigen" lässt ihn als eigenständige Identität bestehen.
    </p>

    <UAlert
      v-if="!auth.angemeldet"
      color="warning"
      variant="soft"
      icon="i-lucide-lock"
      class="my-4"
    >
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a> (Rolle <code>rechnung-pflege</code>), um Fälle zu sehen und zu entscheiden.
      </template>
    </UAlert>
    <UAlert
      v-if="meldung"
      :color="meldung.severity === 'success' ? 'success' : 'error'"
      variant="soft"
      :title="meldung.text"
      close
      class="my-4"
      @update:open="meldung = null"
    />

    <UTable :data="data ?? []" :columns="columns" :empty="'Keine offenen Dubletten-Fälle.'">
      <template #art-cell="{ row }">
        <UBadge :color="row.original.art === 'FIRMA' ? 'info' : 'neutral'" variant="soft" size="sm">{{ row.original.art }}</UBadge>
      </template>
      <template #kandidat-cell="{ row }">
        <strong>{{ row.original.bezeichnung }}</strong> <small class="text-muted">#{{ row.original.kandidatId }}</small>
      </template>
      <template #vorschlaege-cell="{ row }">
        <div v-for="v in row.original.vorschlaege" :key="v.zielId" class="flex items-center gap-2 py-0.5">
          <UBadge :color="einschaetzungSchwere[v.einschaetzung ?? ''] ?? 'neutral'" variant="soft" size="sm">{{ v.einschaetzung }}</UBadge>
          <span class="tabular-nums text-default min-w-14">{{ prozent(v.aehnlichkeit) }}</span>
          <span class="min-w-56">{{ v.bezeichnung }} <small class="text-muted">#{{ v.zielId }}</small></span>
          <UButton
            color="error"
            variant="outline"
            size="sm"
            icon="i-lucide-link"
            :loading="aktiv === row.original.kandidatId"
            @click="zusammenfuehren(row.original, v.zielId!, v.bezeichnung ?? '')"
          >
            Zusammenführen
          </UButton>
          <small class="text-muted">{{ v.begruendung }}</small>
        </div>
      </template>
      <template #aktionen-cell="{ row }">
        <UButton
          color="neutral"
          variant="soft"
          size="sm"
          icon="i-lucide-user-plus"
          :loading="aktiv === row.original.kandidatId"
          @click="neuanlage(row.original)"
        >
          Neuanlage bestätigen
        </UButton>
      </template>
    </UTable>
  </section>
</template>
