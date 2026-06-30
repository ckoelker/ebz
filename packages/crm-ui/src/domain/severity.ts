// Mapping fachlicher Zustände → Nuxt-UI-Farben. Zentral, damit Severity/Branding in allen
// geteilten Primitiven konsistent ist. String-tolerant: deckt die (deutschen) Enum-Werte aus
// Storybook-Mock UND mdm-API ab; unbekannte Werte fallen auf neutral.
import type { Health, UiColor } from './types';

export const healthColor = (h: Health): UiColor =>
  h === 'ok' ? 'success' : h === 'warn' ? 'warning' : 'error';

export const healthLabel = (h: Health): string =>
  h === 'ok' ? 'OK' : h === 'warn' ? 'Achtung' : 'Fehler';

export const statusColor = (s?: string): UiColor =>
  s === 'AKTIV' ? 'success' : s === 'PROVISORISCH' ? 'warning' : s === 'GESPERRT' ? 'error' : 'neutral';

export const einwilligungColor = (s?: string): UiColor =>
  s === 'ERTEILT' ? 'success' : s === 'AUSSTEHEND' ? 'warning' : s === 'WIDERRUFEN' ? 'error' : 'neutral';

export const prioColor = (p?: string): UiColor =>
  p === 'hoch' ? 'error' : p === 'mittel' ? 'warning' : 'neutral';

// Severity → Tailwind-Border-Klasse (linke Karten-Kante).
export const healthBorder = (h: Health): string =>
  h === 'ok' ? 'border-l-success' : h === 'warn' ? 'border-l-warning' : 'border-l-error';

// ── Außenportal-/Self-Service-Status (kundennah) ────────────────────────────────────────────────
// Fachliche Status-Semantik ist invariant (eine bezahlte Rechnung ist überall „grün") → hier zentral
// statt per-View-Maps. Gleicher string-toleranter Stil; unbekannt → neutral.

/** Rechnungs-Status (Portal „Meine Rechnungen"). */
export const rechnungStatusColor = (s?: string): UiColor =>
  s === 'BEZAHLT' ? 'success' : s === 'AUSGESTELLT' ? 'info' : 'neutral';

/** Einschreibungs-/WBT-Status (Portal „Meine Trainings"). */
export const einschreibungStatusColor = (s?: string): UiColor =>
  s === 'EINGESCHRIEBEN' ? 'success'
    : s === 'ANGEFORDERT' ? 'warning'
    : s === 'FEHLGESCHLAGEN' ? 'error'
    : 'neutral';

/** Einschreibungs-Status als kundenfreundlicher Text. */
export const einschreibungStatusText = (s?: string): string =>
  s === 'EINGESCHRIEBEN' ? 'verfügbar'
    : s === 'ANGEFORDERT' ? 'wird bereitgestellt …'
    : s === 'FEHLGESCHLAGEN' ? 'Problem — bitte EBZ kontaktieren'
    : (s ?? '');

/** Azubi-/Anmeldungs-Status (Portal „Meine Azubis"). */
export const azubiStatusColor = (s?: string): UiColor =>
  s === 'AKTIV' ? 'success'
    : s === 'BESTAETIGT_EBZ' ? 'info'
    : s === 'ANGEFRAGT' ? 'warning'
    : 'neutral';

/** Aktivitäts-Kategorie (Portal „Meine Aktivitäten"). */
export const aktivitaetKategorieColor = (k?: string): UiColor =>
  k === 'ANMELDUNG' || k === 'EINSCHREIBUNG' ? 'success'
    : k === 'RECHNUNG' ? 'info'
    : k === 'PRUEFUNG' ? 'warning'
    : 'neutral';
