import {
    dummyPaymentHandler,
    DefaultJobQueuePlugin,
    DefaultSchedulerPlugin,
    DefaultSearchPlugin,
    LanguageCode,
    NativeAuthenticationStrategy,
    VendureConfig,
} from '@vendure/core';
import { defaultEmailHandlers, EmailPlugin, FileBasedTemplateLoader } from '@vendure/email-plugin';
import { AssetServerPlugin } from '@vendure/asset-server-plugin';
import { DashboardPlugin } from '@vendure/dashboard/plugin';
import { GraphiqlPlugin } from '@vendure/graphiql-plugin';
import { StripeSubscriptionPlugin } from '@pinelab/vendure-plugin-stripe-subscription';
import { ShowcaseSubscriptionStrategy } from './subscription-strategy';
import { RecurringInvoicePlugin } from './plugins/recurring-invoice/recurring-invoice.plugin';
import { recurringInvoiceTask } from './plugins/recurring-invoice/recurring-invoice.task';
import { SeminarCostPlugin } from './plugins/seminar-cost/seminar-cost.plugin';
import { KeycloakShopAuthStrategy } from './plugins/keycloak/keycloak-shop.strategy';
import { KeycloakAdminAuthStrategy } from './plugins/keycloak/keycloak-admin.strategy';
import { KeycloakDashboardPlugin } from './plugins/keycloak/keycloak-dashboard.plugin';
import 'dotenv/config';
import path from 'path';

const IS_DEV = process.env.APP_ENV === 'dev';
const serverPort = +process.env.PORT || 3000;
// Showcase: the Vite-based dashboard is only served by the Node server when its
// assets have been built. In the Docker container we skip it (SERVE_DASHBOARD=false)
// and verify via the API; for local `npm run dev` it stays enabled.
const SERVE_DASHBOARD = process.env.SERVE_DASHBOARD !== 'false';
// Keycloak-SSO: Issuer (öffentliche URL im Token) und JWKS (kann interne Container-URL sein), je Realm.
const KC_ISSUER_CUSTOMERS = process.env.KEYCLOAK_ISSUER_CUSTOMERS || 'http://localhost:8088/realms/ebz-customers';
const KC_JWKS_CUSTOMERS = process.env.KEYCLOAK_JWKS_CUSTOMERS || 'http://localhost:8088/realms/ebz-customers/protocol/openid-connect/certs';
const KC_ISSUER_STAFF = process.env.KEYCLOAK_ISSUER_STAFF || 'http://localhost:8088/realms/ebz-staff';
const KC_JWKS_STAFF = process.env.KEYCLOAK_JWKS_STAFF || 'http://localhost:8088/realms/ebz-staff/protocol/openid-connect/certs';

