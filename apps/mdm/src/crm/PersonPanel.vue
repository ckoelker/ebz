<script setup lang="ts">
import { ref, computed } from 'vue';
import { useQuery, useQueryClient, useMutation } from '@tanstack/vue-query';
import { getCrmPersonenId, getCrmPersonenIdAktivitaeten, deleteCrmKontaktpunkteId,
  postCrmMitgliedschaftenIdAusscheiden, getCrmPersonenIdEinwilligungen,
  postCrmEinwilligungenIdErteilen, postCrmEinwilligungenIdWiderrufen,
  getCrmPersonenIdWeiterbildung, deleteCrmWeiterbildungId, getCrmPersonenIdUebersicht,
  postCrmPersonenIdSperren, postCrmPersonenIdAnonymisieren } from '@/api/endpoints/crm-resource/crm-resource';
import type { PersonDetail, KontaktpunktView, MitgliedschaftView, AktivitaetView,
  EinwilligungView, WeiterbildungKontoView, Uebersicht360View } from '@/api/model';
import NeuePersonDialog from '@/crm/dialoge/NeuePersonDialog.vue';
import KontaktpunktDialog from '@/crm/dialoge/KontaktpunktDialog.vue';
import MitgliedschaftDialog from '@/crm/dialoge/MitgliedschaftDialog.vue';
import NotizDialog from '@/crm/dialoge/NotizDialog.vue';
import EinwilligungDialog from '@/crm/dialoge/EinwilligungDialog.vue';
import WeiterbildungDialog from '@/crm/dialoge/WeiterbildungDialog.vue';
import Uebersicht360 from '@/crm/Uebersicht360.vue';
import KontaktDetailHeader from '@crm-ui/ui/KontaktDetailHeader.vue';
import TabBar from '@crm-ui/ui/TabBar.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import { fehlerText } from '@/crm/fehler';
import { hatRolle } from '@/auth';

const props = defineProps<{ id: number }>();
const qc = useQueryClient();
const darfDatenschutz = computed(() => hatRolle('crm-datenschutz'));

const { data: person, isFetching } = useQuery({
  queryKey: computed(() => ['crm-person', props.id]),
  queryFn: async (): Promise<PersonDetail> => await getCrmPersonenId(props.id),
});

const { data: historie } = useQuery({
  queryKey: computed(() => ['crm-person-akt', props.id]),
  queryFn: async (): Promise<AktivitaetView[]> => (await getCrmPersonenIdAktivitaeten(props.id)) ?? [],
});

const { data: einwilligungen } = useQuery({
  queryKey: computed(() => ['crm-person-einw', props.id]),
  queryFn: async (): Promise<EinwilligungView[]> => (await getCrmPersonenIdEinwilligungen(props.id)) ?? [],
});

const { data: weiterbildung } = useQuery({
  queryKey: computed(() => ['crm-person-wb', props.id]),
  queryFn: async (): Promise<WeiterbildungKontoView> => await getCrmPersonenIdWeiterbildung(props.id),
});

const { data: uebersicht } = useQuery({
  queryKey: computed(() => ['crm-person-360', props.id]),
  queryFn: async (): Promise<Uebersicht360View> => await getCrmPersonenIdUebersicht(props.id),
});

function reload() {
  qc.invalidateQueries({ queryKey: ['crm-person', props.id] });
  qc.invalidateQueries({ queryKey: ['crm-person-akt', props.id] });
  qc.invalidateQueries({ queryKey: ['crm-person-einw', props.id] });
  qc.invalidateQueries({ queryKey: ['crm-person-wb', props.id] });
}

const tab = ref('stammdaten');
const tabs = [
  { key: 'stammdaten', label: 'Stammdaten', icon: 'i-lucide-id-card' },
  { key: 'kommunikation', label: 'Kommunikation', icon: 'i-lucide-mail' },
  { key: 'zugehoerigkeiten', label: 'Zugehörigkeiten', icon: 'i-lucide-building-2' },
  { key: 'historie', label: 'Historie', icon: 'i-lucide-history' },
  { key: 'uebersicht', label: '360°', icon: 'i-lucide-layout-dashboard' },
  { key: 'einwilligung', label: 'Einwilligung', icon: 'i-lucide-mail-check' },
  { key: 'weiterbildung', label: 'Weiterbildung', icon: 'i-lucide-graduation-cap' },
  { key: 'dsgvo', label: 'DSGVO', icon: 'i-lucide-shield' },
];

