<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useToast } from 'primevue/usetoast';
import InputText from 'primevue/inputtext';
import Button from 'primevue/button';
import RadioButton from 'primevue/radiobutton';
import { useAuthStore } from '@/stores/auth';
import { useCartStore } from '@/stores/cart';
import { useCheckoutStore, type AddressInput } from '@/stores/checkout';
import { formatPrice } from '@/lib/format';
import { toUserMessage } from '@/api/result';

// F1-Checkout (§8a-12). Zugang nur mit Login (Router-Guard, §8a-3).
const auth = useAuthStore();
const cart = useCartStore();
const checkout = useCheckoutStore();
const toast = useToast();

type Step = 'address' | 'shipping' | 'payment' | 'done';
const step = ref<Step>('address');
const selectedShipping = ref<string | null>(null);
const selectedPayment = ref<string | null>(null);

const address = ref<AddressInput>({
  fullName: '',
  streetLine1: '',
  city: '',
  postalCode: '',
  countryCode: 'DE',
});

onMounted(async () => {
  await cart.refresh();
  // Name aus dem Keycloak-Kundenprofil vorbelegen (Komfort).
  if (auth.customer && !address.value.fullName) {
    address.value.fullName = `${auth.customer.firstName} ${auth.customer.lastName}`.trim();
  }
});

function fail(e: unknown) {
  toast.add({ severity: 'error', summary: 'Fehler', detail: toUserMessage(e), life: 5000 });
}

async function submitAddress() {
  try {
    await checkout.setShippingAddress({ ...address.value });
    selectedShipping.value = checkout.shippingMethods[0]?.id ?? null;
    step.value = 'shipping';
  } catch (e) { fail(e); }
}

async function submitShipping() {
  if (!selectedShipping.value) return;
  try {
    await checkout.setShippingMethod(selectedShipping.value);
    await checkout.toArrangingPayment();
    selectedPayment.value = checkout.paymentMethods[0]?.code ?? null;
    step.value = 'payment';
  } catch (e) { fail(e); }
}

async function submitPayment() {
  if (!selectedPayment.value) return;
  try {
    await checkout.pay(selectedPayment.value);
    await cart.refresh();
    step.value = 'done';
  } catch (e) { fail(e); }
}
</script>

<template>
  <h1>Kasse</h1>

  <p class="who">Angemeldet als <strong>{{ auth.customer?.emailAddress }}</strong></p>

  <!-- Schritt 1: Lieferadresse -->
  <section v-if="step === 'address'" class="panel">
    <h2>1 · Lieferadresse</h2>
    <div class="form">
      <label>Name<InputText v-model="address.fullName" /></label>
      <label>Straße & Hausnr.<InputText v-model="address.streetLine1" /></label>
      <div class="row">
        <label class="plz">PLZ<InputText v-model="address.postalCode" /></label>
        <label class="city">Ort<InputText v-model="address.city" /></label>
      </div>
    </div>
    <Button label="Weiter zum Versand" icon="pi pi-arrow-right" iconPos="right"
      :loading="checkout.busy" @click="submitAddress" />
  </section>

  <!-- Schritt 2: Versandart -->
  <section v-else-if="step === 'shipping'" class="panel">
    <h2>2 · Versandart</h2>
    <div v-for="m in checkout.shippingMethods" :key="m.id" class="option">
      <RadioButton v-model="selectedShipping" :inputId="m.id" :value="m.id" />
      <label :for="m.id">{{ m.name }} — {{ formatPrice(m.priceWithTax) }}</label>
    </div>
    <Button label="Weiter zur Zahlung" icon="pi pi-arrow-right" iconPos="right"
      :loading="checkout.busy" :disabled="!selectedShipping" @click="submitShipping" />
  </section>

  <!-- Schritt 3: Zahlung -->
  <section v-else-if="step === 'payment'" class="panel">
    <h2>3 · Zahlung</h2>
    <div class="summary" v-if="cart.order">
      <div><span>Zwischensumme</span><span>{{ formatPrice(cart.order.subTotalWithTax) }}</span></div>
      <div><span>Versand</span><span>{{ formatPrice(cart.order.shippingWithTax) }}</span></div>
      <div class="total"><span>Gesamt</span><span>{{ formatPrice(cart.order.totalWithTax) }}</span></div>
    </div>
    <div v-for="m in checkout.paymentMethods" :key="m.id" class="option">
      <RadioButton v-model="selectedPayment" :inputId="m.code" :value="m.code" />
      <label :for="m.code">{{ m.name }}</label>
    </div>
    <Button label="Kostenpflichtig bestellen" icon="pi pi-check"
      :loading="checkout.busy" :disabled="!selectedPayment" @click="submitPayment" />
  </section>

  <!-- Schritt 4: Bestätigung -->
  <section v-else class="panel">
    <div class="success">
      <i class="pi pi-check-circle" />
      <div>
        <strong>Vielen Dank!</strong>
        <p>Deine Bestellung <strong>{{ checkout.placedOrderCode }}</strong> ist eingegangen.</p>
      </div>
    </div>
    <RouterLink to="/">Zurück zum Katalog</RouterLink>
  </section>
</template>

<style scoped>
.who { color: #6b7280; }
.panel { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 1.5rem; max-width: 540px; }
.panel h2 { margin-top: 0; font-size: 1.1rem; }
.form { display: flex; flex-direction: column; gap: 1rem; margin-bottom: 1.5rem; }
.form label { display: flex; flex-direction: column; gap: 0.35rem; font-size: 0.9rem; color: #374151; }
.form .row { display: flex; gap: 1rem; }
.form .plz { width: 8rem; }
.form .city { flex: 1; }
.form :deep(.p-inputtext) { width: 100%; }
.option { display: flex; align-items: center; gap: 0.6rem; padding: 0.5rem 0; }
.summary { margin-bottom: 1.25rem; display: flex; flex-direction: column; gap: 0.4rem; }
.summary > div { display: flex; justify-content: space-between; }
.summary .total { font-weight: 700; border-top: 1px solid #e5e7eb; padding-top: 0.4rem; }
.success { display: flex; gap: 0.85rem; align-items: flex-start; background: #ecfdf3; border: 1px solid #abefc6; border-radius: 8px; padding: 1rem 1.25rem; margin-bottom: 1rem; }
.success .pi { color: #079455; font-size: 1.5rem; margin-top: 0.1rem; }
.success p { margin: 0.25rem 0 0; color: #1f2328; }
</style>
