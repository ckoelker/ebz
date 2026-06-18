// Warenkorb-Position ändern: Menge und/oder Teilnehmer:in-Daten (Custom-Fields).
import type { Order } from '../utils/order'

export default defineEventHandler(async (event) => {
  const body = await readBody<{
    lineId: string
    quantity: number
    participantName?: string
    participantEmail?: string
  }>(event)
  if (!body?.lineId) {
    throw createError({ statusCode: 400, statusMessage: 'lineId fehlt' })
  }
  const customFields = {
    participantName: body.participantName || null,
    participantEmail: body.participantEmail || null,
  }
  const data = await shopGql<{ adjustOrderLine: Order & { errorCode?: string; message?: string } }>(
    event,
    `mutation($lineId: ID!, $qty: Int!, $cf: OrderLineCustomFieldsInput) {
      adjustOrderLine(orderLineId: $lineId, quantity: $qty, customFields: $cf) {
        __typename
        ...OrderFields
        ... on ErrorResult { errorCode message }
      }
    } ${ORDER_FIELDS}`,
    { lineId: body.lineId, qty: body.quantity, cf: customFields },
  )
  return unwrapOrder(data.adjustOrderLine)
})
