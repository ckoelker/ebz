import { createRouter, createWebHistory } from 'vue-router';
import Anfrage from '@/views/Anfrage.vue';
import MeineAzubis from '@/views/MeineAzubis.vue';

// EBZ Außenportal: öffentliche Ausbildungsbetrieb-Anfrage (/) + Login-Bereich „Meine Azubis"
// (/azubis: anmelden + Vertrag bestätigen). Künftig weitere Selbst-Service-Funktionen.
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'anfrage', component: Anfrage },
    { path: '/azubis', name: 'azubis', component: MeineAzubis },
  ],
});
