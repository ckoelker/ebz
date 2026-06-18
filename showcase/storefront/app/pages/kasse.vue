<script setup lang="ts">
// Kasse (P4): Gastbuchung — Kontakt + Adresse + Zahlart → eine orchestrierte Checkout-Route.
// Zahlung: Kauf auf Rechnung (B2B) ODER Karte/SEPA (Stripe-Stand-in). Beide settlen im Showcase
// automatisch (dummyPaymentHandler) → Bestätigung mit Bestellnummer.
const { order, refresh, clearLocal, isEmpty } = useCart()
await refresh()

useHead({ title: 'Kasse' })

const form = reactive({
  email: '',
  firstName: '',
  lastName: '',
  company: '',
  streetLine1: '',
  postalCode: '',
  city: '',
  countryCode: 'DE',
  paymentMethod: 'rechnung',
})

const zahlarten = [
  { value: 'rechnung', label: 'Kauf auf Rechnung', beschreibung: 'Zahlung nach Erhalt der Rechnung (B2B).', icon: 'i-lucide-file-text' },
  { value: 'stripe-sepa', label: 'Kreditkarte / SEPA-Lastschrift', beschreibung: 'Sofortzahlung über Stripe (Showcase).', icon: 'i-lucide-credit-card' },
]

const busy = ref(false)
const fehler = ref('')
const bestellnummer = ref('')

const pflichtOk = computed(() =>
  form.email && form.firstName && form.lastName && form.streetLine1 && form.postalCode && form.city,
)

async function bestellen() {
  fehler.value = ''
  if (!pflichtOk.value) {
    fehler.value = 'Bitte alle Pflichtfelder ausfüllen.'
    return
  }
  busy.value = true
  try {
    const res = await $fetch<{ code: string; state: string }>('/api/checkout', {
      method: 'POST',
      body: {
        email: form.email,
        firstName: form.firstName,
        lastName: form.lastName,
        company: form.company,
        address: { streetLine1: form.streetLine1, city: form.city, postalCode: form.postalCode, countryCode: form.countryCode },
        paymentMethod: form.paymentMethod,
      },
    })
    bestellnummer.value = res.code
    clearLocal() // Warenkorb ist nach Bestellung verbraucht
  } catch (e: unknown) {
    const err = e as { statusMessage?: string; data?: { statusMessage?: string } }
    fehler.value = err.data?.statusMessage || err.statusMessage || 'Bestellung fehlgeschlagen.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div>
    <!-- Bestätigung -->
    <div v-if="bestellnummer" class="mx-auto max-w-xl rounded-lg border border-(--ui-border) p-8 text-center">
      <UIcon name="i-lucide-circle-check" class="mx-auto mb-3 size-12 text-(--ui-primary)" />
      <h1 class="text-2xl font-semibold text-(--ui-text-highlighted)">Vielen Dank für Ihre Bestellung!</h1>
      <p class="mt-2 text-(--ui-text-muted)">
        Ihre Bestellnummer lautet <strong class="text-(--ui-text-highlighted)">{{ bestellnummer }}</strong>.
        Eine Bestätigung geht Ihnen per E-Mail zu.
      </p>
      <UButton to="/" class="mt-6" color="primary" icon="i-lucide-arrow-left">Zurück zum Katalog</UButton>
    </div>

    <!-- Leerer Warenkorb -->
    <div v-else-if="isEmpty" class="rounded-lg border border-(--ui-border) py-16 text-center">
      <p class="text-(--ui-text-muted)">Ihr Warenkorb ist leer.</p>
      <UButton to="/" class="mt-4" color="primary" variant="subtle" icon="i-lucide-arrow-left">Zum Katalog</UButton>
    </div>

    <!-- Checkout-Formular -->
    <div v-else class="grid gap-8 lg:grid-cols-[1fr_20rem] items-start">
      <form class="space-y-6" @submit.prevent="bestellen">
        <section>
          <h2 class="mb-3 text-lg font-medium text-(--ui-text-highlighted)">Kontakt</h2>
          <div class="grid gap-3 sm:grid-cols-2">
            <UFormField label="Vorname" required><UInput v-model="form.firstName" /></UFormField>
            <UFormField label="Nachname" required><UInput v-model="form.lastName" /></UFormField>
            <UFormField label="E-Mail" required class="sm:col-span-2"><UInput v-model="form.email" type="email" /></UFormField>
            <UFormField label="Firma (optional)" class="sm:col-span-2"><UInput v-model="form.company" /></UFormField>
          </div>
        </section>

        <section>
          <h2 class="mb-3 text-lg font-medium text-(--ui-text-highlighted)">Rechnungs-/Lieferadresse</h2>
          <div class="grid gap-3 sm:grid-cols-2">
            <UFormField label="Straße & Nr." required class="sm:col-span-2"><UInput v-model="form.streetLine1" /></UFormField>
            <UFormField label="PLZ" required><UInput v-model="form.postalCode" /></UFormField>
            <UFormField label="Ort" required><UInput v-model="form.city" /></UFormField>
          </div>
        </section>

        <section>
          <h2 class="mb-3 text-lg font-medium text-(--ui-text-highlighted)">Zahlart</h2>
          <div class="space-y-2">
            <label
              v-for="z in zahlarten"
              :key="z.value"
              class="flex cursor-pointer items-start gap-3 rounded border border-(--ui-border) p-3 has-[:checked]:border-primary has-[:checked]:ring-1 has-[:checked]:ring-primary"
            >
              <input v-model="form.paymentMethod" type="radio" :value="z.value" class="mt-1 accent-(--ui-primary)">
              <span class="flex-1">
                <span class="flex items-center gap-2 font-medium text-(--ui-text-highlighted)"><UIcon :name="z.icon" /> {{ z.label }}</span>
                <span class="block text-sm text-(--ui-text-muted)">{{ z.beschreibung }}</span>
              </span>
            </label>
          </div>
        </section>

        <UAlert v-if="fehler" color="error" variant="subtle" :title="fehler" icon="i-lucide-triangle-alert" />
      </form>

      <UCard class="lg:sticky lg:top-6">
        <h2 class="mb-3 font-medium text-(--ui-text-highlighted)">Bestellübersicht</h2>
        <ul class="mb-3 space-y-1 text-sm">
          <li v-for="l in order?.lines ?? []" :key="l.id" class="flex justify-between gap-2">
            <span class="min-w-0 truncate text-(--ui-text-muted)">{{ l.quantity }}× {{ l.productVariant.name }}</span>
            <span class="shrink-0">{{ euro(l.linePriceWithTax, order!.currencyCode) }}</span>
          </li>
        </ul>
        <div class="flex justify-between border-t border-(--ui-border) pt-2 text-base font-semibold">
          <span>Gesamt</span><span class="text-(--ui-primary)">{{ euro(order?.totalWithTax ?? 0, order?.currencyCode) }}</span>
        </div>
        <UButton block class="mt-4" color="primary" :loading="busy" icon="i-lucide-check" @click="bestellen">
          Zahlungspflichtig bestellen
        </UButton>
        <UButton to="/warenkorb" block class="mt-2" variant="ghost" color="neutral">Zurück zum Warenkorb</UButton>
      </UCard>
    </div>
  </div>
</template>
