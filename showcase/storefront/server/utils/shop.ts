import type { H3Event } from 'h3'

// Serverseitiger GraphQL-Aufruf an die Vendure-Shop-API. Läuft nur im Nuxt-Server
// (SSR + /api-Routen) → die Vendure-URL bleibt server-only (kein CORS im Browser).
//
// Session-Kontinuität (Warenkorb/activeOrder): Vendure-Bearer-Token-Methode. Der Token
// wird in einer eigenen httpOnly-Cookie `vendure_token` gehalten, bei jedem Aufruf als
// `Authorization: Bearer …` an Vendure gereicht und bei Erneuerung aus dem Response-Header
// `vendure-auth-token` zurückgeschrieben. So hängt der anonyme Warenkorb an dieser Cookie
// und übersteht Reloads — ohne dass der Browser je direkt mit Vendure spricht.
const TOKEN_COOKIE = 'vendure_token'

export async function shopGql<T>(
  event: H3Event,
  query: string,
  variables: Record<string, unknown> = {},
): Promise<T> {
  const config = useRuntimeConfig()
  // In-Request-Kontinuität: ein zuvor (im selben Request) rotiertes Token steht in der Cookie noch
  // nicht zur Verfügung → über event.context durchreichen, damit Folge-Calls dieselbe Session nutzen.
  const token = (event.context.vendureToken as string | undefined) ?? getCookie(event, TOKEN_COOKIE)
  const headers: Record<string, string> = { 'content-type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await $fetch.raw<{ data?: T; errors?: Array<{ message: string }> }>(config.shopApiUrl as string, {
    method: 'POST',
    body: { query, variables },
    headers,
  })

  // Token-Erneuerung übernehmen (neuer/rotierter Session-Token).
  const newToken = res.headers.get('vendure-auth-token')
  if (newToken && newToken !== token) {
    event.context.vendureToken = newToken
    setCookie(event, TOKEN_COOKIE, newToken, {
      httpOnly: true,
      sameSite: 'lax',
      path: '/',
      maxAge: 60 * 60 * 24 * 30,
    })
  }

  const body = res._data
  if (body?.errors && body.errors.length) {
    throw createError({ statusCode: 502, statusMessage: 'Shop-API-Fehler: ' + body.errors.map((e) => e.message).join('; ') })
  }
  return body?.data as T
}
