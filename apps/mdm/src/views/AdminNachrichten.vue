<script setup lang="ts">
// Admin↔Person-Nachrichten (K2, Sachbearbeiter-Sicht): das Backoffice sieht alle Vorgänge (links),
// liest den Verlauf (rechts), antwortet und kann sich vom Co-Pilot einen KI-Antwortentwurf vorschlagen
// lassen (HITL — prüfen/bearbeiten/selbst senden, EU-AI-Act-konform gekennzeichnet). Neue Nachrichten
// kommen live über den Thread-WebSocket; eine gesendete Antwort wird zusätzlich ins CRM-Log gespiegelt.
import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue';
import {
  adminKonversationen, adminNachrichten, adminAntworten, adminEntwurf, adminEroeffne, adminGelesen,
  ApiFehler, type KonversationView, type NachrichtView,
} from '@/kommunikation';
import { auth, login, getAccessToken } from '@/auth';
import NichtAbgestimmtBanner from '@/components/NichtAbgestimmtBanner.vue';
import { datumZeitKurz as zeit } from '@crm-ui/domain/format';

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const threads = ref<KonversationView[]>([]);
const aktiv = ref<KonversationView | null>(null);
const verlauf = ref<NachrichtView[]>([]);
const text = ref('');
const sende = ref(false);
const entwurfLaeuft = ref(false);
const entwurfAktiv = ref(false);
const verlaufBox = ref<HTMLElement | null>(null);
let socket: WebSocket | null = null;

// „Neuer Vorgang"
const dialogOffen = ref(false);
const neu = ref({ personId: undefined as number | undefined, betreff: '', text: '' });

const angemeldet = computed(() => auth.angemeldet);

onMounted(async () => { if (auth.angemeldet) await ladeThreads(); });
onBeforeUnmount(() => trenneSocket());

async function ladeThreads() {
  laden.value = true;
  try {
    threads.value = await adminKonversationen();
  } catch (e) {
    fehler(e);
  } finally {
    laden.value = false;
  }
}

async function oeffne(t: KonversationView) {
  aktiv.value = t;
  entwurfAktiv.value = false;
  await ladeVerlauf();
  if (t.id != null) {
    await adminGelesen(t.id).catch(() => {});
    t.ungelesen = false;
    verbindeSocket(t.id);
  }
}

async function ladeVerlauf() {
  if (aktiv.value?.id == null) return;
  verlauf.value = await adminNachrichten(aktiv.value.id);
  await nextTick();
  if (verlaufBox.value) verlaufBox.value.scrollTop = verlaufBox.value.scrollHeight;
}

async function senden() {
  if (aktiv.value?.id == null || !text.value.trim()) return;
  sende.value = true;
  try {
    await adminAntworten(aktiv.value.id, `<p>${escapeHtml(text.value.trim())}</p>`);
    text.value = '';
    entwurfAktiv.value = false;
    await ladeVerlauf();
    await ladeThreads();
  } catch (e) {
    fehler(e);
  } finally {
    sende.value = false;
  }
}

// Co-Pilot: KI-Vorschlag ins Eingabefeld holen (HITL — wird vor dem Senden geprüft/bearbeitet).
async function coPilot() {
  if (aktiv.value?.id == null) return;
  entwurfLaeuft.value = true;
  try {
    text.value = await adminEntwurf(aktiv.value.id);
    entwurfAktiv.value = true;
  } catch (e) {
    fehler(e);
  } finally {
    entwurfLaeuft.value = false;
  }
}

async function vorgangEroeffnen() {
  if (!neu.value.personId || !neu.value.betreff.trim() || !neu.value.text.trim()) {
    meldung.value = { text: 'Bitte Person-ID, Betreff und Nachricht angeben.', severity: 'error' };
    return;
  }
  sende.value = true;
  try {
    await adminEroeffne({
      personId: neu.value.personId,
      betreff: neu.value.betreff.trim(),
      inhaltHtml: `<p>${escapeHtml(neu.value.text.trim())}</p>`,
    });
    dialogOffen.value = false;
    neu.value = { personId: undefined, betreff: '', text: '' };
    meldung.value = { text: 'Vorgang eröffnet.', severity: 'success' };
    await ladeThreads();
  } catch (e) {
    fehler(e);
  } finally {
    sende.value = false;
  }
}

// RBAC am Handshake: Browser-WebSocket kann keinen Authorization-Header → das Staff-access_token (Realm
// ebz-staff) als ?access_token; serverseitig hebt es der RealtimeAuthRouteFilter in den Header, der
// KonversationSocket prüft Token (Cross-Realm per Issuer) + Thread-Mitgliedschaft.
async function verbindeSocket(konversationId: number) {
  trenneSocket();
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  const token = await getAccessToken();
  const auth_ = token ? `?access_token=${encodeURIComponent(token)}` : '';
  socket = new WebSocket(`${proto}://${location.host}/ws/kommunikation/konversationen/${konversationId}${auth_}`);
  socket.onmessage = () => { ladeVerlauf(); ladeThreads(); };
  socket.onerror = () => { /* best effort */ };
}

function trenneSocket() {
  if (socket) { socket.onmessage = null; socket.close(); socket = null; }
}

