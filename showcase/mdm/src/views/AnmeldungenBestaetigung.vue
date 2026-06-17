<script setup lang="ts">
// EBZ-Bestätigung (Anmeldung Berufsschule, Schritt I/E): offene Anmeldungen prüfen und bestätigen
// (ANGEFRAGT → BESTAETIGT_EBZ; löst die Benachrichtigung an Azubi + Firma aus). Noch nicht
// abrechenbar — das wird die Anmeldung erst mit der Vertragsbestätigung der Firma (AKTIV).
import { ref, computed, h, resolveComponent } from 'vue';
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import { getPaginationRowModel } from '@tanstack/vue-table';
import type { TableColumn } from '@nuxt/ui';
import type { Column } from '@tanstack/vue-table';
import { AnmeldungStatus } from '@/api/model';
import { offeneAnmeldungen, bestaetigeAnmeldung, ApiFehler, type OffeneAnmeldungView } from '@/dubletten';
import { auth, login } from '@/auth';
import NichtAbgestimmtBanner from '@/components/NichtAbgestimmtBanner.vue';

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

const statusFarbe: Record<string, 'warning' | 'info'> = { ANGEFRAGT: 'warning', BESTAETIGT_EBZ: 'info' };
const istAngefragt = computed(() => status.value === 'ANGEFRAGT');

const statusItems = ['ANGEFRAGT', 'BESTAETIGT_EBZ'];

function sortHeader(label: string) {
  return ({ column }: { column: Column<OffeneAnmeldungView> }) => {
    const UButton = resolveComponent('UButton');
    const dir = column.getIsSorted();
    return h(UButton, {
      color: 'neutral',
      variant: 'ghost',
      label,
      class: '-mx-2.5',
      icon: dir === 'asc' ? 'i-lucide-arrow-up' : dir === 'desc' ? 'i-lucide-arrow-down' : 'i-lucide-arrow-up-down',
      onClick: () => column.toggleSorting(column.getIsSorted() === 'asc'),
    });
  };
}

const columns: TableColumn<OffeneAnmeldungView>[] = [
  { accessorKey: 'teilnehmerName', header: sortHeader('Teilnehmer:in') },
  { accessorKey: 'schuljahr', header: sortHeader('Schuljahr') },
  { accessorKey: 'halbjahr', header: 'HJ' },
  { id: 'status', header: 'Status' },
  { id: 'aktionen', header: '' },
];

const pagination = ref({ pageIndex: 0, pageSize: 15 });
const page = computed({
  get: () => pagination.value.pageIndex + 1,
  set: (p: number) => {
    pagination.value.pageIndex = p - 1;
  },
});
</script>

<template>
  <section>
    <NichtAbgestimmtBanner />
    <div class="flex justify-between items-center">
      <h2 class="text-xl font-bold">Anmeldungen — EBZ-Bestätigung</h2>
      <div class="flex gap-2 items-center">
        <USelect v-model="status" :items="statusItems" class="w-48" />
        <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="() => { refetch(); }" />
      </div>
    </div>

    <UAlert
      v-if="!auth.angemeldet"
      color="warning"
      variant="soft"
      icon="i-lucide-lock"
      class="my-4"
    >
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a> (Rolle <code>rechnung-pflege</code>).
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

    <UTable
      v-model:pagination="pagination"
      :data="data ?? []"
      :columns="columns"
      :pagination-options="{ getPaginationRowModel: getPaginationRowModel() }"
      :empty="`Keine Anmeldungen im Status ${status}.`"
    >
      <template #status-cell="{ row }">
        <UBadge :color="statusFarbe[row.original.status ?? ''] ?? 'neutral'" variant="soft" size="sm">{{ row.original.status }}</UBadge>
      </template>
      <template #aktionen-cell="{ row }">
        <UButton
          v-if="istAngefragt"
          size="sm"
          icon="i-lucide-check"
          :loading="aktiv === row.original.anmeldungId"
          @click="bestaetigen(row.original)"
        >
          EBZ bestätigen
        </UButton>
        <span v-else class="inline-flex items-center gap-1 text-sm text-success">
          <UIcon name="i-lucide-check-circle" /> bestätigt
        </span>
      </template>
    </UTable>

    <div v-if="(data?.length ?? 0) > pagination.pageSize" class="flex justify-end mt-3">
      <UPagination v-model:page="page" :total="data?.length ?? 0" :items-per-page="pagination.pageSize" />
    </div>
  </section>
</template>
