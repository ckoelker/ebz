<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import { getCrmPersonen, getCrmOrganisationen }
  from '@/api/endpoints/crm-resource/crm-resource';
import type { PageViewPersonListItem, PageViewOrgListItem } from '@/api/model';
import PersonPanel from '@/crm/PersonPanel.vue';
import OrganisationPanel from '@/crm/OrganisationPanel.vue';
import NeuePersonDialog from '@/crm/dialoge/NeuePersonDialog.vue';
import NeueFirmaDialog from '@/crm/dialoge/NeueFirmaDialog.vue';
import SegmentedControl from '@crm-ui/ui/SegmentedControl.vue';
import MasterListItem from '@crm-ui/ui/MasterListItem.vue';

// CRM-Kernmaske (m:n-Kundenstamm) — abgestimmt über die Storybook-Abnahme. Master-Detail: links
// Segment-Filter (Alle/Personen/Firmen) + Server-Suche, reiche Zeilen (Avatar, warn/blocked) und
// Tastatur-Navigation; rechts das Detail-Panel der Auswahl.
type Sel = { type: 'person' | 'org'; id: number } | null;
type Zeile = {
  key: string; type: 'person' | 'org'; id: number;
  label: string; sub?: string; sub2?: string; org: boolean; warn: boolean; blocked: boolean;
};

const sel = ref<Sel>(null);
const q = ref('');
const filter = ref<'alle' | 'person' | 'org'>('alle');
const filterOpts = [
  { value: 'alle', label: 'Alle' },
  { value: 'person', label: 'Personen' },
  { value: 'org', label: 'Firmen' },
];

// Server-Suche über die typisierten Endpunkte (deren q-Parameter), damit die Zeilen reich bleiben
// (PersonListItem/OrgListItem) statt über die schlanke globale Suche. Leeres q = Browse-Liste.
const sucheParam = computed(() => (q.value.trim().length >= 2 ? q.value.trim() : undefined));
const zeigtPersonen = computed(() => filter.value !== 'org');
const zeigtFirmen = computed(() => filter.value !== 'person');

const personenQ = useQuery({
  queryKey: computed(() => ['crm-personen-liste', sucheParam.value]),
  queryFn: async (): Promise<PageViewPersonListItem> =>
    await getCrmPersonen({ q: sucheParam.value, size: 50 }),
  enabled: zeigtPersonen,
});
const firmenQ = useQuery({
  queryKey: computed(() => ['crm-firmen-liste', sucheParam.value]),
  queryFn: async (): Promise<PageViewOrgListItem> =>
    await getCrmOrganisationen({ q: sucheParam.value, size: 50 }),
  enabled: zeigtFirmen,
});

const personenZeilen = computed<Zeile[]>(() =>
  (personenQ.data.value?.items ?? []).map((p) => ({
    key: `person-${p.id}`, type: 'person', id: p.id!,
    label: p.anzeigeName ?? '—', org: false,
    sub: p.hauptFirma || 'Privatkontakt',
    sub2: p.ort || undefined,
    warn: p.status === 'PROVISORISCH',
    blocked: !!p.werbesperre || !!p.auskunftssperre,
  })));
const firmenZeilen = computed<Zeile[]>(() =>
  (firmenQ.data.value?.items ?? []).map((o) => ({
    key: `org-${o.id}`, type: 'org', id: o.id!,
    label: o.name ?? '—', org: true,
    sub: o.ort || undefined,
    sub2: o.ustId || undefined,
    warn: o.status === 'ANGEFRAGT',
    blocked: false,
  })));

const zeilen = computed<Zeile[]>(() => {
  if (filter.value === 'person') return personenZeilen.value;
  if (filter.value === 'org') return firmenZeilen.value;
  return [...personenZeilen.value, ...firmenZeilen.value];
});

const laedt = computed(() =>
  (zeigtPersonen.value && personenQ.isLoading.value) || (zeigtFirmen.value && firmenQ.isLoading.value));
const fehler = computed(() =>
  (zeigtPersonen.value && personenQ.isError.value) || (zeigtFirmen.value && firmenQ.isError.value));
function erneutLaden() {
  if (zeigtPersonen.value) personenQ.refetch();
  if (zeigtFirmen.value) firmenQ.refetch();
}

const neuePerson = ref(false);
const neueFirma = ref(false);

function waehle(type: 'person' | 'org', id: number) {
  sel.value = { type, id };
}
function waehleZeile(z: Zeile) {
  cursor.value = zeilen.value.findIndex((x) => x.key === z.key);
  waehle(z.type, z.id);
}

