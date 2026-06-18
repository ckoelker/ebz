<script setup lang="ts">
// Kasse (P4 + Login-Pflicht + Teilnehmer-Erfassung): Checkout NUR für angemeldete Kund:innen.
// Nicht angemeldet → Redirect zur gebrandeten Keycloak-Seite (Login/Registrierung), Rückkehr hierher.
// Teilnehmer:innen je Seminar-Platz werden HIER erfasst (aus der Käufer-Organisation wählbar oder neu).
// Zahlung: Kauf auf Rechnung (B2B) ODER Karte/SEPA (Stripe-Stand-in).
import type { Teilnehmer } from '~~/server/utils/order'
import type { TeilnehmerVorschlag } from '~~/server/utils/integration'

const { order, refresh, adjust, clearLocal, isEmpty } = useCart()
const { customer, istAngemeldet, refresh: refreshAuth, login } = useAuth()
await Promise.all([refresh(), refreshAuth()])

useHead({ title: 'Kasse' })

// Login-Pflicht: nicht angemeldet → Redirect zu Keycloak (mit Rückkehr zur Kasse).
onMounted(() => {
  if (!istAngemeldet.value) login('/kasse')
})

// Teilnehmer-Vorschläge (Personen der Käufer-Organisation) — nur angemeldet befüllt.
const { data: vorschlaege } = await useFetch<TeilnehmerVorschlag[]>('/api/participants', { default: () => [] })
const vorschlagItems = computed(() =>
  (vorschlaege.value ?? []).map((v, i) => ({ label: `${v.vorname} ${v.nachname}`.trim(), value: i })),
)

const geschlechtOptions = [
  { value: 'WEIBLICH', label: 'weiblich' },
  { value: 'MAENNLICH', label: 'männlich' },
  { value: 'DIVERS', label: 'divers' },
  { value: 'KEINE_ANGABE', label: 'keine Angabe' },
]

function leer(): Teilnehmer {
  return { geschlecht: '', titel: '', vorname: '', nachname: '', namensschild: '', email: '' }
}
function istSeminar(l: { productVariant: { customFields: { fulfillmentType: string | null } } }): boolean {
  return l.productVariant.customFields.fulfillmentType === 'seminar'
}

// Teilnehmer:innen-Liste je Seminar-Position (Länge = Menge), aus dem OrderLine-JSON initialisiert.
const felder = reactive<Record<string, Teilnehmer[]>>({})
watchEffect(() => {
  for (const l of order.value?.lines ?? []) {
    if (istSeminar(l) && !felder[l.id]) {
      let liste: Teilnehmer[] = []
      try {
        liste = l.customFields.teilnehmer ? JSON.parse(l.customFields.teilnehmer) : []
      } catch { liste = [] }
      while (liste.length < l.quantity) liste.push(leer())
      felder[l.id] = liste.slice(0, l.quantity)
    }
  }
})

// Person aus der Organisationsliste in einen Slot übernehmen.
function uebernehmen(lineId: string, slot: number, vIndex: number | undefined) {
  const v = (vorschlaege.value ?? [])[vIndex ?? -1]
  if (!v) return
  const t = felder[lineId]![slot]!
  t.vorname = v.vorname
  t.nachname = v.nachname
  t.titel = v.titel ?? ''
  t.geschlecht = v.geschlecht ?? ''
  t.email = v.email ?? ''
}

function tnVollstaendig(t: Teilnehmer): boolean {
  return !!(t.geschlecht && t.vorname?.trim() && t.nachname?.trim())
}
const teilnehmerOk = computed(() =>
  (order.value?.lines ?? [])
    .filter(istSeminar)
    .every((l) => (felder[l.id] ?? []).length > 0 && (felder[l.id] ?? []).every(tnVollstaendig)),
)

