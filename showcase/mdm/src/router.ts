import { createRouter, createWebHistory } from 'vue-router';
import Kundenstamm from '@/views/crm/Kundenstamm.vue';
import AngeboteListe from '@/views/AngeboteListe.vue';
import AngebotPflege from '@/views/AngebotPflege.vue';
import DublettenReview from '@/views/DublettenReview.vue';
import AnmeldungenBestaetigung from '@/views/AnmeldungenBestaetigung.vue';
import AdminNachrichten from '@/views/AdminNachrichten.vue';
import AdminVerteiler from '@/views/AdminVerteiler.vue';
import AdminBestaetigungen from '@/views/AdminBestaetigungen.vue';

// MDM-Cockpit. Startseite = CRM-Kernmaske (m:n-Kundenstamm, über die Storybook-Abnahme abgestimmt).
// Die übrigen Masken (Bildungsangebote, HITL „Anmeldung Berufsschule") sind funktionale Prototypen
// und im Menü als „nicht mit Kunde abgestimmt" gekennzeichnet.
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'crm', component: Kundenstamm },
    { path: '/angebote', name: 'bildung', component: AngeboteListe },
    { path: '/pflege/:typ/:id?', name: 'pflege', component: AngebotPflege, props: true },
    { path: '/reviews', name: 'reviews', component: DublettenReview },
    { path: '/anmeldungen', name: 'anmeldungen', component: AnmeldungenBestaetigung },
    { path: '/nachrichten', name: 'nachrichten', component: AdminNachrichten },
    { path: '/verteiler', name: 'verteiler', component: AdminVerteiler },
    { path: '/bestaetigungen', name: 'bestaetigungen', component: AdminBestaetigungen },
  ],
});
