<script setup lang="ts">
// Warenkorb (P4): Positionen je Durchführung; Sammelbuchung = je Teilnehmer:in eine Position.
// Teilnehmer:in-Daten + Menge editierbar (→ adjustOrderLine), Entfernen mit Bestätigung.
const { order, refresh, adjust, remove, busy } = useCart()

// SSR: Warenkorb serverseitig laden (Cookie wird über useRequestFetch durchgereicht).
await refresh()

useHead({ title: 'Warenkorb' })

const formatLabel: Record<string, string> = { PRAESENZ: 'Präsenz', ONLINE: 'Online', HYBRID: 'Hybrid' }

// Lokale, editierbare Teilnehmer:in-Felder je Position.
const felder = reactive<Record<string, { name: string; email: string }>>({})
watchEffect(() => {
  for (const l of order.value?.lines ?? []) {
    if (!felder[l.id]) {
      felder[l.id] = { name: l.customFields.participantName ?? '', email: l.customFields.participantEmail ?? '' }
    }
  }
})

async function teilnehmerSpeichern(lineId: string, quantity: number) {
  const f = felder[lineId]
  await adjust(lineId, quantity, { participantName: f?.name, participantEmail: f?.email })
}

async function mengeAendern(lineId: string, quantity: number) {
  if (quantity < 1) return
  const f = felder[lineId]
  await adjust(lineId, quantity, { participantName: f?.name, participantEmail: f?.email })
}

// Entfernen mit Bestätigung.
const zuEntfernen = ref<string | null>(null)
const modalOffen = computed({
  get: () => zuEntfernen.value !== null,
  set: (v) => { if (!v) zuEntfernen.value = null },
})
async function entfernenBestaetigen() {
  if (zuEntfernen.value) await remove(zuEntfernen.value)
  zuEntfernen.value = null
}
</script>

<template>
  <div>
    <h1 class="mb-6 text-2xl font-semibold text-(--ui-text-highlighted)">Warenkorb</h1>

    <div v-if="!order || order.lines.length === 0" class="rounded-lg border border-(--ui-border) py-16 text-center">
      <p class="text-(--ui-text-muted)">Ihr Warenkorb ist leer.</p>
      <UButton to="/" class="mt-4" color="primary" variant="subtle" icon="i-lucide-arrow-left">Zum Katalog</UButton>
    </div>

    <div v-else class="grid gap-8 lg:grid-cols-[1fr_20rem] items-start">
      <div class="space-y-4">
        <UCard v-for="l in order.lines" :key="l.id">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <p class="font-medium text-(--ui-text-highlighted)">{{ l.productVariant.name }}</p>
              <p class="text-sm text-(--ui-text-muted)">
                <UBadge color="neutral" variant="subtle" class="mr-1">{{ l.productVariant.sku }}</UBadge>
              </p>
            </div>
            <div class="text-right">
              <p class="font-semibold text-(--ui-primary)">{{ euro(l.linePriceWithTax, order.currencyCode) }}</p>
              <p class="text-xs text-(--ui-text-muted)">{{ euro(l.unitPriceWithTax, order.currencyCode) }} / Platz</p>
            </div>
          </div>

          <div class="mt-3 grid gap-2 sm:grid-cols-2">
            <UFormField label="Teilnehmer:in" size="sm">
              <UInput
                v-model="felder[l.id]!.name"
                placeholder="Name"
                @blur="teilnehmerSpeichern(l.id, l.quantity)"
              />
            </UFormField>
            <UFormField label="Teilnehmer-E-Mail" size="sm">
              <UInput
                v-model="felder[l.id]!.email"
                type="email"
                placeholder="E-Mail"
                @blur="teilnehmerSpeichern(l.id, l.quantity)"
              />
            </UFormField>
          </div>

          <div class="mt-3 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="text-sm text-(--ui-text-muted)">Plätze</span>
              <UButton size="xs" variant="outline" color="neutral" icon="i-lucide-minus" :disabled="busy || l.quantity <= 1" @click="mengeAendern(l.id, l.quantity - 1)" />
              <span class="w-6 text-center text-sm">{{ l.quantity }}</span>
              <UButton size="xs" variant="outline" color="neutral" icon="i-lucide-plus" :disabled="busy" @click="mengeAendern(l.id, l.quantity + 1)" />
            </div>
            <UButton size="xs" variant="ghost" color="error" icon="i-lucide-trash-2" @click="zuEntfernen = l.id">Entfernen</UButton>
          </div>
        </UCard>

        <p class="text-xs text-(--ui-text-muted)">
          Für eine <strong>Sammelbuchung</strong> mehrere Plätze wählen oder dasselbe Angebot erneut in den
          Warenkorb legen — je Teilnehmer:in eine Position mit eigenem Namen.
        </p>
      </div>

      <UCard class="lg:sticky lg:top-6">
        <h2 class="mb-3 font-medium text-(--ui-text-highlighted)">Zusammenfassung</h2>
        <dl class="space-y-1 text-sm">
          <div class="flex justify-between"><dt class="text-(--ui-text-muted)">Zwischensumme</dt><dd>{{ euro(order.subTotalWithTax, order.currencyCode) }}</dd></div>
          <div class="flex justify-between"><dt class="text-(--ui-text-muted)">Versand</dt><dd>{{ euro(order.shippingWithTax, order.currencyCode) }}</dd></div>
          <div class="mt-2 flex justify-between border-t border-(--ui-border) pt-2 text-base font-semibold">
            <dt>Gesamt (inkl. USt.)</dt><dd class="text-(--ui-primary)">{{ euro(order.totalWithTax, order.currencyCode) }}</dd>
          </div>
        </dl>
        <UButton to="/kasse" block class="mt-4" color="primary" icon="i-lucide-arrow-right" trailing>Zur Kasse</UButton>
        <UButton to="/" block class="mt-2" variant="ghost" color="neutral">Weiter einkaufen</UButton>
      </UCard>
    </div>

    <UModal v-model:open="modalOffen" title="Position entfernen?">
      <template #body>
        <p class="text-sm text-(--ui-text-muted)">Soll diese Position wirklich aus dem Warenkorb entfernt werden?</p>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton variant="ghost" color="neutral" @click="zuEntfernen = null">Abbrechen</UButton>
          <UButton color="error" :loading="busy" @click="entfernenBestaetigen">Entfernen</UButton>
        </div>
      </template>
    </UModal>
  </div>
</template>
