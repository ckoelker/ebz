// Mapping fachlicher Zustände → Nuxt-UI-Farben/Badges. Zentral, damit Severity/Branding
// in allen Komponenten konsistent ist.
import type { Health, Status, EinwilligungStatus } from './types'

export type UiColor = 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'error' | 'neutral'

export const healthColor = (h: Health): UiColor =>
  h === 'ok' ? 'success' : h === 'warn' ? 'warning' : 'error'

export const healthLabel = (h: Health): string =>
  h === 'ok' ? 'OK' : h === 'warn' ? 'Achtung' : 'Fehler'

export const statusColor = (s: Status): UiColor =>
  s === 'AKTIV' ? 'success' : s === 'PROVISORISCH' ? 'warning' : s === 'GESPERRT' ? 'error' : 'neutral'

export const einwilligungColor = (s: EinwilligungStatus): UiColor =>
  s === 'ERTEILT' ? 'success' : s === 'AUSSTEHEND' ? 'warning' : 'error'

export const prioColor = (p: 'hoch' | 'mittel' | 'niedrig'): UiColor =>
  p === 'hoch' ? 'error' : p === 'mittel' ? 'warning' : 'neutral'

// Severity → Tailwind-Border-Klasse (für Karten-Linke-Kante). Kein inline-Mapping mehr.
export const healthBorder = (h: Health): string =>
  h === 'ok' ? 'border-l-success' : h === 'warn' ? 'border-l-warning' : 'border-l-error'
