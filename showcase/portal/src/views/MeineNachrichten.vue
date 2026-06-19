<script setup lang="ts">
// Admin↔Person-Threads aus Personensicht (K2): der eingeloggte Kunde sieht seine Vorgänge (links),
// öffnet einen Thread (rechts), liest den Verlauf und antwortet. Neue Nachrichten kommen live über den
// Thread-WebSocket (/ws/kommunikation/konversationen/{id}) — der Inhalt wird über das autorisierte REST
// nachgeladen. KI-generierte Nachrichten sind als solche gekennzeichnet (EU-AI-Act Art. 50).
import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue';
import {
  partyLogin, meineKonversationen, threadNachrichten, threadAntworten, threadGelesen,
  ApiFehler, type KonversationView, type NachrichtView,
} from '@/portal';
import { auth, login } from '@/auth';

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const threads = ref<KonversationView[]>([]);
const aktiv = ref<KonversationView | null>(null);
const verlauf = ref<NachrichtView[]>([]);
const entwurf = ref('');
const sende = ref(false);
const verlaufBox = ref<HTMLElement | null>(null);
let socket: WebSocket | null = null;

const angemeldet = computed(() => auth.angemeldet);

onMounted(async () => {
  if (!auth.angemeldet) return;
  await partyLogin({ email: auth.email, anzeigeName: auth.name || auth.benutzer }).catch(() => {});
  await ladeThreads();
});

onBeforeUnmount(() => trenneSocket());

async function ladeThreads() {
  laden.value = true;
  try {
    threads.value = await meineKonversationen();
    if (aktiv.value) {
      aktiv.value = threads.value.find((t) => t.id === aktiv.value?.id) ?? aktiv.value;
    }
  } catch (e) {
    fehler(e);
  } finally {
    laden.value = false;
  }
}

async function oeffne(t: KonversationView) {
  aktiv.value = t;
  await ladeVerlauf();
  if (t.id != null) {
    await threadGelesen(t.id).catch(() => {});
    t.ungelesen = false;
    verbindeSocket(t.id);
  }
}

async function ladeVerlauf() {
  if (aktiv.value?.id == null) return;
  verlauf.value = await threadNachrichten(aktiv.value.id);
  await nextTick();
  if (verlaufBox.value) verlaufBox.value.scrollTop = verlaufBox.value.scrollHeight;
}

async function senden() {
  if (aktiv.value?.id == null || !entwurf.value.trim()) return;
  sende.value = true;
  try {
    await threadAntworten(aktiv.value.id, `<p>${escapeHtml(entwurf.value.trim())}</p>`);
    entwurf.value = '';
    await ladeVerlauf();
    await ladeThreads();
  } catch (e) {
    fehler(e);
  } finally {
    sende.value = false;
  }
}

// ── Live: Thread-WebSocket (nur ID-Signal → Verlauf neu laden) ──
function verbindeSocket(konversationId: number) {
  trenneSocket();
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  socket = new WebSocket(`${proto}://${location.host}/ws/kommunikation/konversationen/${konversationId}`);
  socket.onmessage = () => { ladeVerlauf(); ladeThreads(); };
  socket.onerror = () => { /* best effort — REST/Reload bleibt korrekt */ };
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

function zeit(iso?: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  // DACH-Strategie: konsequent in Europe/Berlin ausgeben (der Server erfasst bereits in Berlin).
  return isNaN(d.getTime()) ? iso
    : d.toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Berlin' });
}

const eigenes = (n: NachrichtView) => n.absenderTyp === 'PERSON';
</script>

<template>
  <section>
    <h2 class="text-xl font-bold mb-4">Meine Nachrichten</h2>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a>, um Ihre Nachrichten zu sehen.
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
        :title="meldung.text" close class="mb-4" @update:open="meldung = null" />

      <UAlert v-if="!laden && threads.length === 0" color="info" variant="soft"
        title="Sie haben noch keine Nachrichten vom EBZ." />

      <div v-else class="grid grid-cols-1 md:grid-cols-[20rem_1fr] gap-4 h-[34rem]">
        <!-- Thread-Liste -->
        <div class="border border-default rounded-lg overflow-auto divide-y divide-default">
          <button
            v-for="t in threads" :key="t.id" type="button"
            class="w-full text-left px-3 py-2.5 hover:bg-elevated"
            :class="aktiv?.id === t.id ? 'bg-elevated' : ''"
            @click="oeffne(t)"
          >
            <div class="flex items-center gap-2">
              <span v-if="t.ungelesen" class="w-2 h-2 rounded-full bg-primary-500 shrink-0" />
              <span class="font-semibold truncate" :class="t.ungelesen ? '' : 'text-muted'">{{ t.partner }}</span>
              <span class="ml-auto text-dimmed text-xs shrink-0">{{ zeit(t.letzteZeit) }}</span>
            </div>
            <div class="text-sm font-medium truncate">{{ t.betreff }}</div>
            <div class="text-xs text-dimmed truncate">{{ t.letzteVorschau }}</div>
          </button>
        </div>

        <!-- Thread-Verlauf -->
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
                  <!-- Inhalt ist serverseitig sanitisiert (KonversationService.sanitize). -->
                  <div class="prose-sm" v-html="n.inhaltHtml" />
                </div>
              </div>
            </div>
            <div class="border-t border-default p-3 flex items-end gap-2">
              <UTextarea v-model="entwurf" :rows="2" autoresize class="flex-1"
                placeholder="Antwort schreiben …" @keydown.enter.exact.prevent="senden" />
              <UButton icon="i-lucide-send" :loading="sende" :disabled="!entwurf.trim()" @click="senden">
                Senden
              </UButton>
            </div>
          </template>
          <div v-else class="flex-1 flex items-center justify-center text-dimmed text-sm">
            Wählen Sie links einen Vorgang aus.
          </div>
        </div>
      </div>
    </template>
  </section>
</template>
