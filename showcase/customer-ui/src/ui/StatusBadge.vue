<script setup lang="ts">
import { computed } from 'vue'
import {
  einschreibungStatusColor, einschreibungStatusText, rechnungStatusColor, azubiStatusColor,
} from '@crm-ui/domain/severity'

// Kunden-Primitiv: ein Status-Badge für Shop/Portal. Farbe + (optionaler) Klartext kommen aus dem
// geteilten Domain-Core — KEINE Admin-Komponente, nur invariante Logik. Prop-rein, SSR-safe.
const props = withDefaults(defineProps<{
  /** Roh-Status aus dem Backend, z. B. EINGESCHRIEBEN, BEZAHLT, ANGEFORDERT. */
  status?: string
  /** Fachlicher Kontext — bestimmt das Farb-/Text-Mapping. */
  art?: 'einschreibung' | 'rechnung' | 'azubi'
  variant?: 'soft' | 'solid' | 'outline' | 'subtle'
  size?: 'sm' | 'md' | 'lg'
}>(), {
  art: 'einschreibung',
  variant: 'soft',
  size: 'sm',
})

const color = computed(() =>
  props.art === 'rechnung' ? rechnungStatusColor(props.status)
    : props.art === 'azubi' ? azubiStatusColor(props.status)
      : einschreibungStatusColor(props.status))

// Einschreibung hat eine kundenfreundliche Klartext-Übersetzung; Rechnung zeigt den Status direkt.
const label = computed(() =>
  props.art === 'einschreibung' ? einschreibungStatusText(props.status) : (props.status ?? ''))
</script>

<template>
  <UBadge :color="color" :variant="variant" :size="size">{{ label }}</UBadge>
</template>
