import { Args, Mutation, Parent, Query, ResolveField, Resolver } from '@nestjs/graphql';
import { Allow, Asset, AssetService, Ctx, ID, Permission, Product, RequestContext, Transaction } from '@vendure/core';
import { Ansprechpartner, Bewertung, Dozent } from './produktkatalog.entities';
import { BewertungUebersicht, ProduktkatalogService } from './produktkatalog.service';

/**
 * Admin-API: Lese-Queries + idempotente Upserts (Shop-Initializer/CRM-Sync).
 * Schreiben erfordert {@code UpdateCatalog}, Lesen {@code ReadCatalog}.
 */
@Resolver()
export class ProduktkatalogAdminResolver {
    constructor(private service: ProduktkatalogService) {}

    @Query()
    @Allow(Permission.ReadCatalog)
    ansprechpartner(@Ctx() ctx: RequestContext): Promise<Ansprechpartner[]> {
        return this.service.findAnsprechpartner(ctx);
    }

    @Query()
    @Allow(Permission.ReadCatalog)
    dozenten(@Ctx() ctx: RequestContext): Promise<Dozent[]> {
        return this.service.findDozenten(ctx);
    }

    @Query()
    @Allow(Permission.ReadCatalog)
    bewertungen(@Ctx() ctx: RequestContext, @Args() args: { productId: ID }): Promise<BewertungUebersicht> {
        return this.service.bewertungUebersicht(ctx, args.productId);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateCatalog)
    upsertAnsprechpartner(
        @Ctx() ctx: RequestContext,
        @Args() args: { input: Parameters<ProduktkatalogService['upsertAnsprechpartner']>[1] },
    ): Promise<Ansprechpartner> {
        return this.service.upsertAnsprechpartner(ctx, args.input);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateCatalog)
    upsertDozent(
        @Ctx() ctx: RequestContext,
        @Args() args: { input: Parameters<ProduktkatalogService['upsertDozent']>[1] },
    ): Promise<Dozent> {
        return this.service.upsertDozent(ctx, args.input);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateCatalog)
    upsertBewertung(
        @Ctx() ctx: RequestContext,
        @Args() args: { input: Parameters<ProduktkatalogService['upsertBewertung']>[1] },
    ): Promise<Bewertung> {
        return this.service.upsertBewertung(ctx, args.input);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateCatalog)
    kopiereVorlageZuAngebot(
        @Ctx() ctx: RequestContext,
        @Args() args: { vorlageId: ID; titelZusatz?: string; slugZusatz?: string },
    ): Promise<Product> {
        return this.service.kopiereVorlageZuAngebot(ctx, args);
    }
}

/** Shop-API: Lese-Queries für die Storefront (P2+). */
@Resolver()
export class ProduktkatalogShopResolver {
    constructor(private service: ProduktkatalogService) {}

    @Query()
    @Allow(Permission.Public)
    bewertungen(@Ctx() ctx: RequestContext, @Args() args: { productId: ID }): Promise<BewertungUebersicht> {
        return this.service.bewertungUebersicht(ctx, args.productId);
    }
}

/**
 * Feld-Resolver: löst {@code Ansprechpartner.foto} aus {@code fotoAssetId} zu einem Vendure-Asset auf
 * (Preview-URL für die Storefront). Für Admin- UND Shop-API registriert.
 */
@Resolver('Ansprechpartner')
export class AnsprechpartnerEntityResolver {
    constructor(private assetService: AssetService) {}

    @ResolveField()
    foto(@Ctx() ctx: RequestContext, @Parent() ap: Ansprechpartner): Promise<Asset | undefined> | null {
        return ap.fotoAssetId ? this.assetService.findOne(ctx, ap.fotoAssetId) : null;
    }
}
