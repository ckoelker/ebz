import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { VueQueryPlugin } from '@tanstack/vue-query';
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import 'primeicons/primeicons.css';
import App from './App.vue';

// MDM-Cockpit (Formularverwaltung P1.0) — Gerüst. Komponentenbibliothek PrimeVue (Aura),
// Server-State via @tanstack/vue-query (P1.2 auf dem generierten Client). Die typsichere
// Validierungskette (vee-validate + generierte zod) füllt P1.2 die konkreten Masken.
createApp(App)
  .use(createPinia())
  .use(VueQueryPlugin)
  .use(PrimeVue, { theme: { preset: Aura } })
  .mount('#app');
