<script setup lang="ts">
// Katalog-Startseite (SSR): serverseitig über /api/catalog (Vendure search). Freitextsuche;
// Facetten-Filter/Pagination folgen in P3.
const route = useRoute()
const term = ref(typeof route.query.q === 'string' ? route.query.q : '')

const { data, refresh, pending } = await useFetch('/api/catalog', {
  query: { term },
})

useHead({ title: 'Kurskatalog' })

function suchen() {
  refresh()
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

    <p class="mb-4 text-sm text-(--ui-text-muted)">{{ data?.totalItems ?? 0 }} Angebote</p>

    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
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
  </div>
</template>