// Bubble-Zähler am Tab „Einwilligung": offene Double-Opt-In-Bestätigungen (AUSSTEHEND) = Handlungsbedarf.
const offeneEinw = computed(() => (einwilligungen.value ?? []).filter((e) => e.status === 'AUSSTEHEND').length);
const tabsMitBubble = computed(() =>
  tabs.map((t) => (t.key === 'einwilligung' ? { ...t, bubble: offeneEinw.value } : t)));

// Dialog-Status
const editStamm = ref(false);
const kpDialog = ref(false);
const kpEdit = ref<KontaktpunktView | null>(null);
const mgDialog = ref(false);
const mgEdit = ref<MitgliedschaftView | null>(null);
const notizDialog = ref(false);
const notizEdit = ref<AktivitaetView | null>(null);
const einwDialog = ref(false);
const wbDialog = ref(false);
const meldung = ref('');

// ── Generische Lösch-/irreversible-Bestätigung (Regel „Löschen immer mit Bestätigung") ──
const confirmState = ref<{ open: boolean; title: string; message: string; detail?: string;
  confirmLabel: string; confirmColor: string; confirmIcon: string; loading: boolean }>({
  open: false, title: '', message: '', confirmLabel: 'Löschen', confirmColor: 'error',
  confirmIcon: 'i-lucide-trash-2', loading: false,
});
let confirmAction: null | (() => Promise<unknown>) = null;
function frage(o: { title: string; message: string; detail?: string; confirmLabel?: string;
  confirmColor?: string; confirmIcon?: string }, action: () => Promise<unknown>) {
  confirmState.value = {
    open: true, loading: false, confirmLabel: 'Löschen', confirmColor: 'error',
    confirmIcon: 'i-lucide-trash-2', detail: undefined, ...o,
  };
  confirmAction = action;
}
async function confirmJa() {
  if (!confirmAction) return;
  confirmState.value.loading = true;
  try {
    await confirmAction();
    confirmState.value.open = false;
  } catch (e) {
    meldung.value = fehlerText(e);
  } finally {
    confirmState.value.loading = false;
    confirmAction = null;
  }
}

const richtungIcon = (r?: string) =>
  r === 'EINGEHEND' ? 'i-lucide-arrow-down-left' : r === 'INTERN' ? 'i-lucide-dot' : 'i-lucide-arrow-up-right';

function neuerKp() { kpEdit.value = null; kpDialog.value = true; }
function bearbeiteKp(k: KontaktpunktView) { kpEdit.value = k; kpDialog.value = true; }
function neueMg() { mgEdit.value = null; mgDialog.value = true; }
function bearbeiteMg(m: MitgliedschaftView) { mgEdit.value = m; mgDialog.value = true; }
function neueNotiz() { notizEdit.value = null; notizDialog.value = true; }
function bearbeiteNotiz(a: AktivitaetView) { notizEdit.value = a; notizDialog.value = true; }

const kpDel = useMutation({ mutationFn: (id: number) => deleteCrmKontaktpunkteId(id), onSuccess: reload });
const mgAus = useMutation({ mutationFn: (id: number) => postCrmMitgliedschaftenIdAusscheiden(id), onSuccess: reload });
const wbDel = useMutation({ mutationFn: (id: number) => deleteCrmWeiterbildungId(id), onSuccess: reload });
const a7Sperren = useMutation({ mutationFn: (id: number) => postCrmPersonenIdSperren(id, {}), onSuccess: reload });
const a7Anon = useMutation({ mutationFn: (id: number) => postCrmPersonenIdAnonymisieren(id), onSuccess: reload });
const einwErteilen = useMutation({
  mutationFn: (id: number) => postCrmEinwilligungenIdErteilen(id),
  onSuccess: reload,
  onError: (e) => { meldung.value = fehlerText(e); },
});
const einwWiderrufen = useMutation({ mutationFn: (id: number) => postCrmEinwilligungenIdWiderrufen(id), onSuccess: reload });

