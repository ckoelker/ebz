<script setup lang="ts">
import { ref, computed } from 'vue';
import { useQuery, useQueryClient, useMutation } from '@tanstack/vue-query';
import { getCrmPersonenId, getCrmPersonenIdAktivitaeten, deleteCrmKontaktpunkteId,
  postCrmMitgliedschaftenIdAusscheiden } from '@/api/endpoints/crm-resource/crm-resource';
import type { PersonDetail, KontaktpunktView, MitgliedschaftView, AktivitaetView } from '@/api/model';
import NeuePersonDialog from '@/crm/dialoge/NeuePersonDialog.vue';
import KontaktpunktDialog from '@/crm/dialoge/KontaktpunktDialog.vue';
import MitgliedschaftDialog from '@/crm/dialoge/MitgliedschaftDialog.vue';
import NotizDialog from '@/crm/dialoge/NotizDialog.vue';
import { fehlerText } from '@/crm/fehler';

const props = defineProps<{ id: number }>();
const qc = useQueryClient();

const { data: person, isFetching } = useQuery({
  queryKey: computed(() => ['crm-person', props.id]),
  queryFn: async (): Promise<PersonDetail> => await getCrmPersonenId(props.id),
});

const { data: historie } = useQuery({
  queryKey: computed(() => ['crm-person-akt', props.id]),
  queryFn: async (): Promise<AktivitaetView[]> => (await getCrmPersonenIdAktivitaeten(props.id)) ?? [],
});

function reload() {
  qc.invalidateQueries({ queryKey: ['crm-person', props.id] });
  qc.invalidateQueries({ queryKey: ['crm-person-akt', props.id] });
}

const tab = ref('stammdaten');
const tabs = [
  { key: 'stammdaten', label: 'Stammdaten', icon: 'i-lucide-id-card' },
  { key: 'kommunikation', label: 'Kommunikation', icon: 'i-lucide-mail' },
  { key: 'zugehoerigkeiten', label: 'Zugehörigkeiten', icon: 'i-lucide-building-2' },
  { key: 'historie', label: 'Historie', icon: 'i-lucide-history' },
  { key: 'dsgvo', label: 'DSGVO', icon: 'i-lucide-shield' },
];

// Dialog-Status
const editStamm = ref(false);
const kpDialog = ref(false);
const kpEdit = ref<KontaktpunktView | null>(null);
const mgDialog = ref(false);
const mgEdit = ref<MitgliedschaftView | null>(null);
const notizDialog = ref(false);
const meldung = ref('');

const richtungIcon = (r?: string) =>
  r === 'EINGEHEND' ? 'i-lucide-arrow-down-left' : r === 'INTERN' ? 'i-lucide-dot' : 'i-lucide-arrow-up-right';

function neuerKp() { kpEdit.value = null; kpDialog.value = true; }
function bearbeiteKp(k: KontaktpunktView) { kpEdit.value = k; kpDialog.value = true; }
function neueMg() { mgEdit.value = null; mgDialog.value = true; }
function bearbeiteMg(m: MitgliedschaftView) { mgEdit.value = m; mgDialog.value = true; }

const kpDel = useMutation({
  mutationFn: (id: number) => deleteCrmKontaktpunkteId(id),
  onSuccess: reload,
  onError: (e) => { meldung.value = fehlerText(e); },
});
const mgAus = useMutation({
  mutationFn: (id: number) => postCrmMitgliedschaftenIdAusscheiden(id),
  onSuccess: reload,
  onError: (e) => { meldung.value = fehlerText(e); },
});

const kpIcon = (typ?: string) =>
  typ === 'EMAIL' ? 'i-lucide-mail' : typ === 'TELEFON' ? 'i-lucide-phone' : 'i-lucide-map-pin';
function kpText(k: KontaktpunktView): string {
  if (k.typ === 'EMAIL') return k.email ?? '';
  if (k.typ === 'TELEFON') return k.nummerAnzeige ?? '';
  return [k.strasse, k.hausnummer].filter(Boolean).join(' ') + ', '
    + [k.plz, k.ort].filter(Boolean).join(' ') + (k.landCode ? ` (${k.landCode})` : '');
}
const aktiv = (m: MitgliedschaftView) => !m.gueltigBis;
</script>

