import { Injectable } from '@nestjs/common';
import { ID, RequestContext, TransactionalConnection } from '@vendure/core';
import { Ansprechpartner, Bewertung, Dozent } from './produktkatalog.entities';

interface UpsertAnsprechpartnerInput {
    crmPersonId: string;
    name: string;
    email?: string;
    telefon?: string;
    fotoAssetId?: string;
}

interface UpsertDozentInput {
    crmPersonId: string;
    name: string;
    vita?: string;
    fotoAssetId?: string;
}

interface UpsertBewertungInput {
    productId: string;
    autor: string;
    text: string;
    sterne: number;
    datum?: Date;
}

export interface BewertungUebersicht {
    productId: string;
    anzahl: number;
    durchschnitt: number;
    items: Bewertung[];
}

/**
 * Idempotente Pflege der Katalog-Personen (Schlüssel {@code crmPersonId}, CRM-Sync)
 * und Produkt-Bewertungen. Upserts sind so geschnitten, dass der Java-Initializer
 * (Shop-Aufbau) sie mehrfach gefahrlos aufrufen kann.
 */
@Injectable()
export class ProduktkatalogService {
    constructor(private connection: TransactionalConnection) {}

    // ── Ansprechpartner ──
    findAnsprechpartner(ctx: RequestContext): Promise<Ansprechpartner[]> {
        return this.connection.getRepository(ctx, Ansprechpartner).find({ order: { name: 'ASC' } });
    }

    async upsertAnsprechpartner(ctx: RequestContext, input: UpsertAnsprechpartnerInput): Promise<Ansprechpartner> {
        const repo = this.connection.getRepository(ctx, Ansprechpartner);
        let entity = await repo.findOne({ where: { crmPersonId: input.crmPersonId } });
        if (!entity) entity = new Ansprechpartner({ crmPersonId: input.crmPersonId });
        entity.name = input.name;
        entity.email = input.email ?? null;
        entity.telefon = input.telefon ?? null;
        entity.fotoAssetId = input.fotoAssetId ?? null;
        return repo.save(entity);
    }

    // ── Dozent ──
    findDozenten(ctx: RequestContext): Promise<Dozent[]> {
        return this.connection.getRepository(ctx, Dozent).find({ order: { name: 'ASC' } });
    }

    async upsertDozent(ctx: RequestContext, input: UpsertDozentInput): Promise<Dozent> {
        const repo = this.connection.getRepository(ctx, Dozent);
        let entity = await repo.findOne({ where: { crmPersonId: input.crmPersonId } });
        if (!entity) entity = new Dozent({ crmPersonId: input.crmPersonId });
        entity.name = input.name;
        entity.vita = input.vita ?? null;
        entity.fotoAssetId = input.fotoAssetId ?? null;
        return repo.save(entity);
    }

    // ── Bewertung ──
    findBewertungen(ctx: RequestContext, productId: ID): Promise<Bewertung[]> {
        return this.connection.getRepository(ctx, Bewertung).find({
            where: { productId: String(productId) },
            order: { datum: 'DESC' },
        });
    }

    async bewertungUebersicht(ctx: RequestContext, productId: ID): Promise<BewertungUebersicht> {
        const items = await this.findBewertungen(ctx, productId);
        const anzahl = items.length;
        const durchschnitt = anzahl === 0 ? 0 : items.reduce((s, b) => s + b.sterne, 0) / anzahl;
        return { productId: String(productId), anzahl, durchschnitt: Math.round(durchschnitt * 10) / 10, items };
    }

    /** Idempotent über (productId, autor) — derselbe Autor aktualisiert seine Bewertung statt zu duplizieren. */
    async upsertBewertung(ctx: RequestContext, input: UpsertBewertungInput): Promise<Bewertung> {
        const repo = this.connection.getRepository(ctx, Bewertung);
        let entity = await repo.findOne({ where: { productId: input.productId, autor: input.autor } });
        if (!entity) entity = new Bewertung({ productId: input.productId, autor: input.autor });
        entity.text = input.text;
        entity.sterne = input.sterne;
        entity.datum = input.datum ?? new Date();
        return repo.save(entity);
    }
}