function loescheKp(k: KontaktpunktView) {
  frage({ title: 'Kontaktpunkt löschen?', message: kpText(k) }, () => kpDel.mutateAsync(k.id!));
}
function scheideAus(m: MitgliedschaftView) {
  frage({
    title: 'Zugehörigkeit beenden?', message: `${m.organisation} · ${m.rolle}`,
    detail: 'Die Mitgliedschaft wird zum heutigen Tag historisiert (ausgeschieden), nicht gelöscht.',
    confirmLabel: 'Ausscheiden', confirmColor: 'warning', confirmIcon: 'i-lucide-log-out',
  }, () => mgAus.mutateAsync(m.id!));
}
function loescheWb(w: { id?: number; titel?: string }) {
  frage({ title: 'Nachweis löschen?', message: w.titel ?? 'Weiterbildungsnachweis' },
    () => wbDel.mutateAsync(w.id!));
}
function widerrufe(e: EinwilligungView) {
  frage({
    title: 'Einwilligung widerrufen?', message: `${e.zweck} · ${e.kanal}`,
    detail: 'Der Widerruf wird mit Zeitstempel protokolliert (Art. 7 Abs. 3 DSGVO).',
    confirmLabel: 'Widerrufen', confirmColor: 'error', confirmIcon: 'i-lucide-ban',
  }, () => einwWiderrufen.mutateAsync(e.id!));
}
function sperren() {
  frage({
    title: 'Person sperren (Art. 18)?',
    message: `${person.value?.anzeigeName}: Verarbeitung einschränken, Werbe-/Auskunftssperre setzen.`,
    detail: 'Die Anonymisierung wird auf das Ende der Aufbewahrungsfrist terminiert. Reversibel-nah.',
    confirmLabel: 'Sperren', confirmColor: 'warning', confirmIcon: 'i-lucide-lock',
  }, () => a7Sperren.mutateAsync(props.id));
}
function anonymisieren() {
  frage({
    title: 'Endgültig anonymisieren (Art. 17)?',
    message: `${person.value?.anzeigeName}: alle personenbezogenen Daten werden unwiderruflich gelöscht.`,
    detail: 'Kontaktkanäle, Logins und die komplette Versionshistorie werden entfernt. Nicht umkehrbar!',
    confirmLabel: 'Anonymisieren', confirmColor: 'error', confirmIcon: 'i-lucide-user-x',
  }, () => a7Anon.mutateAsync(props.id));
}

const einwColor = (s?: string) =>
  s === 'ERTEILT' ? 'success' : s === 'WIDERRUFEN' ? 'error' : 'warning';
