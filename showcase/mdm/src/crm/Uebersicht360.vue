<script setup lang="ts">
import type { Uebersicht360View } from '@/api/model';

// 360°-Sicht (Plan A18): read-only Buchungen/Anmeldungen + festgeschriebene Rechnungen eines Kontakts.
// Reine Anzeige (keine Schreib-Ops); DSGVO-Scope steckt schon in der API (Firmenkontext ohne Privates).
defineProps<{ data?: Uebersicht360View }>();

const euro = (cent?: number) =>
  ((cent ?? 0) / 100).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });

const anmeldungColor = (s?: string) =>
  s === 'AKTIV' ? 'success' : s === 'ANGEFRAGT' ? 'warning' : s === 'STORNIERT' ? 'error' : 'neutral';
const rechnungColor = (s?: string) =>
  s === 'BEZAHLT' ? 'success' : s === 'STORNIERT' ? 'error' : 'info';
</script>

<template>
  <div class="space-y-4">
    <!-- Buchungen / Anmeldungen -->
    <div>
      <h4 class="text-sm font-semibold text-muted mb-2 flex items-center gap-1.5">
        <UIcon name="i-lucide-graduation-cap" /> Buchungen & Anmeldungen
      </h4>
      <p v-if="!data?.anmeldungen?.length" class="text-sm text-muted">Keine Buchungen.</p>
      <ul v-else class="divide-y divide-default">
        <li v-for="a in data.anmeldungen" :key="a.id" class="flex items-center gap-3 py-2">
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium truncate">
              {{ a.teilnehmerName }}
              <span v-if="a.zeitraum" class="text-muted">· {{ a.zeitraum }}</span>
            </div>
            <div class="text-xs text-muted">
              {{ a.typ }}<span v-if="a.kontextOrganisation"> · {{ a.kontextOrganisation }}</span>
            </div>
          </div>
          <span class="text-sm tabular-nums">{{ euro(a.betragCent) }}</span>
          <UBadge :color="anmeldungColor(a.status)" variant="soft" size="sm">{{ a.status }}</UBadge>
        </li>
      </ul>
    </div>

    <!-- Rechnungen -->
    <div>
      <h4 class="text-sm font-semibold text-muted mb-2 flex items-center gap-1.5">
        <UIcon name="i-lucide-receipt" /> Rechnungen
      </h4>
      <p v-if="!data?.rechnungen?.length" class="text-sm text-muted">Keine Rechnungen.</p>
      <ul v-else class="divide-y divide-default">
        <li v-for="r in data.rechnungen" :key="r.id" class="flex items-center gap-3 py-2">
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium truncate">
              {{ r.nummer }} <span class="text-muted">· {{ r.bereich }}</span>
            </div>
            <div class="text-xs text-muted">
              {{ r.ausstellungsdatum || '—' }}
              <span v-if="r.versandStatus"> · Versand: {{ r.versandStatus }}</span>
            </div>
          </div>
          <span class="text-sm tabular-nums">{{ euro(r.summeCent) }}</span>
          <UBadge :color="rechnungColor(r.status)" variant="soft" size="sm">{{ r.status }}</UBadge>
        </li>
      </ul>
    </div>

    <p class="text-xs text-dimmed">
      Nur-Lese-Sicht · aus dem Firmenkontext werden keine privaten Vorgänge angezeigt (DSGVO, Plan A18).
    </p>
  </div>
</template>
