<script setup lang="ts">
// Typ-spezifischer Teil der Pflege — rendert die Felder des gewählten Typs (der „spezielle Teil").
// Bindet an dasselbe vee-validate-Formular wie die Stammdaten → ein atomarer Save, ein DTO.
import { computed } from 'vue';
import AngebotFeld from './AngebotFeld.vue';
import { typen, type Typ } from '@/bildung';

const props = defineProps<{ typ: Typ }>();
const config = computed(() => typen[props.typ]);
</script>

<template>
  <fieldset class="block">
    <legend>{{ config.label }} – spezifische Felder</legend>
    <div class="grid">
      <AngebotFeld v-for="f in config.felder" :key="f.name" :field="f" />
    </div>
  </fieldset>
</template>

<style scoped>
.block {
  border: 1px solid var(--p-primary-color, #6366f1);
  border-radius: 8px;
  padding: 1rem 1.25rem;
  margin-bottom: 1.25rem;
}
legend {
  font-weight: 700;
  padding: 0 0.5rem;
  color: var(--p-primary-color, #6366f1);
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 1.5rem;
}
</style>
