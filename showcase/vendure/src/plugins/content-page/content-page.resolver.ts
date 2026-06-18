import { Args, Mutation, Query, Resolver } from '@nestjs/graphql';
import { Allow, Ctx, Permission, RequestContext, Transaction } from '@vendure/core';
import { ContentPage } from './content-page.entity';
import { ContentPageService, UpsertContentPageInput } from './content-page.service';

/** Admin-API: CMS-Pflege. Lesen = ReadCatalog, Schreiben/Löschen = UpdateCatalog/DeleteCatalog. */
@Resolver()
export class ContentPageAdminResolver {
    constructor(private service: ContentPageService) {}

    @Query()
    @Allow(Permission.ReadCatalog)
    contentPages(@Ctx() ctx: RequestContext): Promise<ContentPage[]> {
        return this.service.findAll(ctx);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateCatalog)
    upsertContentPage(@Ctx() ctx: RequestContext, @Args() args: { input: UpsertContentPageInput }): Promise<ContentPage> {
        return this.service.upsert(ctx, args.input);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.DeleteCatalog)
    deleteContentPage(@Ctx() ctx: RequestContext, @Args() args: { slug: string }): Promise<boolean> {
        return this.service.delete(ctx, args.slug);
    }
}

/** Shop-API: veröffentlichte Seite + Menü (öffentlich). */
@Resolver()
export class ContentPageShopResolver {
    constructor(private service: ContentPageService) {}

    @Query()
    @Allow(Permission.Public)
    contentPage(@Ctx() ctx: RequestContext, @Args() args: { slug: string }): Promise<ContentPage | null> {
        return this.service.findPublishedBySlug(ctx, args.slug);
    }

    @Query()
    @Allow(Permission.Public)
    menuPages(@Ctx() ctx: RequestContext): Promise<ContentPage[]> {
        return this.service.findMenu(ctx);
    }
}
