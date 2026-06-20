<script setup lang="ts">
// System→Person: der eingeloggte Kunde sieht seinen Aktivitätslog (Zeitstrahl) mit Ungelesen-Badge,
// markiert Einträge als gelesen, quittiert kenntnisnahmepflichtige Benachrichtigungen und schaltet die
// Kanäle (E-Mail/SMS) an/aus. Eigen-skopiert über den Token (kein Fremdzugriff). Threads folgen ab K2.
import { ref, onMounted, computed } from 'vue';
import {
  partyLogin, meineAktivitaeten, ungelesenAnzahl, ereignisGelesen, ereignisBestaetigen,
  kanalPraeferenzen, setzeKanalPraeferenz, ApiFehler, Kanal,
  type EreignisView,
} from '@/portal';
import { auth, login } from '@/auth';

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const ereignisse = ref<EreignisView[]>([]);
const ungelesen = ref(0);
const emailAktiv = ref(true);
const smsAktiv = ref(true);
const busy = ref<number | null>(null);

const angemeldet = computed(() => auth.angemeldet);

// K5-Gate: noch offene Pflicht-Kenntnisnahmen (abgeleitet aus dem Log) — das Portal blendet einen
// Hinweis-Banner ein, solange welche ausstehen; überfällige/eskalierte werden hervorgehoben.
const offenePflicht = computed(() =>
  ereignisse.value.filter((e) => e.bestaetigungErforderlich && !e.bestaetigtAm));
const ueberfaellig = computed(() =>
  offenePflicht.value.some((e) => e.status === 'UEBERFAELLIG' || e.status === 'ESKALIERT'));

onMounted(async () => {
  if (!auth.angemeldet) return;
  await partyLogin({ email: auth.email, anzeigeName: auth.name || auth.benutzer }).catch(() => {});
  await laden_();
});

async function laden_() {
  laden.value = true;
  try {
    const [evs, anz, prefs] = await Promise.all([
      meineAktivitaeten(), ungelesenAnzahl(), kanalPraeferenzen(),
    ]);
    ereignisse.value = evs;
    ungelesen.value = anz;
    // nur die globalen (kategorielosen) Kanal-Schalter steuern die Toggles
    emailAktiv.value = prefs.find((p) => p.kanal === 'EMAIL' && !p.kategorie)?.aktiv ?? true;
    smsAktiv.value = prefs.find((p) => p.kanal === 'SMS' && !p.kategorie)?.aktiv ?? true;
  } catch (e) {
    fehler(e);
  } finally {
    laden.value = false;
  }
}

async function gelesen(ev: EreignisView) {
  if (!ev.id || ev.gelesen) return;
  busy.value = ev.id;
  try {
    await ereignisGelesen(ev.id);
    ev.gelesen = true;
    ungelesen.value = Math.max(0, ungelesen.value - 1);
  } catch (e) {
    fehler(e);
  } finally {
    busy.value = null;
  }
}

async function bestaetigen(ev: EreignisView) {
  if (!ev.id) return;
  busy.value = ev.id;
  try {
    await ereignisBestaetigen(ev.id);
    ev.bestaetigtAm = new Date().toISOString();
    if (!ev.gelesen) { await ereignisGelesen(ev.id); ev.gelesen = true; ungelesen.value = Math.max(0, ungelesen.value - 1); }
    meldung.value = { text: 'Kenntnisnahme bestätigt.', severity: 'success' };
  } catch (e) {
    fehler(e);
  } finally {
    busy.value = null;
  }
}

async function kanalUmschalten(kanal: Kanal, wert: boolean) {
  try {
    await setzeKanalPraeferenz(kanal, wert);
  } catch (e) {
    fehler(e);
    if (kanal === Kanal.EMAIL) emailAktiv.value = !wert; else smsAktiv.value = !wert;
  }
}

function fehler(e: unknown) {
  if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}

function zeit(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  // DACH-Strategie: konsequent in Europe/Berlin ausgeben (der Server erfasst bereits in Berlin).
  return isNaN(d.getTime()) ? iso
    : d.toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Berlin' });
}

const kategorieFarbe: Record<string, 'info' | 'success' | 'warning' | 'neutral'> = {
  RECHNUNG: 'info',
  ANMELDUNG: 'success',
  EINSCHREIBUNG: 'success',
  PRUEFUNG: 'warning',
  SYSTEM: 'neutral',
};
</script>

