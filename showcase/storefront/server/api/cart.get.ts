// Aktueller Warenkorb (Vendure activeOrder), session-gebunden über die vendure_token-Cookie.
import type { Order } from '../utils/order'

export default defineEventHandler(async (event) => {
  const data = await shopGql<{ activeOrder: Order | null }>(
    event,
    `query { activeOrder { ...OrderFields } } ${ORDER_FIELDS}`,
  )
  return data.activeOrder
})
