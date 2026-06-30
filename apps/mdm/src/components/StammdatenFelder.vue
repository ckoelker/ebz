<script setup lang="ts">
// Gemeinsame Stammdaten — EINE Komponente, in allen vier Typ-Masken wiederverwendet
// (genau der „allgemeine Teil" der Pflege). Bindet an dasselbe vee-validate-Formular.
import AngebotFeld from './AngebotFeld.vue';
import { gemeinsameFelder } from '@/bildung';
</script>

<template>
  <fieldset class="rounded-lg border border-default p-4 px-5 mb-5">
    <legend class="font-bold px-2">Stammdaten (alle Bildungsangebote)</legend>
    <div class="grid grid-cols-2 gap-x-6">
      <AngebotFeld v-for="f in gemeinsameFelder" :key="f.name" :field="f" />
    </div>
  </fieldset>
</template>
