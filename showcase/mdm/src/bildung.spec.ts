import { describe, it, expect } from 'vitest';
import {
  zSeminarDto,
  zTagungDto,
  zBerufsschuljahrDto,
  zStudiengangDto,
} from './gen/zod.gen';

/**
 * Spec-Test 2 (Plan §5, Krit. 2) — über ALLE vier per-Typ-zod-Schemas (P1.1-Kernbeweis).
 * Die generierten zod (aus /q/openapi, das die Bean-Validation der per-Typ-DTOs spiegelt) setzen
 * den Vertrag durch. Polymorphie ohne oneOf/discriminatedUnion: je Typ ein eigenes flaches Schema.
 */
const gemeinsam = {
  bereich: 'AKADEMIE',
  status: 'AKTIV',
  gueltigAb: '2026-09-01',
  preisModell: 'EINMALIG',
  shopVerkauf: true,
} as const;

const valid = {
  seminar: { ...gemeinsam, code: 'SEM-IT-001', titel: 'IT-Sicherheit', kategorie: 'IT_DIGITAL', dauerUE: 16, minTN: 6, maxTN: 18 },
  tagung: { ...gemeinsam, code: 'TAG-IMMO-2026', titel: 'Immobilienkongress', thema: 'Markt', terminVon: '2026-10-12', programmUrl: 'https://ebz.de/k', maxTN: 300 },
  berufsschuljahr: { ...gemeinsam, bereich: 'BERUFSSCHULE', code: 'BSJ-IMMO-2026', titel: 'Immobilienkaufleute', fachrichtung: 'Immobilien', schuljahr: '2026/27', jahrgang: 1, plaetze: 28 },
  studiengang: { ...gemeinsam, bereich: 'HOCHSCHULE', code: 'STG-REM-WS2026', titel: 'B.A. Real Estate', abschluss: 'BACHELOR', studienform: 'DUAL', startsemester: 'WS2026', regelstudienzeitSemester: 7, ratenAnzahl: 42, plaetze: 40 },
};

describe('per-Typ-zod (generiert aus /q/openapi, P1.1)', () => {
  it('akzeptiert je ein valides Angebot pro Typ', () => {
    expect(zSeminarDto.safeParse(valid.seminar).success).toBe(true);
    expect(zTagungDto.safeParse(valid.tagung).success).toBe(true);
    expect(zBerufsschuljahrDto.safeParse(valid.berufsschuljahr).success).toBe(true);
    expect(zStudiengangDto.safeParse(valid.studiengang).success).toBe(true);
  });

  it.each([
    ['Seminar: code @Pattern', zSeminarDto, 'seminar', { code: 'klein 1' }],
    ['Seminar: dauerUE @Min(1)', zSeminarDto, 'seminar', { dauerUE: 0 }],
    ['Seminar: kategorie required', zSeminarDto, 'seminar', { kategorie: undefined }],
    ['Tagung: thema @NotBlank', zTagungDto, 'tagung', { thema: '' }],
    ['Tagung: programmUrl @Pattern', zTagungDto, 'tagung', { programmUrl: 'ftp://x' }],
    ['Tagung: terminVon required', zTagungDto, 'tagung', { terminVon: undefined }],
    ['Berufsschuljahr: schuljahr-Format', zBerufsschuljahrDto, 'berufsschuljahr', { schuljahr: '2026' }],
    ['Studiengang: startsemester-Format', zStudiengangDto, 'studiengang', { startsemester: '2026' }],
    ['Studiengang: abschluss required', zStudiengangDto, 'studiengang', { abschluss: undefined }],
    ['Studiengang: studienform Enum', zStudiengangDto, 'studiengang', { studienform: 'TEILZEIT' }],
  ])('verwirft: %s', (_label, schema, key, patch) => {
    const input = { ...(valid as Record<string, Record<string, unknown>>)[key as string], ...(patch as object) };
    expect((schema as { safeParse: (v: unknown) => { success: boolean } }).safeParse(input).success).toBe(false);
  });
});