const ampel = computed(() => {
  const a = weiterbildung.value?.ampel;
  return a === 'GRUEN'
    ? { color: 'success' as const, icon: 'i-lucide-circle-check', iconClass: 'text-success-500 size-8', text: 'Erfüllt' }
    : a === 'ROT'
      ? { color: 'error' as const, icon: 'i-lucide-circle-alert', iconClass: 'text-error-500 size-8', text: 'Frist kritisch' }
      : { color: 'warning' as const, icon: 'i-lucide-circle-dot', iconClass: 'text-warning-500 size-8', text: 'In Bearbeitung' };
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
const istAnonymisiert = computed(() => person.value?.loeschStatus === 'ANONYMISIERT');
</script>

<template>
  <div v-if="person" class="space-y-4">
    <!-- Kopf + Tabs (sticky, während der Tab-Inhalt scrollt) -->
    <div class="sticky top-0 z-10 bg-default pb-1 space-y-4">
      <UCard>
        <KontaktDetailHeader
          :title="person.anzeigeName ?? ''"
          :status="person.status"
          :werbesperre="person.werbesperre"
          :auskunftssperre="person.auskunftssperre"
          :anrede="person.briefanrede"
        >
          <template #badges>
            <UBadge v-if="person.loeschStatus && person.loeschStatus !== 'AKTIV'" color="error" variant="soft" size="sm">
              {{ person.loeschStatus }}
            </UBadge>
          </template>
          <template #actions>
            <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="reload" />
          </template>
        </KontaktDetailHeader>
      </UCard>
      <TabBar :tabs="tabsMitBubble" :model-value="tab" @update:model-value="tab = $event" />
    </div>

    <UAlert v-if="meldung" color="error" variant="soft" :title="meldung" close class="" @update:open="meldung = ''" />

    <!-- Stammdaten -->
    <UCard v-if="tab === 'stammdaten'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Stammdaten</h3>
        <UButton size="sm" icon="i-lucide-pencil" :disabled="istAnonymisiert" @click="editStamm = true">Bearbeiten</UButton>
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
          <UButton color="error" variant="ghost" size="xs" icon="i-lucide-trash-2" @click="loescheKp(k)" />
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
                   title="Ausscheiden (historisieren)" @click="scheideAus(m)" />
        </li>
      </ul>
    </UCard>

    <!-- Historie -->
    <UCard v-else-if="tab === 'historie'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Kontakthistorie</h3>
        <UButton size="sm" icon="i-lucide-plus" @click="neueNotiz">Aktivität erfassen</UButton>
      </div>
      <p v-if="!historie?.length" class="text-sm text-muted">Noch keine Aktivitäten.</p>
      <ol class="relative border-s border-default ml-2">
        <li v-for="a in historie" :key="a.id" class="ms-5 py-2 group">
          <span class="absolute -start-1.5 mt-1.5 w-3 h-3 rounded-full bg-primary-500" />
          <div class="flex items-center gap-2">
            <UIcon :name="richtungIcon(a.richtung)" class="text-dimmed" />
            <span class="text-sm font-medium">{{ a.betreff }}</span>
            <UBadge color="neutral" variant="soft" size="sm">{{ a.typ }}</UBadge>
            <UButton color="neutral" variant="ghost" size="xs" icon="i-lucide-pencil"
                     class="ml-auto opacity-0 group-hover:opacity-100" title="Bearbeiten"
                     @click="bearbeiteNotiz(a)" />
          </div>
          <div class="text-xs text-muted">
            {{ a.zeitpunkt }} <span v-if="a.dauerMinuten">· {{ a.dauerMinuten }} Min.</span>
          </div>
          <p v-if="a.inhaltHtml" class="text-sm text-default mt-1 whitespace-pre-wrap">{{ a.inhaltHtml }}</p>
        </li>
      </ol>
    </UCard>

    <!-- 360°-Sicht (A18) -->
    <UCard v-else-if="tab === 'uebersicht'">
      <h3 class="font-semibold mb-3">360°-Sicht</h3>
      <Uebersicht360 :data="uebersicht" />
    </UCard>

    <!-- Einwilligung / Opt-In (A6) -->
    <UCard v-else-if="tab === 'einwilligung'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Marketing-Einwilligungen</h3>
        <UButton size="sm" icon="i-lucide-plus" @click="einwDialog = true">Opt-In erfassen</UButton>
      </div>
      <UAlert v-if="person.werbesperre" color="warning" variant="soft" class="mb-3"
              icon="i-lucide-shield-alert"
              title="Werbesperre aktiv – Marketing-Opt-Ins werden in der Verarbeitung überstimmt." />
      <p v-if="!einwilligungen?.length" class="text-sm text-muted">Noch keine Einwilligungen erfasst.</p>
      <ul class="divide-y divide-default">
        <li v-for="e in einwilligungen" :key="e.id" class="flex items-center gap-3 py-2">
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium">
              {{ e.zweck }} <span class="text-muted">· {{ e.kanal }}</span>
              <span v-if="e.organisation" class="text-muted"> · {{ e.organisation }}</span>
            </div>
            <div class="text-xs text-muted">
              {{ e.rechtsgrundlage }}<span v-if="e.quelleCode"> · Quelle {{ e.quelleCode }}</span>
            </div>
          </div>
          <UBadge :color="einwColor(e.status)" variant="soft" size="sm">{{ e.status }}</UBadge>
          <UButton v-if="e.status === 'AUSSTEHEND'" color="success" variant="ghost" size="xs"
                   icon="i-lucide-check" title="Double-Opt-In bestätigen" @click="einwErteilen.mutate(e.id!)" />
          <UButton v-if="e.status !== 'WIDERRUFEN'" color="error" variant="ghost" size="xs"
                   icon="i-lucide-ban" title="Widerrufen" @click="widerrufe(e)" />
        </li>
      </ul>
    </UCard>

    <!-- Weiterbildung §34c / §15b MaBV (A19) -->
    <UCard v-else-if="tab === 'weiterbildung'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Weiterbildungspflicht §34c GewO</h3>
        <UButton size="sm" icon="i-lucide-plus" @click="wbDialog = true">Nachweis erfassen</UButton>
      </div>
      <div v-if="weiterbildung" class="flex items-center gap-4 p-3 rounded-lg bg-elevated mb-4">
        <UIcon :name="ampel.icon" :class="ampel.iconClass" />
        <div class="flex-1">
          <div class="text-sm font-semibold">
            {{ weiterbildung.summe }} / {{ weiterbildung.soll }} Std.
            <UBadge :color="ampel.color" variant="soft" size="sm" class="ml-1">{{ ampel.text }}</UBadge>
          </div>
          <div class="text-xs text-muted">
            Zeitraum {{ weiterbildung.zeitraumVon }} – {{ weiterbildung.zeitraumBis }}
            <span v-if="!weiterbildung.erfuellt"> · noch {{ weiterbildung.rest }} Std. offen</span>
          </div>
          <UProgress :model-value="Number(weiterbildung.summe) || 0" :max="Number(weiterbildung.soll) || 20"
                     :color="ampel.color" size="sm" class="mt-2" />
        </div>
      </div>
      <p v-if="!weiterbildung?.nachweise?.length" class="text-sm text-muted">Noch keine Nachweise erfasst.</p>
      <ul class="divide-y divide-default">
        <li v-for="w in weiterbildung?.nachweise" :key="w.id" class="flex items-center gap-3 py-2">
          <UIcon name="i-lucide-award" class="text-dimmed" />
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium">{{ w.titel }}</div>
            <div class="text-xs text-muted">
              {{ w.datum }} <span v-if="w.anbieter">· {{ w.anbieter }}</span>
            </div>
          </div>
          <UBadge color="neutral" variant="soft" size="sm">{{ w.stunden }} Std.</UBadge>
          <UBadge v-if="w.extern" color="info" variant="outline" size="sm">extern</UBadge>
          <UButton color="error" variant="ghost" size="xs" icon="i-lucide-trash-2"
                   title="Nachweis löschen" @click="loescheWb(w)" />
        </li>
      </ul>
    </UCard>

    <!-- DSGVO (read-only Daten + bestätigte Betroffenenrechte A7) -->
    <UCard v-else-if="tab === 'dsgvo'">
      <h3 class="font-semibold mb-3">Sperren & Betroffenenrechte</h3>
      <dl class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <div><dt class="text-muted">Werbesperre</dt><dd>{{ person.werbesperre ? 'ja' : 'nein' }}</dd></div>
        <div><dt class="text-muted">Auskunftssperre</dt><dd>{{ person.auskunftssperre ? 'ja' : 'nein' }}</dd></div>
        <div><dt class="text-muted">Lösch-Status</dt><dd>{{ person.loeschStatus }}</dd></div>
        <div><dt class="text-muted">Anonymisierung ab</dt><dd>{{ person.anonymisierenAb || '—' }}</dd></div>
        <div class="col-span-2"><dt class="text-muted">E-Mail-Adressen</dt><dd>{{ person.emails?.join(', ') || '—' }}</dd></div>
      </dl>
      <p class="text-xs text-dimmed mt-3">
        Diese Daten sind <strong>nicht inline editierbar</strong>. Werbe-/Auskunftssperre überstimmen jedes
        Marketing-Opt-In (Plan A1/A6); Änderungen laufen ausschließlich über die bestätigten Pflege-Pfade.
      </p>

      <!-- Recht auf Vergessen (Art. 17/18 DSGVO) — nur mit Rolle crm-datenschutz -->
      <div class="mt-4 pt-4 border-t border-default">
        <h4 class="font-semibold text-sm mb-2 flex items-center gap-2">
          <UIcon name="i-lucide-shield-x" class="text-error-500" /> Recht auf Vergessen (Art. 17 DSGVO)
        </h4>
        <UAlert v-if="!darfDatenschutz" color="neutral" variant="soft" icon="i-lucide-lock"
                title="Vorbehalten für die Rolle „crm-datenschutz“."
                description="Sperren und Anonymisieren sind nur dem Datenschutz vorbehalten." />
        <template v-else-if="istAnonymisiert">
          <UAlert color="neutral" variant="soft" icon="i-lucide-check"
                  title="Datensatz ist anonymisiert." description="Es sind keine personenbezogenen Daten mehr vorhanden." />
        </template>
        <div v-else class="flex flex-wrap gap-2">
          <UButton color="warning" variant="outline" size="sm" icon="i-lucide-lock"
                   :disabled="person.loeschStatus === 'GESPERRT'" @click="sperren">
            Sperren (Art. 18)
          </UButton>
          <UButton color="error" variant="solid" size="sm" icon="i-lucide-user-x" @click="anonymisieren">
            Endgültig anonymisieren
          </UButton>
        </div>
      </div>
    </UCard>

    <!-- Dialoge -->
    <NeuePersonDialog v-model:open="editStamm" :existing="person" @saved="reload" />
    <KontaktpunktDialog
      v-model:open="kpDialog" owner-type="person" :owner-id="id" :existing="kpEdit" @saved="reload" />
    <MitgliedschaftDialog v-model:open="mgDialog" :person-id="id" :existing="mgEdit" @saved="reload" />
    <NotizDialog v-model:open="notizDialog" owner-type="person" :owner-id="id" :existing="notizEdit" @saved="reload" />
    <EinwilligungDialog v-model:open="einwDialog" :person-id="id" @saved="reload" />
    <WeiterbildungDialog v-model:open="wbDialog" :person-id="id" @saved="reload" />

    <ConfirmDialog
      v-model:open="confirmState.open"
      :title="confirmState.title"
      :message="confirmState.message"
      :detail="confirmState.detail"
      :confirm-label="confirmState.confirmLabel"
      :confirm-color="confirmState.confirmColor"
      :confirm-icon="confirmState.confirmIcon"
      :loading="confirmState.loading"
      @confirm="confirmJa"
    />
  </div>
</template>
