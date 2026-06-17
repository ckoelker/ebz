<script setup lang="ts">
// Verwaltungsliste über ALLE Typen (Registry-Endpunkt /bildung/angebote). Filter typ/bereich/
// status; Neu (Typ wählen) → Pflege-Maske; Zeile → Bearbeiten; Archivieren = Soft-Delete.
import { ref, computed, h, resolveComponent } from 'vue';
import { useRouter } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import { getPaginationRowModel } from '@tanstack/vue-table';
import type { TableColumn } from '@nuxt/ui';
import type { Column } from '@tanstack/vue-table';
import { Bereich, AngebotStatus } from '@/api/model';
import type { RegistryItemDto } from '@/api/model';
import { getBildungAngebote } from '@/api/endpoints/bildung-resource/bildung-resource';
import { typen, alleTypen, projiziereInShop, type Typ } from '@/bildung';
import { login } from '@/auth';

const router = useRouter();

const { data, isFetching, refetch } = useQuery({
  queryKey: ['angebote'],
  queryFn: async (): Promise<RegistryItemDto[]> => (await getBildungAngebote()) ?? [],
});

// Filter — leerer Wert ('') = kein Filter; Items mit „Alle …"-Eintrag statt PrimeVue-showClear.
const fTyp = ref('');
const fBereich = ref('');
const fStatus = ref('');

const typItems = [{ label: 'Alle Typen', value: '' }, ...alleTypen.map((t) => ({ label: typen[t].label, value: t }))];
const bereichItems = [{ label: 'Alle Bereiche', value: '' }, ...Object.values(Bereich).map((b) => ({ label: b, value: b }))];
const statusItems = [{ label: 'Alle Status', value: '' }, ...Object.values(AngebotStatus).map((s) => ({ label: s, value: s }))];
const neuItems = alleTypen.map((t) => ({ label: typen[t].label, value: t }));

const gefiltert = computed(() =>
  (data.value ?? []).filter(
    (a) =>
      (!fTyp.value || a.typ === fTyp.value) &&
      (!fBereich.value || a.bereich === fBereich.value) &&
      (!fStatus.value || a.status === fStatus.value),
  ),
);

const neuTyp = ref<Typ | undefined>();
function neu() {
  if (neuTyp.value) router.push(`/pflege/${neuTyp.value}`);
}
function bearbeiten(row: RegistryItemDto) {
  router.push(`/pflege/${row.typ}/${row.id}`);
}
async function archivieren(row: RegistryItemDto) {
  if (!row.id || !row.typ) return;
  await typen[row.typ as Typ].remove(row.id as unknown as number);
  await refetch();
}

// ── Shop-Projektion (P1.3) ──
const projektionMeldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const projizierendeId = ref<number | null>(null);

/** Eine Zeile ist projizierbar, wenn sie verkäuflich und aktiv ist (Server-Guard spiegelt das). */
function projizierbar(row: RegistryItemDto): boolean {
  return !!row.shopVerkauf && row.status === 'AKTIV';
}

async function projizieren(row: RegistryItemDto) {
  if (!row.id) return;
  projektionMeldung.value = null;
  projizierendeId.value = row.id;
  try {
    const res = await projiziereInShop(row.id);
    const status = res.response?.status;
    if (res.error || (res.response && !res.response.ok)) {
      if (status === 401) return login(); // SSO-Redirect
      if (status === 403) {
        projektionMeldung.value = { text: 'Keine Berechtigung: Rolle „katalog-pflege" erforderlich.', severity: 'error' };
        return;
      }
      const msg = (res.error as { message?: string } | undefined)?.message;
      projektionMeldung.value = {
        text: msg ?? `Projektion fehlgeschlagen (HTTP ${status ?? '?'}).`,
        severity: 'error',
      };
      return;
    }
    const pid = (res.data as { vendureProductId?: string } | undefined)?.vendureProductId;
    projektionMeldung.value = { text: `„${row.titel}" im Shop veröffentlicht (Vendure-Produkt ${pid}).`, severity: 'success' };
    await refetch();
  } finally {
    projizierendeId.value = null;
  }
}

const statusFarbe: Record<string, 'neutral' | 'success' | 'warning'> = {
  ENTWURF: 'neutral',
  AKTIV: 'success',
  ARCHIVIERT: 'warning',
};

