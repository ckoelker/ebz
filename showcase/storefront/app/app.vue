<script setup lang="ts">
// App-Shell der EBZ-Akademie-Storefront mit Burger-/Hauptmenü (P6): Collections + CMS-Seiten.
const { count, refresh } = useCart()
const { customer, istAngemeldet, refresh: refreshAuth, login, register, logout } = useAuth()
// Warenkorb- + Login-Stand initial laden (client).
onMounted(() => { refresh(); refreshAuth() })

// Navigation serverseitig laden (Collections + veröffentlichte Menü-Seiten).
const { data: nav } = await useFetch('/api/navigation')
const menuOffen = ref(false)
// Menü bei Navigation schließen.
const route = useRoute()
watch(() => route.fullPath, () => { menuOffen.value = false })
// „Mein Konto" → Kunden-Self-Service-Portal (eigene SPA, geteilte Keycloak-SSO).
const portalUrl = useRuntimeConfig().public.portalUrl
</script>

<template>
  <UApp>
    <header class="border-b border-(--ui-border) bg-(--ui-bg)">
      <UContainer class="flex items-center justify-between gap-4 py-3">
        <div class="flex items-center gap-2">
          <UButton
            variant="ghost"
            color="neutral"
            icon="i-lucide-menu"
            aria-label="Menü"
            @click="menuOffen = true"
          />
          <NuxtLink to="/" class="flex items-center gap-2 font-semibold text-(--ui-primary)">
            <span class="inline-flex size-8 items-center justify-center rounded bg-primary text-white font-bold">E</span>
            <span class="text-lg">EBZ Akademie</span>
          </NuxtLink>
        </div>
        <nav class="flex items-center gap-2 text-sm">
          <UButton v-if="route.path !== '/'" to="/" variant="ghost" color="neutral" class="hidden sm:inline-flex">Katalog</UButton>
          <UChip :text="count" :show="count > 0" color="primary" size="2xl">
            <UButton to="/warenkorb" variant="ghost" color="neutral" icon="i-lucide-shopping-cart" aria-label="Warenkorb" />
          </UChip>
          <template v-if="istAngemeldet">
            <UButton :to="portalUrl" target="_blank" variant="ghost" color="neutral" icon="i-lucide-user" :label="customer?.firstName" title="Mein Konto (Kundenportal)" />
            <UButton variant="ghost" color="neutral" icon="i-lucide-log-out" aria-label="Abmelden" @click="logout" />
          </template>
          <template v-else>
            <UButton variant="ghost" color="neutral" icon="i-lucide-user" @click="login()">Anmelden</UButton>
            <UButton variant="subtle" color="primary" class="hidden sm:inline-flex" @click="register()">Registrieren</UButton>
          </template>
        </nav>
      </UContainer>
    </header>

    <!-- Burger-/Hauptmenü: Themen (Collections) + Seiten (CMS) -->
    <USlideover v-model:open="menuOffen" title="Menü" side="left">
      <template #body>
        <nav class="space-y-6">
          <div>
            <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-(--ui-text-dimmed)">Katalog</p>
            <NuxtLink to="/" class="block rounded px-2 py-1.5 text-sm hover:bg-(--ui-bg-elevated)">Alle Angebote</NuxtLink>
            <NuxtLink
              v-for="c in nav?.collections ?? []"
              :key="c.slug"
              :to="{ path: '/', query: { collection: c.slug } }"
              class="block rounded px-2 py-1.5 text-sm hover:bg-(--ui-bg-elevated)"
            >{{ c.name }}</NuxtLink>
          </div>
          <div v-if="(nav?.pages?.length ?? 0) > 0">
            <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-(--ui-text-dimmed)">Informationen</p>
            <NuxtLink
              v-for="p in nav?.pages ?? []"
              :key="p.slug"
              :to="`/seite/${p.slug}`"
              class="block rounded px-2 py-1.5 text-sm hover:bg-(--ui-bg-elevated)"
            >{{ p.titel }}</NuxtLink>
          </div>
        </nav>
      </template>
    </USlideover>

    <main>
      <UContainer class="py-8">
        <NuxtPage />
      </UContainer>
    </main>

    <footer class="mt-12 border-t border-(--ui-border) py-6 text-(--ui-text-muted)">
      <UContainer class="flex flex-wrap items-center justify-between gap-3 text-sm">
        <span>EBZ Akademie · Weiterbildung für die Immobilienwirtschaft</span>
        <span class="flex flex-wrap gap-3">
          <NuxtLink v-for="p in nav?.pages ?? []" :key="p.slug" :to="`/seite/${p.slug}`" class="hover:text-(--ui-primary)">
            {{ p.titel }}
          </NuxtLink>
        </span>
      </UContainer>
    </footer>

  </UApp>
</template>
