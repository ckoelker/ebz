<script setup lang="ts" generic="T = Record<string, unknown>">
// Neutrales UI-Primitiv (brand- & domain-frei): einheitliche Listen-/Tabellen-Hülle um <UTable> —
// konsistenter Leer-/Lade-Zustand und Standard-Styling. Zell-Slots werden 1:1 durchgereicht
// (z. B. #status-cell), damit die App Zellen frei gestaltet, ohne UTable direkt zu benutzen.
// Von allen Design-Systemen (customer-ui & crm-ui) + Apps geteilt. Prop-rein, SSR-safe.
//
// Generisch über die Zeilen-Type T (aus `:data` am Call-Site abgeleitet) → die durchgereichten
// Zell-Slots sind getypt (`row.original` ist T, nicht any). `columns` bleibt bewusst `any[]`: das ist
// die einzige vertretbare Ausnahme, um KEINE @nuxt/ui-Typabhängigkeit (TableColumn) ins dependency-freie
// @ui-base zu ziehen — die App typt die Spalten am Call-Site selbst.
withDefaults(defineProps<{
  data: T[]
  columns: any[]
  loading?: boolean
  empty?: string
}>(), {
  loading: false,
  empty: 'Keine Einträge.',
})

// Slot-Vertrag: jeder durchgereichte (Zell-)Slot erhält { row: { original: T } } → im Consumer ist
// `row.original` wieder T. Wert `| undefined`, damit der Vertrag zu Vues `InternalSlots` (optionale
// Slots) assignbar bleibt; (Slot-Render-Rückgabe ist nach Vue-Konvention `any`.)
defineSlots<Record<string, ((props: { row: { original: T } }) => any) | undefined>>()
</script>

<template>
  <UTable :data="data" :columns="columns" :loading="loading" :empty="empty">
    <!-- alle (Zell-)Slots transparent an UTable weiterreichen -->
    <template v-for="(_, name) in $slots" #[name]="slotData">
      <slot :name="name" v-bind="slotData ?? {}" />
    </template>
  </UTable>
</template>