// ── Tastatur-Navigation (Best-Practice Master-Detail): ↑/↓ bewegt den Cursor, Enter wählt. ──
const cursor = ref(-1);
const listeEl = ref<HTMLElement | null>(null);
function onKeydown(e: KeyboardEvent) {
  const n = zeilen.value.length;
  if (!n) return;
  if (e.key === 'ArrowDown') { e.preventDefault(); cursor.value = Math.min(n - 1, cursor.value + 1); scrolleZuCursor(); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); cursor.value = Math.max(0, cursor.value - 1); scrolleZuCursor(); }
  else if (e.key === 'Home') { e.preventDefault(); cursor.value = 0; scrolleZuCursor(); }
  else if (e.key === 'End') { e.preventDefault(); cursor.value = n - 1; scrolleZuCursor(); }
  else if (e.key === 'Enter' && cursor.value >= 0) { e.preventDefault(); waehleZeile(zeilen.value[cursor.value]); }
}
async function scrolleZuCursor() {
  await nextTick();
  listeEl.value?.querySelector<HTMLElement>(`[data-idx="${cursor.value}"]`)?.scrollIntoView({ block: 'nearest' });
}
// Cursor zurücksetzen, wenn sich die Liste (Filter/Suche) ändert.
watch([filter, sucheParam], () => { cursor.value = -1; });

// CTI-Sprung: ein eingehender Anruf (AnrufToast) navigiert hierher mit ?person=/?org= → Kontakt wählen.
const route = useRoute();
const router = useRouter();
watch(() => route.query, (qy) => {
  if (qy.person) { waehle('person', Number(qy.person)); router.replace({ query: {} }); }
  else if (qy.org) { waehle('org', Number(qy.org)); router.replace({ query: {} }); }
}, { immediate: true });
</script>

<template>
  <div class="flex gap-4 h-[calc(100vh-9rem)]">
    <!-- Master: Filter + Suche + Liste -->
    <aside class="w-80 shrink-0 flex flex-col gap-3 border-r border-default pr-4">
      <SegmentedControl
        :model-value="filter"
        :options="filterOpts"
        @update:model-value="filter = $event as 'alle' | 'person' | 'org'"
      />
      <UInput v-model="q" icon="i-lucide-search" placeholder="Personen & Firmen suchen…" size="sm" />
      <div class="flex gap-2">
        <UButton size="sm" icon="i-lucide-user-plus" @click="neuePerson = true">Person</UButton>
        <UButton size="sm" variant="outline" icon="i-lucide-building-2" @click="neueFirma = true">Firma</UButton>
      </div>

      <div
        ref="listeEl"
        class="flex-1 overflow-auto -mx-1 px-1 focus:outline-none"
        tabindex="0"
        role="listbox"
        aria-label="Kundenstamm"
        @keydown="onKeydown"
      >
        <!-- Fehler -->
        <UAlert v-if="fehler" color="error" variant="soft" icon="i-lucide-triangle-alert"
                title="Liste konnte nicht geladen werden." class="mb-2">
          <template #actions>
            <UButton size="xs" color="error" variant="outline" @click="erneutLaden">Erneut</UButton>
          </template>
        </UAlert>

        <!-- Laden (Skeleton) -->
        <div v-else-if="laedt" class="space-y-2 py-1">
          <div v-for="i in 6" :key="i" class="flex items-center gap-3 px-3 py-2.5">
            <USkeleton class="size-8 rounded-full" />
            <div class="flex-1 space-y-1.5">
              <USkeleton class="h-3.5 w-2/3" />
              <USkeleton class="h-2.5 w-1/2" />
            </div>
          </div>
        </div>

        <!-- Leer -->
        <div v-else-if="!zeilen.length" class="text-center text-muted text-sm py-10 px-3">
          <UIcon name="i-lucide-search-x" class="text-3xl text-dimmed mb-2" />
          <p v-if="sucheParam">Keine Treffer für „{{ q }}".</p>
          <p v-else>Keine Einträge.</p>
        </div>

        <!-- Treffer -->
        <template v-else>
          <p class="text-xs text-muted px-2 mb-1">{{ zeilen.length }} Einträge</p>
          <div
            v-for="(z, i) in zeilen"
            :key="z.key"
            :data-idx="i"
            class="rounded-md"
            :class="cursor === i ? 'ring-2 ring-primary-400' : ''"
          >
            <MasterListItem
              :label="z.label"
              :sub="z.sub"
              :sub2="z.sub2"
              :org="z.org"
              :warn="z.warn"
              :blocked="z.blocked"
              :active="sel?.type === z.type && sel?.id === z.id"
              @click="waehleZeile(z)"
            />
          </div>
        </template>
      </div>
    </aside>

    <!-- Detail -->
    <section class="flex-1 min-w-0 overflow-auto">
      <PersonPanel v-if="sel?.type === 'person'" :id="sel.id"
                   @select-person="waehle('person', $event)" />
      <OrganisationPanel v-else-if="sel?.type === 'org'" :id="sel.id"
                         @select-person="waehle('person', $event)" @select-org="waehle('org', $event)" />
      <div v-else class="h-full flex flex-col items-center justify-center text-muted">
        <UIcon name="i-lucide-contact" class="text-5xl mb-3 text-dimmed" />
        <p>Person oder Firma links wählen — oder neu anlegen.</p>
      </div>
    </section>

    <NeuePersonDialog v-model:open="neuePerson"
                      @created="waehle('person', $event)" @verwenden="waehle('person', $event)" />
    <NeueFirmaDialog v-model:open="neueFirma"
                     @created="waehle('org', $event)" @verwenden="waehle('org', $event)" />
  </div>
</template>
