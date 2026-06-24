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
