<script setup lang="ts">
// Person→Gruppe (K3, Sachbearbeiter-Sicht): Verteiler pflegen (manuell oder als Organisations-Kreis) und
// einen Broadcast an alle (zum Sendezeitpunkt aufgelösten) Mitglieder senden. Die Consent-/Werbesperre-
// Durchsetzung passiert serverseitig (E-Mail nur ohne Sperre, Portal-Postfach immer) — pro Mitglied ein
// GRUPPEN_INFO-Ereignis im Aktivitätslog. Löschen wird vorher bestätigt.
import { ref, onMounted, computed } from 'vue';
import {
  gruppen as ladeGruppen, gruppeAnlegen, gruppeLoeschen, mitgliedHinzu,
  broadcast as sendeBroadcast, ApiFehler, type GruppeView,
} from '@/kommunikation';
import { auth, login } from '@/auth';
import NichtAbgestimmtBanner from '@/components/NichtAbgestimmtBanner.vue';

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const liste = ref<GruppeView[]>([]);
const aktiv = ref<GruppeView | null>(null);
const busy = ref(false);

// Anlegen
const dialogOffen = ref(false);
const neu = ref({ name: '', beschreibung: '', quelle: 'MANUELL', organisationId: undefined as number | undefined });
const quellen = [{ label: 'Manuell', value: 'MANUELL' }, { label: 'Organisations-Kreis', value: 'ORGANISATION' }];

// Mitglied hinzufügen
const neuesMitglied = ref<number | undefined>(undefined);

// Broadcast
const nachricht = ref('');

// Löschen bestätigen
const loeschDialog = ref(false);
const zuLoeschen = ref<GruppeView | null>(null);

const angemeldet = computed(() => auth.angemeldet);

onMounted(async () => { if (auth.angemeldet) await neuLaden(); });

async function neuLaden() {
  laden.value = true;
  try {
    liste.value = await ladeGruppen();
    if (aktiv.value) aktiv.value = liste.value.find((g) => g.id === aktiv.value?.id) ?? null;
  } catch (e) {
    fehler(e);
  } finally {
    laden.value = false;
  }
}

async function anlegen() {
  if (!neu.value.name.trim()) { meldung.value = { text: 'Bitte einen Namen angeben.', severity: 'error' }; return; }
  if (neu.value.quelle === 'ORGANISATION' && !neu.value.organisationId) {
    meldung.value = { text: 'Bitte die Organisations-ID angeben.', severity: 'error' }; return;
  }
  busy.value = true;
  try {
    await gruppeAnlegen({
      name: neu.value.name.trim(),
      beschreibung: neu.value.beschreibung.trim() || undefined,
      quelle: neu.value.quelle,
      organisationId: neu.value.quelle === 'ORGANISATION' ? neu.value.organisationId : undefined,
    });
    dialogOffen.value = false;
    neu.value = { name: '', beschreibung: '', quelle: 'MANUELL', organisationId: undefined };
    await neuLaden();
  } catch (e) { fehler(e); } finally { busy.value = false; }
}

async function mitgliedAdd() {
  if (aktiv.value?.id == null || !neuesMitglied.value) return;
  busy.value = true;
  try {
    await mitgliedHinzu(aktiv.value.id, neuesMitglied.value);
    neuesMitglied.value = undefined;
    await neuLaden();
  } catch (e) { fehler(e); } finally { busy.value = false; }
}

async function broadcasten() {
  if (aktiv.value?.id == null || !nachricht.value.trim()) return;
  busy.value = true;
  try {
    const erreicht = await sendeBroadcast(aktiv.value.id, nachricht.value.trim());
    nachricht.value = '';
    meldung.value = { text: `Broadcast gesendet — ${erreicht} Empfänger erreicht.`, severity: 'success' };
  } catch (e) { fehler(e); } finally { busy.value = false; }
}

function loeschenFragen(g: GruppeView) { zuLoeschen.value = g; loeschDialog.value = true; }

async function loeschenBestaetigt() {
  if (zuLoeschen.value?.id == null) return;
  busy.value = true;
  try {
    await gruppeLoeschen(zuLoeschen.value.id);
    if (aktiv.value?.id === zuLoeschen.value.id) aktiv.value = null;
    loeschDialog.value = false;
    zuLoeschen.value = null;
    await neuLaden();
  } catch (e) { fehler(e); } finally { busy.value = false; }
}

function fehler(e: unknown) {
  if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}

const istManuell = computed(() => aktiv.value?.quelle === 'MANUELL');
</script>

