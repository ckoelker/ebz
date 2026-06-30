<script setup lang="ts">
// OAuth-Rückkehr von Keycloak: Code → Token (im Browser, PKCE) → Vendure-Bindung → zurück zur Zielseite.
const route = useRoute()
const { handleCallback } = useAuth()
const fehler = ref('')

useHead({ title: 'Anmeldung' })

onMounted(async () => {
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  const state = typeof route.query.state === 'string' ? route.query.state : ''
  if (route.query.error) {
    fehler.value = String(route.query.error_description || route.query.error)
    return
  }
  if (!code) {
    fehler.value = 'Kein Autorisierungscode erhalten.'
    return
  }
  try {
    const returnTo = await handleCallback(code, state)
    await navigateTo(returnTo, { replace: true })
  } catch (e: unknown) {
    fehler.value = (e as Error).message || 'Anmeldung fehlgeschlagen.'
  }
})
</script>

<template>
  <div class="mx-auto max-w-md py-16 text-center">
    <template v-if="!fehler">
      <UIcon name="i-lucide-loader-circle" class="mx-auto mb-3 size-8 animate-spin text-(--ui-primary)" />
      <p class="text-(--ui-text-muted)">Anmeldung wird abgeschlossen …</p>
    </template>
    <template v-else>
      <UIcon name="i-lucide-triangle-alert" class="mx-auto mb-3 size-8 text-(--ui-error)" />
      <p class="mb-4 text-(--ui-text-highlighted)">{{ fehler }}</p>
      <UButton to="/" color="primary" variant="subtle">Zur Startseite</UButton>
    </template>
  </div>
</template>
