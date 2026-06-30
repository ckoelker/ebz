import { PluginCommonModule, VendurePlugin } from '@vendure/core';
import { adminApiExtensions, shopApiExtensions } from './api-extensions';
import { ContentPage } from './content-page.entity';
import { ContentPageAdminResolver, ContentPageShopResolver } from './content-page.resolver';
import { ContentPageService } from './content-page.service';

/**
 * Showcase CMS „ContentPage" (P6): redaktionelle Seiten in EINEM Pflege-Backend
 * (Admin-API/Dashboard), serverseitig in der Nuxt-Storefront gerendert. Das Burger-/
 * Hauptmenü bündelt Collections (built-in) + `imMenu`-Seiten (Shop-Query `menuPages`).
 */
@VendurePlugin({
    imports: [PluginCommonModule],
    entities: [ContentPage],
    providers: [ContentPageService],
    adminApiExtensions: {
        schema: adminApiExtensions,
        resolvers: [ContentPageAdminResolver],
    },
    shopApiExtensions: {
        schema: shopApiExtensions,
        resolvers: [ContentPageShopResolver],
    },
    compatibility: '^3.0.0',
})
export class ContentPagePlugin {}
