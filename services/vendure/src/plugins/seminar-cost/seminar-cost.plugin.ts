import { PluginCommonModule, VendurePlugin } from '@vendure/core';
import { adminApiExtensions } from './api-extensions';
import { SeminarCost } from './seminar-cost.entity';
import { SeminarCostResolver } from './seminar-cost.resolver';
import { SeminarCostService } from './seminar-cost.service';

/**
 * Showcase M2 — Seminar-Kosten / Deckungsbeitragsrechnung.
 * Erfasst Kostenpositionen (fix/variabel, je Seminar/je Teilnehmer:in) zu den
 * Seminar-Produktvarianten. Die Erlösseite liegt bereits in den Vendure-Orders;
 * das Controlling-Warehouse joint beide Seiten später (dlt → dbt) zu DB/Break-even.
 */
@VendurePlugin({
    imports: [PluginCommonModule],
    entities: [SeminarCost],
    providers: [SeminarCostService],
    adminApiExtensions: {
        schema: adminApiExtensions,
        resolvers: [SeminarCostResolver],
    },
    compatibility: '^3.0.0',
})
export class SeminarCostPlugin {}
