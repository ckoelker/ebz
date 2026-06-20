import { createRouter, createWebHistory } from 'vue-router';
import Anfrage from '@/views/Anfrage.vue';
import MeineAzubis from '@/views/MeineAzubis.vue';
import MeineRechnungen from '@/views/MeineRechnungen.vue';
import MeineTrainings from '@/views/MeineTrainings.vue';
import MeineAktivitaeten from '@/views/MeineAktivitaeten.vue';
import MeineNachrichten from '@/views/MeineNachrichten.vue';
import MeineEinstellungen from '@/views/MeineEinstellungen.vue';

// EBZ Außenportal: öffentliche Ausbildungsbetrieb-Anfrage (/) + Login-Bereich „Meine Azubis"
// (/azubis), „Meine Rechnungen" (/rechnungen), „Meine Trainings" (/trainings: WBT via SSO) und
// „Meine Aktivitäten" (/aktivitaeten: System→Person-Benachrichtigungen, Zeitstrahl + Quittierung).
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'anfrage', component: Anfrage },
    { path: '/azubis', name: 'azubis', component: MeineAzubis },
    { path: '/rechnungen', name: 'rechnungen', component: MeineRechnungen },
    { path: '/trainings', name: 'trainings', component: MeineTrainings },
    { path: '/aktivitaeten', name: 'aktivitaeten', component: MeineAktivitaeten },
    { path: '/nachrichten', name: 'nachrichten', component: MeineNachrichten },
    { path: '/einstellungen', name: 'einstellungen', component: MeineEinstellungen },
  ],
});
