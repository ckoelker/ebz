import { createRouter, createWebHistory } from 'vue-router';
import AngeboteListe from '@/views/AngeboteListe.vue';
import AngebotPflege from '@/views/AngebotPflege.vue';

// MDM-Cockpit P1.2: Liste + Pflege (Neu /pflege/:typ, Bearbeiten /pflege/:typ/:id).
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'liste', component: AngeboteListe },
    { path: '/pflege/:typ/:id?', name: 'pflege', component: AngebotPflege, props: true },
  ],
});
