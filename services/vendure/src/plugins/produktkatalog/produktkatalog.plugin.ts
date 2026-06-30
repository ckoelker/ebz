import { PluginCommonModule, VendurePlugin } from '@vendure/core';
import { adminApiExtensions, shopApiExtensions } from './api-extensions';
import { Ansprechpartner, Bewertung, Dozent } from './produktkatalog.entities';
import {
    AnsprechpartnerEntityResolver,
    ProduktkatalogAdminResolver,
    ProduktkatalogShopResolver,
} from './produktkatalog.resolver';
import { ProduktkatalogService } from './produktkatalog.service';

/**
 * Showcase Produktkatalog (P1) — Custom-Entities rund um den Bildungs-Shop:
 *  - {@link Ansprechpartner}/{@link Dozent}: per Sync aus dem CRM-Party-Kern gepflegt
 *    (CRM bleibt Personen-SoR). Über die Product-Relation-Custom-Fields
 *    ({@code ansprechpartner}/{@code dozenten} in vendure-config.ts) mit Produkten verknüpft.
 *  - {@link Bewertung}: Produkt-Rezensionen (Stern-Rating).
 *
 * Die Entitäten MÜSSEN hier registriert sein, damit die Relation-Custom-Fields
 * (vendure-config.ts) auf sie zeigen können.
 */
@VendurePlugin({
    imports: [PluginCommonModule],
    entities: [Ansprechpartner, Dozent, Bewertung],
    providers: [ProduktkatalogService],
    adminApiExtensions: {
        schema: adminApiExtensions,
        resolvers: [ProduktkatalogAdminResolver, AnsprechpartnerEntityResolver],
    },
    shopApiExtensions: {
        schema: shopApiExtensions,
        resolvers: [ProduktkatalogShopResolver, AnsprechpartnerEntityResolver],
    },
    compatibility: '^3.0.0',
})
export class ProduktkatalogPlugin {}
