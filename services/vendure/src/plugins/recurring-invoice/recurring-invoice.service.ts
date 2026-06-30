import { Injectable } from '@nestjs/common';
import { ID, OrderService, RequestContext, TransactionalConnection } from '@vendure/core';
import { Installment } from './installment.entity';

type Interval = 'week' | 'month' | 'year';

// Für unbefristete Pläne (F4) materialisieren wir im Showcase ein rollierendes Fenster.
const INDEFINITE_HORIZON = 12;

@Injectable()
export class RecurringInvoiceService {
    constructor(
        private connection: TransactionalConnection,
        private orderService: OrderService,
    ) {}

    /**
     * Materialisiert den Ratenplan aller Subscription-Positionen einer Bestellung
     * als Installment-Datensätze (idempotent je Bestellung + Variante).
     * Rate 1 ist sofort fällig, Rate n fällt n-1 Perioden später an.
     */
    async materializeForOrder(ctx: RequestContext, orderId: ID): Promise<number> {
        const order = await this.orderService.findOne(ctx, orderId, ['lines', 'lines.productVariant']);
        if (!order) return 0;
        const repo = this.connection.getRepository(ctx, Installment);
        let created = 0;
        for (const line of order.lines) {
            const cf = (line.productVariant.customFields as any) ?? {};
            const interval: Interval | undefined = cf.subscriptionInterval;
            if (!interval) continue; // nur Subscription-Positionen
            const intervalCount: number = cf.subscriptionIntervalCount ?? 1;
            const total: number = cf.subscriptionTotalCount ?? 0;
            const count = total > 0 ? total : INDEFINITE_HORIZON;

            const already = await repo.count({
                where: { orderId: String(order.id), variantName: line.productVariant.name },
            });
            if (already > 0) continue;

            const amount = line.unitPriceWithTax;
            for (let seq = 1; seq <= count; seq++) {
                const dueDate = this.addInterval(new Date(), interval, intervalCount * (seq - 1));
                await repo.save(
                    new Installment({
                        orderId: String(order.id),
                        orderCode: order.code,
                        variantName: line.productVariant.name,
                        sequence: seq,
                        totalCount: total,
                        amount,
                        currencyCode: order.currencyCode,
                        dueDate,
                        status: 'scheduled',
                    }),
                );
                created++;
            }
        }
        return created;
    }

    /**
     * Der Rechnungslauf: stellt alle eingeplanten, fälligen Raten in Rechnung.
     * (Im Showcase: Statuswechsel scheduled → invoiced. Hier würde später die
     * E-Rechnung/DATEV- bzw. SEPA-Übergabe andocken.)
     */
    async runBilling(ctx: RequestContext): Promise<number> {
        const repo = this.connection.getRepository(ctx, Installment);
        const scheduled = await repo.find({ where: { status: 'scheduled' } });
        // Fälligkeit auf Tagesebene vergleichen (unabhängig von der Uhrzeit).
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        let invoiced = 0;
        for (const inst of scheduled) {
            const due = new Date(inst.dueDate);
            due.setHours(0, 0, 0, 0);
            if (due.getTime() <= today.getTime()) {
                inst.status = 'invoiced';
                await repo.save(inst);
                invoiced++;
            }
        }
        return invoiced;
    }

    getForOrder(ctx: RequestContext, orderId: ID): Promise<Installment[]> {
        return this.connection.getRepository(ctx, Installment).find({
            where: { orderId: String(orderId) },
            order: { sequence: 'ASC' },
        });
    }

    private addInterval(from: Date, interval: Interval, count: number): Date {
        const d = new Date(from.getFullYear(), from.getMonth(), from.getDate(), 12);
        if (interval === 'week') d.setDate(d.getDate() + 7 * count);
        else if (interval === 'month') d.setMonth(d.getMonth() + count);
        else d.setFullYear(d.getFullYear() + count);
        return d;
    }
}
