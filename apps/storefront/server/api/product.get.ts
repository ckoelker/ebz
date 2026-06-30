// Produkt-Detail über die Vendure-Shop-API (per Slug). Liefert Detailblöcke (Custom-Fields),
// Personen-Relationen und die buchbaren Durchführungen (Varianten). Bewertungen folgen in P4.
export default defineEventHandler(async (event) => {
  const q = getQuery(event)
  const slug = typeof q.slug === 'string' ? q.slug : ''
  if (!slug) {
    throw createError({ statusCode: 400, statusMessage: 'slug fehlt' })
  }
  const data = await shopGql<{ product: unknown }>(
    event,
    `query($slug: String!) {
      product(slug: $slug) {
        id
        name
        slug
        description
        featuredAsset { preview }
        facetValues { code name facet { code name } }
        customFields {
          angebotsnummer zielgruppe abschluss
          inhalteHtml lernzieleHtml nutzenHtml methodikHtml voraussetzungenHtml
          foerderhinweisHtml ablaufHtml leistungenHtml faqHtml
          bestellbar anmeldungUrl
          ansprechpartner { name email telefon foto { preview } }
          dozenten { name vita }
        }
        variants {
          id name sku
          priceWithTax
          currencyCode
          customFields { terminDatum ort veranstaltungsformat }
        }
      }
    }`,
    { slug },
  )
  if (!data.product) {
    throw createError({ statusCode: 404, statusMessage: 'Produkt nicht gefunden' })
  }
  return data.product
})
