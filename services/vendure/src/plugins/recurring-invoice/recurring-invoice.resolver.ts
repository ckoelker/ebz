import { Args, Mutation, Query, Resolver } from '@nestjs/graphql';
import { Allow, Ctx, ID, Permission, RequestContext, Transaction } from '@vendure/core';
import { Installment } from './installment.entity';
import { RecurringInvoiceService } from './recurring-invoice.service';

@Resolver()
export class RecurringInvoiceResolver {
    constructor(private service: RecurringInvoiceService) {}

    @Query()
    @Allow(Permission.ReadOrder)
    installmentsForOrder(
        @Ctx() ctx: RequestContext,
        @Args() args: { orderId: ID },
    ): Promise<Installment[]> {
        return this.service.getForOrder(ctx, args.orderId);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateOrder)
    materializeInstallments(
        @Ctx() ctx: RequestContext,
        @Args() args: { orderId: ID },
    ): Promise<number> {
        return this.service.materializeForOrder(ctx, args.orderId);
    }

    @Mutation()
    @Transaction()
    @Allow(Permission.UpdateOrder)
    runRecurringInvoiceRun(@Ctx() ctx: RequestContext): Promise<number> {
        return this.service.runBilling(ctx);
    }
}
