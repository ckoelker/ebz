<script setup lang="ts">
// Login-Bereich des Außenportals: der Firmen-Ansprechpartner sieht die Azubis seiner Organisation,
// meldet neue an (Status ANGEFRAGT) und bestätigt nach EBZ-Prüfung den Vertrag (→ AKTIV, abrechenbar).
// Der Aufrufer wird über den Token aufgelöst (party-Login claimt/aktiviert die Person).
import { ref, reactive, onMounted, computed } from 'vue';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import InputText from 'primevue/inputtext';
import InputNumber from 'primevue/inputnumber';
import Select from 'primevue/select';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import Message from 'primevue/message';
import {
  partyLogin, kontexte, firmensicht, azubiAnmelden, vertragBestaetigen, ApiFehler,
  type KontextView, type BuchungZeile,
} from '@/portal';
import { auth, login } from '@/auth';

const laden = ref(false);
const meldung = ref<{ text: string; severity: 'success' | 'error' } | null>(null);
const personId = ref<number | null>(null);
const firmen = ref<KontextView[]>([]);
const orgId = ref<number | null>(null);
const zeilen = ref<BuchungScreenZeile[]>([]);
const aktiv = ref<number | null>(null);

type BuchungScreenZeile = BuchungZeile;

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
      zimmerart: neu.zimmerart,
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

async function bestaetigen(z: BuchungScreenZeile) {
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
  if (e instanceof ApiFehler && e.status === 401) return login();
  meldung.value = { text: (e as Error).message, severity: 'error' };
}

const statusSchwere: Record<string, 'warn' | 'info' | 'success' | 'secondary'> = {
  ANGEFRAGT: 'warn',
  BESTAETIGT_EBZ: 'info',
  AKTIV: 'success',
};
</script>

<template>
  <section>
    <h2>Meine Azubis</h2>

    <Message v-if="!angemeldet" severity="warn">
      Bitte <a href="#" @click.prevent="login">anmelden</a>, um die Azubis Ihres Betriebs zu sehen und anzumelden.
    </Message>

    <template v-else>
      <Message v-if="meldung" :severity="meldung.severity" closable @close="meldung = null">{{ meldung.text }}</Message>

      <Message v-if="!laden && firmen.length === 0" severity="info">
        Ihrem Login ist noch kein buchungsberechtigter Betrieb zugeordnet. Bitte wenden Sie sich an den EBZ.
      </Message>

      <template v-else>
        <div class="kopf">
          <label v-if="firmen.length > 1" class="orgwahl">
            Betrieb
            <Select v-model="orgId" :options="firmen" optionLabel="bezeichnung" optionValue="organisationId"
              @change="ladeFirmensicht" />
          </label>
          <span v-else-if="firmen[0]" class="orgname">{{ firmen[0].bezeichnung }}</span>
        </div>

        <h3>Neuen Azubi anmelden</h3>
        <form class="formular" @submit.prevent="anmelden">
          <div class="zeile">
            <label>Name<InputText v-model="neu.azubiName" /></label>
            <label>E-Mail<InputText v-model="neu.azubiEmail" type="email" /></label>
          </div>
          <div class="zeile">
            <label>Schuljahr<InputText v-model="neu.schuljahr" placeholder="2025/2026" /></label>
            <label class="schmal">Halbjahr<Select v-model="neu.halbjahr" :options="[1, 2]" /></label>
            <label>Zimmer<Select v-model="neu.zimmerart" :options="['KEINE', 'DOPPEL', 'EINZEL']" /></label>
          </div>
          <div class="zeile">
            <label>Unterricht (Cent)<InputNumber v-model="neu.unterrichtBetragCent" :min="0" /></label>
            <label v-if="neu.zimmerart !== 'KEINE'">Übernachtung (Cent)<InputNumber v-model="neu.uebernachtungBetragCent" :min="0" /></label>
          </div>
          <div class="aktionen">
            <Button type="submit" label="Azubi anmelden" icon="pi pi-user-plus" :loading="aktiv === -1" />
          </div>
        </form>

        <h3>Angemeldete Azubis</h3>
        <DataTable :value="zeilen" dataKey="anmeldungId" :loading="laden" stripedRows size="small">
          <Column field="teilnehmerName" header="Azubi" sortable />
          <Column field="schuljahr" header="Schuljahr" sortable />
          <Column field="halbjahr" header="HJ" style="width: 4rem" />
          <Column header="Status" style="width: 11rem">
            <template #body="{ data: z }"><Tag :value="z.status" :severity="statusSchwere[z.status] ?? 'secondary'" /></template>
          </Column>
          <Column header="" style="width: 12rem">
            <template #body="{ data: z }">
              <Button v-if="z.status === 'BESTAETIGT_EBZ'" label="Vertrag bestätigen" size="small" icon="pi pi-file-check"
                :loading="aktiv === z.anmeldungId" @click="bestaetigen(z)" />
              <span v-else-if="z.status === 'AKTIV'" class="aktiv"><i class="pi pi-check-circle" /> aktiv</span>
              <span v-else class="wartet">wartet auf EBZ</span>
            </template>
          </Column>
          <template #empty><div class="leer">Noch keine Azubis angemeldet.</div></template>
        </DataTable>
      </template>
    </template>
  </section>
</template>

<style scoped>
.kopf {
  margin: 0.5rem 0;
}
.orgwahl {
  display: inline-flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
  color: #444;
}
.orgname {
  font-weight: 700;
}
.formular {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-width: 620px;
}
.zeile {
  display: flex;
  gap: 0.75rem;
}
.zeile label {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
  color: #444;
}
.zeile .schmal {
  max-width: 6rem;
}
.aktionen {
  margin-top: 0.25rem;
}
.aktiv {
  color: #16a34a;
}
.wartet {
  color: #999;
  font-size: 0.9rem;
}
.leer {
  padding: 1rem;
  color: #888;
}
</style>
