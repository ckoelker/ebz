<script setup lang="ts">
// Redaktionelle CMS-Seite (SSR), z. B. /seite/ueber-uns. Inhalt aus Vendure (ContentPage-Plugin).
const route = useRoute()
const slug = computed(() => String(route.params.slug))

const { data: page, error } = await useFetch('/api/content', { query: { slug } })
if (error.value) {
  throw createError({ statusCode: error.value.statusCode || 404, statusMessage: 'Seite nicht verfügbar', fatal: true })
}

const url = useRequestURL()
useHead(() => ({
  title: (page.value as any)?.metaTitle || (page.value as any)?.titel,
  link: [{ rel: 'canonical', href: `${url.origin}/seite/${slug.value}` }],
  meta: [{ name: 'description', content: (page.value as any)?.metaDescription ?? '' }],
}))
</script>

<template>
  <article v-if="page" class="mx-auto max-w-3xl">
    <UButton to="/" variant="link" color="neutral" icon="i-lucide-arrow-left" class="mb-2 -ml-2">Zum Katalog</UButton>
    <h1 class="text-2xl font-semibold text-(--ui-text-highlighted)">{{ (page as any).titel }}</h1>
    <!-- Inhalt aus Vendure (ContentPage.inhaltHtml), serverseitig gerendert -->
    <div class="prose prose-sm mt-4 max-w-none text-(--ui-text)" v-html="(page as any).inhaltHtml" />
  </article>
</template>
