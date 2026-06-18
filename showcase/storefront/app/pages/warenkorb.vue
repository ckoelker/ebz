<script setup lang="ts">
// Warenkorb (P4 + Teilnehmer-Erfassung): je Position eine Durchführung mit n Plätzen.
// Pro Platz wird ein:e Teilnehmer:in strukturiert wie im MDM erfasst (Geschlecht/Titel/Vorname/
// Nachname + Namensschild-Freitext). Menge = Anzahl Teilnehmer:innen. Entfernen mit Bestätigung.
import type { Teilnehmer } from '~~/server/utils/order'

const { order, refresh, adjust, remove, busy } = useCart()
await refresh()
useHead({ title: 'Warenkorb' })

const geschlechtOptions = [
  { value: 'WEIBLICH', label: 'weiblich' },
  { value: 'MAENNLICH', label: 'männlich' },
  { value: 'DIVERS', label: 'divers' },
  { value: 'KEINE_ANGABE', label: 'keine Angabe' },
]

function leer(): Teilnehmer {
  return { geschlecht: '', titel: '', vorname: '', nachname: '', namensschild: '', email: '' }
}

// Teilnehmer:innen nur bei Seminaren/Veranstaltungen erfassen — nicht bei physischer Ware (Buch)
// oder Digitalprodukten (Skript/WBT): dort ist der/die Besteller:in der/die Empfänger:in.
function brauchtTeilnehmer(l: { productVariant: { customFields: { fulfillmentType: string | null } } }): boolean {
  return l.productVariant.customFields.fulfillmentType === 'seminar'
}

// Lokale Teilnehmer:innen-Liste je Seminar-Position (Länge = Menge), aus dem JSON-Feld initialisiert.
const felder = reactive<Record<string, Teilnehmer[]>>({})
watchEffect(() => {
  for (const l of order.value?.lines ?? []) {
    if (brauchtTeilnehmer(l) && !felder[l.id]) {
      let liste: Teilnehmer[] = []
      try {
        liste = l.customFields.teilnehmer ? JSON.parse(l.customFields.teilnehmer) : []
      } catch { liste = [] }
      while (liste.length < l.quantity) liste.push(leer())
      felder[l.id] = liste.slice(0, l.quantity)
    }
  }
})

async function speichern(lineId: string) {
  await adjust(lineId, felder[lineId]!.length, felder[lineId])
}
async function teilnehmerHinzufuegen(lineId: string) {
  felder[lineId]!.push(leer())
  await speichern(lineId)
}
async function teilnehmerEntfernen(lineId: string) {
  if (felder[lineId]!.length <= 1) return
  felder[lineId]!.pop()
  await speichern(lineId)
}
// Menge für Nicht-Seminar-Positionen (Buch/Skript/WBT) — ohne Teilnehmer:innen.
async function mengeAendern(lineId: string, quantity: number) {
  if (quantity < 1) return
  await adjust(lineId, quantity)
}

function tnVollstaendig(t: Teilnehmer): boolean {
  return !!(t.geschlecht && t.vorname?.trim() && t.nachname?.trim())
}
const allesVollstaendig = computed(() =>
  (order.value?.lines ?? [])
    .filter(brauchtTeilnehmer)
    .every((l) => (felder[l.id] ?? []).length > 0 && (felder[l.id] ?? []).every(tnVollstaendig)),
)

