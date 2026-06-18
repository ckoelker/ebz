// Katalog-Trefferliste über die Vendure-Shop-API `search`-Query (Freitext + Pagination).
// Facetten-Filter folgen in P3.
export default defineEventHandler(async (event) => {
  const q = getQuery(event)
  const term = typeof q.term === 'string' ? q.term : ''
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
      }
    }`,
    { input: { groupByProduct: true, take: 24, term } },
  )
  return data.search
})

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
}
