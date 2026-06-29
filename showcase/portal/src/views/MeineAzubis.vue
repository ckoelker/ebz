<script setup lang="ts">
// Login-Bereich des Außenportals: der Firmen-Ansprechpartner sieht die Azubis seiner Organisation,
// meldet neue an (Status ANGEFRAGT) und bestätigt nach EBZ-Prüfung den Vertrag (→ AKTIV, abrechenbar).
// Der Aufrufer wird über den Token aufgelöst (party-Login claimt/aktiviert die Person).
import { ref, reactive, onMounted, computed } from 'vue';
import type { TableColumn } from '@nuxt/ui';
import {
  partyLogin, kontexte, firmensicht, azubiAnmelden, vertragBestaetigen, ApiFehler,
  type KontextView, type BuchungZeile,
} from '@/portal';
import type { Zimmerart } from '@/api/model';
import { auth, login } from '@/auth';
import ListenTabelle from '@ui-base/ui/ListenTabelle.vue';
import StatusBadge from '@customer-ui/ui/StatusBadge.vue';
import FormFeld from '@ui-base/ui/FormFeld.vue';

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const personId = ref<number | null>(null);
const firmen = ref<KontextView[]>([]);
const orgId = ref<number | null>(null);
const zeilen = ref<BuchungZeile[]>([]);
const aktiv = ref<number | null>(null);

const neu = reactive({
  azubiEmail: '',
  azubiName: '',
  schuljahr: '',
  halbjahr: 1,
  zimmerart: 'KEINE' as 'KEINE' | 'DOPPEL' | 'EINZEL',
  unterrichtBetragCent: 150000,
  uebernachtungBetragCent: 0,
});

const angemeldet = computed(() => auth.angemeldet);
const firmenItems = computed(() =>
  firmen.value.map((f) => ({ label: f.bezeichnung ?? `Organisation ${f.organisationId}`, value: f.organisationId })));

onMounted(async () => {
  if (!auth.angemeldet) return;
  laden.value = true;
  try {
    const person = await partyLogin({ email: auth.email, anzeigeName: auth.name || auth.benutzer });
    personId.value = person.id ?? null;
    const ctx = await kontexte(personId.value!);
    firmen.value = ctx.filter((k) => k.art === 'FIRMA');
    orgId.value = firmen.value[0]?.organisationId ?? null;
    await ladeFirmensicht();
  } catch (e) {
    fehler(e);
  } finally {
    laden.value = false;
  }
});

async function ladeFirmensicht() {
  if (!orgId.value) {
    zeilen.value = [];
    return;
  }
  zeilen.value = await firmensicht(orgId.value);
}

async function anmelden() {
  if (!orgId.value) return;
  meldung.value = null;
  if (!neu.azubiName.trim() || !/.+@.+\..+/.test(neu.azubiEmail) || !neu.schuljahr.trim()) {
    meldung.value = { text: 'Bitte Name, gültige E-Mail und Schuljahr angeben.', severity: 'error' };
    return;
  }
  aktiv.value = -1;
  try {
    await azubiAnmelden({
      organisationId: orgId.value,
      azubiEmail: neu.azubiEmail,
      azubiName: neu.azubiName,
      schuljahr: neu.schuljahr,
      halbjahr: neu.halbjahr,
      zimmerart: neu.zimmerart as Zimmerart,
      unterrichtBetragCent: neu.unterrichtBetragCent,
      uebernachtungBetragCent: neu.zimmerart === 'KEINE' ? undefined : neu.uebernachtungBetragCent,
    });
    meldung.value = { text: `„${neu.azubiName}" angemeldet (Status: angefragt).`, severity: 'success' };
    neu.azubiEmail = '';
    neu.azubiName = '';
    await ladeFirmensicht();
  } catch (e) {
    fehler(e);
  } finally {
    aktiv.value = null;
  }
}

async function bestaetigen(z: BuchungZeile) {
  if (!z.anmeldungId) return;
  meldung.value = null;
  aktiv.value = z.anmeldungId;
  try {
    await vertragBestaetigen(z.anmeldungId);
    meldung.value = { text: `Vertrag für „${z.teilnehmerName}" bestätigt — jetzt aktiv.`, severity: 'success' };
    await ladeFirmensicht();
  } catch (e) {
    fehler(e);
  } finally {
    aktiv.value = null;
  }
}

function fehler(e: unknown) {
  if (e instanceof ApiFehler && e.status === 401 && !auth.angemeldet) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}

