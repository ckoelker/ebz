// Aktueller eingeloggter Kunde (Vendure activeCustomer) — null wenn Gast.
export default defineEventHandler(async (event) => {
  const data = await shopGql<{ activeCustomer: unknown | null }>(
    event,
    `query { activeCustomer { id emailAddress firstName lastName } }`,
  )
  return data.activeCustomer
})
