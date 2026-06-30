import { PluginCommonModule, VendurePlugin } from '@vendure/core';
import { adminApiExtensions } from './api-extensions';
import { Installment } from './installment.entity';
import { RecurringInvoiceResolver } from './recurring-invoice.resolver';
import { RecurringInvoiceService } from './recurring-invoice.service';

/**
 * Showcase M4 — interner Rechnungslauf für wiederkehrende Raten (Rechnungs-/SEPA-Pfad).
 * Materialisiert Ratenpläne (F4–F6) als Installment-Datensätze und stellt fällige
 * Raten über einen geplanten Worker-Job (siehe recurring-invoice.task.ts) in Rechnung.
 */
@VendurePlugin({
    imports: [PluginCommonModule],
    entities: [Installment],
    providers: [RecurringInvoiceService],
    adminApiExtensions: {
        schema: adminApiExtensions,
        resolvers: [RecurringInvoiceResolver],
    },
    compatibility: '^3.0.0',
})
export class RecurringInvoicePlugin {}