export const config: VendureConfig = {
    apiOptions: {
        port: serverPort,
        adminApiPath: 'admin-api',
        shopApiPath: 'shop-api',
        trustProxy: IS_DEV ? false : 1,
        // The following options are useful in development mode,
        // but are best turned off for production for security
        // reasons.
        ...(IS_DEV ? {
            adminApiDebug: true,
            shopApiDebug: true,
        } : {}),
    },
    authOptions: {
        tokenMethod: ['bearer', 'cookie'],
        superadminCredentials: {
            identifier: process.env.SUPERADMIN_USERNAME,
            password: process.env.SUPERADMIN_PASSWORD,
        },
        cookieOptions: {
          secret: process.env.COOKIE_SECRET,
        },
        // SSO: Native (Superadmin/Passwort) + Keycloak je Fläche, strikt nach Realm getrennt.
        shopAuthenticationStrategy: [
            new NativeAuthenticationStrategy(),
            new KeycloakShopAuthStrategy({ jwksUri: KC_JWKS_CUSTOMERS, issuer: KC_ISSUER_CUSTOMERS }),
        ],
        adminAuthenticationStrategy: [
            new NativeAuthenticationStrategy(),
            new KeycloakAdminAuthStrategy({ jwksUri: KC_JWKS_STAFF, issuer: KC_ISSUER_STAFF }),
        ],
    },
    dbConnectionOptions: {
        type: 'postgres',
        // Showcase: in dev we let TypeORM create the schema automatically
        // (synchronize). For production this MUST be false and every schema
        // change goes through a migration — Leitplanke L4 (versionsneutral).
        synchronize: IS_DEV,
        migrations: [path.join(__dirname, './migrations/*.+(js|ts)')],
        logging: false,
        host: process.env.DB_HOST || 'localhost',
        port: +(process.env.DB_PORT || 5432),
        username: process.env.DB_USERNAME || 'vendure',
        password: process.env.DB_PASSWORD || 'vendure',
        database: process.env.DB_NAME || 'vendure',
    },
    paymentOptions: {
        paymentMethodHandlers: [dummyPaymentHandler],
    },
    // When adding or altering custom field definitions, the database will
    // need to be updated. See the "Migrations" section in README.md.
    // Showcase M2: Zusatzdaten, die ein Standard-Shop nicht erfasst
    // (Schüler-/Studien-/Seminardaten). Erscheinen automatisch in Admin-API
    // und Shop-API → das Vue-Frontend liest/schreibt sie über GraphQL.
    customFields: {
        // Kunde: Stammdaten für Berufsschule/Studium
        Customer: [
            { name: 'studentNumber', type: 'string', nullable: true,
              label: [{ languageCode: LanguageCode.de, value: 'Matrikel-/Schülernummer' }] },
            { name: 'birthDate', type: 'datetime', nullable: true,
              label: [{ languageCode: LanguageCode.de, value: 'Geburtsdatum' }] },
        ],
        // Bestellung: Art der Anmeldung + Ausbildungsbetrieb (Berufsschule)
        Order: [
            { name: 'enrollmentType', type: 'string', nullable: true,
              options: [{ value: 'berufsschule' }, { value: 'studium' }, { value: 'seminar' }],
              label: [{ languageCode: LanguageCode.de, value: 'Anmeldeart' }] },
            { name: 'trainingCompany', type: 'string', nullable: true,
              label: [{ languageCode: LanguageCode.de, value: 'Ausbildungsbetrieb' }] },
        ],
        // Bestellposition: Teilnehmer ≠ Besteller (Seminarbuchung)
        OrderLine: [
            { name: 'participantName', type: 'string', nullable: true,
              label: [{ languageCode: LanguageCode.de, value: 'Teilnehmer:in' }] },
            { name: 'participantEmail', type: 'string', nullable: true,
              label: [{ languageCode: LanguageCode.de, value: 'Teilnehmer-E-Mail' }] },
        ],
        // Variante: Discriminator für den Frontend-/Fulfillment-Flow je Warengruppe
        // + datengetriebene Subscription-Konfiguration (M3, Fälle F4–F6).
        ProductVariant: [
            { name: 'fulfillmentType', type: 'string', nullable: true, defaultValue: 'physical',
              options: [{ value: 'physical' }, { value: 'digital' }, { value: 'seminar' }, { value: 'subscription' }],
              label: [{ languageCode: LanguageCode.de, value: 'Abwicklungsart' }] },
            // Subscription-Plan: gesetzt = Variante ist eine Subscription (siehe ShowcaseSubscriptionStrategy)
            { name: 'subscriptionInterval', type: 'string', nullable: true,
              options: [{ value: 'week' }, { value: 'month' }, { value: 'year' }],
              label: [{ languageCode: LanguageCode.de, value: 'Abrechnungsintervall' }] },
            { name: 'subscriptionIntervalCount', type: 'int', nullable: true, defaultValue: 1,
              label: [{ languageCode: LanguageCode.de, value: 'Intervall-Faktor (z. B. 6 = halbjährlich)' }] },
            { name: 'subscriptionTotalCount', type: 'int', nullable: true, defaultValue: 0,
              label: [{ languageCode: LanguageCode.de, value: 'Anzahl Raten gesamt (0 = unbefristet)' }] },
        ],
    },
    // M4: geplanter Worker-Job für den internen Rechnungslauf.
    schedulerOptions: {
        tasks: [recurringInvoiceTask],
    },
    plugins: [
        GraphiqlPlugin.init(),
        AssetServerPlugin.init({
            route: 'assets',
            assetUploadDir: path.join(__dirname, '../static/assets'),
            // For local dev, the correct value for assetUrlPrefix should
            // be guessed correctly, but for production it will usually need
            // to be set manually to match your production url.
            assetUrlPrefix: IS_DEV ? undefined : 'https://www.my-shop.com/assets/',
        }),
        DefaultSchedulerPlugin.init(),
        DefaultJobQueuePlugin.init({ useDatabaseForBuffer: true }),
        DefaultSearchPlugin.init({ bufferUpdates: false, indexStockStatus: true }),
        // M3: wiederkehrende Abrechnung (F4–F6) über eine datengetriebene Strategy.
        // Stripe-API-Key wird je Payment-Method im Admin gesetzt (nicht hier);
        // die Preview-Query rechnet den Zahlplan ohne Stripe-Charge.
        StripeSubscriptionPlugin.init({
            vendureHost: process.env.VENDURE_HOST || 'http://localhost:3000',
            subscriptionStrategy: new ShowcaseSubscriptionStrategy(),
        }),
        // M4: interner Rechnungslauf (Rechnungs-/SEPA-Pfad)
        RecurringInvoicePlugin,
        // M2: Seminar-Kosten / Deckungsbeitragsrechnung (Erlöse liegen in den Orders)
        SeminarCostPlugin,
        EmailPlugin.init({
            devMode: true,
            outputPath: path.join(__dirname, '../static/email/test-emails'),
            route: 'mailbox',
            handlers: defaultEmailHandlers,
            templateLoader: new FileBasedTemplateLoader(path.join(__dirname, '../static/email/templates')),
            globalTemplateVars: {
                // The following variables will change depending on your storefront implementation.
                // Here we are assuming a storefront running at http://localhost:8080.
                fromAddress: '"example" <noreply@example.com>',
                verifyEmailAddressUrl: 'http://localhost:8080/verify',
                passwordResetUrl: 'http://localhost:8080/password-reset',
                changeEmailAddressUrl: 'http://localhost:8080/verify-email-address-change'
            },
        }),
        // Trägt die Dashboard-Login-Extension bei (zentraler Keycloak-Mitarbeiter-Login, Realm ebz-staff).
        KeycloakDashboardPlugin,
        ...(SERVE_DASHBOARD ? [DashboardPlugin.init({
            route: 'dashboard',
            appDir: IS_DEV
                ? path.join(__dirname, '../dist/dashboard')
                : path.join(__dirname, 'dashboard'),
        })] : []),
    ],
};
