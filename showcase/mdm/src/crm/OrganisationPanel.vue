<script setup lang="ts">
import { ref, computed } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import { getCrmOrganisationenId, getCrmOrganisationenIdAktivitaeten, getCrmOrganisationenIdUebersicht,
  getCrmOrganisationenIdEinwilligungen, getCrmOrganisationenIdWeiterbildung }
  from '@/api/endpoints/crm-resource/crm-resource';
import type { OrgDetail, AktivitaetView, Uebersicht360View, EinwilligungView,
  WeiterbildungOrgZeileView } from '@/api/model';
import NeueFirmaDialog from '@/crm/dialoge/NeueFirmaDialog.vue';
import KontaktpunktDialog from '@/crm/dialoge/KontaktpunktDialog.vue';
import NotizDialog from '@/crm/dialoge/NotizDialog.vue';
import Uebersicht360 from '@/crm/Uebersicht360.vue';

const props = defineProps<{ id: number }>();
const emit = defineEmits<{ (e: 'select-person', id: number): void; (e: 'select-org', id: number): void }>();
const qc = useQueryClient();

const { data: org, isFetching } = useQuery({
  queryKey: computed(() => ['crm-org', props.id]),
  queryFn: async (): Promise<OrgDetail> => await getCrmOrganisationenId(props.id),
});
const { data: historie } = useQuery({
  queryKey: computed(() => ['crm-org-akt', props.id]),
  queryFn: async (): Promise<AktivitaetView[]> => (await getCrmOrganisationenIdAktivitaeten(props.id)) ?? [],
});
const { data: uebersicht } = useQuery({
  queryKey: computed(() => ['crm-org-360', props.id]),
  queryFn: async (): Promise<Uebersicht360View> => await getCrmOrganisationenIdUebersicht(props.id),
});
const { data: einwilligungen } = useQuery({
  queryKey: computed(() => ['crm-org-einw', props.id]),
  queryFn: async (): Promise<EinwilligungView[]> => (await getCrmOrganisationenIdEinwilligungen(props.id)) ?? [],
});
const { data: weiterbildung } = useQuery({
  queryKey: computed(() => ['crm-org-wb', props.id]),
  queryFn: async (): Promise<WeiterbildungOrgZeileView[]> => (await getCrmOrganisationenIdWeiterbildung(props.id)) ?? [],
});
function reload() {
  qc.invalidateQueries({ queryKey: ['crm-org', props.id] });
  qc.invalidateQueries({ queryKey: ['crm-org-akt', props.id] });
}

const tab = ref('stammdaten');
const tabs = [
  { key: 'stammdaten', label: 'Stammdaten', icon: 'i-lucide-building-2' },
  { key: 'personen', label: 'Personen', icon: 'i-lucide-users' },
  { key: 'kommunikation', label: 'Kommunikation', icon: 'i-lucide-history' },
  { key: 'uebersicht', label: '360°', icon: 'i-lucide-layout-dashboard' },
  { key: 'einwilligung', label: 'Einwilligung', icon: 'i-lucide-mail-check' },
  { key: 'weiterbildung', label: 'Weiterbildung', icon: 'i-lucide-graduation-cap' },
  { key: 'hierarchie', label: 'Hierarchie', icon: 'i-lucide-network' },
];
const editStamm = ref(false);
const kpDialog = ref(false);
const notizDialog = ref(false);
const notizEdit = ref<AktivitaetView | null>(null);
function neueNotiz() { notizEdit.value = null; notizDialog.value = true; }
function bearbeiteNotiz(a: AktivitaetView) { notizEdit.value = a; notizDialog.value = true; }

const richtungIcon = (r?: string) =>
  r === 'EINGEHEND' ? 'i-lucide-arrow-down-left' : r === 'INTERN' ? 'i-lucide-dot' : 'i-lucide-arrow-up-right';
const einwColor = (s?: string) =>
  s === 'ERTEILT' ? 'success' : s === 'WIDERRUFEN' ? 'error' : 'warning';
const ampelColor = (a?: string) => (a === 'GRUEN' ? 'success' : a === 'ROT' ? 'error' : 'warning');
const ampelIcon = (a?: string) =>
  a === 'GRUEN' ? 'i-lucide-circle-check' : a === 'ROT' ? 'i-lucide-circle-alert' : 'i-lucide-circle-dot';

const adresse = computed(() => {
  const o = org.value;
  if (!o) return '—';
  return [[o.strasse].filter(Boolean).join(' '), [o.plz, o.ort].filter(Boolean).join(' ')]
    .filter(Boolean).join(', ') || '—';
});
const aktiv = (bis?: string) => !bis;
</script>

