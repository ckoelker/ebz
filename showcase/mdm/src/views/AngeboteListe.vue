<script setup lang="ts">
// Verwaltungsliste über ALLE Typen (Registry-Endpunkt /bildung/angebote). Filter typ/bereich/
// status; Neu (Typ wählen) → Pflege-Maske; Zeile → Bearbeiten; Archivieren = Soft-Delete.
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Select from 'primevue/select';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import * as zod from '@/gen/zod.gen';
import type { RegistryItemDto } from '@/gen/types.gen';
import { getBildungAngebote } from '@/gen/sdk.gen';
import { typen, alleTypen, projiziereInShop, type Typ } from '@/bildung';
import { login } from '@/auth';

const router = useRouter();

const { data, isFetching, refetch } = useQuery({
  queryKey: ['angebote'],
  queryFn: async (): Promise<RegistryItemDto[]> => (await getBildungAngebote()).data ?? [],
});

const fTyp = ref<Typ | null>(null);
const fBereich = ref<string | null>(null);
const fStatus = ref<string | null>(null);

const gefiltert = computed(() =>
  (data.value ?? []).filter(
    (a) =>
      (!fTyp.value || a.typ === fTyp.value) &&
      (!fBereich.value || a.bereich === fBereich.value) &&
      (!fStatus.value || a.status === fStatus.value),
  ),
);

const neuTyp = ref<Typ | null>(null);
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

const statusSchwere: Record<string, 'secondary' | 'success' | 'warn'> = {
  ENTWURF: 'secondary',
  AKTIV: 'success',
  ARCHIVIERT: 'warn',
};
</script>

<template>
  <section>
    <div class="kopf">
      <h2>Bildungsangebote</h2>
      <div class="neu">
        <Select v-model="neuTyp" :options="alleTypen" placeholder="Typ für Neu…" />
        <Button label="Neu" icon="pi pi-plus" :disabled="!neuTyp" @click="neu" />
      </div>
    </div>

    <div class="filter">
      <Select v-model="fTyp" :options="alleTypen" placeholder="Typ" showClear />
      <Select v-model="fBereich" :options="[...zod.zBereich.options]" placeholder="Bereich" showClear />
      <Select v-model="fStatus" :options="[...zod.zAngebotStatus.options]" placeholder="Status" showClear />
      <Button text icon="pi pi-refresh" :loading="isFetching" @click="() => refetch()" />
    </div>

    <Message v-if="projektionMeldung" :severity="projektionMeldung.severity" :closable="true" @close="projektionMeldung = null">
      {{ projektionMeldung.text }}
    </Message>

    <DataTable :value="gefiltert" dataKey="id" stripedRows paginator :rows="10" size="small">
      <Column field="code" header="Code" sortable />
      <Column field="titel" header="Titel" sortable />
      <Column field="typ" header="Typ" sortable>
        <template #body="{ data: r }"><Tag :value="typen[r.typ as Typ]?.label ?? r.typ" /></template>
      </Column>
      <Column field="bereich" header="Bereich" sortable />
      <Column field="status" header="Status" sortable>
        <template #body="{ data: r }"><Tag :value="r.status" :severity="statusSchwere[r.status]" /></template>
      </Column>
      <Column header="Shop" style="width: 12rem">
        <template #body="{ data: r }">
          <span v-if="r.vendureProductId" class="shop-status">
            <Tag value="im Shop" severity="success" />
            <small class="vid">Produkt {{ r.vendureProductId }}</small>
            <Button v-if="projizierbar(r)" text rounded size="small" icon="pi pi-sync"
              v-tooltip.top="'Erneut projizieren'" :loading="projizierendeId === r.id" @click="projizieren(r)" />
          </span>
          <Button v-else-if="projizierbar(r)" text size="small" icon="pi pi-shopping-cart" label="Veröffentlichen"
            :loading="projizierendeId === r.id" @click="projizieren(r)" />
          <i v-else :class="r.shopVerkauf ? 'pi pi-clock' : 'pi pi-minus'"
            v-tooltip.top="r.shopVerkauf ? 'Erst im Status AKTIV projizierbar' : 'Nicht für den Shop markiert'" />
        </template>
      </Column>
      <Column header="" style="width: 6rem">
        <template #body="{ data: r }">
          <Button text rounded icon="pi pi-pencil" @click="bearbeiten(r)" />
          <Button v-if="r.status !== 'ARCHIVIERT'" text rounded severity="warn" icon="pi pi-inbox" @click="archivieren(r)" />
        </template>
      </Column>
      <template #empty><div class="leer">Keine Bildungsangebote.</div></template>
    </DataTable>
  </section>
</template>

<style scoped>
.kopf {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.neu,
.filter {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
.filter {
  margin: 1rem 0;
}
.leer {
  padding: 1rem;
  color: #888;
}
.shop-status {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.vid {
  color: #888;
  white-space: nowrap;
}
</style>
