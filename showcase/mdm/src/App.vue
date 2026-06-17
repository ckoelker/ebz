<script setup lang="ts">
// Rahmen des MDM-Cockpits: Kopfzeile (mit SSO-Status) + Router-Outlet.
// UApp stellt Overlays/Tooltips/Toasts für Nuxt UI bereit.
import { RouterLink, RouterView } from 'vue-router';
import { auth, login, logout } from './auth';
</script>

<template>
  <UApp>
    <!-- Theming wie in der Storybook-Abnahme: App-Fläche auf den semantischen
         Nuxt-UI-Tokens (bg-default/text-default), Default Light. -->
    <div class="min-h-screen bg-default text-default">
      <header class="flex items-baseline gap-4 px-6 py-3 bg-primary-500 text-white">
        <RouterLink to="/" class="font-extrabold no-underline text-white">EBZ MDM</RouterLink>
        <nav class="flex gap-4 ml-4">
          <RouterLink to="/" class="topnav">Bildungsangebote</RouterLink>
          <RouterLink to="/reviews" class="topnav">Dubletten-Review</RouterLink>
          <RouterLink to="/anmeldungen" class="topnav">Anmeldungen</RouterLink>
        </nav>
        <span class="flex-1" />
        <template v-if="auth.bereit">
          <span v-if="auth.angemeldet" class="text-sm inline-flex items-center gap-1">
            <UIcon name="i-lucide-user" /> {{ auth.benutzer }}
          </span>
          <UButton
            v-if="auth.angemeldet"
            color="neutral"
            variant="ghost"
            size="sm"
            class="text-white hover:bg-white/10"
            @click="logout"
          >
            Abmelden
          </UButton>
          <UButton
            v-else
            color="neutral"
            variant="ghost"
            size="sm"
            icon="i-lucide-log-in"
            class="text-white hover:bg-white/10"
            @click="login"
          >
            Anmelden
          </UButton>
        </template>
      </header>
      <main class="max-w-5xl mx-auto px-5 py-6">
        <RouterView />
      </main>
    </div>
  </UApp>
</template>

<style scoped>
.topnav {
  color: #fff;
  text-decoration: none;
  opacity: 0.85;
  font-size: 0.95rem;
}
.topnav.router-link-active {
  opacity: 1;
  font-weight: 700;
  border-bottom: 2px solid #fff;
}
</style>
