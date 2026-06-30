// Warenkorb-Position entfernen.
import type { Order } from '../utils/order'

export default defineEventHandler(async (event) => {
  const { lineId } = getQuery(event)
  if (typeof lineId !== 'string' || !lineId) {
    throw createError({ statusCode: 400, statusMessage: 'lineId fehlt' })
  }
  const data = await shopGql<{ removeOrderLine: Order & { errorCode?: string; message?: string } }>(
    event,
    `mutation($lineId: ID!) {
      removeOrderLine(orderLineId: $lineId) {
        __typename
        ...OrderFields
        ... on ErrorResult { errorCode message }
      }
    } ${ORDER_FIELDS}`,
    { lineId },
  )
  return unwrapOrder(data.removeOrderLine)
})
