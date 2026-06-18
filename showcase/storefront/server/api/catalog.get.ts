// Katalog-Trefferliste über die Vendure-Shop-API `search` (P3): Freitext-/Angebotsnr-Suche,
// Facetten-Filter (AND zwischen Facetten, OR innerhalb), Collection, Sortierung, Pagination.
// Liefert zusätzlich die Facetten-/Collection-Aggregation für die Sidebar (Counts).
//
// Facetten kommen als feste Query-Keys (= Facet-Codes aus dem Seed) mit kommaseparierten
// FacetValue-IDs: ?thema=10,12&region=18  → facetValueFilters [{or:[10,12]},{or:[18]}].
const FACET_KEYS = ['veranstaltungsart', 'thema', 'branche', 'region', 'format'] as const

const TAKE = 12

export default defineEventHandler(async (event) => {
  const q = getQuery(event)
  const term = typeof q.term === 'string' ? q.term : ''
  const collectionSlug = typeof q.collection === 'string' && q.collection ? q.collection : undefined
  const page = Math.max(1, Number.parseInt(typeof q.page === 'string' ? q.page : '1', 10) || 1)
  const skip = (page - 1) * TAKE

  // Sortierung: relevanz (Default), name, preis auf-/absteigend.
  const sortMap: Record<string, Record<string, 'ASC' | 'DESC'>> = {
    'name-asc': { name: 'ASC' },
    'price-asc': { price: 'ASC' },
    'price-desc': { price: 'DESC' },
  }
  const sort = typeof q.sort === 'string' ? sortMap[q.sort] : undefined

  // Facetten-Filter aus den festen Keys lesen und gruppiert als OR-Listen bauen.
  const facetValueFilters: Array<{ or: string[] }> = []
  for (const key of FACET_KEYS) {
    const raw = q[key]
    const ids = (typeof raw === 'string' ? raw.split(',') : Array.isArray(raw) ? raw : [])
      .map((s) => String(s).trim())
      .filter(Boolean)
    if (ids.length) facetValueFilters.push({ or: ids })
  }

  const input: Record<string, unknown> = {
    groupByProduct: true,
    take: TAKE,
    skip,
    term,
    ...(collectionSlug ? { collectionSlug } : {}),
    ...(facetValueFilters.length ? { facetValueFilters } : {}),
    ...(sort ? { sort } : {}),
  }

  const data = await shopGql<{ search: SearchResult }>(
    `query($input: SearchInput!) {
      search(input: $input) {
        totalItems
        items {
          productName
          slug
          description
          sku
          currencyCode
          productAsset { preview }
          priceWithTax { __typename ... on SinglePrice { value } ... on PriceRange { min max } }
        }
        facetValues {
          count
          facetValue { id code name facet { id code name } }
        }
        collections {
          count
          collection { id slug name }
        }
      }
    }`,
    { input },
  )

  // Facetten für die Sidebar nach Facet gruppieren (stabile Reihenfolge per FACET_KEYS).
  const groups = new Map<string, FacetGroup>()
  for (const fv of data.search.facetValues) {
    const f = fv.facetValue.facet
    let g = groups.get(f.code)
    if (!g) {
      g = { code: f.code, name: f.name, values: [] }
      groups.set(f.code, g)
    }
    g.values.push({ id: fv.facetValue.id, code: fv.facetValue.code, name: fv.facetValue.name, count: fv.count })
  }
  const facetGroups = FACET_KEYS.map((k) => groups.get(k)).filter((g): g is FacetGroup => !!g)

  return {
    totalItems: data.search.totalItems,
    items: data.search.items,
    facetGroups,
    collections: data.search.collections.map((c) => ({
      id: c.collection.id,
      slug: c.collection.slug,
      name: c.collection.name,
      count: c.count,
    })),
    page,
    pageSize: TAKE,
    pageCount: Math.max(1, Math.ceil(data.search.totalItems / TAKE)),
  }
})

interface FacetGroup {
  code: string
  name: string
  values: Array<{ id: string; code: string; name: string; count: number }>
}

interface SearchResult {
  totalItems: number
  items: Array<{
    productName: string
    slug: string
    description: string
    sku: string
    currencyCode: string
    productAsset: { preview: string } | null
    priceWithTax:
      | { __typename: 'SinglePrice'; value: number }
      | { __typename: 'PriceRange'; min: number; max: number }
  }>
  facetValues: Array<{
    count: number
    facetValue: { id: string; code: string; name: string; facet: { id: string; code: string; name: string } }
  }>
  collections: Array<{ count: number; collection: { id: string; slug: string; name: string } }>
}
