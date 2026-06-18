// Warenkorb-Position ändern: Menge und/oder Teilnehmer:innen-Liste (strukturiert wie im MDM).
// Die Liste wird als JSON im OrderLine-Feld `teilnehmer` gespeichert; participantName/-Email werden
// aus dem/der ersten Teilnehmer:in abgeleitet (Kompatibilität mit bestehenden Abläufen).
import type { Order, Teilnehmer } from '../utils/order'

export default defineEventHandler(async (event) => {
  const body = await readBody<{ lineId: string; quantity: number; teilnehmer?: Teilnehmer[] }>(event)
  if (!body?.lineId) throw createError({ statusCode: 400, statusMessage: 'lineId fehlt' })

  const liste = Array.isArray(body.teilnehmer) ? body.teilnehmer : []
  const erste = liste[0]
  const customFields = {
    teilnehmer: liste.length ? JSON.stringify(liste) : null,
    participantName: erste ? `${erste.titel ? erste.titel + ' ' : ''}${erste.vorname} ${erste.nachname}`.trim() : null,
    participantEmail: erste?.email || null,
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
