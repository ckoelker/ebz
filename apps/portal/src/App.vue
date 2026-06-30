<script setup lang="ts">
// Rahmen des EBZ-Außenportals (Nuxt UI 4, Vue-only): Navy-Topbar mit horizontaler Navigation + SSO-Status.
// UApp stellt Overlays/Tooltips/Toasts bereit. Light-Theme + EBZ-Navy wie im MDM-Cockpit.
import { RouterLink, RouterView } from 'vue-router';
import { auth, login, logout } from './auth';

const nav = [
  { to: '/', label: 'Ausbildungsbetrieb anmelden', exact: true },
  { to: '/azubis', label: 'Meine Azubis' },
  { to: '/rechnungen', label: 'Meine Rechnungen' },
  { to: '/trainings', label: 'Meine Trainings' },
  { to: '/aktivitaeten', label: 'Meine Aktivitäten' },
  { to: '/nachrichten', label: 'Meine Nachrichten' },
  { to: '/einstellungen', label: 'Einstellungen' },
];
</script>

<template>
  <UApp>
    <div class="min-h-screen flex flex-col bg-muted text-default">
      <!-- Topbar (EBZ-Navy) -->
      <header class="flex items-center gap-4 px-6 py-3 bg-primary-500 text-white shrink-0">
        <RouterLink to="/" class="font-extrabold no-underline text-white">EBZ Portal</RouterLink>
        <nav class="flex items-center gap-1 ml-2">
          <RouterLink
            v-for="n in nav"
            :key="n.to"
            :to="n.to"
            class="px-3 py-1.5 rounded-md text-sm text-white/80 no-underline hover:bg-white/10"
            :active-class="n.exact ? '' : '!text-white font-semibold bg-white/15'"
            :exact-active-class="n.exact ? '!text-white font-semibold bg-white/15' : ''"
          >
            {{ n.label }}
          </RouterLink>
        </nav>
        <span class="flex-1" />
        <template v-if="auth.bereit">
          <span v-if="auth.angemeldet" class="text-sm inline-flex items-center gap-1">
            <UIcon name="i-lucide-user" /> {{ auth.name || auth.benutzer }}
          </span>
          <UButton v-if="auth.angemeldet" color="neutral" variant="ghost" size="sm"
                   class="text-white hover:bg-white/10" @click="logout">Abmelden</UButton>
          <UButton v-else color="neutral" variant="ghost" size="sm" icon="i-lucide-log-in"
                   class="text-white hover:bg-white/10" @click="login">Anmelden</UButton>
        </template>
      </header>

      <!-- Inhalt -->
      <main class="flex-1 min-w-0 overflow-auto">
        <div class="max-w-5xl mx-auto px-5 py-6">
          <RouterView />
        </div>
      </main>
    </div>
  </UApp>
</template>
