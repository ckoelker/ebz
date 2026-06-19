import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { VueQueryPlugin } from '@tanstack/vue-query';
import ui from '@nuxt/ui/vue-plugin';
import App from './App.vue';
import { router } from './router';
import { authInit } from './auth';
import './assets/css/main.css';

// EBZ Außenportal (ebz-customers) — Nuxt UI 4 (Vue-only), identischer Stack wie das MDM-Cockpit.
// Server-State via @tanstack/vue-query auf dem generierten orval-Client; Auth via Keycloak-SSO.
//
// Light-Theme erzwingen: Das @nuxt/ui-Vue-Plugin nutzt useDark() (@vueuse/core), das sonst der
// System-Präferenz folgt. Storage-Key vor dem Plugin-Install auf 'light' pinnen → kein Flash, kein Dark.
localStorage.setItem('vueuse-color-scheme', 'light');
document.documentElement.classList.remove('dark');

// authInit() verarbeitet auch den Login-Redirect-Callback; danach erst mounten, damit die Sicht den
// Anmeldestatus kennt.
authInit().finally(() => {
  createApp(App)
    .use(createPinia())
    .use(router)
    .use(VueQueryPlugin)
    .use(ui)
    .mount('#app');
});
