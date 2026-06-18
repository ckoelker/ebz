// Artikel in den Warenkorb legen (Durchführungs-Variante). Optional mit Teilnehmer:in
// (Sammelbuchung: je Teilnehmer:in eine eigene Position via abweichender Custom-Fields).
import type { Order } from '../utils/order'

export default defineEventHandler(async (event) => {
  const body = await readBody<{
    variantId: string
    quantity?: number
    participantName?: string
    participantEmail?: string
  }>(event)
  if (!body?.variantId) {
    throw createError({ statusCode: 400, statusMessage: 'variantId fehlt' })
  }
  const customFields = {
    participantName: body.participantName || null,
    participantEmail: body.participantEmail || null,
  }
  const data = await shopGql<{ addItemToOrder: Order & { errorCode?: string; message?: string } }>(
    event,
    `mutation($variantId: ID!, $qty: Int!, $cf: OrderLineCustomFieldsInput) {
      addItemToOrder(productVariantId: $variantId, quantity: $qty, customFields: $cf) {
        __typename
        ...OrderFields
        ... on ErrorResult { errorCode message }
      }
    } ${ORDER_FIELDS}`,
    { variantId: body.variantId, qty: body.quantity ?? 1, cf: customFields },
  )
  return unwrapOrder(data.addItemToOrder)
})
