// Rechnungs-Cockpit-Barrel: bindet die per orval GENERIERTE rechnung-Schicht (axios-Funktionen +
// Modelle/Enums) an die Oberfläche. Same-origin (Vite-Proxy / nginx → /rechnung) + Auth über den
// orval-Mutator src/api/http.ts. Schreiben verlangt die Rolle `rechnung-pflege`.
import type { AxiosError } from 'axios';
import {
  getRechnungRechnungen, getRechnungRechnungenId, getRechnungRechnungenIdZugferd,
  postRechnungRechnungen, postRechnungRechnungenIdAusstellen, postRechnungRechnungenIdVersenden,
  postRechnungRechnungenIdBezahlen, postRechnungRechnungenIdStorno, postRechnungRechnungenIdGutschrift,
  postRechnungRechnungenIdNachberechnung, postRechnungRechnungenIdPositionen, getRechnungDebitoren,
} from '@/api/endpoints/rechnung-resource/rechnung-resource';
import { RechnungStatus, Bereich1 as Bereich, Steuerfall, Leistungsart } from '@/api/model';
import type {
  RechnungDto, RechnungPositionDto, ManuellePositionDto, KorrekturRequest, ZahlungseingangDto,
  SonderrechnungDto, DebitorDto,
} from '@/api/model';

export { RechnungStatus, Bereich, Steuerfall, Leistungsart };
export type {
  RechnungDto, RechnungPositionDto, ManuellePositionDto, KorrekturRequest, ZahlungseingangDto,
  SonderrechnungDto, DebitorDto,
};

/** Fehler mit HTTP-Status, damit Views 401 (→ Login) / 403 / 404 / 409 unterscheiden können. */
export class ApiFehler extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
  }
}

// orval-Funktionen werfen AxiosError bei non-2xx → in ApiFehler (mit Status + Server-Meldung) übersetzen.
async function run<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    const ax = e as AxiosError<{ message?: string }>;
    const status = ax.response?.status;
    throw new ApiFehler(ax.response?.data?.message ?? `Anfrage fehlgeschlagen (HTTP ${status ?? '?'}).`, status);
  }
}

export const rechnungen = async (status?: RechnungStatus, bereich?: Bereich): Promise<RechnungDto[]> =>
  ((await run(() => getRechnungRechnungen({
    status: status ?? undefined,
    bereich: bereich ?? undefined,
  }))) as RechnungDto[]) ?? [];

export const rechnung = async (id: number): Promise<RechnungDto> =>
  (await run(() => getRechnungRechnungenId(id))) as RechnungDto;

export const debitoren = async (): Promise<DebitorDto[]> =>
  ((await run(() => getRechnungDebitoren())) as DebitorDto[]) ?? [];

export const sonderrechnungAnlegen = (body: SonderrechnungDto): Promise<unknown> =>
  run(() => postRechnungRechnungen(body));

export const positionErgaenzen = (id: number, pos: ManuellePositionDto): Promise<RechnungDto> =>
  run(() => postRechnungRechnungenIdPositionen(id, pos)) as Promise<RechnungDto>;

export const ausstellen = (id: number): Promise<RechnungDto> =>
  run(() => postRechnungRechnungenIdAusstellen(id)) as Promise<RechnungDto>;

export const versenden = (id: number): Promise<RechnungDto> =>
  run(() => postRechnungRechnungenIdVersenden(id)) as Promise<RechnungDto>;

export const bezahlen = (id: number, body: ZahlungseingangDto): Promise<RechnungDto> =>
  run(() => postRechnungRechnungenIdBezahlen(id, body)) as Promise<RechnungDto>;

export const stornieren = (id: number): Promise<RechnungDto> =>
  run(() => postRechnungRechnungenIdStorno(id)) as Promise<RechnungDto>;

export const gutschrift = (id: number, body: KorrekturRequest): Promise<RechnungDto> =>
  run(() => postRechnungRechnungenIdGutschrift(id, body)) as Promise<RechnungDto>;

export const nachberechnung = (id: number, body: KorrekturRequest): Promise<RechnungDto> =>
  run(() => postRechnungRechnungenIdNachberechnung(id, body)) as Promise<RechnungDto>;

/** Lädt das validierte ZUGFeRD-PDF als Blob und stößt den Browser-Download an. */
export async function zugferdHerunterladen(id: number, nummer?: string | null): Promise<void> {
  const blob = (await run(() => getRechnungRechnungenIdZugferd(id))) as Blob;
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `beleg-${nummer ?? id}.pdf`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/** Cent → „1.234,56 €" (Europe/Berlin, de-DE). */
export const euro = (cent?: number | null): string =>
  ((cent ?? 0) / 100).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
