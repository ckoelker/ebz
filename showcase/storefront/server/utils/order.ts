// Gemeinsame Order-Felder (Warenkorb/activeOrder) für alle Cart-/Checkout-Routen.
// Teilnehmer:in liegt als OrderLine-Custom-Field (Sammelbuchung: 1 Position = 1 Teilnehmer:in).
export const ORDER_FIELDS = `
  fragment OrderFields on Order {
    id
    code
    state
    totalQuantity
    subTotalWithTax
    shippingWithTax
    totalWithTax
    currencyCode
    discounts { description amountWithTax }
    lines {
      id
      quantity
      unitPriceWithTax
      linePriceWithTax
      productVariant { id name sku }
      featuredAsset { preview }
      customFields { participantName participantEmail }
    }
  }
`

export interface Order {
  id: string
  code: string
  state: string
  totalQuantity: number
  subTotalWithTax: number
  shippingWithTax: number
  totalWithTax: number
  currencyCode: string
  discounts: Array<{ description: string; amountWithTax: number }>
  lines: Array<{
    id: string
    quantity: number
    unitPriceWithTax: number
    linePriceWithTax: number
    productVariant: { id: string; name: string; sku: string }
    featuredAsset: { preview: string } | null
    customFields: { participantName: string | null; participantEmail: string | null }
  }>
}

// Vendure-Union-Rückgaben: ErrorResult → Exception mit Vendure-Meldung.
export function unwrapOrder(result: { __typename?: string; errorCode?: string; message?: string } & Partial<Order>): Order {
  if (result.errorCode) {
    throw createError({ statusCode: 400, statusMessage: result.message || result.errorCode })
  }
  return result as Order
}
