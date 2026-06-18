// Hauptnavigation: Top-Level-Collections (built-in) + veröffentlichte Menü-Seiten (CMS).
export default defineEventHandler(async (event) => {
  const data = await shopGql<{
    collections: { items: Array<{ slug: string; name: string; parent: { slug: string } | null }> }
    menuPages: Array<{ slug: string; titel: string; menuTitel: string | null }>
  }>(
    event,
    `query {
      collections(options: { topLevelOnly: true, take: 50 }) {
        items { slug name parent { slug } }
      }
      menuPages { slug titel menuTitel }
    }`,
  )
  return {
    collections: data.collections.items
      .filter((c) => c.slug !== '__root_collection__')
      .map((c) => ({ slug: c.slug, name: c.name })),
    pages: data.menuPages.map((p) => ({ slug: p.slug, titel: p.menuTitel || p.titel })),
  }
})
