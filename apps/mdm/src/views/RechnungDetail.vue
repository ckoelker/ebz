<script setup lang="ts">
// Rechnungs-Cockpit (Detail): ein Beleg mit Positionen + der vollständige Lebenszyklus als bedienbare
// Aktionen — Entwurf bestücken/ausstellen, Versand, Zahlungseingang, Storno/Gutschrift/Nachberechnung,
// ZUGFeRD-Download. Status-Guards spiegeln die Server-Regeln; destruktive Schritte mit Bestätigung.
import { ref, computed, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import {
  rechnung, ausstellen, versenden, bezahlen, stornieren, gutschrift, nachberechnung,
  positionErgaenzen, zugferdHerunterladen, euro, ApiFehler, Steuerfall, Leistungsart,
  type RechnungDto, type ManuellePositionDto, type KorrekturRequest, type ZahlungseingangDto,
} from '@/rechnung';
import { login } from '@/auth';

const route = useRoute();
const router = useRouter();
const id = Number(route.params.id);

const { data: r, isFetching, refetch, error } = useQuery({
  queryKey: ['rechnung', id],
  queryFn: async (): Promise<RechnungDto> => await rechnung(id),
});

const istEntwurf = computed(() => r.value?.status === 'ENTWURF');
const festgeschrieben = computed(() => !!r.value && r.value.status !== 'ENTWURF');
const istAusgestellt = computed(() => r.value?.status === 'AUSGESTELLT');
const korrigierbar = computed(() => r.value?.status === 'AUSGESTELLT' || r.value?.status === 'BEZAHLT');
const forderung = computed(() => r.value?.belegart === 'RECHNUNG' || r.value?.belegart === 'NACHBERECHNUNG');

const statusFarbe: Record<string, 'neutral' | 'info' | 'success' | 'error'> = {
  ENTWURF: 'neutral', AUSGESTELLT: 'info', BEZAHLT: 'success', STORNIERT: 'error',
};

const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const busy = ref(false);
async function tu(fn: () => Promise<unknown>, erfolg?: string) {
  busy.value = true;
  meldung.value = null;
  try {
    await fn();
    await refetch();
    if (erfolg) meldung.value = { text: erfolg, severity: 'success' };
    return true;
  } catch (e) {
    if (e instanceof ApiFehler && e.status === 401) { login(); return false; }
    meldung.value = { text: e instanceof Error ? e.message : 'Fehler.', severity: 'error' };
    return false;
  } finally {
    busy.value = false;
  }
}

async function pdf() {
  if (!r.value) return;
  await tu(() => zugferdHerunterladen(id, r.value!.nummer));
}

// ── Position ergänzen (nur Entwurf) ──
const posOffen = ref(false);
const leerePos = (): ManuellePositionDto => ({
  teilnehmerName: '', beschreibung: '', menge: 1, einzelbetragCent: 0,
  steuerfall: Steuerfall.STANDARD, steuersatz: 19, befreiungsgrund: '', leistungsart: Leistungsart.SONSTIGE,
});
const pos = reactive<{ beschreibung: string; menge: number; betragEuro: number; steuersatz: number; steuerfall: Steuerfall }>(
  { beschreibung: '', menge: 1, betragEuro: 0, steuersatz: 19, steuerfall: Steuerfall.STANDARD });
async function posSpeichern() {
  const dto: ManuellePositionDto = {
    ...leerePos(),
    beschreibung: pos.beschreibung, menge: pos.menge,
    einzelbetragCent: Math.round(pos.betragEuro * 100), steuersatz: pos.steuersatz, steuerfall: pos.steuerfall,
  };
  if (await tu(() => positionErgaenzen(id, dto), 'Position ergänzt.')) {
    posOffen.value = false;
    Object.assign(pos, { beschreibung: '', menge: 1, betragEuro: 0, steuersatz: 19, steuerfall: Steuerfall.STANDARD });
  }
}

// ── Ausstellen / Versenden (Bestätigung) ──
const ausstellenOffen = ref(false);
const stornoOffen = ref(false);

// ── Zahlungseingang ──
const zahlOffen = ref(false);
const zahl = reactive<{ bezahltAm?: string; betragEuro?: number; referenz?: string }>({});
async function zahlSpeichern() {
  const body: ZahlungseingangDto = {
    bezahltAm: zahl.bezahltAm || undefined,
    zahlbetragCent: zahl.betragEuro != null ? Math.round(zahl.betragEuro * 100) : undefined,
    zahlungsReferenz: zahl.referenz || undefined,
  };
  if (await tu(() => bezahlen(id, body), 'Zahlungseingang verbucht.')) zahlOffen.value = false;
}

// ── Korrektur (Gutschrift / Nachberechnung) ──
const korrOffen = ref(false);
const korrArt = ref<'GUTSCHRIFT' | 'NACHBERECHNUNG'>('GUTSCHRIFT');
const korr = reactive<{ grund: string; beschreibung: string; betragEuro: number; steuersatz: number }>(
  { grund: '', beschreibung: '', betragEuro: 0, steuersatz: 19 });
function oeffneKorrektur(art: 'GUTSCHRIFT' | 'NACHBERECHNUNG') {
  korrArt.value = art;
  Object.assign(korr, { grund: '', beschreibung: '', betragEuro: 0, steuersatz: 19 });
  meldung.value = null;
  korrOffen.value = true;
}
async function korrSpeichern() {
  const body: KorrekturRequest = {
    grund: korr.grund || undefined,
    positionen: [{
      teilnehmerName: '', beschreibung: korr.beschreibung, menge: 1,
      einzelbetragCent: Math.round(korr.betragEuro * 100), steuerfall: Steuerfall.STANDARD,
      steuersatz: korr.steuersatz, befreiungsgrund: '', leistungsart: Leistungsart.KORREKTUR,
    }],
  };
  const fn = korrArt.value === 'GUTSCHRIFT' ? () => gutschrift(id, body) : () => nachberechnung(id, body);
  if (await tu(fn, `${korrArt.value === 'GUTSCHRIFT' ? 'Gutschrift' : 'Nachberechnung'} erstellt.`)) korrOffen.value = false;
}

const steuerfallItems = Object.values(Steuerfall).map((s) => ({ label: s, value: s }));
</script>

<template>
  <section v-if="r">
    <div class="flex items-center gap-3 mb-1">
      <UButton color="neutral" variant="ghost" icon="i-lucide-arrow-left" @click="router.push('/rechnungen')" />
      <h2 class="text-xl font-bold">{{ r.nummer ?? '— Entwurf —' }}</h2>
      <UBadge :color="statusFarbe[r.status ?? ''] ?? 'neutral'" variant="soft">{{ r.status }}</UBadge>
      <UBadge color="neutral" variant="subtle">{{ r.belegart }}</UBadge>
      <UBadge color="neutral" variant="subtle">{{ r.bereich }}</UBadge>
      <span class="flex-1" />
      <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="() => { refetch(); }" />
    </div>

    <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
            :title="meldung.text" close class="my-3" @update:open="meldung = null" />
    <UAlert v-if="error" color="error" variant="soft" class="my-3" :title="(error as Error).message" />

    <!-- Kopfdaten -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-3 my-4 text-sm">
      <div><div class="text-muted text-xs">Debitor</div>{{ r.debitorId ?? '—' }}</div>
      <div><div class="text-muted text-xs">Zeitraum</div>{{ r.zeitraumBezeichnung ?? '—' }}</div>
      <div><div class="text-muted text-xs">Ausstellung</div>{{ r.ausstellungsdatum ?? '—' }}</div>
      <div><div class="text-muted text-xs">Zahlungsziel</div>{{ r.zahlungszielTage }} Tage</div>
      <div><div class="text-muted text-xs">Versand</div>{{ r.versandStatus ?? 'NICHT_VERSENDET' }}<span v-if="r.versendetAn" class="text-muted"> · {{ r.versendetAn }}</span></div>
      <div v-if="r.bezahltAm"><div class="text-muted text-xs">Bezahlt am</div>{{ r.bezahltAm }}</div>
      <div v-if="r.zahlbetragCent != null"><div class="text-muted text-xs">Zahlbetrag</div>{{ euro(r.zahlbetragCent) }}</div>
      <div v-if="r.zahlungsReferenz"><div class="text-muted text-xs">Referenz</div>{{ r.zahlungsReferenz }}</div>
    </div>

    <!-- Positionen -->
    <div class="border border-default rounded-lg overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="bg-elevated/50 text-muted text-left">
          <tr>
            <th class="px-3 py-2 font-medium">Beschreibung</th>
            <th class="px-3 py-2 font-medium text-right">Menge</th>
            <th class="px-3 py-2 font-medium text-right">Einzel</th>
            <th class="px-3 py-2 font-medium">USt</th>
            <th class="px-3 py-2 font-medium text-right">Betrag</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in r.positionen" :key="p.id" class="border-t border-default">
            <td class="px-3 py-2">{{ p.beschreibung }}<span v-if="p.teilnehmerName" class="text-muted"> · {{ p.teilnehmerName }}</span></td>
            <td class="px-3 py-2 text-right tabular-nums">{{ p.menge }}</td>
            <td class="px-3 py-2 text-right tabular-nums">{{ euro(p.einzelbetragCent) }}</td>
            <td class="px-3 py-2">{{ p.steuerfall }} {{ p.steuersatz }}%</td>
            <td class="px-3 py-2 text-right tabular-nums">{{ euro(p.betragCent) }}</td>
          </tr>
          <tr v-if="!r.positionen || r.positionen.length === 0">
            <td colspan="5" class="px-3 py-6 text-center text-muted">Keine Positionen.</td>
          </tr>
        </tbody>
        <tfoot>
          <tr class="border-t border-default font-semibold">
            <td class="px-3 py-2" colspan="4">Summe</td>
            <td class="px-3 py-2 text-right tabular-nums">{{ euro(r.summeCent) }}</td>
          </tr>
        </tfoot>
      </table>
    </div>

    <!-- Aktionen -->
    <div class="flex flex-wrap gap-2 mt-4">
      <UButton v-if="istEntwurf" icon="i-lucide-plus" variant="soft" :disabled="busy" @click="posOffen = true">Position</UButton>
      <UButton v-if="istEntwurf" icon="i-lucide-stamp" :disabled="busy || !r.positionen?.length" @click="ausstellenOffen = true">Ausstellen</UButton>
      <UButton v-if="festgeschrieben" icon="i-lucide-mail" variant="soft" :loading="busy" @click="tu(() => versenden(id), 'E-Rechnung versendet.')">Versenden</UButton>
      <UButton v-if="festgeschrieben" icon="i-lucide-download" variant="soft" :loading="busy" @click="pdf">ZUGFeRD</UButton>
      <UButton v-if="istAusgestellt && forderung" icon="i-lucide-banknote" color="success" variant="soft" :disabled="busy" @click="zahlOffen = true">Zahlungseingang</UButton>
      <UButton v-if="korrigierbar" icon="i-lucide-undo-2" color="warning" variant="soft" :disabled="busy" @click="oeffneKorrektur('GUTSCHRIFT')">Gutschrift</UButton>
      <UButton v-if="korrigierbar" icon="i-lucide-plus-circle" color="warning" variant="soft" :disabled="busy" @click="oeffneKorrektur('NACHBERECHNUNG')">Nachberechnung</UButton>
      <UButton v-if="korrigierbar" icon="i-lucide-ban" color="error" variant="soft" :disabled="busy" @click="stornoOffen = true">Storno</UButton>
    </div>

    <!-- Position-Modal -->
    <UModal v-model:open="posOffen" title="Position ergänzen">
      <template #body>
        <div class="space-y-3">
          <UFormField label="Beschreibung" required><UInput v-model="pos.beschreibung" class="w-full" /></UFormField>
          <div class="grid grid-cols-3 gap-3">
            <UFormField label="Menge"><UInputNumber v-model="pos.menge" :min="1" class="w-full" /></UFormField>
            <UFormField label="Einzelbetrag (€)"><UInputNumber v-model="pos.betragEuro" :min="0" :step="0.01" class="w-full" /></UFormField>
            <UFormField label="USt %"><UInputNumber v-model="pos.steuersatz" :min="0" :max="19" class="w-full" /></UFormField>
          </div>
          <UFormField label="Steuerfall"><USelect v-model="pos.steuerfall" :items="steuerfallItems" class="w-full" /></UFormField>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="posOffen = false">Abbrechen</UButton>
          <UButton :disabled="!pos.beschreibung || busy" :loading="busy" @click="posSpeichern">Hinzufügen</UButton>
        </div>
      </template>
    </UModal>

    <!-- Ausstellen-Bestätigung -->
    <UModal v-model:open="ausstellenOffen" title="Beleg ausstellen?">
      <template #body>
        <p class="text-sm">Die Festschreibung vergibt eine lückenlose Belegnummer und macht den Beleg
          <b>unveränderbar</b>. Korrekturen sind danach nur über Storno/Gutschrift möglich.</p>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="ausstellenOffen = false">Abbrechen</UButton>
          <UButton :loading="busy" @click="tu(() => ausstellen(id), 'Beleg ausgestellt.').then((ok) => { if (ok) ausstellenOffen = false; })">Ausstellen</UButton>
        </div>
      </template>
    </UModal>

    <!-- Storno-Bestätigung -->
    <UModal v-model:open="stornoOffen" title="Beleg stornieren?">
      <template #body>
        <p class="text-sm">Erzeugt einen <b>Storno-Beleg</b> mit gespiegelten Beträgen und setzt das
          Original auf STORNIERT. Dieser Schritt ist nicht umkehrbar.</p>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="stornoOffen = false">Abbrechen</UButton>
          <UButton color="error" :loading="busy" @click="tu(() => stornieren(id), 'Storno-Beleg erstellt.').then((ok) => { if (ok) stornoOffen = false; })">Stornieren</UButton>
        </div>
      </template>
    </UModal>

    <!-- Zahlungseingang -->
    <UModal v-model:open="zahlOffen" title="Zahlungseingang verbuchen">
      <template #body>
        <div class="space-y-3">
          <p class="text-sm text-muted">Offene Posten/Mahnwesen liegen bei DATEV — hier nur der „bezahlt"-Vermerk.
            Leer = heute / volle Belegsumme.</p>
          <div class="grid grid-cols-2 gap-3">
            <UFormField label="Bezahlt am"><UInput v-model="zahl.bezahltAm" type="date" class="w-full" /></UFormField>
            <UFormField label="Zahlbetrag (€)" :help="`Belegsumme ${euro(r.summeCent)}`"><UInputNumber v-model="zahl.betragEuro" :min="0" :step="0.01" class="w-full" /></UFormField>
          </div>
          <UFormField label="Referenz"><UInput v-model="zahl.referenz" placeholder="Kontoauszug / Überweisung" class="w-full" /></UFormField>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="zahlOffen = false">Abbrechen</UButton>
          <UButton color="success" :loading="busy" @click="zahlSpeichern">Verbuchen</UButton>
        </div>
      </template>
    </UModal>

    <!-- Korrektur (Gutschrift / Nachberechnung) -->
    <UModal v-model:open="korrOffen" :title="korrArt === 'GUTSCHRIFT' ? 'Gutschrift erstellen' : 'Nachberechnung erstellen'">
      <template #body>
        <div class="space-y-3">
          <p class="text-sm text-muted">
            {{ korrArt === 'GUTSCHRIFT' ? 'Negativbeleg (Erstattung) mit Bezug auf das Original.' : 'Zusätzliche Forderung mit Bezug auf das Original.' }}
            Betrag positiv eingeben — bei der Gutschrift negiert das System.
          </p>
          <UFormField label="Grund"><UInput v-model="korr.grund" placeholder="z. B. Kulanz / Korrektur Teilnehmerzahl" class="w-full" /></UFormField>
          <UFormField label="Beschreibung" required><UInput v-model="korr.beschreibung" class="w-full" /></UFormField>
          <div class="grid grid-cols-2 gap-3">
            <UFormField label="Betrag (€)"><UInputNumber v-model="korr.betragEuro" :min="0" :step="0.01" class="w-full" /></UFormField>
            <UFormField label="USt %"><UInputNumber v-model="korr.steuersatz" :min="0" :max="19" class="w-full" /></UFormField>
          </div>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="korrOffen = false">Abbrechen</UButton>
          <UButton color="warning" :disabled="!korr.beschreibung || korr.betragEuro <= 0 || busy" :loading="busy" @click="korrSpeichern">Erstellen</UButton>
        </div>
      </template>
    </UModal>
  </section>
  <section v-else-if="error" class="text-error">{{ (error as Error).message }}</section>
</template>
