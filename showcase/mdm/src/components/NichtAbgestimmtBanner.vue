<script setup lang="ts">
// Kennzeichnet Screens, deren UI NOCH NICHT mit dem Kunden abgestimmt ist (im Gegensatz zur
// CRM-Kernmaske, die über die Storybook-Abnahme gelaufen ist). Bewusst auffällig, damit in der
// Vorführung klar ist, was Abnahme-Stand und was funktionaler Prototyp ist.
</script>

<template>
  <UAlert
    color="warning"
    variant="subtle"
    icon="i-lucide-flask-conical"
    title="Funktionaler Prototyp – nicht mit Kunde abgestimmt"
    description="Diese Maske dient der Funktionsdemonstration. Das UI-Design ist noch nicht über die Storybook-Abnahme gelaufen (anders als die CRM-Kernmaske)."
    class="mb-5"
  />
</template>
