<script setup lang="ts">
// Minimaler Dev-Einstieg (`pnpm dev`). Die eigentliche Abnahme läuft über Storybook
// (`pnpm storybook`). Hier nur ein Hinweis, damit Vite einen Entry hat.
</script>

<template>
  <UApp>
    <UContainer class="py-16 text-center space-y-3">
      <h1 class="text-2xl font-bold text-highlighted">EBZ CRM-Kernmaske</h1>
      <p class="text-muted">Komponenten-Abnahme über Storybook: <code>pnpm storybook</code></p>
      <UButton to="http://localhost:6007" target="_blank" color="primary" icon="i-lucide-book-open">
        Storybook öffnen
      </UButton>
    </UContainer>
  </UApp>
</template>
