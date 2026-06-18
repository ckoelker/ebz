import { Injectable } from '@nestjs/common';
import {
    EntityDuplicatorService,
    ID,
    Product,
    ProductService,
    RequestContext,
    TransactionalConnection,
} from '@vendure/core';
import { Ansprechpartner, Bewertung, Dozent } from './produktkatalog.entities';

interface UpsertAnsprechpartnerInput {
    crmPersonId: string;
    name: string;
    email?: string;
    telefon?: string;
    fotoAssetId?: string;
    fotoHash?: string;
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
    constructor(
        private connection: TransactionalConnection,
        private entityDuplicatorService: EntityDuplicatorService,
        private productService: ProductService,
    ) {}

    /**
     * Veröffentlicht eine Produktvorlage als eigenständiges, bestellbares Angebot (Produktvorlagen,
     * notizen #2). Nutzt Vendures EntityDuplicator (Deep-Copy inkl. Varianten/Assets/Facetten/
     * Custom-Fields) → die Kopie ist ein **Snapshot**: spätere Änderungen an der Vorlage wirken NICHT
     * mehr auf diese Kopie, sondern erst auf die nächste. Die Kopie wird aktiviert (enabled),
     * als bestellbar markiert und per {@code vorlageProductId} auf die Vorlage zurückverlinkt.
     */
    async kopiereVorlageZuAngebot(
        ctx: RequestContext,
        input: { vorlageId: ID; titelZusatz?: string; slugZusatz?: string },
    ): Promise<Product> {
        const result = await this.entityDuplicatorService.duplicateEntity(ctx, {
            entityName: 'Product',
            entityId: input.vorlageId,
            duplicatorInput: {
                code: 'product-duplicator',
                arguments: [{ name: 'includeVariants', value: 'true' }],
            },
        });
        if (!('newEntityId' in result) || result.newEntityId == null) {
            const msg = 'message' in result ? (result as { message: string }).message : 'unbekannt';
            throw new Error(`Vorlage konnte nicht kopiert werden: ${msg}`);
        }
        const newId = result.newEntityId;

        // Übersetzungen der Vorlage lesen → Titel/Slug der Kopie ableiten (eindeutiger Slug).
        const vorlage = await this.connection.getRepository(ctx, Product).findOne({
            where: { id: input.vorlageId as string },
            relations: { translations: true },
        });
        const zusatz = input.titelZusatz?.trim();
        const slugZusatz = (input.slugZusatz?.trim() || zusatz || 'kopie')
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/(^-|-$)/g, '');
        const eindeutig = `${slugZusatz}-${Date.now().toString(36)}`;
        const translations = (vorlage?.translations ?? []).map((t) => ({
            languageCode: t.languageCode,
            name: zusatz ? `${t.name} – ${zusatz}` : t.name,
            slug: `${t.slug}-${eindeutig}`,
            description: t.description,
        }));

        await this.productService.update(ctx, {
            id: newId,
            enabled: true,
            ...(translations.length ? { translations } : {}),
            customFields: {
                istVorlage: false,
                bestellbar: true,
                vorlageProductId: String(input.vorlageId),
            },
        });
        return this.productService.findOne(ctx, newId) as Promise<Product>;
    }

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
        // Foto nur überschreiben, wenn explizit übergeben (Stammdaten-Sync ohne Foto bewahrt das Bild).
        if (input.fotoAssetId !== undefined) {
            entity.fotoAssetId = input.fotoAssetId || null;
            entity.fotoHash = input.fotoHash || null;
        }
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
