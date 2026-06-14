import { createRouter, createWebHistory } from 'vue-router';
import AngeboteListe from '@/views/AngeboteListe.vue';
import AngebotPflege from '@/views/AngebotPflege.vue';
import DublettenReview from '@/views/DublettenReview.vue';
import AnmeldungenBestaetigung from '@/views/AnmeldungenBestaetigung.vue';

// MDM-Cockpit: Bildungsangebote (Liste/Pflege) + HITL-Cockpit „Anmeldung Berufsschule" (Schritt I:
// Dubletten-Review, EBZ-Bestätigung der Anmeldungen).
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'liste', component: AngeboteListe },
    { path: '/pflege/:typ/:id?', name: 'pflege', component: AngebotPflege, props: true },
    { path: '/reviews', name: 'reviews', component: DublettenReview },
    { path: '/anmeldungen', name: 'anmeldungen', component: AnmeldungenBestaetigung },
  ],
});