const form = reactive({
  email: '', firstName: '', lastName: '', company: '',
  streetLine1: '', postalCode: '', city: '', countryCode: 'DE',
  paymentMethod: 'rechnung',
})
watchEffect(() => {
  if (customer.value) {
    form.email = customer.value.emailAddress
    form.firstName = customer.value.firstName
    form.lastName = customer.value.lastName
  }
})

const zahlarten = [
  { value: 'rechnung', label: 'Kauf auf Rechnung', beschreibung: 'Zahlung nach Erhalt der Rechnung (B2B).', icon: 'i-lucide-file-text' },
  { value: 'stripe-sepa', label: 'Kreditkarte / SEPA-Lastschrift', beschreibung: 'Sofortzahlung über Stripe (Showcase).', icon: 'i-lucide-credit-card' },
]

const busy = ref(false)
const fehler = ref('')
const bestellnummer = ref('')

const emailOk = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()))
const agbAkzeptiert = ref(false)
const pflichtfelderOk = computed(() =>
  !!(form.firstName.trim() && form.lastName.trim() && form.streetLine1.trim() && form.postalCode.trim() && form.city.trim()),
)
const bestellbar = computed(() => pflichtfelderOk.value && emailOk.value && teilnehmerOk.value && agbAkzeptiert.value)

