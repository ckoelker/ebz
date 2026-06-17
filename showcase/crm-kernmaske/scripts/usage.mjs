// Erzeugt src/docs/usage-data.json — Verwendungsnachweis der Komponenten:
// eigene Sub-Komponenten (explizite Imports) + Nuxt-UI-Komponenten (Template-Scan,
// da Auto-Import). Wird von der Storybook-Seite „Übersicht/Komponenten-Verwendung"
// gerendert. Reproduzierbar:  pnpm usage
import { readdirSync, readFileSync, statSync, writeFileSync, mkdirSync } from 'node:fs'
import { join, basename } from 'node:path'

const SRC = 'src'
const files = []
const walk = (d) => {
  for (const e of readdirSync(d)) {
    const p = join(d, e)
    if (statSync(p).isDirectory()) walk(p)
    else if (p.endsWith('.vue')) files.push(p.replaceAll('\\', '/'))
  }
}
walk(join(SRC, 'ui'))
walk(join(SRC, 'features'))

const groupOf = (p) => {
  if (p.includes('/ui/')) return 'ui (Primitives)'
  const m = p.match(/features\/([^/]+)\//)
  return m && m[1] !== basename(p) ? m[1] : 'features (root)'
}

const nodes = []
const uiUsedIn = {}
for (const f of files) {
  const src = readFileSync(f, 'utf8')
  const name = basename(f).replace('.vue', '')
  const children = [...src.matchAll(/import\s+(\w+)\s+from\s+['"][^'"]+\.vue['"]/g)].map(m => m[1])
  const ui = [...new Set([...src.matchAll(/<(U[A-Z][A-Za-z0-9]*)/g)].map(m => m[1]))].sort()
  for (const u of ui) (uiUsedIn[u] ??= []).push(name)
  nodes.push({ name, group: groupOf(f), children: [...new Set(children)].sort(), ui })
}
nodes.sort((a, b) => a.group.localeCompare(b.group) || a.name.localeCompare(b.name))
for (const k of Object.keys(uiUsedIn)) uiUsedIn[k].sort()

mkdirSync(join(SRC, 'docs'), { recursive: true })
const out = join(SRC, 'docs', 'usage-data.json')
writeFileSync(out, JSON.stringify({ generatedAt: new Date().toISOString().slice(0, 10), nodes, uiUsedIn }, null, 2))
console.log(`→ ${out} (${nodes.length} Komponenten, ${Object.keys(uiUsedIn).length} Nuxt-UI-Komponenten)`)