// Sortierbarer Spaltenkopf (Nuxt-UI/TanStack-Muster): Klick toggelt asc/desc.
function sortHeader(label: string) {
  return ({ column }: { column: Column<RegistryItemDto> }) => {
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

const columns: TableColumn<RegistryItemDto>[] = [
  { accessorKey: 'code', header: sortHeader('Code') },
  { accessorKey: 'titel', header: sortHeader('Titel') },
  { accessorKey: 'typ', header: sortHeader('Typ') },
  { accessorKey: 'bereich', header: sortHeader('Bereich') },
  { accessorKey: 'status', header: sortHeader('Status') },
  { id: 'shop', header: 'Shop' },
  { id: 'aktionen', header: '' },
];

// Pagination wie zuvor (10 Zeilen/Seite) — TanStack-Modell + separate UPagination.
const pagination = ref({ pageIndex: 0, pageSize: 10 });
const page = computed({
  get: () => pagination.value.pageIndex + 1,
  set: (p: number) => {
    pagination.value.pageIndex = p - 1;
  },
});
</script>

<template>
  <section>
    <div class="flex justify-between items-center">
      <h2 class="text-xl font-bold">Bildungsangebote</h2>
      <div class="flex gap-2 items-center">
        <USelect v-model="neuTyp" :items="neuItems" placeholder="Typ für Neu…" class="w-44" />
        <UButton icon="i-lucide-plus" :disabled="!neuTyp" @click="neu">Neu</UButton>
      </div>
    </div>

    <div class="flex gap-2 items-center my-4">
      <USelect v-model="fTyp" :items="typItems" class="w-40" />
      <USelect v-model="fBereich" :items="bereichItems" class="w-40" />
      <USelect v-model="fStatus" :items="statusItems" class="w-40" />
      <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="() => { refetch(); }" />
    </div>

    <UAlert
      v-if="projektionMeldung"
      :color="projektionMeldung.severity === 'success' ? 'success' : 'error'"
      variant="soft"
      :title="projektionMeldung.text"
      close
      class="mb-4"
      @update:open="projektionMeldung = null"
    />

    <UTable
      ref="table"
      v-model:pagination="pagination"
      :data="gefiltert"
      :columns="columns"
      :pagination-options="{ getPaginationRowModel: getPaginationRowModel() }"
      :empty="'Keine Bildungsangebote.'"
    >
      <template #typ-cell="{ row }">
        <UBadge color="neutral" variant="subtle" size="sm">{{ typen[row.original.typ as Typ]?.label ?? row.original.typ }}</UBadge>
      </template>
      <template #status-cell="{ row }">
        <UBadge :color="statusFarbe[row.original.status ?? ''] ?? 'neutral'" variant="soft" size="sm">{{ row.original.status }}</UBadge>
      </template>
      <template #shop-cell="{ row }">
        <span v-if="row.original.vendureProductId" class="inline-flex items-center gap-1.5">
          <UBadge color="success" variant="soft" size="sm">im Shop</UBadge>
          <small class="text-muted whitespace-nowrap">Produkt {{ row.original.vendureProductId }}</small>
          <UTooltip v-if="projizierbar(row.original)" text="Erneut projizieren">
            <UButton
              color="neutral"
              variant="ghost"
              size="sm"
              icon="i-lucide-refresh-cw"
              :loading="projizierendeId === row.original.id"
              @click="projizieren(row.original)"
            />
          </UTooltip>
        </span>
        <UButton
          v-else-if="projizierbar(row.original)"
          color="neutral"
          variant="ghost"
          size="sm"
          icon="i-lucide-shopping-cart"
          :loading="projizierendeId === row.original.id"
          @click="projizieren(row.original)"
        >
          Veröffentlichen
        </UButton>
        <UTooltip
          v-else
          :text="row.original.shopVerkauf ? 'Erst im Status AKTIV projizierbar' : 'Nicht für den Shop markiert'"
        >
          <UIcon :name="row.original.shopVerkauf ? 'i-lucide-clock' : 'i-lucide-minus'" class="text-muted" />
        </UTooltip>
      </template>
      <template #aktionen-cell="{ row }">
        <div class="flex gap-1 justify-end">
          <UButton color="neutral" variant="ghost" size="sm" icon="i-lucide-pencil" @click="bearbeiten(row.original)" />
          <UButton
            v-if="row.original.status !== 'ARCHIVIERT'"
            color="warning"
            variant="ghost"
            size="sm"
            icon="i-lucide-archive"
            @click="archivieren(row.original)"
          />
        </div>
      </template>
    </UTable>

    <div v-if="gefiltert.length > pagination.pageSize" class="flex justify-end mt-3">
      <UPagination v-model:page="page" :total="gefiltert.length" :items-per-page="pagination.pageSize" />
    </div>
  </section>
</template>
