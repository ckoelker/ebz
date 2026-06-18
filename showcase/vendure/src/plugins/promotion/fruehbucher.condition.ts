import { LanguageCode, PromotionCondition, ProductVariant, TransactionalConnection } from '@vendure/core';

/**
 * Showcase Frühbucher-Promotion (P7): greift, wenn mindestens eine Bestellposition eine
 * Durchführung ist, deren {@code terminDatum} (ProductVariant-Custom-Field) noch mindestens
 * {@code daysBefore} Tage in der Zukunft liegt. Kombiniert mit der eingebauten Aktion
 * `order_percentage_discount` ergibt das einen „X % Frühbucherrabatt bis N Tage vor Beginn".
 */
let connection: TransactionalConnection;

export const fruehbucherCondition = new PromotionCondition({
    code: 'fruehbucher',
    description: [
        { languageCode: LanguageCode.de, value: 'Frühbucher: Termin liegt mind. { daysBefore } Tage in der Zukunft' },
        { languageCode: LanguageCode.en, value: 'Early bird: event date at least { daysBefore } days ahead' },
    ],
    args: {
        daysBefore: { type: 'int', defaultValue: 30, ui: { component: 'number-form-input' } },
    },
    init(injector) {
        connection = injector.get(TransactionalConnection);
    },
    async check(ctx, order, args) {
        const schwelleMs = Date.now() + args.daysBefore * 24 * 60 * 60 * 1000;
        for (const line of order.lines) {
            const variant = await connection
                .getRepository(ctx, ProductVariant)
                .findOne({ where: { id: line.productVariant.id } });
            const termin = (variant?.customFields as { terminDatum?: Date | string | null })?.terminDatum;
            if (termin && new Date(termin).getTime() >= schwelleMs) {
                return true;
            }
        }
        return false;
    },
});
