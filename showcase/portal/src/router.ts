import { createRouter, createWebHistory } from 'vue-router';
import Anfrage from '@/views/Anfrage.vue';
import MeineAzubis from '@/views/MeineAzubis.vue';
import MeineRechnungen from '@/views/MeineRechnungen.vue';
import MeineTrainings from '@/views/MeineTrainings.vue';

// EBZ Außenportal: öffentliche Ausbildungsbetrieb-Anfrage (/) + Login-Bereich „Meine Azubis"
// (/azubis), „Meine Rechnungen" (/rechnungen) und „Meine Trainings" (/trainings: WBT starten via SSO).
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'anfrage', component: Anfrage },
    { path: '/azubis', name: 'azubis', component: MeineAzubis },
    { path: '/rechnungen', name: 'rechnungen', component: MeineRechnungen },
    { path: '/trainings', name: 'trainings', component: MeineTrainings },
  ],
});
