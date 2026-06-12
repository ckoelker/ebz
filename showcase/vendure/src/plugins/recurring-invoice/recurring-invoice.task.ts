import { ScheduledTask } from '@vendure/core';
import { RecurringInvoiceService } from './recurring-invoice.service';

/**
 * Geplanter Worker-Job (täglich 02:00): stellt fällige Raten in Rechnung.
 * Für den Showcase-Smoke-Test wird derselbe Service zusätzlich on-demand über
 * die Admin-Mutation `runRecurringInvoiceRun` ausgelöst.
 */
export const recurringInvoiceTask = new ScheduledTask({
    id: 'recurring-invoice-run',
    description: 'Interner Rechnungslauf: stellt fällige Subscription-Raten in Rechnung.',
    schedule: '0 2 * * *',
    execute: async ({ injector, scheduledContext }) => {
        const service = injector.get(RecurringInvoiceService);
        const invoiced = await service.runBilling(scheduledContext);
        return { invoiced };
    },
});
