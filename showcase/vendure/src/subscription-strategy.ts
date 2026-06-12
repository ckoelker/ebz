import { Injector, Order, ProductVariant, RequestContext } from '@vendure/core';
import { Subscription, SubscriptionStrategy } from '@pinelab/vendure-plugin-stripe-subscription';

type Interval = 'week' | 'month' | 'year';

/**
 * Showcase-Strategy für die wiederkehrenden Fälle F4–F6.
 *
 * Eine Variante ist genau dann eine Subscription, wenn das Custom-Field
 * `subscriptionInterval` gesetzt ist. Der komplette Zahlplan ist datengetrieben
 * über Varianten-Custom-Fields — dieselbe Strategy drückt damit aus:
 *
 *   F4 Abo-Reihe     interval=month, count=1,  total=0   → monatlich, unbefristet (kein endDate)
 *   F5 Berufsschule  interval=month, count=6,  total=6   → Halbjahresrechnung, 6 Raten (3 Jahre)
 *   F6 Studiengang   interval=month, count=1,  total=36  → 36 Monatsraten, festes Ende
 *
 * Konvention: die **erste** Rate wird beim Checkout fällig (`amountDueNow`), der
 * wiederkehrende Plan deckt die restlichen (`total - 1`) Raten ab und startet eine
 * Periode nach dem Checkout. `total = 0` bedeutet unbefristet.
 */
export class ShowcaseSubscriptionStrategy implements SubscriptionStrategy {
    isSubscription(ctx: RequestContext, variant: ProductVariant): boolean {
        return !!(variant.customFields as any)?.subscriptionInterval;
    }

    defineSubscription(
        ctx: RequestContext,
        injector: Injector,
        productVariant: ProductVariant,
    ): Subscription {
        return this.build(productVariant);
    }

    previewSubscription(
        ctx: RequestContext,
        injector: Injector,
        productVariant: ProductVariant,
    ): Subscription {
        return this.build(productVariant);
    }

    private build(variant: ProductVariant): Subscription {
        const cf = (variant.customFields as any) ?? {};
        const interval: Interval = (cf.subscriptionInterval as Interval) ?? 'month';
        const intervalCount: number = cf.subscriptionIntervalCount ?? 1;
        const total: number = cf.subscriptionTotalCount ?? 0; // 0 = unbefristet
        const price = variant.listPrice; // Preis im aktuellen Kontext (eine Rate)

        // Erste Rate jetzt; der wiederkehrende Plan beginnt eine Periode später.
        const startDate = this.addInterval(new Date(), interval, intervalCount);

        const subscription: Subscription = {
            name: variant.name,
            priceIncludesTax: variant.listPriceIncludesTax,
            amountDueNow: price,
            recurring: {
                amount: price,
                interval,
                intervalCount,
                startDate,
            },
        };

        // Befristete Pläne (F5/F6): endDate = Datum der letzten wiederkehrenden Rate.
        // recurring deckt (total - 1) Raten ab → letzte liegt (recurring - 1) Perioden nach startDate.
        const recurringCharges = total > 0 ? total - 1 : 0;
        if (recurringCharges >= 1) {
            subscription.recurring.endDate = this.addInterval(
                startDate,
                interval,
                intervalCount * (recurringCharges - 1),
            );
        }
        return subscription;
    }

    private addInterval(from: Date, interval: Interval, count: number): Date {
        const d = new Date(from.getFullYear(), from.getMonth(), from.getDate(), 12);
        if (interval === 'week') d.setDate(d.getDate() + 7 * count);
        else if (interval === 'month') d.setMonth(d.getMonth() + count);
        else d.setFullYear(d.getFullYear() + count);
        return d;
    }
}
