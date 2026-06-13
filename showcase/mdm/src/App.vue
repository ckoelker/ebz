<script setup lang="ts">
// Rahmen des MDM-Cockpits: Kopfzeile + Router-Outlet. Inhalt = Liste / Pflege (siehe router.ts).
import { RouterLink, RouterView } from 'vue-router';
</script>

<template>
  <header class="topbar">
    <RouterLink to="/" class="marke">EBZ MDM · Bildungsangebote</RouterLink>
    <span class="hint">Formularverwaltung-Showcase · Stack B</span>
  </header>
  <main class="inhalt">
    <RouterView />
  </main>
</template>

<style>
body {
  margin: 0;
  font-family: system-ui, sans-serif;
  background: var(--p-content-background, #fff);
  color: var(--p-text-color, #1f2937);
}
.topbar {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  padding: 0.9rem 1.5rem;
  background: var(--p-primary-color, #6366f1);
  color: #fff;
}
.marke {
  font-weight: 800;
  color: #fff;
  text-decoration: none;
}
.hint {
  font-size: 0.8rem;
  opacity: 0.85;
}
.inhalt {
  max-width: 1000px;
  margin: 1.5rem auto;
  padding: 0 1.25rem;
}
</style>
