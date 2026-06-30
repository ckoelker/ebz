import { Injectable } from '@nestjs/common';
import { ID, RequestContext, TransactionalConnection } from '@vendure/core';
import { SeminarCost } from './seminar-cost.entity';

interface CreateInput {
    productVariantId: ID;
    costType: string;
    label: string;
    amount: number;
    currencyCode?: string;
    isVariable: boolean;
    perParticipant: boolean;
}

interface UpdateInput {
    id: ID;
    costType?: string;
    label?: string;
    amount?: number;
    currencyCode?: string;
    isVariable?: boolean;
    perParticipant?: boolean;
}

@Injectable()
export class SeminarCostService {
    constructor(private connection: TransactionalConnection) {}

    findForVariant(ctx: RequestContext, productVariantId: ID): Promise<SeminarCost[]> {
        return this.connection.getRepository(ctx, SeminarCost).find({
            where: { productVariantId: String(productVariantId) },
            order: { isVariable: 'ASC', costType: 'ASC' },
        });
    }

    create(ctx: RequestContext, input: CreateInput): Promise<SeminarCost> {
        const entity = new SeminarCost({
            productVariantId: String(input.productVariantId),
            costType: input.costType,
            label: input.label,
            amount: input.amount,
            currencyCode: input.currencyCode ?? 'EUR',
            isVariable: input.isVariable,
            perParticipant: input.perParticipant,
        });
        return this.connection.getRepository(ctx, SeminarCost).save(entity);
    }

    async update(ctx: RequestContext, input: UpdateInput): Promise<SeminarCost> {
        const repo = this.connection.getRepository(ctx, SeminarCost);
        const entity = await this.connection.getEntityOrThrow(ctx, SeminarCost, input.id);
        if (input.costType !== undefined) entity.costType = input.costType;
        if (input.label !== undefined) entity.label = input.label;
        if (input.amount !== undefined) entity.amount = input.amount;
        if (input.currencyCode !== undefined) entity.currencyCode = input.currencyCode;
        if (input.isVariable !== undefined) entity.isVariable = input.isVariable;
        if (input.perParticipant !== undefined) entity.perParticipant = input.perParticipant;
        return repo.save(entity);
    }

    async delete(ctx: RequestContext, id: ID): Promise<boolean> {
        await this.connection.getRepository(ctx, SeminarCost).delete({ id: Number(id) });
        return true;
    }
}
