import { DeepPartial } from '@vendure/common/lib/shared-types';
import { VendureEntity } from '@vendure/core';
import { Column, Entity } from 'typeorm';

/**
 * Showcase CMS (P6) — einfache redaktionelle Seite (Über uns, Kontakt, AGB …).
 * Ein Pflege-Backend (Dashboard/Admin-API), serverseitig in der Nuxt-Storefront gerendert.
 * Im Burger-/Hauptmenü erscheinen `published && imMenu`-Seiten neben den Collections,
 * sortiert nach {@code menuSortierung}.
 */
@Entity()
export class ContentPage extends VendureEntity {
    constructor(input?: DeepPartial<ContentPage>) {
        super(input);
    }

    /** Sprechende URL (eindeutig), z. B. „ueber-uns" → /seite/ueber-uns. */
    @Column({ unique: true }) slug: string;

    @Column() titel: string;

    @Column({ type: 'text', nullable: true }) inhaltHtml: string | null;

    @Column({ type: 'varchar', nullable: true }) metaTitle: string | null;

    @Column({ type: 'varchar', nullable: true }) metaDescription: string | null;

    /** Nur veröffentlichte Seiten liefert die Shop-API aus. */
    @Column({ default: false }) published: boolean;

    /** Im Hauptmenü anzeigen. */
    @Column({ default: false }) imMenu: boolean;

    /** Abweichender, kürzerer Menütitel (sonst {@link titel}). */
    @Column({ type: 'varchar', nullable: true }) menuTitel: string | null;

    @Column({ default: 0 }) menuSortierung: number;
}
