// Abmelden: Vendure-Session beenden + kc_sub-Cookie verwerfen.
export default defineEventHandler(async (event) => {
  await shopGql<{ logout: { success: boolean } }>(event, `mutation { logout { success } }`)
  deleteCookie(event, 'kc_sub', { path: '/' })
  return { success: true }
})
