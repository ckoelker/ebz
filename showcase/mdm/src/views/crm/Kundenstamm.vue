<script setup lang="ts">
import { ref, computed } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import { getCrmPersonen, getCrmOrganisationen, getCrmSuche }
  from '@/api/endpoints/crm-resource/crm-resource';
import type { PageViewPersonListItem, PageViewOrgListItem, Treffer } from '@/api/model';
import PersonPanel from '@/crm/PersonPanel.vue';
import OrganisationPanel from '@/crm/OrganisationPanel.vue';
import NeuePersonDialog from '@/crm/dialoge/NeuePersonDialog.vue';
import NeueFirmaDialog from '@/crm/dialoge/NeueFirmaDialog.vue';

// CRM-Kernmaske (m:n-Kundenstamm) — abgestimmt über die Storybook-Abnahme. Master-Detail: links
// globale Sofortsuche (Personen+Firmen) bzw. Typ-Liste, rechts das Detail-Panel der Auswahl.
type Sel = { type: 'person' | 'org'; id: number } | null;
const sel = ref<Sel>(null);
const q = ref('');
const listType = ref<'person' | 'org'>('person');
const typItems = [
  { label: 'Personen', value: 'person' },
  { label: 'Firmen', value: 'org' },
];

const suchMlautet = computed(() => q.value.trim().length >= 2);

const { data: treffer } = useQuery({
  queryKey: computed(() => ['crm-suche', q.value]),
  queryFn: async (): Promise<Treffer[]> => (await getCrmSuche({ q: q.value })) ?? [],
  enabled: suchMlautet,
});

const { data: personen } = useQuery({
  queryKey: ['crm-personen-liste'],
  queryFn: async (): Promise<PageViewPersonListItem> => await getCrmPersonen({ size: 50 }),
});
const { data: firmen } = useQuery({
  queryKey: ['crm-firmen-liste'],
  queryFn: async (): Promise<PageViewOrgListItem> => await getCrmOrganisationen({ size: 50 }),
});

const neuePerson = ref(false);
const neueFirma = ref(false);

function waehle(type: 'person' | 'org', id: number) {
  sel.value = { type, id };
}
function ausTreffer(t: Treffer) {
  waehle(t.art === 'ORGANISATION' ? 'org' : 'person', t.id!);
}
</script>

<template>
  <div class="flex gap-4 h-[calc(100vh-9rem)]">
    <!-- Master: Suche + Liste -->
    <aside class="w-80 shrink-0 flex flex-col gap-3 border-r border-default pr-4">
      <UInput v-model="q" icon="i-lucide-search" placeholder="Personen & Firmen suchen…" />
      <div class="flex gap-2">
        <UButton size="sm" icon="i-lucide-user-plus" @click="neuePerson = true">Person</UButton>
        <UButton size="sm" variant="outline" icon="i-lucide-building-2" @click="neueFirma = true">Firma</UButton>
      </div>

      <!-- Suchergebnisse (gemischt) -->
      <div v-if="suchMlautet" class="flex-1 overflow-auto">
        <p class="text-xs text-muted mb-1">{{ (treffer ?? []).length }} Treffer</p>
        <ul class="space-y-1">
          <li v-for="t in treffer" :key="`${t.art}-${t.id}`">
            <button
              class="w-full text-left px-3 py-2 rounded-md hover:bg-elevated transition"
              :class="{ 'bg-elevated': sel?.id === t.id && sel?.type === (t.art === 'ORGANISATION' ? 'org' : 'person') }"
              @click="ausTreffer(t)"
            >
              <div class="flex items-center gap-2">
                <UIcon :name="t.art === 'ORGANISATION' ? 'i-lucide-building-2' : 'i-lucide-user'" class="text-dimmed" />
                <span class="text-sm font-medium truncate">{{ t.titel }}</span>
              </div>
              <div class="text-xs text-muted truncate pl-6">{{ t.untertitel }}</div>
            </button>
          </li>
        </ul>
      </div>

      <!-- Typ-Liste -->
      <template v-else>
        <USelect v-model="listType" :items="typItems" size="sm" />
        <div class="flex-1 overflow-auto">
          <ul v-if="listType === 'person'" class="space-y-1">
            <li v-for="p in personen?.items" :key="p.id">
              <button class="w-full text-left px-3 py-2 rounded-md hover:bg-elevated transition"
                      :class="{ 'bg-elevated': sel?.type === 'person' && sel?.id === p.id }"
                      @click="waehle('person', p.id!)">
                <div class="text-sm font-medium truncate">{{ p.anzeigeName }}</div>
                <div class="text-xs text-muted truncate">{{ [p.hauptFirma, p.ort].filter(Boolean).join(' · ') || '—' }}</div>
              </button>
            </li>
          </ul>
          <ul v-else class="space-y-1">
            <li v-for="o in firmen?.items" :key="o.id">
              <button class="w-full text-left px-3 py-2 rounded-md hover:bg-elevated transition"
                      :class="{ 'bg-elevated': sel?.type === 'org' && sel?.id === o.id }"
                      @click="waehle('org', o.id!)">
                <div class="text-sm font-medium truncate">{{ o.name }}</div>
                <div class="text-xs text-muted truncate">{{ [o.ort, o.ustId].filter(Boolean).join(' · ') || '—' }}</div>
              </button>
            </li>
          </ul>
        </div>
      </template>
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