const halbjahrItems = [{ label: '1', value: 1 }, { label: '2', value: 2 }];
const zimmerItems = ['KEINE', 'DOPPEL', 'EINZEL'];

const columns: TableColumn<BuchungZeile>[] = [
  { accessorKey: 'teilnehmerName', header: 'Azubi' },
  { accessorKey: 'schuljahr', header: 'Schuljahr' },
  { accessorKey: 'halbjahr', header: 'HJ' },
  { accessorKey: 'status', header: 'Status' },
  { id: 'aktion', header: '' },
];
</script>

<template>
  <section>
    <h2 class="text-xl font-bold mb-4">Meine Azubis</h2>

    <UAlert v-if="!angemeldet" color="warning" variant="soft" title="Anmeldung erforderlich">
      <template #description>
        Bitte <a href="#" class="underline" @click.prevent="login">anmelden</a>, um die Azubis Ihres Betriebs
        zu sehen und anzumelden.
      </template>
    </UAlert>

    <template v-else>
      <UAlert v-if="meldung" :color="meldung.severity === 'success' ? 'success' : 'error'" variant="soft"
        :title="meldung.text" close class="mb-4" @update:open="meldung = null" />

      <UAlert v-if="!laden && firmen.length === 0" color="info" variant="soft"
        title="Ihrem Login ist noch kein buchungsberechtigter Betrieb zugeordnet. Bitte wenden Sie sich an den EBZ." />

      <template v-else>
        <div class="mb-4">
          <FormFeld v-if="firmen.length > 1" label="Betrieb">
            <USelect v-model="orgId" :items="firmenItems" class="w-72" @update:model-value="ladeFirmensicht" />
          </FormFeld>
          <span v-else-if="firmen[0]" class="font-bold">{{ firmen[0].bezeichnung }}</span>
        </div>

        <h3 class="font-semibold mb-2">Neuen Azubi anmelden</h3>
        <form class="flex flex-col gap-3 max-w-2xl mb-6" @submit.prevent="anmelden">
          <div class="flex gap-3">
            <FormFeld label="Name" class="flex-1"><UInput v-model="neu.azubiName" class="w-full" /></FormFeld>
            <FormFeld label="E-Mail" class="flex-1"><UInput v-model="neu.azubiEmail" type="email" class="w-full" /></FormFeld>
          </div>
          <div class="flex gap-3">
            <FormFeld label="Schuljahr" class="flex-1"><UInput v-model="neu.schuljahr" placeholder="2025/2026" class="w-full" /></FormFeld>
            <FormFeld label="Halbjahr" class="w-24"><USelect v-model="neu.halbjahr" :items="halbjahrItems" class="w-full" /></FormFeld>
            <FormFeld label="Zimmer" class="w-32"><USelect v-model="neu.zimmerart" :items="zimmerItems" class="w-full" /></FormFeld>
          </div>
          <div class="flex gap-3">
            <FormFeld label="Unterricht (Cent)" class="flex-1"><UInputNumber v-model="neu.unterrichtBetragCent" :min="0" class="w-full" /></FormFeld>
            <FormFeld v-if="neu.zimmerart !== 'KEINE'" label="Übernachtung (Cent)" class="flex-1">
              <UInputNumber v-model="neu.uebernachtungBetragCent" :min="0" class="w-full" />
            </FormFeld>
          </div>
          <div>
            <UButton type="submit" icon="i-lucide-user-plus" :loading="aktiv === -1">Azubi anmelden</UButton>
          </div>
        </form>

        <h3 class="font-semibold mb-2">Angemeldete Azubis</h3>
        <ListenTabelle :data="zeilen" :columns="columns" :loading="laden" :empty="'Noch keine Azubis angemeldet.'">
          <template #status-cell="{ row }">
            <StatusBadge art="azubi" :status="row.original.status" />
          </template>
          <template #aktion-cell="{ row }">
            <UButton v-if="row.original.status === 'BESTAETIGT_EBZ'" size="sm" icon="i-lucide-file-check"
              :loading="aktiv === row.original.anmeldungId" @click="bestaetigen(row.original)">Vertrag bestätigen</UButton>
            <span v-else-if="row.original.status === 'AKTIV'" class="inline-flex items-center gap-1 text-success">
              <UIcon name="i-lucide-check-circle" /> aktiv
            </span>
            <span v-else class="text-dimmed text-sm">wartet auf EBZ</span>
          </template>
        </ListenTabelle>
      </template>
    </template>
  </section>
</template>
