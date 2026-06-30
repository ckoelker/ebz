<script setup lang="ts">
// Produkt-Detailseite (SSR), Speaking-URL /<slug>?termin=<nr> mit canonical auf die Produktseite.
// Rendert Detailblöcke je nach gefüllten Feldern; Vertragsangebote (bestellbar=false) zeigen
// statt Buchung einen Anmelde-/Vertrags-Deeplink. Warenkorb/Checkout folgen in P4.
import PreisBadge from '@customer-ui/ui/PreisBadge.vue'

const route = useRoute()
const slug = computed(() => String(route.params.slug))

const { data: product, error } = await useFetch('/api/product', { query: { slug } })

if (error.value) {
  throw createError({ statusCode: error.value.statusCode || 500, statusMessage: 'Angebot nicht verfügbar', fatal: true })
}

const cf = computed<any>(() => (product.value as any)?.customFields ?? {})
const variants = computed<any[]>(() => (product.value as any)?.variants ?? [])

// Vorauswahl der Durchführung über ?termin=<sku>.
const gewaehlt = ref<string>('')
watchEffect(() => {
  const t = typeof route.query.termin === 'string' ? route.query.termin : ''
  gewaehlt.value = t && variants.value.some((v) => v.sku === t) ? t : variants.value[0]?.sku ?? ''
})
const gewaehlteVariante = computed(() => variants.value.find((v) => v.sku === gewaehlt.value))

const { add, busy } = useCart()
async function inDenWarenkorb() {
  const v = gewaehlteVariante.value
  if (!v) return
  await add(v.id)
  await navigateTo('/warenkorb')
}

const url = useRequestURL()
useHead(() => ({
  title: (product.value as any)?.name,
  link: [{ rel: 'canonical', href: `${url.origin}/${slug.value}` }],
  meta: [{ name: 'description', content: (product.value as any)?.description ?? '' }],
}))

const formatLabel: Record<string, string> = { PRAESENZ: 'Präsenz', ONLINE: 'Online', HYBRID: 'Hybrid' }

const bloecke = computed(() => [
  { titel: 'Inhalte', html: cf.value.inhalteHtml },
  { titel: 'Lernziele', html: cf.value.lernzieleHtml },
  { titel: 'Ihr Nutzen', html: cf.value.nutzenHtml },
  { titel: 'Ablauf / Programm', html: cf.value.ablaufHtml },
  { titel: 'Methodik', html: cf.value.methodikHtml },
  { titel: 'Voraussetzungen', html: cf.value.voraussetzungenHtml },
  { titel: 'Im Preis enthalten', html: cf.value.leistungenHtml },
  { titel: 'Förderung', html: cf.value.foerderhinweisHtml },
  { titel: 'FAQ', html: cf.value.faqHtml },
].filter((b) => b.html))
</script>

