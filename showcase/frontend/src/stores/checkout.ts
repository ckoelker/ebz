import { defineStore } from 'pinia';
import { ref } from 'vue';
import { shopClient } from '@/api/client';
import { graphql, type DocumentType } from '@/gql';
import { unwrap } from '@/api/result';

// Showcase M5 — Checkout-Schritte in der von Vendure verlangten Reihenfolge
// (§8a-12): Adresse → eligibleShippingMethods → Versandart → ArrangingPayment
// → Zahlung. Jeder Union-Rückgabewert läuft durch unwrap() (§8a-7).
// Login/Kundenbindung passiert vorher über den Auth-Store (§8a-3), daher kein
// setCustomerForOrder hier. Operationen schema-validiert via Codegen (§8a-19).

export interface AddressInput {
  fullName: string;
  streetLine1: string;
  streetLine2?: string;
  city: string;
  postalCode: string;
  countryCode: string;
}

const SetShippingAddress = graphql(`
  mutation SetShippingAddress($input: CreateAddressInput!) {
    setOrderShippingAddress(input: $input) {
      __typename
      ... on Order { id state }
      ... on ErrorResult { errorCode message }
    }
  }
`);

const EligibleShipping = graphql(`
  query EligibleShipping {
    eligibleShippingMethods { id name priceWithTax }
  }
`);

const SetShippingMethod = graphql(`
  mutation SetShippingMethod($ids: [ID!]!) {
    setOrderShippingMethod(shippingMethodId: $ids) {
      __typename
      ... on Order { id state shippingWithTax totalWithTax }
      ... on ErrorResult { errorCode message }
    }
  }
`);

const TransitionState = graphql(`
  mutation Transition($state: String!) {
    transitionOrderToState(state: $state) {
      __typename
      ... on Order { id state }
      ... on OrderStateTransitionError { errorCode message transitionError fromState toState }
    }
  }
`);

const EligiblePayment = graphql(`
  query EligiblePayment {
    eligiblePaymentMethods { id code name isEligible }
  }
`);

const AddPayment = graphql(`
  mutation AddPayment($input: PaymentInput!) {
    addPaymentToOrder(input: $input) {
      __typename
      ... on Order { id code state totalWithTax }
      ... on ErrorResult { errorCode message }
    }
  }
`);

export type ShippingQuote = DocumentType<typeof EligibleShipping>['eligibleShippingMethods'][number];
export type PaymentQuote = DocumentType<typeof EligiblePayment>['eligiblePaymentMethods'][number];

export const useCheckoutStore = defineStore('checkout', () => {
  const busy = ref(false);
  const shippingMethods = ref<ShippingQuote[]>([]);
  const paymentMethods = ref<PaymentQuote[]>([]);
  const placedOrderCode = ref<string | null>(null);

  async function setShippingAddress(input: AddressInput) {
    busy.value = true;
    try {
      const res = await shopClient.mutation(SetShippingAddress, { input }).toPromise();
      if (res.error) throw res.error;
      unwrap(res.data!.setOrderShippingAddress);
      // §8a-12: Versandarten sind erst NACH gesetzter Adresse (Zone) eligible.
      const q = await shopClient.query(EligibleShipping, {}, { requestPolicy: 'network-only' }).toPromise();
      if (q.error) throw q.error;
      shippingMethods.value = q.data?.eligibleShippingMethods ?? [];
    } finally { busy.value = false; }
  }

  async function setShippingMethod(id: string) {
    busy.value = true;
    try {
      const res = await shopClient.mutation(SetShippingMethod, { ids: [id] }).toPromise();
      if (res.error) throw res.error;
      unwrap(res.data!.setOrderShippingMethod);
    } finally { busy.value = false; }
  }

  /** Schritt in ArrangingPayment + Laden der eligiblen Zahlarten. */
  async function toArrangingPayment() {
    busy.value = true;
    try {
      const res = await shopClient.mutation(TransitionState, { state: 'ArrangingPayment' }).toPromise();
      if (res.error) throw res.error;
      unwrap(res.data!.transitionOrderToState);
      const q = await shopClient.query(EligiblePayment, {}, { requestPolicy: 'network-only' }).toPromise();
      if (q.error) throw q.error;
      paymentMethods.value = q.data?.eligiblePaymentMethods.filter(m => m.isEligible) ?? [];
    } finally { busy.value = false; }
  }

  /** Zahlung auslösen (Showcase: Rechnung/dummyPaymentHandler, automaticSettle). */
  async function pay(methodCode: string) {
    busy.value = true;
    try {
      const res = await shopClient.mutation(AddPayment, { input: { method: methodCode, metadata: {} } }).toPromise();
      if (res.error) throw res.error;
      const order = unwrap(res.data!.addPaymentToOrder);
      placedOrderCode.value = order.code;
      return order;
    } finally { busy.value = false; }
  }

  return {
    busy, shippingMethods, paymentMethods, placedOrderCode,
    setShippingAddress, setShippingMethod, toArrangingPayment, pay,
  };
});
