<script setup lang="ts">
// SFC-Wrapper (kein Runtime-Template-String!), damit der @nuxt/ui/vite-Plugin <UApp> auto-importieren
// kann. UApp ist für Toast/Tooltip/Overlays nötig.
</script>

<template>
  <UApp>
    <div class="bg-default text-default min-h-screen p-4">
      <slot />
    </div>
  </UApp>
</template>