<template>
  <section>
    <div class="flex items-center justify-between mb-4">
      <h2 class="text-xl font-bold flex items-center gap-2">
        Meine Aktivitäten
        <UBadge v-if="ungelesen > 0" color="error" variant="solid" size="sm">{{ ungelesen }} neu</UBadge>
      </h2>
      <UButton v-if="angemeldet" color="neutral" variant="ghost" size="sm" icon="i-lucide-refresh-cw"
        :loading="laden" @click="laden_">Aktualisieren</UButton>
    </div>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a>, um Ihre Aktivitäten zu sehen.
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
        :title="meldung.text" close class="mb-4" @update:open="meldung = null" />

      <!-- K5-Gate: solange Pflicht-Kenntnisnahmen offen sind, ein deutlicher Hinweis-Banner (überfällig rot). -->
      <UAlert v-if="offenePflicht.length > 0" :color="ueberfaellig ? 'error' : 'warning'" variant="soft"
        :icon="ueberfaellig ? 'i-lucide-alarm-clock' : 'i-lucide-bell-ring'" class="mb-4"
        :title="offenePflicht.length === 1 ? 'Eine Kenntnisnahme steht noch aus'
          : `${offenePflicht.length} Kenntnisnahmen stehen noch aus`">
        <template #description>
          Bitte bestätigen Sie die markierten Einträge{{ ueberfaellig ? ' — eine Frist ist bereits abgelaufen.' : '.' }}
        </template>
      </UAlert>

      <div class="flex items-center gap-5 mb-5 px-4 py-3 rounded-lg bg-elevated text-sm">
        <span class="font-semibold">Benachrichtigungen per</span>
        <label class="inline-flex items-center gap-2">
          <USwitch v-model="emailAktiv" @update:model-value="(v: boolean) => kanalUmschalten(Kanal.EMAIL, v)" /> E-Mail
        </label>
        <label class="inline-flex items-center gap-2">
          <USwitch v-model="smsAktiv" @update:model-value="(v: boolean) => kanalUmschalten(Kanal.SMS, v)" /> SMS
        </label>
        <span class="text-dimmed text-xs">Das Portal-Postfach bleibt immer aktiv.</span>
      </div>

      <UAlert v-if="!laden && ereignisse.length === 0" color="info" variant="soft"
        title="Sie haben noch keine Aktivitäten." />

      <ul class="border-l-2 border-default ml-1.5">
        <li v-for="ev in ereignisse" :key="ev.id" class="relative pl-5 py-2.5">
          <span
            class="absolute -left-[7px] top-3.5 w-3 h-3 rounded-full"
            :class="ev.gelesen ? 'bg-default border border-muted' : 'bg-primary-500'"
          />
          <div class="flex flex-col gap-1.5">
            <div class="flex items-center gap-2 flex-wrap">
              <UBadge :color="kategorieFarbe[ev.kategorie ?? ''] ?? 'neutral'" variant="soft" size="sm">
                {{ ev.kategorie }}
              </UBadge>
              <span :class="ev.gelesen ? 'font-medium' : 'font-bold'">{{ ev.betreff }}</span>
              <span class="ml-auto text-dimmed text-xs">{{ zeit(ev.zeitpunkt) }}</span>
            </div>
            <div class="flex items-center gap-2">
              <UBadge v-if="ev.bestaetigtAm" color="success" variant="soft" size="sm" icon="i-lucide-check">
                bestätigt
              </UBadge>
              <template v-else-if="ev.bestaetigungErforderlich">
                <UBadge v-if="ev.status === 'UEBERFAELLIG' || ev.status === 'ESKALIERT'" color="error"
                  variant="soft" size="sm" icon="i-lucide-alarm-clock">überfällig</UBadge>
                <UButton size="sm" icon="i-lucide-check"
                  :color="ev.status === 'UEBERFAELLIG' || ev.status === 'ESKALIERT' ? 'error' : 'primary'"
                  :loading="busy === ev.id" @click="bestaetigen(ev)">Zur Kenntnis genommen</UButton>
                <span v-if="ev.bestaetigenBis" class="text-dimmed text-xs">Frist: {{ zeit(ev.bestaetigenBis) }}</span>
              </template>
              <UButton v-if="!ev.gelesen" color="neutral" variant="ghost" size="sm" icon="i-lucide-eye"
                :loading="busy === ev.id" @click="gelesen(ev)">Als gelesen markieren</UButton>
            </div>
          </div>
        </li>
      </ul>
    </template>
  </section>
</template>
