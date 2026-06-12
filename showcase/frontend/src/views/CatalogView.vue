<script setup lang="ts">
import { useQuery } from '@urql/vue';
import { RouterLink } from 'vue-router';
import Tag from 'primevue/tag';
import { graphql, type DocumentType } from '@/gql';
import { formatPrice } from '@/lib/format';

// Öffentlicher Katalog (kein Login nötig). Liest priceWithTax (Brutto, §8a-5/10)
// und den UI-Discriminator fulfillmentType aus den ProductVariant-Custom-Fields
// (§8a-4). Operation schema-validiert + typisiert via Codegen (§8a-19).
const Catalog = graphql(`
  query Catalog {
    products(options: { take: 50 }) {
      items {
        id
        name
        slug
        featuredAsset { preview }
        variants {
          id
          priceWithTax
          customFields { fulfillmentType }
        }
      }
    }
  }
`);

type Product = DocumentType<typeof Catalog>['products']['items'][number];

const { data, fetching, error } = useQuery({ query: Catalog, variables: {} });

const FULFILLMENT_LABEL: Record<string, { label: string; severity: string }> = {
  physical: { label: 'Versand', severity: 'info' },
  digital: { label: 'Download', severity: 'success' },
  seminar: { label: 'Seminar', severity: 'warn' },
  subscription: { label: 'Abo / Raten', severity: 'danger' },
};

function fulfillment(p: Product) {
  const t = p.variants[0]?.customFields?.fulfillmentType ?? 'physical';
  return FULFILLMENT_LABEL[t] ?? { label: t, severity: 'secondary' };
}
function price(p: Product) {
  return p.variants[0] ? formatPrice(p.variants[0].priceWithTax) : '';
}
</script>

<template>
  <h1>Katalog</h1>

  <p v-if="fetching">Lädt …</p>
  <p v-else-if="error" class="error">Fehler beim Laden: {{ error.message }}</p>

  <div v-else class="grid">
    <RouterLink
      v-for="p in data?.products.items"
      :key="p.id"
      :to="{ name: 'product', params: { slug: p.slug } }"
      class="card"
    >
      <div class="thumb">
        <img v-if="p.featuredAsset" :src="p.featuredAsset.preview" :alt="p.name" />
        <i v-else class="pi pi-box placeholder" />
      </div>
      <Tag :value="fulfillment(p).label" :severity="fulfillment(p).severity" />
      <h3>{{ p.name }}</h3>
      <strong>{{ price(p) }}</strong>
    </RouterLink>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1.25rem; }
.card { display: flex; flex-direction: column; gap: 0.5rem; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 1rem; text-decoration: none; color: inherit; transition: box-shadow .15s; }
.card:hover { box-shadow: 0 4px 14px rgba(0,0,0,.08); }
.thumb { aspect-ratio: 4/3; display: grid; place-items: center; background: #f3f4f6; border-radius: 8px; overflow: hidden; }
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.placeholder { font-size: 2.5rem; color: #9ca3af; }
.card h3 { margin: 0; font-size: 1rem; }
.error { color: #b91c1c; }
</style>
