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
  <fieldset class="rounded-lg border border-primary p-4 px-5 mb-5">
    <legend class="font-bold px-2 text-primary">{{ config.label }} – spezifische Felder</legend>
    <div class="grid grid-cols-2 gap-x-6">
      <AngebotFeld v-for="f in config.felder" :key="f.name" :field="f" />
    </div>
  </fieldset>
</template>
