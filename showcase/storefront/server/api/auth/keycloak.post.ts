// Kunden-SSO (P7c): das im Browser geholte Keycloak-Token (Realm ebz-customers, korrekter
// Issuer localhost:8088) wird hier serverseitig an die Vendure-Shop-API `authenticate` gereicht
// → bindet den Customer an die Session (vendure_token-Cookie). Die Keycloak-`sub` legen wir in
// einer httpOnly-Cookie ab (für den Einschreibungs-Trigger beim WBT-Kauf).
function decodeSub(token: string): string | null {
  try {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'))
    return typeof payload.sub === 'string' ? payload.sub : null
  } catch {
    return null
  }
}

export default defineEventHandler(async (event) => {
  const { token } = await readBody<{ token: string }>(event)
  if (!token) throw createError({ statusCode: 400, statusMessage: 'token fehlt' })

  const data = await shopGql<{ authenticate: { __typename: string; identifier?: string; message?: string } }>(
    event,
    `mutation($t: String!) {
      authenticate(input: { keycloak: { token: $t } }) {
        __typename
        ... on CurrentUser { identifier }
        ... on ErrorResult { message }
      }
    }`,
    { t: token },
  )
  if (data.authenticate.__typename !== 'CurrentUser') {
    throw createError({ statusCode: 401, statusMessage: data.authenticate.message || 'Login fehlgeschlagen' })
  }

  const sub = decodeSub(token)
  if (sub) {
    setCookie(event, 'kc_sub', sub, { httpOnly: true, sameSite: 'lax', path: '/', maxAge: 60 * 60 * 24 })
  }

  const me = await shopGql<{ activeCustomer: Customer | null }>(
    event,
    `query { activeCustomer { id emailAddress firstName lastName } }`,
  )
  return me.activeCustomer
})

interface Customer {
  id: string
  emailAddress: string
  firstName: string
  lastName: string
}
