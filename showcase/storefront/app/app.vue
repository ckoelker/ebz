<script setup lang="ts">
// App-Shell der EBZ-Akademie-Storefront. Burger-/Hauptmenü (Collections + CMS-Seiten) folgt in P6.
</script>

<template>
  <UApp>
    <header class="border-b border-(--ui-border) bg-(--ui-bg)">
      <UContainer class="flex items-center justify-between gap-4 py-3">
        <NuxtLink to="/" class="flex items-center gap-2 font-semibold text-(--ui-primary)">
          <span class="inline-flex size-8 items-center justify-center rounded bg-primary text-white font-bold">E</span>
          <span class="text-lg">EBZ Akademie</span>
        </NuxtLink>
        <nav class="flex items-center gap-2 text-sm">
          <UButton to="/" variant="ghost" color="neutral">Katalog</UButton>
        </nav>
      </UContainer>
    </header>

    <main>
      <UContainer class="py-8">
        <NuxtPage />
      </UContainer>
    </main>

    <footer class="mt-12 border-t border-(--ui-border) py-6 text-center text-sm text-(--ui-text-muted)">
      EBZ Akademie · Weiterbildung für die Immobilienwirtschaft
    </footer>
  </UApp>
</template>
