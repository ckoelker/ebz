// Durchführungs-Variante in den Warenkorb legen. Teilnehmer:innen werden danach im Warenkorb
// erfasst (cart-line.patch) — je nach Menge n Teilnehmer:innen pro Position.
import type { Order } from '../utils/order'

export default defineEventHandler(async (event) => {
  const body = await readBody<{ variantId: string; quantity?: number }>(event)
  if (!body?.variantId) {
    throw createError({ statusCode: 400, statusMessage: 'variantId fehlt' })
  }
  const data = await shopGql<{ addItemToOrder: Order & { errorCode?: string; message?: string } }>(
    event,
    `mutation($variantId: ID!, $qty: Int!) {
      addItemToOrder(productVariantId: $variantId, quantity: $qty) {
        __typename
        ...OrderFields
        ... on ErrorResult { errorCode message }
      }
    } ${ORDER_FIELDS}`,
    { variantId: body.variantId, qty: body.quantity ?? 1 },
  )
  return unwrapOrder(data.addItemToOrder)
})
