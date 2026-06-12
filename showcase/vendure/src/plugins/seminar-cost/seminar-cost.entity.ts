import { DeepPartial } from '@vendure/common/lib/shared-types';
import { VendureEntity } from '@vendure/core';
import { Column, Entity } from 'typeorm';

/**
 * Eine einzelne Kostenposition eines Seminars (Showcase M2 — Deckungsbeitragsrechnung).
 * Bezug über die Seminar-Produktvariante (customField fulfillmentType = 'seminar').
 *
 * Leitplanke L18: `costType`, `amount` (Cent, netto), `isVariable` und `perParticipant`
 * sind Pflicht — ohne diese vier Felder ist kein Break-even rechenbar:
 *   - isVariable = false  → Fixkosten (fallen unabhängig von der Teilnehmerzahl an, z. B. Raummiete)
 *   - isVariable = true   → variable Kosten
 *   - perParticipant = true → Betrag gilt JE Teilnehmer:in (z. B. Catering), sonst je Seminar
 *
 * Beträge sind wie in Vendure überall Integer-Cent, netto (passend zur EUR-Channel-Linie).
 */
@Entity()
export class SeminarCost extends VendureEntity {
    constructor(input?: DeepPartial<SeminarCost>) {
        super(input);
    }

    /** ID der Seminar-Produktvariante, auf die sich die Kosten beziehen. */
    @Column() productVariantId: string;

    /** Maschinenlesbare Kostenart, z. B. 'dozent' | 'raum' | 'material' | 'catering'. */
    @Column() costType: string;

    /** Menschenlesbare Bezeichnung der Position. */
    @Column() label: string;

    /** Betrag in Cent, netto. Bei perParticipant=true je Teilnehmer:in, sonst je Seminar. */
    @Column() amount: number;

    @Column({ default: 'EUR' }) currencyCode: string;

    /** false = Fixkosten, true = variable Kosten. */
    @Column({ default: false }) isVariable: boolean;

    /** true = Betrag je Teilnehmer:in, false = Betrag je Seminar. */
    @Column({ default: false }) perParticipant: boolean;
}
