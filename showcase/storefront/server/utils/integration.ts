import type { H3Event } from 'h3'

// Server-zu-Server-Brücke zum Integration-Backend (E-Learning-Einschreibung, P7c).
// Auth via Keycloak-Service-Account (client_credentials, Realm ebz-staff, Rolle katalog-pflege).
// Wichtig: Das Token wird vom Container über keycloak.localhost:8080 geholt → Issuer
// keycloak.localhost:8080 = genau das, was die Integration erwartet (Split-Horizon).
let cachedToken: { value: string; exp: number } | null = null

async function serviceToken(): Promise<string> {
  const now = Date.now()
  if (cachedToken && cachedToken.exp > now + 5000) return cachedToken.value
  const cfg = useRuntimeConfig()
  const res = await $fetch<{ access_token: string; expires_in: number }>(cfg.kcStaffTokenUrl as string, {
    method: 'POST',
    body: new URLSearchParams({
      grant_type: 'client_credentials',
      client_id: cfg.kcServiceClientId as string,
      client_secret: cfg.kcServiceClientSecret as string,
    }),
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
  })
  cachedToken = { value: res.access_token, exp: now + res.expires_in * 1000 }
  return res.access_token
}

/**
 * E-Learning-Einschreibung anstoßen: die Integration löst je vendureProductId den WBT-Kurs auf und
 * ignoriert Nicht-WBT-Positionen (bestehende Outbox, L0–L3). Best-effort — Fehler dürfen die
 * Bestellung nicht scheitern lassen.
 */
export async function einschreibenWbt(
  _event: H3Event,
  payload: { vendureOrderId: string; keycloakSub: string; email: string; anzeigeName: string; vendureProductIds: string[] },
): Promise<void> {
  if (!payload.vendureProductIds.length) return
  const cfg = useRuntimeConfig()
  const token = await serviceToken()
  await $fetch(`${cfg.integrationUrl}/lms/einschreibungen/bestellung`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: payload,
  })
}
