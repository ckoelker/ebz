// Checkout-Orchestrierung (P4): führt den Vendure-Order in der verlangten Reihenfolge zur Bestellung.
// Gastbuchung (provisorische Person) → Liefer-/Rechnungsadresse → Versandart → ArrangingPayment → Zahlung.
// Zahlung läuft im Showcase über den dummyPaymentHandler (automaticSettle) für beide Zahlarten
// (rechnung = Kauf auf Rechnung, stripe-sepa = Karte/SEPA-Stand-in).
import type { Order } from '../utils/order'

interface CheckoutBody {
  email: string
  firstName: string
  lastName: string
  company?: string
  address: { streetLine1: string; city: string; postalCode: string; countryCode: string }
  paymentMethod: string
}

export default defineEventHandler(async (event) => {
  const b = await readBody<CheckoutBody>(event)
  if (!b?.email || !b.firstName || !b.lastName || !b.address?.streetLine1 || !b.paymentMethod) {
    throw createError({ statusCode: 400, statusMessage: 'Pflichtfelder fehlen' })
  }

  // 0) Vorbedingung: nicht leerer, noch offener Warenkorb.
  const active = await shopGql<{ activeOrder: (Order & { customer: { id: string } | null }) | null }>(
    event,
    `query { activeOrder { id state totalQuantity customer { id } } }`,
  )
  if (!active.activeOrder || active.activeOrder.totalQuantity === 0) {
    throw createError({ statusCode: 409, statusMessage: 'Warenkorb ist leer' })
  }

  // 1) Gast: provisorische Person an die Order binden (nur wenn noch kein Kunde gesetzt).
  if (!active.activeOrder.customer) {
    const r = await shopGql<{ setCustomerForOrder: { errorCode?: string; message?: string } }>(
      event,
      `mutation($input: CreateCustomerInput!) {
        setCustomerForOrder(input: $input) {
          __typename
          ... on Order { id }
          ... on ErrorResult { errorCode message }
        }
      }`,
      { input: { emailAddress: b.email, firstName: b.firstName, lastName: b.lastName } },
    )
    if (r.setCustomerForOrder.errorCode) {
      throw createError({ statusCode: 400, statusMessage: r.setCustomerForOrder.message || r.setCustomerForOrder.errorCode })
    }
  }

  const fullName = `${b.firstName} ${b.lastName}`.trim()

  // 2) Liefer-/Rechnungsadresse (Showcase: eine Adresse für beides).
  await mustOrder(event, `mutation($i: CreateAddressInput!) {
    setOrderShippingAddress(input: $i) { __typename ... on Order { id } ... on ErrorResult { errorCode message } }
  }`, { i: { fullName, company: b.company || null, streetLine1: b.address.streetLine1, city: b.address.city, postalCode: b.address.postalCode, countryCode: b.address.countryCode } }, 'setOrderShippingAddress')

  await mustOrder(event, `mutation($i: CreateAddressInput!) {
    setOrderBillingAddress(input: $i) { __typename ... on Order { id } ... on ErrorResult { errorCode message } }
  }`, { i: { fullName, company: b.company || null, streetLine1: b.address.streetLine1, city: b.address.city, postalCode: b.address.postalCode, countryCode: b.address.countryCode } }, 'setOrderBillingAddress')

  // 3) Versandart: erst nach gesetzter Adresse (Zone) eligible → erste nehmen.
  const ship = await shopGql<{ eligibleShippingMethods: Array<{ id: string }> }>(
    event,
    `query { eligibleShippingMethods { id name priceWithTax } }`,
  )
  if (ship.eligibleShippingMethods.length) {
    await mustOrder(event, `mutation($ids: [ID!]!) {
      setOrderShippingMethod(shippingMethodId: $ids) { __typename ... on Order { id } ... on ErrorResult { errorCode message } }
    }`, { ids: [ship.eligibleShippingMethods[0].id] }, 'setOrderShippingMethod')
  }

  // 4) In den Zahlungs-Zustand wechseln.
  const trans = await shopGql<{ transitionOrderToState: { errorCode?: string; message?: string; transitionError?: string } }>(
    event,
    `mutation($s: String!) {
      transitionOrderToState(state: $s) {
        __typename
        ... on Order { id state }
        ... on OrderStateTransitionError { errorCode message transitionError }
      }
    }`,
    { s: 'ArrangingPayment' },
  )
  if (trans.transitionOrderToState.errorCode) {
    throw createError({ statusCode: 400, statusMessage: trans.transitionOrderToState.transitionError || trans.transitionOrderToState.message })
  }

  // 5) Zahlung auslösen.
  const pay = await shopGql<{ addPaymentToOrder: { __typename: string; code?: string; state?: string; errorCode?: string; message?: string } }>(
    event,
    `mutation($input: PaymentInput!) {
      addPaymentToOrder(input: $input) {
        __typename
        ... on Order { id code state totalWithTax }
        ... on ErrorResult { errorCode message }
      }
    }`,
    { input: { method: b.paymentMethod, metadata: {} } },
  )
  if (pay.addPaymentToOrder.errorCode) {
    throw createError({ statusCode: 400, statusMessage: pay.addPaymentToOrder.message || pay.addPaymentToOrder.errorCode })
  }

  return { code: pay.addPaymentToOrder.code, state: pay.addPaymentToOrder.state }
})

// Hilfsfunktion: GraphQL-Mutation mit Order/ErrorResult-Union ausführen, Fehler werfen.
async function mustOrder(
  event: Parameters<typeof shopGql>[0],
  query: string,
  variables: Record<string, unknown>,
  field: string,
) {
  const data = await shopGql<Record<string, { errorCode?: string; message?: string }>>(event, query, variables)
  const r = data[field]
  if (r?.errorCode) {
    throw createError({ statusCode: 400, statusMessage: r.message || r.errorCode })
  }
}
