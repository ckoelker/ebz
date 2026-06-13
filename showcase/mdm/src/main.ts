import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { VueQueryPlugin } from '@tanstack/vue-query';
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import 'primeicons/primeicons.css';
import App from './App.vue';
import { router } from './router';

// MDM-Cockpit (Formularverwaltung P1.2) — Liste + Pflege der Bildungsangebote.
// PrimeVue (Aura), Server-State via @tanstack/vue-query auf dem generierten Client,
// typsichere Validierung via vee-validate + generierte zod.
createApp(App)
  .use(createPinia())
  .use(router)
  .use(VueQueryPlugin)
  .use(PrimeVue, { theme: { preset: Aura } })
  .mount('#app');
