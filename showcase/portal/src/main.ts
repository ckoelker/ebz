import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { VueQueryPlugin } from '@tanstack/vue-query';
import PrimeVue from 'primevue/config';
import Tooltip from 'primevue/tooltip';
import Aura from '@primeuix/themes/aura';
import 'primeicons/primeicons.css';
import App from './App.vue';
import { router } from './router';
import { authInit } from './auth';

// EBZ Außenportal (ebz-customers). authInit() verarbeitet auch den Login-Redirect-Callback; danach
// erst mounten, damit die Sicht den Anmeldestatus kennt.
authInit().finally(() => {
  createApp(App)
    .use(createPinia())
    .use(router)
    .use(VueQueryPlugin)
    .use(PrimeVue, { theme: { preset: Aura } })
    .directive('tooltip', Tooltip)
    .mount('#app');
});