<template>
  <section>
    <NichtAbgestimmtBanner />
    <div class="flex items-center justify-between mb-4">
      <h2 class="text-xl font-bold">Verteiler &amp; Broadcast</h2>
      <UButton v-if="angemeldet" icon="i-lucide-plus" size="sm" @click="dialogOffen = true">Neuer Verteiler</UButton>
    </div>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a> (Rolle crm-pflege).
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
        :title="meldung.text" close class="mb-4" @update:open="meldung = null" />

      <div class="grid grid-cols-1 md:grid-cols-[22rem_1fr] gap-4">
        <!-- Verteiler-Liste -->
        <div class="border border-default rounded-lg overflow-auto divide-y divide-default">
          <div v-if="!laden && liste.length === 0" class="p-4 text-sm text-dimmed">Noch keine Verteiler.</div>
          <button v-for="g in liste" :key="g.id" type="button"
            class="w-full text-left px-3 py-2.5 hover:bg-elevated" :class="aktiv?.id === g.id ? 'bg-elevated' : ''"
            @click="aktiv = g">
            <div class="flex items-center gap-2">
              <span class="font-semibold truncate">{{ g.name }}</span>
              <UBadge :color="g.quelle === 'ORGANISATION' ? 'info' : 'neutral'" variant="soft" size="xs">
                {{ g.quelle === 'ORGANISATION' ? 'Org' : 'manuell' }}
              </UBadge>
              <span class="ml-auto text-dimmed text-xs shrink-0">{{ g.anzahl }} Mitgl.</span>
            </div>
            <div v-if="g.beschreibung" class="text-xs text-dimmed truncate">{{ g.beschreibung }}</div>
          </button>
        </div>

        <!-- Detail -->
        <div class="border border-default rounded-lg p-4">
          <template v-if="aktiv">
            <div class="flex items-start justify-between mb-4">
              <div>
                <div class="font-semibold text-lg">{{ aktiv.name }}</div>
                <div class="text-sm text-dimmed">
                  {{ aktiv.quelle === 'ORGANISATION' ? 'Organisations-Kreis (dynamisch)' : 'Manueller Verteiler' }}
                  · {{ aktiv.anzahl }} Empfänger
                </div>
              </div>
              <UButton color="error" variant="ghost" size="sm" icon="i-lucide-trash-2"
                @click="loeschenFragen(aktiv)">Löschen</UButton>
            </div>

            <!-- Mitglieder (nur manuell) -->
            <div v-if="istManuell" class="mb-5">
              <h3 class="font-semibold text-sm mb-2">Mitglied hinzufügen</h3>
              <div class="flex items-end gap-2">
                <UFormField label="Person-ID" class="w-48">
                  <UInputNumber v-model="neuesMitglied" :min="1" class="w-full" />
                </UFormField>
                <UButton icon="i-lucide-user-plus" :loading="busy" :disabled="!neuesMitglied" @click="mitgliedAdd">
                  Hinzufügen
                </UButton>
              </div>
            </div>
            <UAlert v-else color="info" variant="soft" class="mb-5"
              title="Organisations-Kreis: die Mitglieder werden zum Sendezeitpunkt aus den Mitgliedschaften aufgelöst." />

            <!-- Broadcast -->
            <h3 class="font-semibold text-sm mb-2">Broadcast senden</h3>
            <UTextarea v-model="nachricht" :rows="3" class="w-full mb-2" :maxlength="200"
              placeholder="Nachricht an alle Mitglieder (max. 200 Zeichen) …" />
            <div class="flex items-center gap-3">
              <UButton icon="i-lucide-megaphone" :loading="busy" :disabled="!nachricht.trim() || aktiv.anzahl === 0"
                @click="broadcasten">An {{ aktiv.anzahl }} Empfänger senden</UButton>
              <span class="text-xs text-dimmed">
                E-Mail nur ohne Werbe-/Auskunftssperre; das Portal-Postfach bekommt jeder.
              </span>
            </div>
          </template>
          <div v-else class="text-dimmed text-sm py-10 text-center">Wählen Sie links einen Verteiler.</div>
        </div>
      </div>
    </template>

    <!-- Verteiler anlegen -->
    <UModal v-model:open="dialogOffen" title="Neuen Verteiler anlegen">
      <template #body>
        <div class="flex flex-col gap-3">
          <UFormField label="Name"><UInput v-model="neu.name" class="w-full" /></UFormField>
          <UFormField label="Beschreibung"><UInput v-model="neu.beschreibung" class="w-full" /></UFormField>
          <UFormField label="Art"><USelect v-model="neu.quelle" :items="quellen" class="w-full" /></UFormField>
          <UFormField v-if="neu.quelle === 'ORGANISATION'" label="Organisations-ID"
            help="Alle Personen mit Mitgliedschaft in dieser Organisation.">
            <UInputNumber v-model="neu.organisationId" :min="1" class="w-full" />
          </UFormField>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="dialogOffen = false">Abbrechen</UButton>
          <UButton icon="i-lucide-check" :loading="busy" @click="anlegen">Anlegen</UButton>
        </div>
      </template>
    </UModal>

    <!-- Löschen bestätigen -->
    <UModal v-model:open="loeschDialog" title="Verteiler löschen?">
      <template #body>
        <p class="text-sm">Soll der Verteiler „{{ zuLoeschen?.name }}" wirklich gelöscht werden?</p>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2 w-full">
          <UButton color="neutral" variant="ghost" @click="loeschDialog = false">Abbrechen</UButton>
          <UButton color="error" icon="i-lucide-trash-2" :loading="busy" @click="loeschenBestaetigt">Löschen</UButton>
        </div>
      </template>
    </UModal>
  </section>
</template>
