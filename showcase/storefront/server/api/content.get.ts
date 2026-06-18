// Redaktionelle CMS-Seite (nur veröffentlicht) über die Vendure-Shop-API, nach slug.
export default defineEventHandler(async (event) => {
  const q = getQuery(event)
  const slug = typeof q.slug === 'string' ? q.slug : ''
  if (!slug) throw createError({ statusCode: 400, statusMessage: 'slug fehlt' })

  const data = await shopGql<{ contentPage: ContentPage | null }>(
    event,
    `query($slug: String!) {
      contentPage(slug: $slug) {
        slug titel inhaltHtml metaTitle metaDescription
      }
    }`,
    { slug },
  )
  if (!data.contentPage) throw createError({ statusCode: 404, statusMessage: 'Seite nicht gefunden' })
  return data.contentPage
})

interface ContentPage {
  slug: string
  titel: string
  inhaltHtml: string | null
  metaTitle: string | null
  metaDescription: string | null
}
