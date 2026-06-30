import { Injectable } from '@nestjs/common';
import { RequestContext, TransactionalConnection } from '@vendure/core';
import { ContentPage } from './content-page.entity';

export interface UpsertContentPageInput {
    slug: string;
    titel: string;
    inhaltHtml?: string;
    metaTitle?: string;
    metaDescription?: string;
    published?: boolean;
    imMenu?: boolean;
    menuTitel?: string;
    menuSortierung?: number;
}

/**
 * CMS-Pflege (idempotent über {@code slug}, damit der Java-Initializer mehrfach laufen kann)
 * + Lese-Zugriffe für die Storefront (nur veröffentlichte Seiten, Menü-Liste).
 */
@Injectable()
export class ContentPageService {
    constructor(private connection: TransactionalConnection) {}

    findAll(ctx: RequestContext): Promise<ContentPage[]> {
        return this.connection.getRepository(ctx, ContentPage).find({ order: { menuSortierung: 'ASC', titel: 'ASC' } });
    }

    /** Storefront: nur veröffentlichte Seite nach slug. */
    findPublishedBySlug(ctx: RequestContext, slug: string): Promise<ContentPage | null> {
        return this.connection.getRepository(ctx, ContentPage).findOne({ where: { slug, published: true } });
    }

    /** Storefront: veröffentlichte Menü-Seiten, sortiert. */
    findMenu(ctx: RequestContext): Promise<ContentPage[]> {
        return this.connection.getRepository(ctx, ContentPage).find({
            where: { published: true, imMenu: true },
            order: { menuSortierung: 'ASC', titel: 'ASC' },
        });
    }

    async upsert(ctx: RequestContext, input: UpsertContentPageInput): Promise<ContentPage> {
        const repo = this.connection.getRepository(ctx, ContentPage);
        let entity = await repo.findOne({ where: { slug: input.slug } });
        if (!entity) entity = new ContentPage({ slug: input.slug });
        entity.titel = input.titel;
        entity.inhaltHtml = input.inhaltHtml ?? null;
        entity.metaTitle = input.metaTitle ?? null;
        entity.metaDescription = input.metaDescription ?? null;
        entity.published = input.published ?? false;
        entity.imMenu = input.imMenu ?? false;
        entity.menuTitel = input.menuTitel ?? null;
        entity.menuSortierung = input.menuSortierung ?? 0;
        return repo.save(entity);
    }

    async delete(ctx: RequestContext, slug: string): Promise<boolean> {
        const res = await this.connection.getRepository(ctx, ContentPage).delete({ slug });
        return (res.affected ?? 0) > 0;
    }
}
