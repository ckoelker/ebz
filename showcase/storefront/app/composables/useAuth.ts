interface Customer {
  id: string
  emailAddress: string
  firstName: string
  lastName: string
}

// Kunden-SSO (Keycloak ebz-customers). Das Token wird im Browser geholt (korrekter Issuer
// localhost:8088) und serverseitig an Vendure `authenticate` gereicht (server/api/auth/keycloak).
// Hinweis: Im Showcase per Resource-Owner-Password-Grant; Produktivpfad = Authorization-Code-Flow
// (wie die übrigen SPAs). Vendure-Shop-API bleibt server-only.
export function useAuth() {
  const customer = useState<Customer | null>('customer', () => null)
  const fehler = useState<string>('auth-fehler', () => '')
  const busy = useState<boolean>('auth-busy', () => false)
  const istAngemeldet = computed(() => customer.value !== null)

  async function refresh() {
    customer.value = await useRequestFetch()<Customer | null>('/api/auth/me')
  }

  async function login(username: string, password: string): Promise<boolean> {
    busy.value = true
    fehler.value = ''
    try {
      const cfg = useRuntimeConfig().public
      // 1) Token im Browser bei Keycloak holen (CORS für localhost:3001 freigegeben).
      const form = new URLSearchParams({ grant_type: 'password', client_id: cfg.kcClientId, username, password })
      const tok = await $fetch<{ access_token?: string }>(
        `${cfg.kcUrl}/realms/${cfg.kcRealm}/protocol/openid-connect/token`,
        { method: 'POST', body: form, headers: { 'content-type': 'application/x-www-form-urlencoded' } },
      ).catch(() => ({ access_token: undefined }))
      if (!tok.access_token) {
        fehler.value = 'Anmeldung fehlgeschlagen (Benutzername/Passwort).'
        return false
      }
      // 2) Token serverseitig an Vendure binden.
      customer.value = await $fetch<Customer>('/api/auth/keycloak', { method: 'POST', body: { token: tok.access_token } })
      return true
    } catch (e: unknown) {
      const err = e as { data?: { statusMessage?: string }; statusMessage?: string }
      fehler.value = err.data?.statusMessage || err.statusMessage || 'Anmeldung fehlgeschlagen.'
      return false
    } finally {
      busy.value = false
    }
  }

  async function logout() {
    await $fetch('/api/auth/logout', { method: 'POST' })
    customer.value = null
  }

  return { customer, istAngemeldet, fehler, busy, refresh, login, logout }
}
