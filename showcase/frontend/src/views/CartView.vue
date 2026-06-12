<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Button from 'primevue/button';
import { useCartStore } from '@/stores/cart';
import { formatPrice } from '@/lib/format';

const cart = useCartStore();
const router = useRouter();

onMounted(() => cart.refresh());
</script>

<template>
  <h1>Warenkorb</h1>

  <p v-if="cart.isEmpty">Dein Warenkorb ist leer. <RouterLink to="/">Zum Katalog</RouterLink></p>

  <template v-else>
    <table class="lines">
      <thead>
        <tr><th>Artikel</th><th>Menge</th><th>Preis</th><th></th></tr>
      </thead>
      <tbody>
        <tr v-for="line in cart.order?.lines" :key="line.id">
          <td>{{ line.productVariant.name }}</td>
          <td>
            <Button icon="pi pi-minus" text rounded size="small"
              :disabled="cart.busy" @click="cart.adjustItem(line.id, line.quantity - 1)" />
            {{ line.quantity }}
            <Button icon="pi pi-plus" text rounded size="small"
              :disabled="cart.busy" @click="cart.adjustItem(line.id, line.quantity + 1)" />
          </td>
          <td>{{ formatPrice(line.linePriceWithTax) }}</td>
          <td>
            <Button icon="pi pi-trash" text rounded severity="danger" size="small"
              :disabled="cart.busy" @click="cart.removeItem(line.id)" />
          </td>
        </tr>
      </tbody>
    </table>

    <div class="summary">
      <div><span>Zwischensumme</span><span>{{ formatPrice(cart.order!.subTotalWithTax) }}</span></div>
      <div><span>Versand</span><span>{{ formatPrice(cart.order!.shippingWithTax) }}</span></div>
      <div class="total"><span>Gesamt (inkl. MwSt.)</span><span>{{ formatPrice(cart.order!.totalWithTax) }}</span></div>
      <!-- §8a-3: Login erst hier; der Checkout-Guard erzwingt die Anmeldung. -->
      <Button label="Zur Kasse" icon="pi pi-arrow-right" iconPos="right"
        @click="router.push({ name: 'checkout' })" />
    </div>
  </template>
</template>

<style scoped>
.lines { width: 100%; border-collapse: collapse; background: #fff; border-radius: 10px; overflow: hidden; }
.lines th, .lines td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid #eef0f2; }
.lines th { font-size: .8rem; text-transform: uppercase; color: #6b7280; }
.summary { margin-top: 1.5rem; margin-left: auto; max-width: 340px; display: flex; flex-direction: column; gap: 0.5rem; }
.summary > div { display: flex; justify-content: space-between; }
.summary .total { font-weight: 700; font-size: 1.1rem; border-top: 1px solid #e5e7eb; padding-top: 0.5rem; }
.summary .p-button { margin-top: 0.5rem; }
</style>
