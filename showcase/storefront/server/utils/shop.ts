// Serverseitiger GraphQL-Aufruf an die Vendure-Shop-API. Läuft nur im Nuxt-Server
// (SSR + /api-Routen) → die Vendure-URL bleibt server-only (kein CORS im Browser).
export async function shopGql<T>(query: string, variables: Record<string, unknown> = {}): Promise<T> {
  const config = useRuntimeConfig()
  const res = await $fetch<{ data?: T; errors?: Array<{ message: string }> }>(config.shopApiUrl as string, {
    method: 'POST',
    body: { query, variables },
  })
  if (res.errors && res.errors.length) {
    throw createError({ statusCode: 502, statusMessage: 'Shop-API-Fehler: ' + res.errors.map((e) => e.message).join('; ') })
  }
  return res.data as T
}
