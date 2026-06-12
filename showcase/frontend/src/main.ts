import { createApp } from 'vue';
import { createPinia } from 'pinia';
import urql from '@urql/vue';
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import ToastService from 'primevue/toastservice';

import 'primeicons/primeicons.css';

import App from './App.vue';
import { router } from './router';
import { shopClient } from './api/client';
import { useAuthStore } from './stores/auth';

const app = createApp(App);

const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(urql, shopClient);
app.use(PrimeVue, {
  theme: { preset: Aura, options: { darkModeSelector: '.app-dark' } },
  locale: { /* de-Locale wird bei Bedarf ergänzt */ },
});
app.use(ToastService);

// §8a-3: Keycloak einmal initialisieren (lautloses check-sso), BEVOR der erste
// Checkout-Guard läuft — sonst würde ein bereits eingeloggter Nutzer fälschlich
// zum Login geschickt.
const auth = useAuthStore(pinia);
auth.init().finally(() => {
  app.mount('#app');
});
