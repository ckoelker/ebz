<script setup lang="ts">
// Neutrales UI-Primitiv (brand- & domain-frei): einheitliches Formularfeld um <UFormField>
// (Label/Pflicht/Hilfe/Fehler). Default-Slot trägt den Input (UInput/USelect/…). Von allen
// Design-Systemen + Apps geteilt. Prop-rein, SSR-safe.
defineProps<{
  label?: string
  /** Feldname für Form-Validierung (vee-validate/zod). */
  name?: string
  required?: boolean
  help?: string
  error?: string
}>()
</script>

<template>
  <UFormField :label="label" :name="name" :required="required" :help="help" :error="error">
    <slot />
  </UFormField>
</template>
