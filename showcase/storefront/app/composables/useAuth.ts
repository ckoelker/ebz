interface Customer {
  id: string
  emailAddress: string
  firstName: string
  lastName: string
}

// Kunden-SSO (Keycloak ebz-customers) per Authorization-Code-Flow mit PKCE und der gebrandeten
// Keycloak-Login-/Registrierungsseite (Redirect statt In-App-Formular — Voraussetzung für
// Self-Registration, Google-Login und E-Mail-/SMS-Verifizierung). Der Code-Tausch läuft im Browser
// (öffentlicher Client, korrekter Issuer localhost:8088) → das Access-Token wird serverseitig an
// Vendure `authenticate` gebunden (server/api/auth/keycloak). Die Vendure-Shop-API bleibt server-only.
export function useAuth() {
  const customer = useState<Customer | null>('customer', () => null)
  const busy = useState<boolean>('auth-busy', () => false)
  // Nur ein echtes Customer-Objekt (mit id) gilt als angemeldet — ein leerer /me-Body darf nicht
  // fälschlich als „angemeldet" zählen (sonst greift die Checkout-Login-Pflicht nicht).
  const istAngemeldet = computed(() => !!customer.value?.id)

  async function refresh() {
    const c = await useRequestFetch()<Customer | null>('/api/auth/me')
    customer.value = c && c.id ? c : null
  }

  // ── PKCE-Helfer (nur Browser) ──
  function randomString(len = 64): string {
    const a = new Uint8Array(len)
    crypto.getRandomValues(a)
    return base64url(a)
  }
  function base64url(bytes: Uint8Array | ArrayBuffer): string {
    const b = bytes instanceof ArrayBuffer ? new Uint8Array(bytes) : bytes
    let s = ''
    for (const x of b) s += String.fromCharCode(x)
    return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  }
  async function challenge(verifier: string): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
    return base64url(digest)
  }

  // Redirect zur gebrandeten Keycloak-Seite (path 'auth' = Login, 'registrations' = Registrierung).
  async function go(path: 'auth' | 'registrations', returnTo?: string) {
    const cfg = useRuntimeConfig().public
    const verifier = randomString()
    const state = randomString(16)
    sessionStorage.setItem('pkce_verifier', verifier)
    sessionStorage.setItem('oauth_state', state)
    sessionStorage.setItem('oauth_return', returnTo || useRoute().fullPath)
    const params = new URLSearchParams({
      client_id: cfg.kcClientId,
      response_type: 'code',
      scope: 'openid profile email',
      redirect_uri: `${location.origin}/auth/callback`,
      code_challenge: await challenge(verifier),
      code_challenge_method: 'S256',
      state,
    })
    location.href = `${cfg.kcUrl}/realms/${cfg.kcRealm}/protocol/openid-connect/${path}?${params}`
  }

  const login = (returnTo?: string) => go('auth', returnTo)
  const register = (returnTo?: string) => go('registrations', returnTo)

  // Code-Tausch nach Rückkehr von Keycloak (auf /auth/callback aufgerufen).
  async function handleCallback(code: string, state: string): Promise<string> {
    if (state !== sessionStorage.getItem('oauth_state')) {
      throw new Error('Ungültiger Login-Status (state).')
    }
    const verifier = sessionStorage.getItem('pkce_verifier') || ''
    const cfg = useRuntimeConfig().public
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: cfg.kcClientId,
      code,
      redirect_uri: `${location.origin}/auth/callback`,
      code_verifier: verifier,
    })
    const tok = await $fetch<{ access_token?: string }>(
      `${cfg.kcUrl}/realms/${cfg.kcRealm}/protocol/openid-connect/token`,
      { method: 'POST', body, headers: { 'content-type': 'application/x-www-form-urlencoded' } },
    )
    if (!tok.access_token) throw new Error('Kein Token erhalten.')
    customer.value = await $fetch<Customer>('/api/auth/keycloak', { method: 'POST', body: { token: tok.access_token } })
    const returnTo = sessionStorage.getItem('oauth_return') || '/'
    sessionStorage.removeItem('pkce_verifier')
    sessionStorage.removeItem('oauth_state')
    sessionStorage.removeItem('oauth_return')
    return returnTo
  }

  async function logout() {
    await $fetch('/api/auth/logout', { method: 'POST' })
    customer.value = null
    const cfg = useRuntimeConfig().public
    const params = new URLSearchParams({
      client_id: cfg.kcClientId,
      post_logout_redirect_uri: `${location.origin}/`,
    })
    // Auch die Keycloak-SSO-Session beenden (sonst sofortiger Auto-Re-Login).
    location.href = `${cfg.kcUrl}/realms/${cfg.kcRealm}/protocol/openid-connect/logout?${params}`
  }

  return { customer, istAngemeldet, busy, refresh, login, register, handleCallback, logout }
}
