import { Args, Mutation, Query, Resolver } from '@nestjs/graphql';
import { Allow, Ctx, ID, Permission, RequestContext, Transaction } from '@vendure/core';
import { SeminarCost } from './seminar-cost.entity';
import { SeminarCostService } from './seminar-cost.service';

@Resolver()
export class SeminarCostResolver {
    constructor(private service: SeminarCostService) {}

    @Query()
    @Allow(Permission.ReadCatalog)
    seminarCosts(
        @Ctx() ctx: RequestContext,
        @Args() args: { productVariantId: ID },
    ): Promise<SeminarCost[]> {
        return this.service.findForVariant(ctx, args.productVariantId);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateCatalog)
    createSeminarCost(
        @Ctx() ctx: RequestContext,
        @Args() args: { input: Parameters<SeminarCostService['create']>[1] },
    ): Promise<SeminarCost> {
        return this.service.create(ctx, args.input);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateCatalog)
    updateSeminarCost(
        @Ctx() ctx: RequestContext,
        @Args() args: { input: Parameters<SeminarCostService['update']>[1] },
    ): Promise<SeminarCost> {
        return this.service.update(ctx, args.input);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateCatalog)
    deleteSeminarCost(
        @Ctx() ctx: RequestContext,
        @Args() args: { id: ID },
    ): Promise<boolean> {
        return this.service.delete(ctx, args.id);
    }
}