<template>
  <div v-if="product" class="grid gap-8 lg:grid-cols-[1fr_20rem]">
    <article class="min-w-0">
      <UButton to="/" variant="link" color="neutral" icon="i-lucide-arrow-left" class="mb-2 -ml-2">Zum Katalog</UButton>
      <div class="mb-2 flex flex-wrap items-center gap-2">
        <UBadge v-if="cf.angebotsnummer" color="neutral" variant="subtle">{{ cf.angebotsnummer }}</UBadge>
        <UBadge
          v-for="fv in (product as any).facetValues?.filter((f:any)=>f.facet.code==='veranstaltungsart')"
          :key="fv.code"
          color="primary"
          variant="subtle"
        >{{ fv.name }}</UBadge>
      </div>
      <h1 class="text-2xl font-semibold text-(--ui-text-highlighted)">{{ (product as any).name }}</h1>
      <p class="mt-1 text-(--ui-text-muted)">{{ (product as any).description }}</p>

      <img
        v-if="(product as any).featuredAsset?.preview"
        :src="`${(product as any).featuredAsset.preview}?preset=large`"
        :alt="(product as any).name"
        class="mt-4 aspect-video w-full rounded-lg object-cover"
      >

      <section v-for="b in bloecke" :key="b.titel" class="mt-6">
        <h2 class="mb-1 text-lg font-medium text-(--ui-text-highlighted)">{{ b.titel }}</h2>
        <!-- Inhalt aus Vendure (Rich-Text-Custom-Fields), serverseitig gerendert -->
        <div class="prose prose-sm max-w-none text-(--ui-text)" v-html="b.html" />
      </section>

      <section v-if="cf.dozenten?.length" class="mt-6">
        <h2 class="mb-1 text-lg font-medium text-(--ui-text-highlighted)">Dozent:innen</h2>
        <ul class="space-y-1">
          <li v-for="d in cf.dozenten" :key="d.name"><span class="font-medium">{{ d.name }}</span> — {{ d.vita }}</li>
        </ul>
      </section>
    </article>

    <aside class="lg:sticky lg:top-6 self-start">
      <UCard>
        <template v-if="cf.bestellbar === false">
          <p class="mb-3 text-sm text-(--ui-text-muted)">Dieses Angebot wird über den Anmelde-/Vertragsprozess gebucht.</p>
          <UButton :to="cf.anmeldungUrl" target="_blank" block color="primary" icon="i-lucide-file-signature">
            Anmeldung / Vertrag
          </UButton>
        </template>

        <template v-else>
          <h3 class="mb-2 font-medium text-(--ui-text-highlighted)">Durchführung wählen</h3>
          <div class="space-y-2">
            <label
              v-for="v in variants"
              :key="v.sku"
              class="flex cursor-pointer items-start gap-2 rounded border border-(--ui-border) p-2 has-[:checked]:border-primary has-[:checked]:ring-1 has-[:checked]:ring-primary"
            >
              <input v-model="gewaehlt" type="radio" :value="v.sku" class="mt-1 accent-(--ui-primary)">
              <span class="flex-1 text-sm">
                <span class="block font-medium">{{ datum(v.customFields?.terminDatum) || 'Termin offen' }}</span>
                <span class="block text-(--ui-text-muted)">
                  {{ v.customFields?.ort }}<template v-if="v.customFields?.veranstaltungsformat"> · {{ formatLabel[v.customFields.veranstaltungsformat] || v.customFields.veranstaltungsformat }}</template>
                </span>
                <PreisBadge :cent="v.priceWithTax" :currency="v.currencyCode" tone="primary" size="sm" class="block" />
              </span>
            </label>
          </div>
          <UButton
            block
            class="mt-3"
            color="primary"
            icon="i-lucide-shopping-cart"
            :loading="busy"
            :disabled="!gewaehlteVariante"
            @click="inDenWarenkorb"
          >
            In den Warenkorb
          </UButton>
        </template>

        <div v-if="cf.ansprechpartner" class="mt-4 border-t border-(--ui-border) pt-3 text-sm">
          <p class="mb-2 font-medium text-(--ui-text-highlighted)">Ihre Ansprechpartnerin</p>
          <div class="flex items-center gap-3">
            <img
              v-if="cf.ansprechpartner.foto?.preview"
              :src="`${cf.ansprechpartner.foto.preview}?preset=thumb`"
              :alt="cf.ansprechpartner.name"
              class="size-12 shrink-0 rounded-full object-cover"
            >
            <span v-else class="flex size-12 shrink-0 items-center justify-center rounded-full bg-(--ui-bg-elevated) text-(--ui-text-dimmed)">
              <UIcon name="i-lucide-user" class="size-6" />
            </span>
            <span>
              <span class="block">{{ cf.ansprechpartner.name }}</span>
              <span class="block text-(--ui-text-muted)">{{ cf.ansprechpartner.telefon }}</span>
              <span class="block text-(--ui-text-muted)">{{ cf.ansprechpartner.email }}</span>
            </span>
          </div>
        </div>
      </UCard>
    </aside>
  </div>
</template>
