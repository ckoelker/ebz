import { DeepPartial } from '@vendure/common/lib/shared-types';
import { VendureEntity } from '@vendure/core';
import { Column, Entity } from 'typeorm';

/**
 * Eine einzelne fällige Rate eines Subscription-/Ratenplans (interner Rechnungslauf, M4).
 * status: 'scheduled' = eingeplant, 'invoiced' = in Rechnung gestellt (vom Rechnungslauf).
 */
@Entity()
export class Installment extends VendureEntity {
    constructor(input?: DeepPartial<Installment>) {
        super(input);
    }

    @Column() orderId: string;
    @Column() orderCode: string;
    @Column() variantName: string;
    @Column() sequence: number;
    @Column() totalCount: number;
    @Column() amount: number;
    @Column() currencyCode: string;
    @Column() dueDate: Date;
    @Column({ default: 'scheduled' }) status: string;
}
