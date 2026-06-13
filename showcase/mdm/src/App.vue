<script setup lang="ts">
// P1.0-Landing — Beleg, dass das Cockpit-Gerüst (Vue + PrimeVue) lädt. Die Verwaltungsmaske
// „Bildungsangebote" (Liste/Detail/Edit aus generierter zod) baut P1.2 auf diesem Gerüst auf.
import Card from 'primevue/card';

const stack = [
  'Quelle: /q/openapi (Bean Validation, eine Wahrheit)',
  '→ @hey-api/openapi-ts: TS-Typen + Fetch-Client + zod',
  '→ vee-validate + @vee-validate/zod (P1.2)',
  '→ PrimeVue 4 Masken (P1.2)',
];
</script>

<template>
  <main style="max-width: 760px; margin: 3rem auto; font-family: system-ui, sans-serif">
    <h1>EBZ MDM — Bildungsangebote</h1>
    <p>Formularverwaltung-Showcase · PoC P1.0 · Stack B (typsicher, spec-getestet, generiert).</p>
    <Card>
      <template #title>Generator-Spine</template>
      <template #content>
        <ol>
          <li v-for="s in stack" :key="s">{{ s }}</li>
        </ol>
      </template>
    </Card>
  </main>
</template>
