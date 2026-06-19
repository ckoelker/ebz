<script setup lang="ts">
// Rahmen des MDM-Cockpits im Storybook-Shell-Look (Topbar + linke Rail): die CRM-Kernmaske ist die
// abgestimmte Startseite; die übrigen Masken sind als funktionale Prototypen gruppiert/gekennzeichnet.
// UApp stellt Overlays/Tooltips/Toasts für Nuxt UI bereit.
import { RouterLink, RouterView } from 'vue-router';
import { auth, login, logout } from './auth';
import AnrufToast from '@/crm/AnrufToast.vue';

const prototypen = [
  { to: '/angebote', label: 'Bildungsangebote', icon: 'i-lucide-book-open' },
  { to: '/reviews', label: 'Dubletten-Review', icon: 'i-lucide-copy-check' },
  { to: '/anmeldungen', label: 'Anmeldungen', icon: 'i-lucide-clipboard-check' },
  { to: '/nachrichten', label: 'Nachrichten', icon: 'i-lucide-messages-square' },
];
</script>

<template>
  <UApp>
    <div class="min-h-screen flex flex-col bg-muted text-default">
      <!-- Topbar (EBZ-Navy) -->
      <header class="flex items-center gap-4 px-6 py-3 bg-primary-500 text-white shrink-0">
        <RouterLink to="/" class="font-extrabold no-underline text-white">EBZ MDM</RouterLink>
        <span class="text-white/60 text-sm">Stammdaten-Cockpit</span>
        <span class="flex-1" />
        <AnrufToast />
        <template v-if="auth.bereit">
          <span v-if="auth.angemeldet" class="text-sm inline-flex items-center gap-1">
            <UIcon name="i-lucide-user" /> {{ auth.benutzer }}
          </span>
          <UButton v-if="auth.angemeldet" color="neutral" variant="ghost" size="sm"
                   class="text-white hover:bg-white/10" @click="logout">Abmelden</UButton>
          <UButton v-else color="neutral" variant="ghost" size="sm" icon="i-lucide-log-in"
                   class="text-white hover:bg-white/10" @click="login">Anmelden</UButton>
        </template>
      </header>

      <div class="flex-1 flex min-h-0">
        <!-- Rail -->
        <nav class="w-56 shrink-0 border-r border-default bg-default px-3 py-4 flex flex-col gap-1">
          <RouterLink
            to="/"
            class="flex items-center gap-2 px-3 py-2 rounded-md text-sm text-muted hover:bg-elevated"
            exact-active-class="!bg-elevated !text-highlighted font-semibold"
          >
            <UIcon name="i-lucide-contact" /> Kundenstamm
          </RouterLink>

          <div class="mt-5 mb-1 px-2 text-[11px] font-semibold uppercase tracking-wide text-dimmed">
            Prototyp · nicht abgestimmt
          </div>
          <RouterLink
            v-for="p in prototypen"
            :key="p.to"
            :to="p.to"
            class="flex items-center gap-2 px-3 py-2 rounded-md text-sm text-muted hover:bg-elevated"
            active-class="!bg-elevated !text-highlighted font-semibold"
          >
            <UIcon :name="p.icon" /> {{ p.label }}
          </RouterLink>
        </nav>

        <!-- Inhalt -->
        <main class="flex-1 min-w-0 overflow-auto p-6">
          <RouterView />
        </main>
      </div>
    </div>
  </UApp>
</template>
