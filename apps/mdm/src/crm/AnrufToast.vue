<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { postCrmCtiSimuliereAnruf } from '@/api/endpoints/crm-resource/crm-resource';

// CTI (Plan A13), anbieter-neutral: lauscht am WebSocket /ws/crm/cti auf eingehende Anrufe und zeigt
// einen „Anruf von …"-Toast mit Sprung in den passenden Kontakt. Zusätzlich ein Showcase-Auslöser
// „Anruf simulieren" (postet an /crm/cti/simuliere-anruf → Backend matcht + broadcastet an alle Cockpits).
type AnrufEvent = {
  nummerE164: string; bekannt: boolean; personId?: number | null; personName?: string | null;
  organisationId?: number | null; organisationName?: string | null; zeitpunkt?: string;
};

const router = useRouter();
const anruf = ref<AnrufEvent | null>(null);
const simOffen = ref(false);
const simNummer = ref('+49231555012');
const simLaeuft = ref(false);
let ws: WebSocket | null = null;
let reconnect: ReturnType<typeof setTimeout> | null = null;

function verbinde() {
  const url = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/crm/cti`;
  ws = new WebSocket(url);
  ws.onmessage = (ev) => {
    try {
      anruf.value = JSON.parse(ev.data) as AnrufEvent;
    } catch { /* ignore */ }
  };
  // Verbindung halten: bei Abbruch nach kurzer Pause neu aufbauen.
  ws.onclose = () => { reconnect = setTimeout(verbinde, 3000); };
  ws.onerror = () => ws?.close();
}

async function simuliere() {
  if (!simNummer.value.trim()) return;
  simLaeuft.value = true;
  try {
    await postCrmCtiSimuliereAnruf({ nummerE164: simNummer.value, richtung: 'EINGEHEND' });
    simOffen.value = false;
  } catch { /* Fehler ignorieren (Showcase) */ } finally {
    simLaeuft.value = false;
  }
}

function oeffneKontakt() {
  const a = anruf.value;
  if (!a) return;
  if (a.personId) router.push({ path: '/', query: { person: String(a.personId) } });
  else if (a.organisationId) router.push({ path: '/', query: { org: String(a.organisationId) } });
  anruf.value = null;
}

onMounted(verbinde);
onBeforeUnmount(() => {
  if (reconnect) clearTimeout(reconnect);
  if (ws) { ws.onclose = null; ws.close(); }
});
</script>

<template>
  <!-- Auslöser in der Topbar -->
  <UPopover v-model:open="simOffen">
    <UButton color="neutral" variant="ghost" size="sm" icon="i-lucide-phone-call"
             class="text-white hover:bg-white/10" title="Anruf simulieren (CTI-Showcase)" />
    <template #content>
      <div class="p-3 w-72 space-y-2">
        <p class="text-sm font-medium">Eingehenden Anruf simulieren</p>
        <p class="text-xs text-muted">E.164-Nummer eines Kontaktpunkts — wird gematcht und an alle Cockpits gesendet.</p>
        <UInput v-model="simNummer" placeholder="+49231555012" class="w-full" />
        <UButton block size="sm" icon="i-lucide-phone-call" :loading="simLaeuft" @click="simuliere">
          Anruf auslösen
        </UButton>
      </div>
    </template>
  </UPopover>

  <!-- Eingehender-Anruf-Toast (fix unten rechts) -->
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="anruf" class="fixed bottom-5 right-5 z-50 w-80 rounded-xl border border-default bg-default shadow-xl p-4">
        <div class="flex items-start gap-3">
          <div class="shrink-0 size-10 rounded-full grid place-items-center"
               :class="anruf.bekannt ? 'bg-primary-100 text-primary-600' : 'bg-elevated text-dimmed'">
            <UIcon name="i-lucide-phone-incoming" class="size-5" />
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-xs text-muted">Eingehender Anruf</p>
            <p class="text-sm font-semibold truncate">
              {{ anruf.bekannt ? (anruf.personName || anruf.organisationName) : 'Unbekannte Nummer' }}
            </p>
            <p v-if="anruf.bekannt && anruf.personName && anruf.organisationName" class="text-xs text-muted truncate">
              {{ anruf.organisationName }}
            </p>
            <p class="text-xs text-dimmed mt-0.5">{{ anruf.nummerE164 }}</p>
          </div>
          <UButton color="neutral" variant="ghost" size="xs" icon="i-lucide-x" @click="anruf = null" />
        </div>
        <div class="flex gap-2 mt-3">
          <UButton v-if="anruf.bekannt" color="primary" size="sm" icon="i-lucide-user-round"
                   class="flex-1" @click="oeffneKontakt">Kontakt öffnen</UButton>
          <UButton v-else color="neutral" variant="outline" size="sm" class="flex-1" disabled>
            Kein Treffer im Bestand
          </UButton>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity .2s, transform .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(8px); }
</style>
