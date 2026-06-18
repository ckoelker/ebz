<script setup lang="ts">
// Katalog-Startseite (SSR, P3): Freitext-/Angebotsnr-Suche + Facetten-Sidebar
// (Veranstaltungsart/Thema/Branche/Region/Format) + Sortierung + Pagination.
// Sämtlicher Filterzustand liegt in der URL-Query → SSR, teilbar, Back-Button-fähig.
const route = useRoute()
const router = useRouter()

const FACET_KEYS = ['veranstaltungsart', 'thema', 'branche', 'region', 'format'] as const

// Freitextfeld lokal gebunden; Übernahme in die URL erst bei „Suchen".
const term = ref(typeof route.query.q === 'string' ? route.query.q : '')
watch(() => route.query.q, (v) => { term.value = typeof v === 'string' ? v : '' })

// Server-Parameter aus der URL ableiten (reaktiv → useFetch lädt bei Änderung neu).
const apiQuery = computed<Record<string, string>>(() => {
  const out: Record<string, string> = {}
  if (typeof route.query.q === 'string' && route.query.q) out.term = route.query.q
  if (typeof route.query.collection === 'string') out.collection = route.query.collection
  if (typeof route.query.sort === 'string') out.sort = route.query.sort
  if (typeof route.query.page === 'string') out.page = route.query.page
  for (const k of FACET_KEYS) {
    const v = route.query[k]
    if (typeof v === 'string' && v) out[k] = v
  }
  return out
})

const { data, pending } = await useFetch('/api/catalog', { query: apiQuery })

useHead({ title: 'Kurskatalog' })

// URL-Query gezielt patchen (leere Werte entfernen); Seite i. d. R. auf 1 zurück.
function patchQuery(patch: Record<string, string | undefined>) {
  const next: Record<string, string> = {}
  for (const [k, v] of Object.entries({ ...route.query, ...patch })) {
    if (typeof v === 'string' && v) next[k] = v
  }
  router.push({ query: next })
}

function suchen() {
  patchQuery({ q: term.value || undefined, page: undefined })
}

function selectedFor(code: string): string[] {
  const v = route.query[code]
  return typeof v === 'string' && v ? v.split(',') : []
}

function toggleFacet(code: string, id: string) {
  const cur = selectedFor(code)
  const next = cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]
  patchQuery({ [code]: next.length ? next.join(',') : undefined, page: undefined })
}

function setSort(value: string) {
  // 'relevanz' = Default → kein sort-Param in der URL.
  patchQuery({ sort: value && value !== 'relevanz' ? value : undefined, page: undefined })
}

function setPage(p: number) {
  patchQuery({ page: p > 1 ? String(p) : undefined })
}

const sortOptions = [
  { label: 'Relevanz', value: 'relevanz' },
  { label: 'Titel (A–Z)', value: 'name-asc' },
  { label: 'Preis aufsteigend', value: 'price-asc' },
  { label: 'Preis absteigend', value: 'price-desc' },
]
const sortValue = computed(() => (typeof route.query.sort === 'string' ? route.query.sort : 'relevanz'))

const aktiveFilter = computed(() => FACET_KEYS.reduce((n, k) => n + selectedFor(k).length, 0))
function filterZuruecksetzen() {
  const next: Record<string, string> = {}
  if (typeof route.query.q === 'string' && route.query.q) next.q = route.query.q
  router.push({ query: next })
}
</script>

<template>
  <div>
    <div class="mb-6">
      <h1 class="text-2xl font-semibold text-(--ui-text-highlighted)">Weiterbildung & Studium</h1>
      <p class="text-(--ui-text-muted)">Seminare, Lehrgänge, Tagungen und Studiengänge der EBZ Akademie.</p>
    </div>

    <form class="mb-6 flex gap-2" @submit.prevent="suchen">
      <UInput v-model="term" placeholder="Suche (Thema, Titel, Angebotsnummer …)" class="flex-1" icon="i-lucide-search" />
      <UButton type="submit" :loading="pending">Suchen</UButton>
    </form>

    <div class="grid gap-8 lg:grid-cols-[16rem_1fr]">
      <!-- Facetten-Sidebar -->
      <aside class="space-y-6">
        <div class="flex items-center justify-between">
          <h2 class="font-semibold text-(--ui-text-highlighted)">Filter</h2>
          <UButton
            v-if="aktiveFilter > 0"
            size="xs"
            variant="ghost"
            color="neutral"
            icon="i-lucide-x"
            @click="filterZuruecksetzen"
          >
            {{ aktiveFilter }} zurücksetzen
          </UButton>
        </div>

        <div v-for="group in data?.facetGroups ?? []" :key="group.code">
          <h3 class="mb-2 text-sm font-medium text-(--ui-text-highlighted)">{{ group.name }}</h3>
          <div class="space-y-1.5">
            <label
              v-for="fv in group.values"
              :key="fv.id"
              class="flex cursor-pointer items-center gap-2 text-sm text-(--ui-text-toned)"
            >
              <UCheckbox
                :model-value="selectedFor(group.code).includes(fv.id)"
                @update:model-value="toggleFacet(group.code, fv.id)"
              />
              <span class="flex-1">{{ fv.name }}</span>
              <span class="text-xs text-(--ui-text-dimmed)">{{ fv.count }}</span>
            </label>
          </div>
        </div>

        <p v-if="(data?.facetGroups?.length ?? 0) === 0" class="text-sm text-(--ui-text-muted)">
          Keine Filter verfügbar.
        </p>
      </aside>

      <!-- Trefferliste -->
      <div>
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
          <p class="text-sm text-(--ui-text-muted)">{{ data?.totalItems ?? 0 }} Angebote</p>
          <USelect
            :model-value="sortValue"
            :items="sortOptions"
            value-key="value"
            class="w-52"
            @update:model-value="setSort($event as string)"
          />
        </div>

        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          <UCard
            v-for="item in data?.items ?? []"
            :key="item.slug"
            class="flex flex-col transition hover:ring-2 hover:ring-primary/30"
          >
            <NuxtLink :to="`/${item.slug}`" class="flex h-full flex-col gap-2">
              <h2 class="font-medium text-(--ui-text-highlighted) line-clamp-2">{{ item.productName }}</h2>
              <p class="flex-1 text-sm text-(--ui-text-muted) line-clamp-3" v-text="item.description" />
              <div class="mt-2 flex items-center justify-between">
                <UBadge color="neutral" variant="subtle">{{ item.sku }}</UBadge>
                <span class="font-semibold text-(--ui-primary)">{{ preis(item.priceWithTax, item.currencyCode) }}</span>
              </div>
            </NuxtLink>
          </UCard>
        </div>

        <p v-if="(data?.items?.length ?? 0) === 0" class="py-12 text-center text-(--ui-text-muted)">
          Keine Angebote gefunden.
        </p>

        <div v-if="(data?.pageCount ?? 1) > 1" class="mt-8 flex justify-center">
          <UPagination
            :page="data?.page ?? 1"
            :items-per-page="data?.pageSize ?? 12"
            :total="data?.totalItems ?? 0"
            @update:page="setPage"
          />
        </div>
      </div>
    </div>
  </div>
</template>
