import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { VueQueryPlugin } from '@tanstack/vue-query';
import ui from '@nuxt/ui/vue-plugin';
import App from './App.vue';
import { router } from './router';
import { authInit } from './auth';
import './assets/css/main.css';

// MDM-Cockpit (Formularverwaltung P1.2) — Liste + Pflege der Bildungsangebote.
// Nuxt UI 4 (Vue-only), Server-State via @tanstack/vue-query auf dem generierten Client,
// typsichere Validierung via vee-validate + generierte zod, RBAC via Keycloak-SSO.
// authInit() verarbeitet auch den Login-Redirect-Callback; danach erst mounten.
authInit().finally(() => {
  createApp(App)
    .use(createPinia())
    .use(router)
    .use(VueQueryPlugin)
    .use(ui)
    .mount('#app');
});
