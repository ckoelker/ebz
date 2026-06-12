// Showcase M5 — zentraler ErrorResult-Handler (Leitplanke §8a-7).
//
// Vendure-Mutations werfen KEINE Exceptions, sondern liefern ein GraphQL-Union
// aus Erfolgs-Typ und einem oder mehreren `ErrorResult`-Typen, z. B.:
//   addItemToOrder: UpdateOrderItemsResult =
//       Order | OrderModificationError | InsufficientStockError | ...
// Jeder ErrorResult-Typ hat das gemeinsame Feld `errorCode` + `message`.
// Wer nur try/catch nutzt, übersieht diese Fehler. Daher hier EIN Helfer, durch
// den alle Mutationsergebnisse laufen.

/** Gemeinsame Form aller Vendure-ErrorResult-Typen. */
export interface VendureError {
  __typename: string;
  errorCode: string;
  message: string;
}

/** Liste der bekannten ErrorResult-__typename-Werte der Shop-API. */
const ERROR_TYPENAMES = new Set<string>([
  'OrderModificationError',
  'OrderLimitError',
  'NegativeQuantityError',
  'InsufficientStockError',
  'OrderInterceptorError',
  'OrderStateTransitionError',
  'IneligibleShippingMethodError',
  'NoActiveOrderError',
  'OrderPaymentStateError',
  'PaymentFailedError',
  'PaymentDeclinedError',
  'IneligiblePaymentMethodError',
  'AlreadyLoggedInError',
  'InvalidCredentialsError',
  'NotVerifiedError',
  'MissingPasswordError',
  'PasswordValidationError',
  'EmailAddressConflictError',
  'GuestCheckoutError',
  'CouponCodeInvalidError',
  'CouponCodeExpiredError',
  'CouponCodeLimitError',
]);

/** Type-Guard: ist das übergebene Union-Ergebnis ein ErrorResult? */
export function isErrorResult(value: unknown): value is VendureError {
  if (!value || typeof value !== 'object') return false;
  const t = (value as { __typename?: string }).__typename;
  return typeof t === 'string' && ERROR_TYPENAMES.has(t);
}

/**
 * Wirft eine sprechende Exception, wenn das Mutationsergebnis ein ErrorResult
 * ist — sonst gibt es den Erfolgs-Typ typsicher zurück. `Exclude<T, VendureError>`
 * schneidet die Error-Member aus dem GraphQL-Union (§8a-7), sodass der Aufrufer
 * direkt mit dem Erfolgs-Typ (z. B. Order) weiterarbeitet — ein try/catch genügt.
 */
export function unwrap<T extends object>(value: T | null | undefined): Exclude<T, VendureError> {
  if (value == null) {
    // Manche Mutations/Queries sind im Schema nullable (z. B. kein aktiver Order).
    throw new ShopError('NO_ACTIVE_ORDER_ERROR', 'Keine aktive Bestellung vorhanden.', 'NoActiveOrderError');
  }
  if (isErrorResult(value)) {
    throw new ShopError(value.errorCode, value.message, value.__typename);
  }
  return value as Exclude<T, VendureError>;
}

/** Fehler aus einem Vendure-ErrorResult (trägt errorCode für gezieltes Mapping). */
export class ShopError extends Error {
  constructor(
    public readonly errorCode: string,
    message: string,
    public readonly typename: string,
  ) {
    super(message);
    this.name = 'ShopError';
  }
}

/** Nutzerfreundliche deutsche Meldung je nach errorCode (Fallback = Server-Text). */
export function toUserMessage(err: unknown): string {
  if (err instanceof ShopError) {
    switch (err.errorCode) {
      case 'INSUFFICIENT_STOCK_ERROR':
        return 'Leider nicht mehr genügend Plätze/Bestand verfügbar.';
      case 'INVALID_CREDENTIALS_ERROR':
        return 'Anmeldung fehlgeschlagen.';
      case 'GUEST_CHECKOUT_ERROR':
        return 'Für diese Bestellung ist eine Anmeldung erforderlich.';
      case 'ORDER_STATE_TRANSITION_ERROR':
        return 'Die Bestellung kann in diesem Schritt nicht fortgesetzt werden.';
      default:
        return err.message;
    }
  }
  if (err instanceof Error) return err.message;
  return 'Unbekannter Fehler.';
}
