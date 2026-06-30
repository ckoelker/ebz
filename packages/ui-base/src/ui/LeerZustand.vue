<script setup lang="ts">
// Neutrales UI-Primitiv (brand- & domain-frei): einheitlicher Leer-/Lade-Zustand für Listen & Masken
// (Icon, Titel, Text, optionale Aktion über #aktion-Slot). Von allen geteilt. Prop-rein, SSR-safe.
withDefaults(defineProps<{
  icon?: string
  titel?: string
  text?: string
  loading?: boolean
}>(), {
  icon: 'i-lucide-inbox',
  titel: 'Nichts vorhanden',
  loading: false,
})
</script>

<template>
  <div class="flex flex-col items-center justify-center text-center gap-2 py-12 text-muted">
    <UIcon :name="loading ? 'i-lucide-loader-circle' : icon" :class="['size-8', loading ? 'animate-spin' : '']" />
    <p class="font-medium text-highlighted">{{ loading ? 'Wird geladen …' : titel }}</p>
    <p v-if="text && !loading" class="text-sm">{{ text }}</p>
    <div v-if="!loading" class="mt-2"><slot name="aktion" /></div>
  </div>
</template>
