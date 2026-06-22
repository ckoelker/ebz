// HubSpot-Sync-Cockpit-Barrel: bindet die per orval GENERIERTE hubspot-sync-Schicht an die Oberfläche.
// Same-origin (Vite-Proxy / nginx → /hubspot) + Auth über den orval-Mutator src/api/http.ts.
// Schreiben verlangt die Rolle `katalog-pflege`.
import type { AxiosError } from 'axios';
import {
  getHubspotSyncAuftraege, postHubspotSyncBackfill, postHubspotSyncRun, postHubspotSyncRetryAuftragId,
  postHubspotSyncContactsPersonId, postHubspotSyncCompaniesOrganisationId, postHubspotSyncErasurePersonId,
} from '@/api/endpoints/hubspot-sync-resource/hubspot-sync-resource';

/** Strikte Auftragszeile fürs Cockpit (gemappt aus dem generierten, durchweg optionalen AuftragDto). */
export interface AuftragDto {
  id: number;
  objektTyp: string;
  operation: string;
  status: string;
  partei: string;
  versuche: number;
  letzterFehler?: string | null;
  erstelltAm?: string | null;
  erledigtAm?: string | null;
}

export interface BackfillErgebnis {
  kontakte: number;
  firmen: number;
}

/** Fehler mit HTTP-Status, damit Views 401 (→ Login) / 403 unterscheiden können. */
export class ApiFehler extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

async function run<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    const ax = e as AxiosError<{ message?: string }>;
    const status = ax.response?.status;
    throw new ApiFehler(ax.response?.data?.message ?? `Anfrage fehlgeschlagen (HTTP ${status ?? '?'}).`, status);
  }
}

export const auftraege = async (): Promise<AuftragDto[]> =>
  ((await run(() => getHubspotSyncAuftraege())) ?? []).map((a) => ({
    id: a.id ?? 0,
    objektTyp: a.objektTyp ?? '—',
    operation: a.operation ?? '—',
    status: a.status ?? '—',
    partei: a.partei ?? '—',
    versuche: a.versuche ?? 0,
    letzterFehler: a.letzterFehler,
    erstelltAm: a.erstelltAm,
    erledigtAm: a.erledigtAm,
  }));

export const backfill = async (): Promise<BackfillErgebnis> => {
  const r = (await run(() => postHubspotSyncBackfill())) as Record<string, number>;
  return { kontakte: r.kontakte ?? 0, firmen: r.firmen ?? 0 };
};

export const verarbeiten = async (): Promise<{ verarbeitet: number }> => {
  const r = (await run(() => postHubspotSyncRun())) as Record<string, number>;
  return { verarbeitet: r.verarbeitet ?? 0 };
};

export const retry = (auftragId: number): Promise<unknown> =>
  run(() => postHubspotSyncRetryAuftragId(auftragId));

export const syncContact = (personId: number): Promise<unknown> =>
  run(() => postHubspotSyncContactsPersonId(personId));

export const syncCompany = (organisationId: number): Promise<unknown> =>
  run(() => postHubspotSyncCompaniesOrganisationId(organisationId));

export const erasure = (personId: number): Promise<unknown> =>
  run(() => postHubspotSyncErasurePersonId(personId));

/** ISO-Zeitstempel → „22.06.2026, 14:10" (Europe/Berlin, de-DE). */
export const zeit = (iso?: string | null): string =>
  iso
    ? new Date(iso).toLocaleString('de-DE', {
        dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Berlin',
      })
    : '—';
