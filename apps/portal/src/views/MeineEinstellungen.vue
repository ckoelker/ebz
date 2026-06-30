<script setup lang="ts">
// K1b: Komfort-Einstellungen der Person — Digest-Bündelung, Quiet-Hours (Ruhezeiten) und Rate-Limit der
// externen Benachrichtigungen, dazu die Kanal×Kategorie-Präferenzen (E-Mail/SMS fein je Themenbereich).
// Das Portal-Postfach bleibt immer aktiv; transaktionale Pflicht-Infos lassen sich nicht abschalten
// (das setzt der Server durch). Eigen-skopiert über den Token.
import { ref, onMounted, computed } from 'vue';
import {
  partyLogin, kanalPraeferenzen, setzeKanalPraeferenz, kommunikationEinstellungen, setzeEinstellungen,
  ApiFehler, Kanal, Kategorie, type PraeferenzView, type EinstellungView,
} from '@/portal';
import { auth, login } from '@/auth';

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const praef = ref<PraeferenzView[]>([]);
const busy = ref(false);

// Komfort
const digest = ref(false);
const quietVon = ref('');
const quietBis = ref('');
const maxProStunde = ref(0);

const angemeldet = computed(() => auth.angemeldet);

// Nur personenrelevante Kategorien (INTERN ist nie sichtbar; PRUEFUNG hat noch keinen Produzenten).
const kategorien: { wert: Kategorie; label: string }[] = [
  { wert: Kategorie.ANMELDUNG, label: 'Anmeldung' },
  { wert: Kategorie.RECHNUNG, label: 'Rechnung' },
  { wert: Kategorie.EINSCHREIBUNG, label: 'Einschreibung' },
  { wert: Kategorie.SYSTEM, label: 'System & Hinweise' },
];
const kanaele: { wert: Kanal; label: string }[] = [
  { wert: Kanal.EMAIL, label: 'E-Mail' },
  { wert: Kanal.SMS, label: 'SMS' },
];

onMounted(async () => {
  if (!auth.angemeldet) return;
  await partyLogin({ email: auth.email, anzeigeName: auth.name || auth.benutzer }).catch(() => {});
  await laden_();
});

async function laden_() {
  laden.value = true;
  try {
    const [prefs, e] = await Promise.all([kanalPraeferenzen(), kommunikationEinstellungen()]);
    praef.value = prefs;
    digest.value = e.digest ?? false;
    quietVon.value = (e.quietVon ?? '').slice(0, 5);
    quietBis.value = (e.quietBis ?? '').slice(0, 5);
    maxProStunde.value = e.maxProStunde ?? 0;
  } catch (err) {
    fehler(err);
  } finally {
    laden.value = false;
  }
}

/** Effektive Erlaubnis: exakter Override, sonst (bei Kategorie) der globale Schalter, sonst an. */
function erlaubt(kanal: Kanal, kategorie?: Kategorie): boolean {
  const exact = praef.value.find((p) => p.kanal === kanal
    && (kategorie ? p.kategorie === kategorie : !p.kategorie));
  if (exact) return exact.aktiv ?? true;
  if (kategorie) {
    const global = praef.value.find((p) => p.kanal === kanal && !p.kategorie);
    if (global) return global.aktiv ?? true;
  }
  return true;
}

async function umschalten(kanal: Kanal, wert: boolean, kategorie?: Kategorie) {
  busy.value = true;
  try {
    await setzeKanalPraeferenz(kanal, wert, kategorie);
    praef.value = await kanalPraeferenzen();
  } catch (err) {
    fehler(err);
  } finally {
    busy.value = false;
  }
}

async function komfortSpeichern() {
  busy.value = true;
  try {
    const dto: EinstellungView = {
      digest: digest.value,
      quietVon: quietVon.value || undefined,
      quietBis: quietBis.value || undefined,
      maxProStunde: maxProStunde.value,
    };
    await setzeEinstellungen(dto);
    meldung.value = { text: 'Einstellungen gespeichert.', severity: 'success' };
  } catch (err) {
    fehler(err);
  } finally {
    busy.value = false;
  }
}

function fehler(e: unknown) {
  if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}
</script>

<template>
  <section>
    <h2 class="text-xl font-bold mb-4">Benachrichtigungs-Einstellungen</h2>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a>, um Ihre Einstellungen zu verwalten.
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
        :title="meldung.text" close class="mb-4" @update:open="meldung = null" />

      <!-- Kanäle: global + je Kategorie -->
      <div class="border border-default rounded-lg p-4 mb-5">
        <h3 class="font-semibold mb-1">Kanäle</h3>
        <p class="text-dimmed text-sm mb-4">
          Das Portal-Postfach bleibt immer aktiv. Transaktionale Pflicht-Informationen lassen sich nicht
          abschalten. Ein Kategorie-Schalter überschreibt den globalen.
        </p>
        <div class="overflow-auto">
          <table class="w-full text-sm">
            <thead class="text-left text-dimmed">
              <tr>
                <th class="py-2 pr-4 font-medium">Bereich</th>
                <th v-for="k in kanaele" :key="k.wert" class="py-2 px-3 font-medium text-center w-24">{{ k.label }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-default">
              <tr>
                <td class="py-2 pr-4 font-semibold">Alle (global)</td>
                <td v-for="k in kanaele" :key="k.wert" class="py-2 px-3 text-center">
                  <USwitch :model-value="erlaubt(k.wert)" :disabled="busy"
                    @update:model-value="(v: boolean) => umschalten(k.wert, v)" />
                </td>
              </tr>
              <tr v-for="kat in kategorien" :key="kat.wert">
                <td class="py-2 pr-4">{{ kat.label }}</td>
                <td v-for="k in kanaele" :key="k.wert" class="py-2 px-3 text-center">
                  <USwitch :model-value="erlaubt(k.wert, kat.wert)" :disabled="busy"
                    @update:model-value="(v: boolean) => umschalten(k.wert, v, kat.wert)" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Komfort: Digest / Quiet-Hours / Rate-Limit -->
      <div class="border border-default rounded-lg p-4">
        <h3 class="font-semibold mb-4">Komfort</h3>
        <div class="flex flex-col gap-4 max-w-md">
          <label class="inline-flex items-center gap-3">
            <USwitch v-model="digest" />
            <span>
              <span class="font-medium">Sammel-Benachrichtigung (Digest)</span>
              <span class="block text-dimmed text-xs">Externe Hinweise gebündelt statt einzeln zustellen.</span>
            </span>
          </label>

          <div>
            <div class="font-medium mb-1">Ruhezeiten (Quiet Hours)</div>
            <div class="text-dimmed text-xs mb-2">In diesem Zeitfenster wird der Versand aufgeschoben.</div>
            <div class="flex items-center gap-2">
              <UInput v-model="quietVon" type="time" class="w-32" />
              <span class="text-dimmed">bis</span>
              <UInput v-model="quietBis" type="time" class="w-32" />
              <UButton v-if="quietVon || quietBis" color="neutral" variant="ghost" size="xs"
                icon="i-lucide-x" @click="quietVon = ''; quietBis = ''">leeren</UButton>
            </div>
          </div>

          <UFormField label="Maximal pro Stunde (Rate-Limit)"
            help="0 = unbegrenzt. Begrenzt die Anzahl externer Benachrichtigungen je Stunde.">
            <UInputNumber v-model="maxProStunde" :min="0" :max="60" class="w-32" />
          </UFormField>

          <div>
            <UButton icon="i-lucide-check" :loading="busy" @click="komfortSpeichern">Speichern</UButton>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>