<template>
  <div v-if="org" class="space-y-4">
    <UCard>
      <div class="flex items-start gap-4">
        <UAvatar :alt="org.name" size="lg" icon="i-lucide-building-2" />
        <div class="flex-1 min-w-0">
          <h2 class="text-xl font-bold text-highlighted">{{ org.name }}</h2>
          <p class="text-sm text-muted">{{ org.rechtsform }} <span v-if="org.ustId">· {{ org.ustId }}</span></p>
          <div class="flex flex-wrap gap-1.5 mt-2">
            <UBadge :color="org.status === 'AKTIV' ? 'success' : 'neutral'" variant="soft" size="sm">{{ org.status }}</UBadge>
            <UBadge v-if="org.ausbildungsbetrieb" color="info" variant="soft" size="sm">Ausbildungsbetrieb</UBadge>
            <UBadge v-for="t in org.unternehmenstypen" :key="t" color="neutral" variant="soft" size="sm">{{ t }}</UBadge>
          </div>
        </div>
        <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="reload" />
      </div>
    </UCard>

    <div class="flex gap-1 border-b border-default flex-wrap">
      <UButton v-for="t in tabs" :key="t.key" :color="tab === t.key ? 'primary' : 'neutral'"
               :variant="tab === t.key ? 'soft' : 'ghost'" :icon="t.icon" size="sm" @click="tab = t.key">
        {{ t.label }}
      </UButton>
    </div>

    <UCard v-if="tab === 'stammdaten'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Stammdaten</h3>
        <div class="flex gap-2">
          <UButton size="sm" variant="outline" icon="i-lucide-map-pin" @click="kpDialog = true">Adresse/Kanal</UButton>
          <UButton size="sm" icon="i-lucide-pencil" @click="editStamm = true">Bearbeiten</UButton>
        </div>
      </div>
      <dl class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <div><dt class="text-muted">Branche</dt><dd>{{ org.brancheCode || '—' }}</dd></div>
        <div><dt class="text-muted">Website</dt><dd>{{ org.website || '—' }}</dd></div>
        <div><dt class="text-muted">HRB</dt><dd>{{ org.handelsregisternummer || '—' }}</dd></div>
        <div><dt class="text-muted">Registergericht</dt><dd>{{ org.registergericht || '—' }}</dd></div>
        <div><dt class="text-muted">Bestandsgröße</dt><dd>{{ org.bestandsgroesse ?? '—' }}</dd></div>
        <div><dt class="text-muted">Gewerbeerlaubnis §34c</dt><dd>{{ org.gewerbeerlaubnis }}</dd></div>
        <div><dt class="text-muted">IHK</dt><dd>{{ org.ihkKammerCode || '—' }}</dd></div>
        <div><dt class="text-muted">Adresse</dt><dd>{{ adresse }}</dd></div>
        <div class="col-span-2"><dt class="text-muted">Schwerpunkte</dt><dd>{{ org.taetigkeitsschwerpunkte?.join(', ') || '—' }}</dd></div>
        <div class="col-span-2"><dt class="text-muted">Verbände</dt><dd>{{ org.verbaende?.join(', ') || '—' }}</dd></div>
      </dl>
    </UCard>

    <UCard v-else-if="tab === 'personen'">
      <h3 class="font-semibold mb-3">Verknüpfte Personen</h3>
      <p v-if="!org.mitglieder?.length" class="text-sm text-muted">Keine verknüpften Personen.</p>
      <ul class="divide-y divide-default">
        <li v-for="m in org.mitglieder" :key="m.mitgliedschaftId" class="flex items-center gap-3 py-2"
            :class="{ 'opacity-50': !aktiv(m.gueltigBis) }">
          <UButton color="primary" variant="link" class="px-0" @click="emit('select-person', m.personId!)">
            {{ m.person }}
          </UButton>
          <span class="text-sm text-muted">· {{ m.rolle }}</span>
          <div class="flex gap-1 ml-auto">
            <UBadge v-if="m.hauptansprechpartner" color="info" variant="soft" size="sm">Hauptansprechpartner</UBadge>
            <UBadge v-if="m.buchungsberechtigt" color="success" variant="soft" size="sm">buchungsberechtigt</UBadge>
            <UBadge v-if="!aktiv(m.gueltigBis)" color="neutral" variant="outline" size="sm">ehemalig</UBadge>
          </div>
        </li>
      </ul>
    </UCard>

    <UCard v-else-if="tab === 'kommunikation'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Kommunikation (Firma + verknüpfte Personen)</h3>
        <UButton size="sm" icon="i-lucide-plus" @click="neueNotiz">Aktivität erfassen</UButton>
      </div>
      <p v-if="!historie?.length" class="text-sm text-muted">Noch keine Aktivitäten.</p>
      <ol class="relative border-s border-default ml-2">
        <li v-for="a in historie" :key="a.id" class="ms-5 py-2 group">
          <span class="absolute -start-1.5 mt-1.5 w-3 h-3 rounded-full bg-primary-500" />
          <div class="flex items-center gap-2 flex-wrap">
            <UIcon :name="richtungIcon(a.richtung)" class="text-dimmed" />
            <span class="text-sm font-medium">{{ a.betreff }}</span>
            <UBadge color="neutral" variant="soft" size="sm">{{ a.typ }}</UBadge>
            <UBadge v-if="a.person" color="info" variant="soft" size="sm">{{ a.person }}</UBadge>
            <UButton v-if="a.organisationId" color="neutral" variant="ghost" size="xs" icon="i-lucide-pencil"
                     class="ml-auto opacity-0 group-hover:opacity-100" title="Bearbeiten" @click="bearbeiteNotiz(a)" />
          </div>
          <div class="text-xs text-muted">{{ a.zeitpunkt }}</div>
          <p v-if="a.inhaltHtml" class="text-sm text-default mt-1 whitespace-pre-wrap">{{ a.inhaltHtml }}</p>
        </li>
      </ol>
      <p class="text-xs text-dimmed mt-3">Aktivitäten verknüpfter Personen sind hier nur sichtbar — bearbeitet werden sie im Personen-Detail.</p>
    </UCard>

    <UCard v-else-if="tab === 'uebersicht'">
      <h3 class="font-semibold mb-3">360°-Sicht (Firmenkontext)</h3>
      <Uebersicht360 :data="uebersicht" />
    </UCard>

    <!-- Einwilligungen im Firmenkontext -->
    <UCard v-else-if="tab === 'einwilligung'">
      <h3 class="font-semibold mb-3">Einwilligungen im Firmenkontext</h3>
      <p v-if="!einwilligungen?.length" class="text-sm text-muted">Keine firmenbezogenen Einwilligungen erfasst.</p>
      <ul class="divide-y divide-default">
        <li v-for="e in einwilligungen" :key="e.id" class="flex items-center gap-3 py-2">
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium">
              <UButton v-if="e.personId" color="primary" variant="link" class="px-0"
                       @click="emit('select-person', e.personId)">{{ e.personName }}</UButton>
              <span class="text-muted"> · {{ e.zweck }} · {{ e.kanal }}</span>
            </div>
            <div class="text-xs text-muted">{{ e.rechtsgrundlage }}<span v-if="e.quelleCode"> · Quelle {{ e.quelleCode }}</span></div>
          </div>
          <UBadge :color="einwColor(e.status)" variant="soft" size="sm">{{ e.status }}</UBadge>
        </li>
      </ul>
      <p class="text-xs text-dimmed mt-3">Erteilen/Widerrufen erfolgt im jeweiligen Personen-Detail (Tab Einwilligung).</p>
    </UCard>

    <!-- Weiterbildungspflicht §34c je Mitarbeiter -->
    <UCard v-else-if="tab === 'weiterbildung'">
      <h3 class="font-semibold mb-3">Weiterbildungspflicht §34c je Mitarbeiter</h3>
      <p v-if="!weiterbildung?.length" class="text-sm text-muted">Keine aktiven Mitarbeiter mit Weiterbildungskonto.</p>
      <ul class="divide-y divide-default">
        <li v-for="z in weiterbildung" :key="z.personId" class="flex items-center gap-3 py-2">
          <UIcon :name="ampelIcon(z.ampel)" :class="`text-${ampelColor(z.ampel)}-500`" />
          <UButton color="primary" variant="link" class="px-0" @click="emit('select-person', z.personId!)">
            {{ z.personName }}
          </UButton>
          <div class="flex-1" />
          <span class="text-sm text-muted">{{ z.summe }} / {{ z.soll }} Std.</span>
          <UBadge :color="ampelColor(z.ampel)" variant="soft" size="sm">
            {{ z.erfuellt ? 'erfüllt' : 'offen' }}
          </UBadge>
        </li>
      </ul>
      <p class="text-xs text-dimmed mt-3">Statutarischer 3-Jahres-Zeitraum (§15b MaBV, 20 Std.). Nachweise pflegt das Personen-Detail.</p>
    </UCard>

    <UCard v-else-if="tab === 'hierarchie'">
      <h3 class="font-semibold mb-3">Firmenhierarchie</h3>
      <p class="text-sm">
        Übergeordnete Organisation:
        <template v-if="org.uebergeordneteId">
          <UButton color="primary" variant="link" class="px-0" @click="emit('select-org', org.uebergeordneteId)">
            #{{ org.uebergeordneteId }}
          </UButton>
        </template>
        <span v-else class="text-muted">— (keine)</span>
      </p>
    </UCard>

    <NeueFirmaDialog v-model:open="editStamm" :existing="org" @saved="reload" />
    <KontaktpunktDialog v-model:open="kpDialog" owner-type="organisation" :owner-id="id" :existing="null" @saved="reload" />
    <NotizDialog v-model:open="notizDialog" owner-type="organisation" :owner-id="id" :existing="notizEdit" @saved="reload" />
  </div>
</template>
