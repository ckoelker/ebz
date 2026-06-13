import { describe, it, expect } from 'vitest';
import { zSeminarDto } from './gen/zod.gen';

/**
 * Spec-Test 2 (Plan §5, Krit. 2 „über die Spezifikation testbar" + schließt die JSON-Schema→zod-Lücke).
 * <p>
 * Die GENERIERTE zod ({@code zSeminarDto}) — abgeleitet aus /q/openapi, das wiederum die
 * Bean-Validation des SeminarDto spiegelt — setzt den Vertrag durch. Geht eine Constraint auf dem
 * Weg Entity→OpenAPI→zod verloren, akzeptiert dieser Test plötzlich eine invalide Eingabe und
 * schlägt fehl. Kein hand-zod (F3); rein generiert.
 */
const valid = {
  code: 'SEM-IT-001',
  titel: 'Grundlagen IT-Sicherheit',
  bereich: 'AKADEMIE',
  status: 'AKTIV',
  gueltigAb: '2026-09-01',
  preisModell: 'EINMALIG',
  kategorie: 'IT_DIGITAL',
  dauerUE: 16,
  shopVerkauf: true,
  minTN: 6,
  maxTN: 18,
};

describe('zSeminarDto (generiert aus /q/openapi)', () => {
  it('akzeptiert ein valides Seminar', () => {
    expect(zSeminarDto.safeParse(valid).success).toBe(true);
  });

  it.each([
    ['code verletzt @Pattern (Leerzeichen/Kleinbuchstaben)', { code: 'bad code' }],
    ['titel verletzt @NotBlank (leer)', { titel: '' }],
    ['titel verletzt @Size(max=200)', { titel: 'x'.repeat(201) }],
    ['bereich nicht im Enum', { bereich: 'GIBTSNICHT' }],
    ['status fehlt (required)', { status: undefined }],
    ['kategorie fehlt (required)', { kategorie: undefined }],
    ['gueltigAb kein ISO-Datum', { gueltigAb: '01.09.2026' }],
    ['dauerUE verletzt @Min(1)', { dauerUE: 0 }],
    ['maxTN verletzt @Min(1)', { maxTN: 0 }],
  ])('verwirft: %s', (_label, patch) => {
    const input: Record<string, unknown> = { ...valid, ...(patch as object) };
    expect(zSeminarDto.safeParse(input).success).toBe(false);
  });
});
