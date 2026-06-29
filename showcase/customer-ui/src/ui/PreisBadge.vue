<script setup lang="ts">
import { euro } from '@crm-ui/domain/format'

// Kunden-Primitiv: Preisanzeige aus Minor Units (Cent) in de-DE/EUR über den geteilten Domain-Core.
const props = withDefaults(defineProps<{
  /** Betrag in Minor Units (Cent), wie Vendure/Rechnung liefern. */
  cent?: number | null
  currency?: string
  size?: 'sm' | 'md' | 'lg' | 'xl'
}>(), { currency: 'EUR', size: 'md' })

const klasse: Record<NonNullable<typeof props.size>, string> = {
  sm: 'text-sm', md: 'text-base', lg: 'text-lg', xl: 'text-2xl',
}
</script>

<template>
  <span class="font-semibold tabular-nums text-highlighted" :class="klasse[size]">{{ euro(cent, currency) }}</span>
</template>