<template>
  <div v-if="person" class="space-y-4">
    <!-- Kopf -->
    <UCard>
      <div class="flex items-start gap-4">
        <UAvatar :alt="person.anzeigeName" size="lg" />
        <div class="flex-1 min-w-0">
          <h2 class="text-xl font-bold text-highlighted">{{ person.anzeigeName }}</h2>
          <p class="text-sm text-muted">{{ person.briefanrede }}</p>
          <div class="flex flex-wrap gap-1.5 mt-2">
            <UBadge :color="person.status === 'AKTIV' ? 'success' : 'neutral'" variant="soft" size="sm">
              {{ person.status }}
            </UBadge>
            <UBadge v-if="person.loeschStatus && person.loeschStatus !== 'AKTIV'" color="error" variant="soft" size="sm">
              {{ person.loeschStatus }}
            </UBadge>
            <UBadge v-if="person.werbesperre" color="warning" variant="soft" size="sm">Werbesperre</UBadge>
            <UBadge v-if="person.auskunftssperre" color="warning" variant="soft" size="sm">Auskunftssperre</UBadge>
          </div>
        </div>
        <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="reload" />
      </div>
    </UCard>

    <UAlert v-if="meldung" color="error" variant="soft" :title="meldung" close class="" @update:open="meldung = ''" />

    <!-- Tabs -->
    <div class="flex gap-1 border-b border-default">
      <UButton
        v-for="t in tabs"
        :key="t.key"
        :color="tab === t.key ? 'primary' : 'neutral'"
        :variant="tab === t.key ? 'soft' : 'ghost'"
        :icon="t.icon"
        size="sm"
        @click="tab = t.key"
      >
        {{ t.label }}
      </UButton>
    </div>

    <!-- Stammdaten -->
    <UCard v-if="tab === 'stammdaten'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Stammdaten</h3>
        <UButton size="sm" icon="i-lucide-pencil" @click="editStamm = true">Bearbeiten</UButton>
      </div>
      <dl class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <div><dt class="text-muted">Vorname</dt><dd>{{ person.vorname }}</dd></div>
        <div><dt class="text-muted">Nachname</dt><dd>{{ person.nachname }}</dd></div>
        <div><dt class="text-muted">Geschlecht</dt><dd>{{ person.geschlecht }}</dd></div>
        <div><dt class="text-muted">Titel</dt><dd>{{ person.titel || '—' }}</dd></div>
        <div><dt class="text-muted">Geburtsdatum</dt><dd>{{ person.geburtsdatum || '—' }}</dd></div>
        <div><dt class="text-muted">Korrespondenzsprache</dt><dd>{{ person.korrespondenzspracheCode || '—' }}</dd></div>
      </dl>
    </UCard>

    <!-- Kommunikation -->
    <UCard v-else-if="tab === 'kommunikation'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Kontaktpunkte</h3>
        <UButton size="sm" icon="i-lucide-plus" @click="neuerKp">Hinzufügen</UButton>
      </div>
      <p v-if="!person.kontaktpunkte?.length" class="text-sm text-muted">Noch keine Kontaktpunkte.</p>
      <ul class="divide-y divide-default">
        <li v-for="k in person.kontaktpunkte" :key="k.id" class="flex items-center gap-3 py-2">
          <UIcon :name="kpIcon(k.typ)" class="text-dimmed" />
          <div class="flex-1 min-w-0">
            <div class="text-sm">{{ kpText(k) }}</div>
            <div class="text-xs text-muted">{{ k.label }} <span v-if="k.primaer">· primär</span> · {{ k.status }}</div>
          </div>
          <UButton color="neutral" variant="ghost" size="xs" icon="i-lucide-pencil" @click="bearbeiteKp(k)" />
          <UButton color="error" variant="ghost" size="xs" icon="i-lucide-trash-2" @click="kpDel.mutate(k.id!)" />
        </li>
      </ul>
    </UCard>

    <!-- Zugehörigkeiten -->
    <UCard v-else-if="tab === 'zugehoerigkeiten'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Zugehörigkeiten (N:M)</h3>
        <UButton size="sm" icon="i-lucide-plus" @click="neueMg">Hinzufügen</UButton>
      </div>
      <p v-if="!person.mitgliedschaften?.length" class="text-sm text-muted">Keine Zugehörigkeiten.</p>
      <ul class="divide-y divide-default">
        <li v-for="m in person.mitgliedschaften" :key="m.id" class="flex items-center gap-3 py-2"
            :class="{ 'opacity-50': !aktiv(m) }">
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium">{{ m.organisation }} <span class="text-muted">· {{ m.rolle }}</span></div>
            <div class="flex flex-wrap gap-1 mt-0.5">
              <UBadge v-if="m.hauptzugehoerigkeit" color="primary" variant="soft" size="sm">Hauptzugehörigkeit</UBadge>
              <UBadge v-if="m.hauptansprechpartner" color="info" variant="soft" size="sm">Hauptansprechpartner</UBadge>
              <UBadge v-if="m.buchungsberechtigt" color="success" variant="soft" size="sm">buchungsberechtigt</UBadge>
              <UBadge v-if="m.rechnungsempfaenger" color="neutral" variant="soft" size="sm">Rechnungsempfänger</UBadge>
              <UBadge v-if="!aktiv(m)" color="neutral" variant="outline" size="sm">ehemalig bis {{ m.gueltigBis }}</UBadge>
            </div>
          </div>
          <UButton color="neutral" variant="ghost" size="xs" icon="i-lucide-pencil" @click="bearbeiteMg(m)" />
          <UButton v-if="aktiv(m)" color="warning" variant="ghost" size="xs" icon="i-lucide-log-out"
                   title="Ausscheiden (historisieren)" @click="mgAus.mutate(m.id!)" />
        </li>
      </ul>
    </UCard>

    <!-- Historie -->
    <UCard v-else-if="tab === 'historie'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Kontakthistorie</h3>
        <UButton size="sm" icon="i-lucide-plus" @click="notizDialog = true">Aktivität erfassen</UButton>
      </div>
      <p v-if="!historie?.length" class="text-sm text-muted">Noch keine Aktivitäten.</p>
      <ol class="relative border-s border-default ml-2">
        <li v-for="a in historie" :key="a.id" class="ms-5 py-2">
          <span class="absolute -start-1.5 mt-1.5 w-3 h-3 rounded-full bg-primary-500" />
          <div class="flex items-center gap-2">
            <UIcon :name="richtungIcon(a.richtung)" class="text-dimmed" />
            <span class="text-sm font-medium">{{ a.betreff }}</span>
            <UBadge color="neutral" variant="soft" size="sm">{{ a.typ }}</UBadge>
          </div>
          <div class="text-xs text-muted">
            {{ a.zeitpunkt }} <span v-if="a.dauerMinuten">· {{ a.dauerMinuten }} Min.</span>
          </div>
          <p v-if="a.inhaltHtml" class="text-sm text-default mt-1 whitespace-pre-wrap">{{ a.inhaltHtml }}</p>
        </li>
      </ol>
    </UCard>

    <!-- DSGVO -->
    <UCard v-else-if="tab === 'dsgvo'">
      <h3 class="font-semibold mb-3">Sperren & Betroffenenrechte</h3>
      <dl class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <div><dt class="text-muted">Werbesperre</dt><dd>{{ person.werbesperre ? 'ja' : 'nein' }}</dd></div>
        <div><dt class="text-muted">Auskunftssperre</dt><dd>{{ person.auskunftssperre ? 'ja' : 'nein' }}</dd></div>
        <div><dt class="text-muted">Lösch-Status</dt><dd>{{ person.loeschStatus }}</dd></div>
        <div><dt class="text-muted">E-Mail-Adressen</dt><dd>{{ person.emails?.join(', ') || '—' }}</dd></div>
      </dl>
      <p class="text-xs text-dimmed mt-3">Werbe-/Auskunftssperre überstimmen jedes Marketing-Opt-In (Plan A1/A6).</p>
    </UCard>

    <!-- Dialoge -->
    <NeuePersonDialog v-model:open="editStamm" :existing="person" @saved="reload" />
    <KontaktpunktDialog
      v-model:open="kpDialog" owner-type="person" :owner-id="id" :existing="kpEdit" @saved="reload" />
    <MitgliedschaftDialog v-model:open="mgDialog" :person-id="id" :existing="mgEdit" @saved="reload" />
    <NotizDialog v-model:open="notizDialog" owner-type="person" :owner-id="id" @saved="reload" />
  </div>
</template>
