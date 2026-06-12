import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { shopClient } from '@/api/client';
import { graphql, type DocumentType } from '@/gql';
import { unwrap } from '@/api/result';

// Showcase M5 — Warenkorb (activeOrder).
// Der Order hängt an der Vendure-Session-Cookie (§8a-2), nicht am Login: er wird
// anonym aufgebaut und beim Checkout-Login dem Kunden zugeordnet (§8a-3).
// Alle Mutationen laufen durch unwrap() → ErrorResult wird zu einer Exception
// mit Vendure-Meldung (§8a-7). Preise sind Cent (§8a-10).
// Operationen sind via graphql()/Codegen schema-validiert + typisiert (§8a-19).

// Gemeinsame Order-Felder. customFields explizit anfordern (§8a-4).
const OrderFields = graphql(`
  fragment OrderFields on Order {
    id
    code
    state
    totalQuantity
    subTotalWithTax
    shippingWithTax
    totalWithTax
    currencyCode
    customFields { enrollmentType trainingCompany }
    lines {
      id
      quantity
      linePriceWithTax
      productVariant { id name }
      featuredAsset { preview }
      customFields { participantName participantEmail }
    }
  }
`);

const ActiveOrderQuery = graphql(`
  query ActiveOrder { activeOrder { ...OrderFields } }
`);

const AddItem = graphql(`
  mutation AddItem($variantId: ID!, $qty: Int!) {
    addItemToOrder(productVariantId: $variantId, quantity: $qty) {
      __typename
      ...OrderFields
      ... on ErrorResult { errorCode message }
    }
  }
`);

const AdjustItem = graphql(`
  mutation AdjustItem($lineId: ID!, $qty: Int!) {
    adjustOrderLine(orderLineId: $lineId, quantity: $qty) {
      __typename
      ...OrderFields
      ... on ErrorResult { errorCode message }
    }
  }
`);

const RemoveItem = graphql(`
  mutation RemoveItem($lineId: ID!) {
    removeOrderLine(orderLineId: $lineId) {
      __typename
      ...OrderFields
      ... on ErrorResult { errorCode message }
    }
  }
`);

// Typen direkt aus dem Fragment ableiten (Single Source of Truth = Schema).
export type ActiveOrder = DocumentType<typeof OrderFields>;
export type OrderLine = ActiveOrder['lines'][number];

export const useCartStore = defineStore('cart', () => {
  const order = ref<ActiveOrder | null>(null);
  const busy = ref(false);

  const count = computed(() => order.value?.totalQuantity ?? 0);
  const isEmpty = computed(() => count.value === 0);

  async function refresh() {
    const res = await shopClient.query(ActiveOrderQuery, {}, { requestPolicy: 'network-only' }).toPromise();
    if (res.error) throw res.error;
    order.value = res.data?.activeOrder ?? null;
  }

  async function addItem(variantId: string, qty = 1) {
    busy.value = true;
    try {
      const res = await shopClient.mutation(AddItem, { variantId, qty }).toPromise();
      if (res.error) throw res.error;
      order.value = unwrap(res.data!.addItemToOrder); // §8a-7
    } finally { busy.value = false; }
  }

  async function adjustItem(lineId: string, qty: number) {
    busy.value = true;
    try {
      const res = await shopClient.mutation(AdjustItem, { lineId, qty }).toPromise();
      if (res.error) throw res.error;
      order.value = unwrap(res.data!.adjustOrderLine);
    } finally { busy.value = false; }
  }

  async function removeItem(lineId: string) {
    busy.value = true;
    try {
      const res = await shopClient.mutation(RemoveItem, { lineId }).toPromise();
      if (res.error) throw res.error;
      order.value = unwrap(res.data!.removeOrderLine);
    } finally { busy.value = false; }
  }

  return { order, busy, count, isEmpty, refresh, addItem, adjustItem, removeItem };
});
