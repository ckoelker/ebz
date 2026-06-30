import type { AxiosError } from 'axios';

// Extrahiert eine anzeigbare Fehlermeldung aus einem fehlgeschlagenen API-Call. Der Quarkus-
// RegelVerletzungMapper liefert je nach Fall einen String oder ein {message}-Objekt; 401 → Login.
export function fehlerText(e: unknown): string {
  const ax = e as AxiosError<{ message?: string } | string>;
  const data = ax?.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  if (data && typeof data === 'object' && data.message) return data.message;
  return (e as Error)?.message ?? 'Unbekannter Fehler.';
}

export function istUnauth(e: unknown): boolean {
  return (e as AxiosError)?.response?.status === 401;
}
