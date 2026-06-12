<script setup lang="ts">
import { useQuery } from '@urql/vue';
import { computed } from 'vue';
import Button from 'primevue/button';
import Tag from 'primevue/tag';
import { graphql } from '@/gql';
import { formatPrice } from '@/lib/format';
import { useCartStore } from '@/stores/cart';

const props = defineProps<{ slug: string }>();
const cart = useCartStore();

const ProductQuery = graphql(`
  query Product($slug: String!) {
    product(slug: $slug) {
      id
      name
      description
      featuredAsset { preview }
      variants {
        id
        name
        priceWithTax
        customFields { fulfillmentType }
      }
    }
  }
`);

const { data, fetching, error } = useQuery({
  query: ProductQuery,
  variables: computed(() => ({ slug: props.slug })),
});

const product = computed(() => data.value?.product ?? null);
const variant = computed(() => product.value?.variants[0] ?? null);
const fulfillmentType = computed(() => variant.value?.customFields?.fulfillmentType ?? 'physical');

async function addToCart() {
  if (!variant.value) return;
  await cart.addItem(variant.value.id, 1);
}
</script>

<template>
  <p v-if="fetching">Lädt …</p>
  <p v-else-if="error" class="error">Fehler: {{ error.message }}</p>
  <p v-else-if="!product">Produkt nicht gefunden.</p>

  <article v-else class="detail">
    <div class="media">
      <img v-if="product.featuredAsset" :src="product.featuredAsset.preview" :alt="product.name" />
      <i v-else class="pi pi-box placeholder" />
    </div>
    <div class="info">
      <Tag :value="fulfillmentType" />
      <h1>{{ product.name }}</h1>
      <p class="desc">{{ product.description }}</p>
      <strong v-if="variant" class="price">{{ formatPrice(variant.priceWithTax) }}</strong>

      <!-- F1: einfacher Warenkorb-Pfad. Seminar/Abo-Flows folgen je Warengruppe. -->
      <Button
        label="In den Warenkorb"
        icon="pi pi-shopping-cart"
        :loading="cart.busy"
        @click="addToCart"
      />
    </div>
  </article>
</template>

<style scoped>
.detail { display: grid; grid-template-columns: 1fr 1fr; gap: 2rem; }
.media { aspect-ratio: 1; display: grid; place-items: center; background: #f3f4f6; border-radius: 12px; overflow: hidden; }
.media img { width: 100%; height: 100%; object-fit: cover; }
.placeholder { font-size: 4rem; color: #9ca3af; }
.info { display: flex; flex-direction: column; gap: 1rem; align-items: flex-start; }
.price { font-size: 1.6rem; color: #0f3d2e; }
.desc { color: #4b5563; }
.error { color: #b91c1c; }
@media (max-width: 720px) { .detail { grid-template-columns: 1fr; } }
</style>
