<script setup lang="ts">
// Pflege EINES Bildungsangebots = ein Bildschirm aus gemeinsamem + typ-spezifischem Teil,
// EIN vee-validate-Formular über der generierten zod (toTypedSchema), EIN atomarer Save.
// Client-Validierung aus zod (Feld-Ebene); Cross-Field (gueltigBis≥gueltigAb) kommt server-seitig
// als 400 und wird via setErrors an die Felder gehängt.
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useForm } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import { useQueryClient } from '@tanstack/vue-query';
import StammdatenFelder from '@/components/StammdatenFelder.vue';
import TypSpezifischeFelder from '@/components/TypSpezifischeFelder.vue';
import { typen, leeresAngebot, violationsZuFehlern, type Typ, type AngebotDto } from '@/bildung';
import { login } from '@/auth';
import NichtAbgestimmtBanner from '@/components/NichtAbgestimmtBanner.vue';

const route = useRoute();
const router = useRouter();
const queryClient = useQueryClient();

const typ = route.params.typ as Typ;
const id = route.params.id ? Number(route.params.id) : null;
const config = typen[typ];

const { handleSubmit, setErrors, resetForm, isSubmitting } = useForm({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  validationSchema: toTypedSchema(config.schema as any),
  initialValues: leeresAngebot(typ),
});

const serverFehler = ref<string | null>(null);
const geladen = ref(id === null);

onMounted(async () => {
  if (id !== null) {
    const res = await config.byId(id);
    if (res.data) resetForm({ values: res.data as Record<string, unknown> });
    geladen.value = true;
  }
});

const speichern = handleSubmit(async (values) => {
  serverFehler.value = null;
  const body = values as unknown as AngebotDto;
  const res = id !== null ? await config.update(id, body) : await config.create(body);
  if (res.error || (res.response && !res.response.ok)) {
    const status = res.response?.status;
    if (status === 401) {
      login(); // nicht angemeldet → SSO-Redirect
      return;
    }
    if (status === 403) {
      serverFehler.value = 'Keine Berechtigung: Rolle „katalog-pflege" erforderlich.';
      return;
    }
    const fehler = violationsZuFehlern(res.error);
    if (Object.keys(fehler).length) setErrors(fehler);
    else serverFehler.value = `Speichern fehlgeschlagen (HTTP ${status ?? '?'}).`;
    return;
  }
  await queryClient.invalidateQueries({ queryKey: ['angebote'] });
  router.push('/angebote');
});
</script>

<template>
  <section v-if="geladen">
    <NichtAbgestimmtBanner />
    <h2 class="text-xl font-bold mb-4">{{ id ? 'Bearbeiten' : 'Neu' }}: {{ config.label }}</h2>

    <form @submit="speichern">
      <StammdatenFelder />
      <TypSpezifischeFelder :typ="typ" />

      <UAlert v-if="serverFehler" color="error" variant="soft" icon="i-lucide-alert-circle" :title="serverFehler" class="mb-4" />

      <div class="flex gap-3 mt-4">
        <UButton type="submit" icon="i-lucide-check" :loading="isSubmitting">Speichern</UButton>
        <UButton color="neutral" variant="ghost" type="button" @click="router.push('/angebote')">Abbrechen</UButton>
      </div>
    </form>
  </section>
  <section v-else>Lade…</section>
</template>
