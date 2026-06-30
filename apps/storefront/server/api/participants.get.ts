// Teilnehmer-Vorschläge für den Checkout: Personen der Käufer-Organisation. Identität der
// angemeldeten Besteller:in = Vendure-activeCustomer-E-Mail + Keycloak-sub (httpOnly-Cookie).
// Nur für angemeldete Kund:innen sinnvoll; sonst leere Liste.
export default defineEventHandler(async (event) => {
  const sub = getCookie(event, 'kc_sub') ?? null
  const me = await shopGql<{ activeCustomer: { emailAddress: string } | null }>(
    event,
    `query { activeCustomer { emailAddress } }`,
  )
  const email = me.activeCustomer?.emailAddress ?? null
  if (!email && !sub) return []
  return teilnehmerVorschlaege(email, sub)
})
