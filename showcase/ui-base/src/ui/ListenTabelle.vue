<script setup lang="ts">
// Neutrales UI-Primitiv (brand- & domain-frei): einheitliche Listen-/Tabellen-Hülle um <UTable> —
// konsistenter Leer-/Lade-Zustand und Standard-Styling. Zell-Slots werden 1:1 durchgereicht
// (z. B. #status-cell), damit die App Zellen frei gestaltet, ohne UTable direkt zu benutzen.
// Von allen Design-Systemen (customer-ui & crm-ui) + Apps geteilt. Prop-rein, SSR-safe.
//
// data/columns sind bewusst lose typisiert (`any[]`), damit das geteilte Paket NICHT von @nuxt/ui-Typen
// abhängt (ui-base hat keine node_modules) und die durchgereichten Zell-Slot-`row` `any` sind — die App
// typt Daten/Spalten am Call-Site selbst.
withDefaults(defineProps<{
  data: any[]
  columns: any[]
  loading?: boolean
  empty?: string
}>(), {
  loading: false,
  empty: 'Keine Einträge.',
})
</script>

<template>
  <UTable :data="data" :columns="columns" :loading="loading" :empty="empty">
    <!-- alle (Zell-)Slots transparent an UTable weiterreichen -->
    <template v-for="(_, name) in $slots" #[name]="slotData">
      <slot :name="name" v-bind="slotData ?? {}" />
    </template>
  </UTable>
</template>