// Entfernen (Position) mit Bestätigung.
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
              <UBadge color="neutral" variant="subtle">{{ l.productVariant.sku }}</UBadge>
            </div>
            <div class="text-right">
              <p class="font-semibold text-(--ui-primary)">{{ euro(l.linePriceWithTax, order.currencyCode) }}</p>
              <p class="text-xs text-(--ui-text-muted)">{{ euro(l.unitPriceWithTax, order.currencyCode) }} / Platz</p>
            </div>
          </div>

          <!-- Teilnehmer:innen (je Platz eine:r) — nur bei Seminaren/Veranstaltungen -->
          <div v-if="brauchtTeilnehmer(l)" class="mt-4 space-y-4">
            <div
              v-for="(t, i) in felder[l.id] ?? []"
              :key="i"
              class="rounded-lg border border-(--ui-border) p-3"
            >
              <div class="mb-2 flex items-center justify-between">
                <span class="text-sm font-medium text-(--ui-text-highlighted)">Teilnehmer:in {{ i + 1 }}</span>
                <span v-if="!tnVollstaendig(t)" class="text-xs text-(--ui-warning)">Pflichtfelder offen</span>
              </div>
              <div class="grid gap-2 sm:grid-cols-2">
                <UFormField label="Geschlecht *" size="sm">
                  <USelect v-model="t.geschlecht" :items="geschlechtOptions" value-key="value" placeholder="bitte wählen" class="w-full" @update:model-value="speichern(l.id)" />
                </UFormField>
                <UFormField label="Titel" size="sm">
                  <UInput v-model="t.titel" placeholder="z. B. Dr." @blur="speichern(l.id)" />
                </UFormField>
                <UFormField label="Vorname *" size="sm">
                  <UInput v-model="t.vorname" @blur="speichern(l.id)" />
                </UFormField>
                <UFormField label="Nachname *" size="sm">
                  <UInput v-model="t.nachname" @blur="speichern(l.id)" />
                </UFormField>
                <UFormField label="E-Mail" size="sm">
                  <UInput v-model="t.email" type="email" @blur="speichern(l.id)" />
                </UFormField>
                <UFormField label="Namensschild (Freitext)" size="sm" help="z. B. Wunschname fürs Namensschild">
                  <UInput v-model="t.namensschild" @blur="speichern(l.id)" />
                </UFormField>
              </div>
            </div>
          </div>

          <div class="mt-3 flex items-center justify-between">
            <div v-if="brauchtTeilnehmer(l)" class="flex items-center gap-2">
              <UButton size="xs" variant="outline" color="neutral" icon="i-lucide-user-minus" :disabled="busy || (felder[l.id]?.length ?? 1) <= 1" @click="teilnehmerEntfernen(l.id)" />
              <span class="text-sm text-(--ui-text-muted)">{{ felder[l.id]?.length ?? l.quantity }} Teilnehmer:in(nen)</span>
              <UButton size="xs" variant="outline" color="neutral" icon="i-lucide-user-plus" :disabled="busy" @click="teilnehmerHinzufuegen(l.id)" />
            </div>
            <div v-else class="flex items-center gap-2">
              <span class="text-sm text-(--ui-text-muted)">Menge</span>
              <UButton size="xs" variant="outline" color="neutral" icon="i-lucide-minus" :disabled="busy || l.quantity <= 1" @click="mengeAendern(l.id, l.quantity - 1)" />
              <span class="w-6 text-center text-sm">{{ l.quantity }}</span>
              <UButton size="xs" variant="outline" color="neutral" icon="i-lucide-plus" :disabled="busy" @click="mengeAendern(l.id, l.quantity + 1)" />
            </div>
            <UButton size="xs" variant="ghost" color="error" icon="i-lucide-trash-2" @click="zuEntfernen = l.id">Entfernen</UButton>
          </div>
        </UCard>
      </div>

      <UCard class="lg:sticky lg:top-6">
        <h2 class="mb-3 font-medium text-(--ui-text-highlighted)">Zusammenfassung</h2>
        <dl class="space-y-1 text-sm">
          <div class="flex justify-between"><dt class="text-(--ui-text-muted)">Zwischensumme</dt><dd>{{ euro(order.subTotalWithTax, order.currencyCode) }}</dd></div>
          <div class="flex justify-between"><dt class="text-(--ui-text-muted)">Versand</dt><dd>{{ euro(order.shippingWithTax, order.currencyCode) }}</dd></div>
          <div v-for="d in order.discounts" :key="d.description" class="flex justify-between text-(--ui-primary)">
            <dt>{{ d.description }}</dt><dd>{{ euro(d.amountWithTax, order.currencyCode) }}</dd>
          </div>
          <div class="mt-2 flex justify-between border-t border-(--ui-border) pt-2 text-base font-semibold">
            <dt>Gesamt (inkl. USt.)</dt><dd class="text-(--ui-primary)">{{ euro(order.totalWithTax, order.currencyCode) }}</dd>
          </div>
        </dl>
        <UButton to="/kasse" block class="mt-4" color="primary" icon="i-lucide-arrow-right" trailing :disabled="!allesVollstaendig">Zur Kasse</UButton>
        <p v-if="!allesVollstaendig" class="mt-2 text-xs text-(--ui-warning)">Bitte für alle Teilnehmer:innen Geschlecht, Vor- und Nachname ausfüllen.</p>
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
