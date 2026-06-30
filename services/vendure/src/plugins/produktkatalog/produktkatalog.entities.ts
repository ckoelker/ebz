import { DeepPartial } from '@vendure/common/lib/shared-types';
import { VendureEntity } from '@vendure/core';
import { Column, Entity } from 'typeorm';

/**
 * Showcase Produktkatalog — Personen & Bewertungen rund um den Bildungs-Shop.
 *
 * Drei Custom-Entities (gebündelt, [[lean-file-structure]]):
 *  - {@link Ansprechpartner} / {@link Dozent}: aus dem CRM-Party-Kern synchronisiert
 *    (CRM bleibt Personen-SoR). Schlüssel ist {@code crmPersonId} — eine Änderung an
 *    EINER Stelle wirkt über die Product-Relation-Custom-Fields auf alle Produkte.
 *  - {@link Bewertung}: Produkt-Rezension (Stern-Rating), je {@code productId}.
 *
 * {@code fotoAssetId} verweist auf ein hochgeladenes Vendure-Asset (Vorschau via
 * Asset-Resolver in der Storefront).
 */
@Entity()
export class Ansprechpartner extends VendureEntity {
    constructor(input?: DeepPartial<Ansprechpartner>) {
        super(input);
    }

    /** CRM-Party-Kern-Personen-ID — idempotenter Sync-Schlüssel. */
    @Column({ unique: true }) crmPersonId: string;

    @Column() name: string;

    @Column({ type: 'varchar', nullable: true }) email: string | null;

    @Column({ type: 'varchar', nullable: true }) telefon: string | null;

    /** ID eines hochgeladenen Vendure-Assets (Porträtfoto), optional. */
    @Column({ type: 'varchar', nullable: true }) fotoAssetId: string | null;

    /** Hash des zuletzt gesyncten Fotos — idempotenter Foto-Sync (kein Re-Upload bei Gleichheit). */
    @Column({ type: 'varchar', nullable: true }) fotoHash: string | null;
}

@Entity()
export class Dozent extends VendureEntity {
    constructor(input?: DeepPartial<Dozent>) {
        super(input);
    }

    /** CRM-Party-Kern-Personen-ID — idempotenter Sync-Schlüssel. */
    @Column({ unique: true }) crmPersonId: string;

    @Column() name: string;

    /** Kurzvita / Referentenprofil (Rich-Text/HTML). */
    @Column({ type: 'text', nullable: true }) vita: string | null;

    /** ID eines hochgeladenen Vendure-Assets (Porträtfoto), optional. */
    @Column({ type: 'varchar', nullable: true }) fotoAssetId: string | null;
}

@Entity()
export class Bewertung extends VendureEntity {
    constructor(input?: DeepPartial<Bewertung>) {
        super(input);
    }

    /** Bezug auf das bewertete Produkt (Vendure Product-ID). */
    @Column() productId: string;

    @Column() autor: string;

    @Column({ type: 'text' }) text: string;

    /** Sterne 1–5. */
    @Column() sterne: number;

    @Column() datum: Date;
}
