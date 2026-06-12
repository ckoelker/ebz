<script setup lang="ts">
import { onMounted } from 'vue';
import { RouterLink, RouterView } from 'vue-router';
import Toast from 'primevue/toast';
import Button from 'primevue/button';
import { useCartStore } from '@/stores/cart';
import { useAuthStore } from '@/stores/auth';

const cart = useCartStore();
const auth = useAuthStore();

onMounted(() => cart.refresh());
</script>

<template>
  <header class="topbar">
    <RouterLink to="/" class="brand">EBZ&nbsp;Shop <small>Showcase</small></RouterLink>
    <nav>
      <RouterLink to="/" class="nav-link">Katalog</RouterLink>
      <RouterLink to="/warenkorb" class="nav-link">
        <i class="pi pi-shopping-cart" /> Warenkorb
        <span v-if="cart.count" class="badge">{{ cart.count }}</span>
      </RouterLink>
      <span v-if="auth.isLoggedIn" class="nav-link account">
        <i class="pi pi-user" /> {{ auth.customer?.firstName || auth.customer?.emailAddress }}
        <Button label="Logout" text size="small" @click="auth.logout()" />
      </span>
    </nav>
  </header>

  <main class="content">
    <RouterView />
  </main>

  <Toast />
</template>

<style>
:root { color-scheme: light; }
body { margin: 0; font-family: system-ui, -apple-system, 'Segoe UI', sans-serif; background: #f7f7f8; color: #1f2328; }
.topbar { display: flex; align-items: center; justify-content: space-between; padding: 0.75rem 1.5rem; background: #fff; border-bottom: 1px solid #e5e7eb; }
.brand { font-weight: 700; font-size: 1.15rem; text-decoration: none; color: #0f3d2e; }
.brand small { font-weight: 400; color: #6b7280; }
.topbar nav { display: flex; gap: 1.25rem; }
.nav-link { text-decoration: none; color: #374151; font-weight: 500; }
.nav-link.router-link-active { color: #0f3d2e; }
.badge { display: inline-grid; place-items: center; min-width: 1.1rem; height: 1.1rem; padding: 0 .3rem; border-radius: 999px; background: #0f3d2e; color: #fff; font-size: .72rem; }
.account { display: inline-flex; align-items: center; gap: .4rem; color: #6b7280; }
.content { max-width: 1100px; margin: 1.5rem auto; padding: 0 1.5rem; }
</style>
