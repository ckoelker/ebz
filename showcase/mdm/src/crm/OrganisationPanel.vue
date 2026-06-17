<script setup lang="ts">
import { ref, computed } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import { getCrmOrganisationenId } from '@/api/endpoints/crm-resource/crm-resource';
import type { OrgDetail } from '@/api/model';
import NeueFirmaDialog from '@/crm/dialoge/NeueFirmaDialog.vue';
import KontaktpunktDialog from '@/crm/dialoge/KontaktpunktDialog.vue';

const props = defineProps<{ id: number }>();
const emit = defineEmits<{ (e: 'select-person', id: number): void; (e: 'select-org', id: number): void }>();
const qc = useQueryClient();

const { data: org, isFetching } = useQuery({
  queryKey: computed(() => ['crm-org', props.id]),
  queryFn: async (): Promise<OrgDetail> => await getCrmOrganisationenId(props.id),
});
function reload() { qc.invalidateQueries({ queryKey: ['crm-org', props.id] }); }

const tab = ref('stammdaten');
const tabs = [
  { key: 'stammdaten', label: 'Stammdaten', icon: 'i-lucide-building-2' },
  { key: 'personen', label: 'Personen', icon: 'i-lucide-users' },
  { key: 'hierarchie', label: 'Hierarchie', icon: 'i-lucide-network' },
];
const editStamm = ref(false);
const kpDialog = ref(false);

const adresse = computed(() => {
  const o = org.value;
  if (!o) return '—';
  return [[o.strasse].filter(Boolean).join(' '), [o.plz, o.ort].filter(Boolean).join(' ')]
    .filter(Boolean).join(', ') || '—';
});
const aktiv = (bis?: string) => !bis;
</script>

<template>
  <div v-if="org" class="space-y-4">
    <UCard>
      <div class="flex items-start gap-4">
        <UAvatar :alt="org.name" size="lg" icon="i-lucide-building-2" />
        <div class="flex-1 min-w-0">
          <h2 class="text-xl font-bold text-highlighted">{{ org.name }}</h2>
          <p class="text-sm text-muted">{{ org.rechtsform }} <span v-if="org.ustId">· {{ org.ustId }}</span></p>
          <div class="flex flex-wrap gap-1.5 mt-2">
            <UBadge :color="org.status === 'AKTIV' ? 'success' : 'neutral'" variant="soft" size="sm">{{ org.status }}</UBadge>
            <UBadge v-if="org.ausbildungsbetrieb" color="info" variant="soft" size="sm">Ausbildungsbetrieb</UBadge>
            <UBadge v-for="t in org.unternehmenstypen" :key="t" color="neutral" variant="soft" size="sm">{{ t }}</UBadge>
          </div>
        </div>
        <UButton color="neutral" variant="ghost" icon="i-lucide-refresh-cw" :loading="isFetching" @click="reload" />
      </div>
    </UCard>

    <div class="flex gap-1 border-b border-default">
      <UButton v-for="t in tabs" :key="t.key" :color="tab === t.key ? 'primary' : 'neutral'"
               :variant="tab === t.key ? 'soft' : 'ghost'" :icon="t.icon" size="sm" @click="tab = t.key">
        {{ t.label }}
      </UButton>
    </div>

    <UCard v-if="tab === 'stammdaten'">
      <div class="flex justify-between items-center mb-3">
        <h3 class="font-semibold">Stammdaten</h3>
        <div class="flex gap-2">
          <UButton size="sm" variant="outline" icon="i-lucide-map-pin" @click="kpDialog = true">Adresse/Kanal</UButton>
          <UButton size="sm" icon="i-lucide-pencil" @click="editStamm = true">Bearbeiten</UButton>
        </div>
      </div>
      <dl class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <div><dt class="text-muted">Branche</dt><dd>{{ org.brancheCode || '—' }}</dd></div>
        <div><dt class="text-muted">Website</dt><dd>{{ org.website || '—' }}</dd></div>
        <div><dt class="text-muted">HRB</dt><dd>{{ org.handelsregisternummer || '—' }}</dd></div>
        <div><dt class="text-muted">Registergericht</dt><dd>{{ org.registergericht || '—' }}</dd></div>
        <div><dt class="text-muted">Bestandsgröße</dt><dd>{{ org.bestandsgroesse ?? '—' }}</dd></div>
        <div><dt class="text-muted">Gewerbeerlaubnis §34c</dt><dd>{{ org.gewerbeerlaubnis }}</dd></div>
        <div><dt class="text-muted">IHK</dt><dd>{{ org.ihkKammerCode || '—' }}</dd></div>
        <div><dt class="text-muted">Adresse</dt><dd>{{ adresse }}</dd></div>
        <div class="col-span-2"><dt class="text-muted">Schwerpunkte</dt><dd>{{ org.taetigkeitsschwerpunkte?.join(', ') || '—' }}</dd></div>
        <div class="col-span-2"><dt class="text-muted">Verbände</dt><dd>{{ org.verbaende?.join(', ') || '—' }}</dd></div>
      </dl>
    </UCard>

    <UCard v-else-if="tab === 'personen'">
      <h3 class="font-semibold mb-3">Verknüpfte Personen</h3>
      <p v-if="!org.mitglieder?.length" class="text-sm text-muted">Keine verknüpften Personen.</p>
      <ul class="divide-y divide-default">
        <li v-for="m in org.mitglieder" :key="m.mitgliedschaftId" class="flex items-center gap-3 py-2"
            :class="{ 'opacity-50': !aktiv(m.gueltigBis) }">
          <UButton color="primary" variant="link" class="px-0" @click="emit('select-person', m.personId!)">
            {{ m.person }}
          </UButton>
          <span class="text-sm text-muted">· {{ m.rolle }}</span>
          <div class="flex gap-1 ml-auto">
            <UBadge v-if="m.hauptansprechpartner" color="info" variant="soft" size="sm">Hauptansprechpartner</UBadge>
            <UBadge v-if="m.buchungsberechtigt" color="success" variant="soft" size="sm">buchungsberechtigt</UBadge>
            <UBadge v-if="!aktiv(m.gueltigBis)" color="neutral" variant="outline" size="sm">ehemalig</UBadge>
          </div>
        </li>
      </ul>
    </UCard>

    <UCard v-else-if="tab === 'hierarchie'">
      <h3 class="font-semibold mb-3">Firmenhierarchie</h3>
      <p class="text-sm">
        Übergeordnete Organisation:
        <template v-if="org.uebergeordneteId">
          <UButton color="primary" variant="link" class="px-0" @click="emit('select-org', org.uebergeordneteId)">
            #{{ org.uebergeordneteId }}
          </UButton>
        </template>
        <span v-else class="text-muted">— (keine)</span>
      </p>
    </UCard>

    <NeueFirmaDialog v-model:open="editStamm" :existing="org" @saved="reload" />
    <KontaktpunktDialog v-model:open="kpDialog" owner-type="organisation" :owner-id="id" :existing="null" @saved="reload" />
  </div>
</template>
