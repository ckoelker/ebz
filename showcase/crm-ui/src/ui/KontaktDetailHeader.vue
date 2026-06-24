<script setup lang="ts">
import PartyAvatar from './PartyAvatar.vue';
import StatusBadges from './StatusBadges.vue';

// Detail-Kopf für Person ODER Firma: Avatar, Titelzeile, Status-/Sperr-Badges, optionale
// abgeleitete Briefanrede (Person) und Meta-Zeile. Prop-rein → in Storybook UND mdm nutzbar.
// Slots: #badges (app-spezifische Zusatz-Badges, z. B. Lösch-Status/Unternehmenstyp), #actions.
defineProps<{
  title: string;
  org?: boolean;
  status?: string;
  werbesperre?: boolean;
  auskunftssperre?: boolean;
  unvollstaendig?: boolean;
  anrede?: string;
  meta?: string[];
}>();
</script>

<template>
  <div class="flex items-start gap-4">
    <PartyAvatar :name="title" :org="org" size="lg" />
    <div class="min-w-0">
      <div class="flex items-center gap-2 flex-wrap">
        <h2 class="text-xl font-bold text-highlighted">{{ title }}</h2>
        <StatusBadges
          :status="status"
          :werbesperre="werbesperre"
          :auskunftssperre="auskunftssperre"
          :unvollstaendig="unvollstaendig"
        />
        <slot name="badges" />
      </div>
      <div v-if="meta?.length" class="text-sm text-muted mt-1 flex gap-2 flex-wrap">
        <span v-for="m in meta" :key="m">{{ m }}</span>
      </div>
      <div v-if="anrede" class="text-sm text-dimmed mt-1 italic">Briefanrede: „{{ anrede }} …"</div>
    </div>
    <div class="ml-auto flex gap-2 shrink-0">
      <slot name="actions" />
    </div>
  </div>
</template>
