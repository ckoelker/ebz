<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { useForm, useField } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import DialogShell from '@/components/DialogShell.vue';
import DublettenWarnung from '@/crm/DublettenWarnung.vue';
import { useLookup, lookupItems } from '@/crm/lookups';
import { fehlerText, istUnauth } from '@/crm/fehler';
import { violationsZuFehlern } from '@/bildung';
import { login } from '@/auth';
import { postCrmOrganisationen, putCrmOrganisationenId } from '@/api/endpoints/crm-resource/crm-resource';
import { PostCrmOrganisationenBody } from '@/api/zod/crm-resource/crm-resource.zod';
import type { OrgDetail } from '@/api/model';

// Anlage/Bearbeitung einer Organisation (Plan A2), Stack B (zod→vee-validate, Server-400→setErrors).
// Beim Anlegen: Live-Dublettenwarnung (A16, Name/USt-IdNr.).
const props = defineProps<{ open: boolean; existing?: OrgDetail | null }>();
const emit = defineEmits<{ (e: 'update:open', v: boolean): void; (e: 'created', id: number): void;
  (e: 'verwenden', id: number): void; (e: 'saved'): void }>();

function aufVerwenden(id: number) {
  emit('verwenden', id);
  emit('update:open', false);
}

const { data: branchen } = useLookup('branche');
const { data: unternehmenstypen } = useLookup('unternehmenstyp');
const { data: schwerpunkte } = useLookup('schwerpunkt');
const { data: verbaende } = useLookup('verband');
const { data: kammern } = useLookup('ihk');

const initial = () => ({
  name: '', rechtsform: '', brancheCode: undefined, website: '', ustId: '', bestandsgroesse: undefined,
  ausbildungsbetrieb: false, ihkKammerCode: undefined, gewerbeerlaubnis: 'KEINE',
  unternehmenstypCodes: [] as string[], schwerpunktCodes: [] as string[], verbandCodes: [] as string[],
});
const { handleSubmit, setErrors, resetForm, isSubmitting, errors } = useForm({
  validationSchema: toTypedSchema(PostCrmOrganisationenBody),
  initialValues: initial(),
});
const { value: name } = useField<string>('name');
const { value: rechtsform } = useField<string>('rechtsform');
const { value: brancheCode } = useField<string>('brancheCode');
const { value: website } = useField<string>('website');
const { value: ustId } = useField<string>('ustId');
const { value: bestandsgroesse } = useField<number | undefined>('bestandsgroesse');
const { value: ausbildungsbetrieb } = useField<boolean>('ausbildungsbetrieb');
const { value: ihkKammerCode } = useField<string>('ihkKammerCode');
const { value: unternehmenstypCodes } = useField<string[]>('unternehmenstypCodes');
const { value: schwerpunktCodes } = useField<string[]>('schwerpunktCodes');
const { value: verbandCodes } = useField<string[]>('verbandCodes');

const serverFehler = ref('');

// ── A15 „Daten online ziehen": Firmen-Anreicherung über WebSocket (gestreamtes JSON) ──
type AnreicherungVorschlag = {
  name?: string; rechtsform?: string; ustId?: string; website?: string;
  strasse?: string; plz?: string; ort?: string; brancheHinweis?: string;
};
type AnreicherungEvent = {
  typ: string; schritt?: string; status?: string; detail?: string; vorschlag?: AnreicherungVorschlag;
};
const anrLog = ref<AnreicherungEvent[]>([]);
const anrLaeuft = ref(false);
const anrVorschlag = ref<AnreicherungVorschlag | null>(null);
let ws: WebSocket | null = null;

function schliesseWs() { ws?.close(); ws = null; }
function anrReset() { schliesseWs(); anrLog.value = []; anrVorschlag.value = null; anrLaeuft.value = false; }

