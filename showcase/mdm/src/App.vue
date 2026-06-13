<script setup lang="ts">
// Rahmen des MDM-Cockpits: Kopfzeile (mit SSO-Status) + Router-Outlet.
import { RouterLink, RouterView } from 'vue-router';
import Button from 'primevue/button';
import { auth, login, logout } from './auth';
</script>

<template>
  <header class="topbar">
    <RouterLink to="/" class="marke">EBZ MDM · Bildungsangebote</RouterLink>
    <span class="hint">Formularverwaltung-Showcase · Stack B</span>
    <span class="spacer" />
    <template v-if="auth.bereit">
      <span v-if="auth.angemeldet" class="user"><i class="pi pi-user" /> {{ auth.benutzer }}</span>
      <Button v-if="auth.angemeldet" label="Abmelden" size="small" text @click="logout" />
      <Button v-else label="Anmelden" icon="pi pi-sign-in" size="small" @click="login" />
    </template>
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
.spacer {
  flex: 1;
}
.user {
  font-size: 0.9rem;
}
.topbar :deep(.p-button) {
  color: #fff;
}
.inhalt {
  max-width: 1000px;
  margin: 1.5rem auto;
  padding: 0 1.25rem;
}
</style>