function fehler(e: unknown) {
  if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

const eigenes = (n: NachrichtView) => n.absenderTyp === 'MITARBEITER';
</script>

<template>
  <section>
    <NichtAbgestimmtBanner />
    <div class="flex items-center justify-between mb-4">
      <h2 class="text-xl font-bold">Nachrichten</h2>
      <UButton v-if="angemeldet" icon="i-lucide-square-pen" size="sm" @click="dialogOffen = true">
        Neuer Vorgang
      </UButton>
    </div>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a> (Rolle crm-pflege).
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
        :title="meldung.text" close class="mb-4" @update:open="meldung = null" />

      <div class="grid grid-cols-1 md:grid-cols-[22rem_1fr] gap-4 h-[36rem]">
        <!-- Vorgangs-Liste -->
        <div class="border border-default rounded-lg overflow-auto divide-y divide-default">
          <div v-if="!laden && threads.length === 0" class="p-4 text-sm text-dimmed">
            Keine Vorgänge vorhanden.
          </div>
          <button
            v-for="t in threads" :key="t.id" type="button"
            class="w-full text-left px-3 py-2.5 hover:bg-elevated"
            :class="aktiv?.id === t.id ? 'bg-elevated' : ''"
            @click="oeffne(t)"
          >
            <div class="flex items-center gap-2">
              <span v-if="t.ungelesen" class="w-2 h-2 rounded-full bg-primary-500 shrink-0" />
              <span class="font-semibold truncate" :class="t.ungelesen ? '' : 'text-muted'">{{ t.partner }}</span>
              <UBadge v-if="t.status === 'GESCHLOSSEN'" color="neutral" variant="soft" size="xs">zu</UBadge>
              <span class="ml-auto text-dimmed text-xs shrink-0">{{ zeit(t.letzteZeit) }}</span>
            </div>
            <div class="text-sm font-medium truncate">{{ t.betreff }}</div>
            <div class="text-xs text-dimmed truncate">{{ t.letzteVorschau }}</div>
          </button>
        </div>

        <!-- Verlauf -->
        <div class="border border-default rounded-lg flex flex-col min-h-0">
          <template v-if="aktiv">
            <div class="px-4 py-2.5 border-b border-default">
              <div class="font-semibold">{{ aktiv.betreff }}</div>
              <div class="text-xs text-dimmed">mit {{ aktiv.partner }}</div>
            </div>
            <div ref="verlaufBox" class="flex-1 overflow-auto px-4 py-3 flex flex-col gap-3 bg-muted/40">
              <div v-for="n in verlauf" :key="n.id" class="flex" :class="eigenes(n) ? 'justify-end' : 'justify-start'">
                <div class="max-w-[75%] rounded-lg px-3 py-2 text-sm"
                  :class="eigenes(n) ? 'bg-primary-500 text-white' : 'bg-default border border-default'">
                  <div class="flex items-center gap-2 mb-1">
                    <span class="text-xs font-semibold" :class="eigenes(n) ? 'text-white/90' : 'text-muted'">
                      {{ n.absender }}
                    </span>
                    <UBadge v-if="n.kiGeneriert" color="warning" variant="soft" size="xs" icon="i-lucide-bot">
                      KI-generiert
                    </UBadge>
                    <span class="text-xs" :class="eigenes(n) ? 'text-white/70' : 'text-dimmed'">{{ zeit(n.zeitpunkt) }}</span>
                  </div>
                  <div class="prose-sm" v-html="n.inhaltHtml" />
                </div>
              </div>
            </div>
            <div class="border-t border-default p-3 flex flex-col gap-2">
              <UAlert v-if="entwurfAktiv" color="warning" variant="soft" icon="i-lucide-bot"
                title="KI-Vorschlag — bitte prüfen und ggf. anpassen, bevor Sie senden." />
              <div class="flex items-end gap-2">
                <UButton icon="i-lucide-sparkles" color="neutral" variant="soft" :loading="entwurfLaeuft"
                  title="KI-Antwortvorschlag (HITL)" @click="coPilot">Co-Pilot</UButton>
                <UTextarea v-model="text" :rows="2" autoresize class="flex-1"
                  placeholder="Antwort schreiben …" @input="entwurfAktiv = false"
                  @keydown.enter.exact.prevent="senden" />
                <UButton icon="i-lucide-send" :loading="sende" :disabled="!text.trim()" @click="senden">
                  Senden
                </UButton>
              </div>
            </div>
          </template>
          <div v-else class="flex-1 flex items-center justify-center text-dimmed text-sm">
            Wählen Sie links einen Vorgang aus.
          </div>
        </div>
      </div>
    </template>

    <!-- Neuer Vorgang -->
    <UModal v-model:open="dialogOffen" title="Neuen Vorgang eröffnen">
      <template #body>
        <div class="flex flex-col gap-3">
          <UFormField label="Person-ID" help="Party-ID der Person (z. B. aus dem Kundenstamm).">
            <UInputNumber v-model="neu.personId" :min="1" class="w-full" />
          </UFormField>
          <UFormField label="Betreff">
            <UInput v-model="neu.betreff" class="w-full" placeholder="z. B. Rückfrage zu Ihrer Anmeldung" />
          </UFormField>
          <UFormField label="Nachricht">
            <UTextarea v-model="neu.text" :rows="4" class="w-full" />
          </UFormField>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="dialogOffen = false">Abbrechen</UButton>
          <UButton icon="i-lucide-send" :loading="sende" @click="vorgangEroeffnen">Vorgang eröffnen</UButton>
        </div>
      </template>
    </UModal>
  </section>
</template>
