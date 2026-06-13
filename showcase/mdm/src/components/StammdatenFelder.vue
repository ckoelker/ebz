<script setup lang="ts">
// Gemeinsame Stammdaten — EINE Komponente, in allen vier Typ-Masken wiederverwendet
// (genau der „allgemeine Teil" der Pflege). Bindet an dasselbe vee-validate-Formular.
import AngebotFeld from './AngebotFeld.vue';
import { gemeinsameFelder } from '@/bildung';
</script>

<template>
  <fieldset class="block">
    <legend>Stammdaten (alle Bildungsangebote)</legend>
    <div class="grid">
      <AngebotFeld v-for="f in gemeinsameFelder" :key="f.name" :field="f" />
    </div>
  </fieldset>
</template>

<style scoped>
.block {
  border: 1px solid var(--p-content-border-color, #ddd);
  border-radius: 8px;
  padding: 1rem 1.25rem;
  margin-bottom: 1.25rem;
}
legend {
  font-weight: 700;
  padding: 0 0.5rem;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 1.5rem;
}
</style>