function datenOnlineZiehen() {
  if (!name.value?.trim()) return;
  anrLog.value = [];
  anrVorschlag.value = null;
  anrLaeuft.value = true;
  schliesseWs();
  const url = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/crm/anreicherung`;
  ws = new WebSocket(url);
  ws.onopen = () => ws?.send(JSON.stringify({ name: name.value, website: website.value, ustId: ustId.value }));
  ws.onmessage = (ev) => {
    const e = JSON.parse(ev.data) as AnreicherungEvent;
    if (e.typ === 'vorschlag') anrVorschlag.value = e.vorschlag ?? null;
    else anrLog.value = [...anrLog.value, e];
  };
  ws.onclose = () => { anrLaeuft.value = false; };
  ws.onerror = () => {
    anrLaeuft.value = false;
    anrLog.value = [...anrLog.value, { typ: 'fehler', detail: 'WebSocket-Verbindung fehlgeschlagen.' }];
  };
}

function anrUebernehmen() {
  const v = anrVorschlag.value;
  if (!v) return;
  if (v.name) name.value = v.name;
  if (v.rechtsform) rechtsform.value = v.rechtsform;
  if (v.ustId) ustId.value = v.ustId;
  if (v.website) website.value = v.website;
  anrVorschlag.value = null;
}

const anrIcon = (status?: string) =>
  status === 'ok' ? 'i-lucide-check' : status === 'fehler' ? 'i-lucide-x'
    : status === 'uebersprungen' ? 'i-lucide-minus' : status === 'fallback' ? 'i-lucide-check'
      : 'i-lucide-loader-circle';

onBeforeUnmount(schliesseWs);

watch(() => props.open, (v) => {
  if (!v) { anrReset(); return; }
  serverFehler.value = '';
  if (props.existing) {
    resetForm({ values: {
      name: props.existing.name ?? '', rechtsform: props.existing.rechtsform ?? '',
      brancheCode: props.existing.brancheCode ?? undefined, website: props.existing.website ?? '',
      ustId: props.existing.ustId ?? '', bestandsgroesse: props.existing.bestandsgroesse,
      ausbildungsbetrieb: props.existing.ausbildungsbetrieb ?? false,
      ihkKammerCode: props.existing.ihkKammerCode ?? undefined,
      gewerbeerlaubnis: props.existing.gewerbeerlaubnis ?? 'KEINE',
      unternehmenstypCodes: props.existing.unternehmenstypen ?? [], schwerpunktCodes: props.existing.taetigkeitsschwerpunkte ?? [],
      verbandCodes: props.existing.verbaende ?? [],
    } });
  } else {
    resetForm({ values: initial() });
  }
});

const speichern = handleSubmit(async (values) => {
  serverFehler.value = '';
  try {
    if (props.existing?.id) {
      await putCrmOrganisationenId(props.existing.id, values);
      emit('saved');
    } else {
      const o = (await postCrmOrganisationen(values)) as OrgDetail;
      emit('created', o.id!);
    }
    emit('update:open', false);
  } catch (e) {
    if (istUnauth(e)) return login();
    const fehler = violationsZuFehlern((e as { response?: { data?: unknown } })?.response?.data);
    if (Object.keys(fehler).length) setErrors(fehler);
    else serverFehler.value = fehlerText(e);
  }
});
</script>

<template>
  <DialogShell
    title="Neue Organisation"
    size="xl"
    :open="open"
    primary-label="Speichern"
    primary-icon="i-lucide-building-2"
    :primary-loading="isSubmitting"
    @update:open="emit('update:open', $event)"
    @primary="speichern"
  >
    <UAlert v-if="serverFehler" color="error" variant="soft" :title="serverFehler" class="mb-4" />
    <DublettenWarnung
      v-if="!existing"
      art="org"
      :name="name"
      :ust-id="ustId"
      @verwenden="aufVerwenden"
    />

    <!-- A15: Firmen-Anreicherung „Daten online ziehen" (gestreamt via WebSocket) -->
    <div v-if="!existing" class="mb-4">
      <UButton size="sm" variant="outline" icon="i-lucide-globe" :loading="anrLaeuft"
               :disabled="!name?.trim()" @click="datenOnlineZiehen">
        Daten online ziehen
      </UButton>
      <div v-if="anrLog.length || anrVorschlag || anrLaeuft"
           class="mt-3 rounded-lg border border-default p-3 bg-elevated">
        <ul class="space-y-1">
          <li v-for="(e, i) in anrLog" :key="i" class="flex items-start gap-2 text-xs">
            <UIcon :name="anrIcon(e.status)"
                   :class="e.typ === 'fehler' || e.status === 'fehler' ? 'text-error-500 mt-0.5' : 'text-dimmed mt-0.5'" />
            <span><span v-if="e.schritt" class="font-medium">{{ e.schritt }}: </span>{{ e.detail }}</span>
          </li>
        </ul>
        <div v-if="anrVorschlag" class="mt-2 pt-2 border-t border-default">
          <div class="text-xs text-muted mb-1">KI-Vorschlag aus VIES + Impressum:</div>
          <div class="text-sm font-medium">
            {{ anrVorschlag.name }}
            <span v-if="anrVorschlag.rechtsform" class="text-muted">· {{ anrVorschlag.rechtsform }}</span>
            <span v-if="anrVorschlag.ustId" class="text-muted">· {{ anrVorschlag.ustId }}</span>
          </div>
          <div v-if="anrVorschlag.strasse || anrVorschlag.ort" class="text-xs text-muted">
            {{ [anrVorschlag.strasse, [anrVorschlag.plz, anrVorschlag.ort].filter(Boolean).join(' ')].filter(Boolean).join(', ') }}
          </div>
          <div v-if="anrVorschlag.brancheHinweis" class="text-xs text-muted">{{ anrVorschlag.brancheHinweis }}</div>
          <UButton size="xs" icon="i-lucide-check" class="mt-2" @click="anrUebernehmen">In Felder übernehmen</UButton>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-4">
      <UFormField label="Name" required class="col-span-2" :error="errors.name">
        <UInput v-model="name" class="w-full" />
      </UFormField>
      <UFormField label="Rechtsform" :error="errors.rechtsform">
        <UInput v-model="rechtsform" placeholder="z. B. GmbH" class="w-full" />
      </UFormField>
      <UFormField label="USt-IdNr." :error="errors.ustId">
        <UInput v-model="ustId" placeholder="DE…" class="w-full" />
      </UFormField>
      <UFormField label="Branche (WZ/NACE)" :error="errors.brancheCode">
        <USelect v-model="brancheCode" :items="lookupItems(branchen)" placeholder="— wählen —" class="w-full" />
      </UFormField>
      <UFormField label="Website" :error="errors.website">
        <UInput v-model="website" class="w-full" />
      </UFormField>
      <UFormField label="Unternehmenstyp(en)" class="col-span-2">
        <USelect v-model="unternehmenstypCodes" :items="lookupItems(unternehmenstypen)" multiple class="w-full" />
      </UFormField>
      <UFormField label="Tätigkeitsschwerpunkte" class="col-span-2">
        <USelect v-model="schwerpunktCodes" :items="lookupItems(schwerpunkte)" multiple class="w-full" />
      </UFormField>
      <UFormField label="Verbände" class="col-span-2">
        <USelect v-model="verbandCodes" :items="lookupItems(verbaende)" multiple class="w-full" />
      </UFormField>
      <UFormField label="IHK / Kammer" :error="errors.ihkKammerCode">
        <USelect v-model="ihkKammerCode" :items="lookupItems(kammern)" placeholder="— wählen —" class="w-full" />
      </UFormField>
      <UFormField label="Bestandsgröße (Einheiten)" :error="errors.bestandsgroesse">
        <UInputNumber v-model="bestandsgroesse" :min="0" class="w-full" />
      </UFormField>
      <UFormField label="Ausbildungsbetrieb" class="col-span-2">
        <UCheckbox v-model="ausbildungsbetrieb" label="Ist Ausbildungsbetrieb (Berufsschul-/Azubi-Prozesse)" />
      </UFormField>
    </div>
  </DialogShell>
</template>
