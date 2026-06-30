<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { postCrmDublettenPruefung } from '@/api/endpoints/crm-resource/crm-resource';
import type { DublettenKandidat } from '@/api/model';
import { einschaetzungSchwere } from '@/dubletten';

// Live-Dublettenprüfung beim Anlegen (Plan A16): debounced Vorab-Check gegen den Bestand
// (Name/USt-IdNr.) + KI-/Regel-Bewertung; nicht-blockierender Hinweis mit „Verwenden statt neu".
// Wird nur im Anlege-Modus eingebunden (nicht beim Bearbeiten).
const props = defineProps<{
  art: 'person' | 'org';
  vorname?: string;
  nachname?: string;
  titel?: string;
  name?: string;
  ustId?: string;
}>();
const emit = defineEmits<{ (e: 'verwenden', id: number): void }>();

const kandidaten = ref<DublettenKandidat[]>([]);
const laeuft = ref(false);
let timer: ReturnType<typeof setTimeout> | undefined;

function genugEingabe(): boolean {
  if (props.art === 'person') {
    return (props.vorname?.trim().length ?? 0) >= 1 && (props.nachname?.trim().length ?? 0) >= 2;
  }
  return (props.name?.trim().length ?? 0) >= 2;
}

async function pruefe() {
  if (!genugEingabe()) { kandidaten.value = []; return; }
  laeuft.value = true;
  try {
    const res = await postCrmDublettenPruefung({
      art: props.art === 'org' ? 'ORGANISATION' : 'PERSON',
      vorname: props.vorname, nachname: props.nachname, titel: props.titel,
      name: props.name, ustId: props.ustId,
    });
    kandidaten.value = res ?? [];
  } catch {
    // Vorab-Check ist nur ein Hinweis — Fehler nicht an den Nutzer durchreichen, Anlage bleibt möglich.
    kandidaten.value = [];
  } finally {
    laeuft.value = false;
  }
}

watch(
  () => [props.vorname, props.nachname, props.titel, props.name, props.ustId],
  () => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(pruefe, 500);
  },
);
onBeforeUnmount(() => { if (timer) clearTimeout(timer); });
</script>

<template>
  <UAlert
    v-if="kandidaten.length"
    color="warning"
    variant="soft"
    icon="i-lucide-users"
    class="mb-4"
    :title="`${kandidaten.length} möglicher Bestandstreffer – evtl. Dublette`"
  >
    <template #description>
      <p class="text-xs mb-2">
        Prüfe, ob die Person/Firma bereits existiert. „Verwenden" öffnet den Bestandseintrag statt neu anzulegen.
      </p>
      <ul class="space-y-1.5">
        <li v-for="k in kandidaten" :key="k.id" class="flex items-center gap-2">
          <UBadge :color="einschaetzungSchwere[k.einschaetzung ?? ''] ?? 'neutral'" variant="soft" size="sm">
            {{ Math.round((k.aehnlichkeit ?? 0) * 100) }}%
          </UBadge>
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium truncate">
              {{ k.bezeichnung }}<span v-if="k.ort" class="text-muted"> · {{ k.ort }}</span>
            </div>
            <div class="text-xs text-muted truncate">{{ k.begruendung }}</div>
          </div>
          <UButton size="xs" variant="outline" icon="i-lucide-corner-up-right" @click="emit('verwenden', k.id!)">
            Verwenden
          </UButton>
        </li>
      </ul>
    </template>
  </UAlert>
</template>
