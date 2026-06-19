<script setup lang="ts">
// System→Person: der eingeloggte Kunde sieht seinen Aktivitätslog (Zeitstrahl) mit Ungelesen-Badge,
// markiert Einträge als gelesen, quittiert kenntnisnahmepflichtige Benachrichtigungen und schaltet die
// Kanäle (E-Mail/SMS) an/aus. Eigen-skopiert über den Token (kein Fremdzugriff). Threads folgen ab K2.
import { ref, onMounted, computed } from 'vue';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import ToggleSwitch from 'primevue/toggleswitch';
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
    emailAktiv.value = prefs.find((p) => p.kanal === 'EMAIL')?.aktiv ?? true;
    smsAktiv.value = prefs.find((p) => p.kanal === 'SMS')?.aktiv ?? true;
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
    // Zustand zurücksetzen bei Fehler
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
  return isNaN(d.getTime()) ? iso : d.toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
}

const kategorieSchwere: Record<string, 'info' | 'success' | 'warn' | 'secondary'> = {
  RECHNUNG: 'info',
  ANMELDUNG: 'success',
  EINSCHREIBUNG: 'success',
  PRUEFUNG: 'warn',
  SYSTEM: 'secondary',
};
</script>

<template>
  <section>
    <div class="kopf">
      <h2>Meine Aktivitäten
        <Tag v-if="ungelesen > 0" :value="`${ungelesen} neu`" severity="danger" rounded />
      </h2>
      <Button v-if="angemeldet" label="Aktualisieren" icon="pi pi-refresh" size="small" text
        :loading="laden" @click="laden_" />
    </div>

    <Message v-if="!angemeldet" severity="warn">
      Bitte <a href="#" @click.prevent="login">anmelden</a>, um Ihre Aktivitäten zu sehen.
    </Message>

    <template v-else>
      <Message v-if="meldung" :severity="meldung.severity" closable @close="meldung = null">{{ meldung.text }}</Message>

      <div class="kanaele">
        <span class="kanaele-titel">Benachrichtigungen per</span>
        <label class="kanal"><ToggleSwitch v-model="emailAktiv" @change="kanalUmschalten(Kanal.EMAIL, emailAktiv)" /> E-Mail</label>
        <label class="kanal"><ToggleSwitch v-model="smsAktiv" @change="kanalUmschalten(Kanal.SMS, smsAktiv)" /> SMS</label>
        <span class="hinweis">Das Portal-Postfach bleibt immer aktiv.</span>
      </div>

      <Message v-if="!laden && ereignisse.length === 0" severity="info">
        Sie haben noch keine Aktivitäten.
      </Message>

      <ul class="zeitstrahl">
        <li v-for="ev in ereignisse" :key="ev.id" :class="{ ungelesen: !ev.gelesen }">
          <span class="punkt" />
          <div class="eintrag">
            <div class="zeile1">
              <Tag :value="ev.kategorie" :severity="kategorieSchwere[ev.kategorie ?? ''] ?? 'secondary'" />
              <strong class="betreff">{{ ev.betreff }}</strong>
              <span class="zeitpunkt">{{ zeit(ev.zeitpunkt) }}</span>
            </div>
            <div class="aktionen">
              <Tag v-if="ev.bestaetigtAm" value="bestätigt" severity="success" icon="pi pi-check" />
              <Button v-else-if="ev.bestaetigungErforderlich" label="Zur Kenntnis genommen" size="small"
                icon="pi pi-check" :loading="busy === ev.id" @click="bestaetigen(ev)" />
              <Button v-if="!ev.gelesen" label="Als gelesen markieren" size="small" text
                icon="pi pi-eye" :loading="busy === ev.id" @click="gelesen(ev)" />
            </div>
          </div>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.kopf {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kopf h2 {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}
.kanaele {
  display: flex;
  align-items: center;
  gap: 1.1rem;
  margin: 0.5rem 0 1rem;
  padding: 0.6rem 0.9rem;
  background: var(--p-content-hover-background, #f4f6f8);
  border-radius: 8px;
  font-size: 0.9rem;
}
.kanaele-titel {
  font-weight: 600;
}
.kanal {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.hinweis {
  color: #888;
  font-size: 0.82rem;
}
.zeitstrahl {
  list-style: none;
  margin: 0;
  padding: 0;
  border-left: 2px solid var(--p-content-border-color, #e3e7ec);
}
.zeitstrahl li {
  position: relative;
  padding: 0.55rem 0 0.55rem 1.1rem;
}
.punkt {
  position: absolute;
  left: -7px;
  top: 0.95rem;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--p-content-border-color, #cbd5e1);
}
.zeitstrahl li.ungelesen .punkt {
  background: var(--p-primary-color, #0ea5e9);
}
.eintrag {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.zeile1 {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}
.betreff {
  font-weight: 500;
}
.zeitstrahl li.ungelesen .betreff {
  font-weight: 700;
}
.zeitpunkt {
  color: #888;
  font-size: 0.82rem;
  margin-left: auto;
}
.aktionen {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
</style>