async function bestellen() {
  fehler.value = ''
  if (!pflichtfelderOk.value) { fehler.value = 'Bitte alle Pflichtfelder ausfüllen.'; return }
  if (!emailOk.value) { fehler.value = 'Bitte eine gültige E-Mail-Adresse angeben.'; return }
  if (!teilnehmerOk.value) { fehler.value = 'Bitte für alle Teilnehmer:innen Geschlecht, Vor- und Nachname angeben.'; return }
  if (!agbAkzeptiert.value) { fehler.value = 'Bitte akzeptieren Sie die AGB.'; return }
  busy.value = true
  try {
    // Teilnehmer:innen je Seminar-Position speichern (OrderLine-Custom-Fields).
    for (const l of order.value?.lines ?? []) {
      if (istSeminar(l)) await adjust(l.id, felder[l.id]!.length, felder[l.id])
    }
    const res = await $fetch<{ code: string }>('/api/checkout', {
      method: 'POST',
      body: {
        email: form.email, firstName: form.firstName, lastName: form.lastName, company: form.company,
        address: { streetLine1: form.streetLine1, city: form.city, postalCode: form.postalCode, countryCode: form.countryCode },
        paymentMethod: form.paymentMethod,
      },
    })
    bestellnummer.value = res.code
    clearLocal()
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

    <!-- Nicht angemeldet (Fallback, während Redirect greift) -->
    <div v-else-if="!istAngemeldet" class="mx-auto max-w-md rounded-lg border border-(--ui-border) py-16 text-center">
      <UIcon name="i-lucide-lock" class="mx-auto mb-3 size-10 text-(--ui-text-dimmed)" />
      <p class="mb-4 text-(--ui-text-muted)">Für den Checkout ist eine Anmeldung erforderlich.</p>
      <UButton color="primary" icon="i-lucide-user" @click="login('/kasse')">Anmelden / Registrieren</UButton>
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
            <UFormField
              label="E-Mail"
              required
              class="sm:col-span-2"
              :error="form.email && !emailOk ? 'Bitte eine gültige E-Mail-Adresse angeben.' : undefined"
            >
              <UInput v-model="form.email" type="email" class="w-full" />
            </UFormField>
            <UFormField label="Firma (optional)" class="sm:col-span-2"><UInput v-model="form.company" /></UFormField>
          </div>
        </section>

        <!-- Teilnehmer:innen je Seminar-Position -->
        <section v-for="l in (order?.lines ?? []).filter(istSeminar)" :key="l.id">
          <h2 class="mb-1 text-lg font-medium text-(--ui-text-highlighted)">Teilnehmer:innen — {{ l.productVariant.name }}</h2>
          <p class="mb-3 text-sm text-(--ui-text-muted)">{{ felder[l.id]?.length ?? l.quantity }} Platz/Plätze gebucht.</p>
          <div class="space-y-4">
            <div v-for="(t, i) in felder[l.id] ?? []" :key="i" class="rounded-lg border border-(--ui-border) p-3">
              <div class="mb-2 flex items-center justify-between gap-2">
                <span class="text-sm font-medium text-(--ui-text-highlighted)">Teilnehmer:in {{ i + 1 }}</span>
                <USelectMenu
                  v-if="vorschlagItems.length"
                  :items="vorschlagItems"
                  value-key="value"
                  placeholder="Aus Organisation übernehmen"
                  size="sm"
                  class="w-56"
                  @update:model-value="(val:number) => uebernehmen(l.id, i, val)"
                />
              </div>
              <div class="grid gap-2 sm:grid-cols-2">
                <UFormField label="Geschlecht *" size="sm">
                  <USelect v-model="t.geschlecht" :items="geschlechtOptions" value-key="value" placeholder="bitte wählen" class="w-full" />
                </UFormField>
                <UFormField label="Titel" size="sm"><UInput v-model="t.titel" placeholder="z. B. Dr." /></UFormField>
                <UFormField label="Vorname *" size="sm"><UInput v-model="t.vorname" /></UFormField>
                <UFormField label="Nachname *" size="sm"><UInput v-model="t.nachname" /></UFormField>
                <UFormField label="E-Mail" size="sm"><UInput v-model="t.email" type="email" /></UFormField>
                <UFormField label="Namensschild (Freitext)" size="sm" help="z. B. Wunschname fürs Namensschild">
                  <UInput v-model="t.namensschild" />
                </UFormField>
              </div>
            </div>
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

        <section>
          <UCheckbox v-model="agbAkzeptiert" required>
            <template #label>
              Ich akzeptiere die
              <ULink to="/seite/agb" target="_blank" class="text-(--ui-primary) underline">AGB</ULink>
              und die Widerrufsbelehrung.
            </template>
          </UCheckbox>
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
        <dl class="space-y-1 border-t border-(--ui-border) pt-2 text-sm">
          <div class="flex justify-between"><dt class="text-(--ui-text-muted)">Zwischensumme</dt><dd>{{ euro(order?.subTotalWithTax ?? 0, order?.currencyCode) }}</dd></div>
          <div class="flex justify-between"><dt class="text-(--ui-text-muted)">Versand</dt><dd>{{ euro(order?.shippingWithTax ?? 0, order?.currencyCode) }}</dd></div>
          <div v-for="d in order?.discounts ?? []" :key="d.description" class="flex justify-between text-(--ui-primary)">
            <dt>{{ d.description }}</dt><dd>{{ euro(d.amountWithTax, order!.currencyCode) }}</dd>
          </div>
          <div class="mt-1 flex justify-between border-t border-(--ui-border) pt-2 text-base font-semibold">
            <dt>Gesamt (inkl. USt.)</dt><dd class="text-(--ui-primary)">{{ euro(order?.totalWithTax ?? 0, order?.currencyCode) }}</dd>
          </div>
        </dl>
        <p v-if="!teilnehmerOk" class="mt-3 text-xs text-(--ui-warning)">Bitte alle Teilnehmer:innen vollständig angeben.</p>
        <p v-else-if="!agbAkzeptiert" class="mt-3 text-xs text-(--ui-warning)">Bitte akzeptieren Sie die AGB.</p>
        <UButton block class="mt-4" color="primary" :loading="busy" :disabled="!bestellbar" icon="i-lucide-check" @click="bestellen">
          Zahlungspflichtig bestellen
        </UButton>
        <UButton to="/warenkorb" block class="mt-2" variant="ghost" color="neutral">Zurück zum Warenkorb</UButton>
      </UCard>
    </div>
  </div>
</template>
