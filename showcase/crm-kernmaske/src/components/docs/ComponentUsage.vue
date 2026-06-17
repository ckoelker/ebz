<script setup lang="ts">
import data from '../../docs/usage-data.json'

// Verwendungsnachweis (generiert via `pnpm usage`): welche Komponente enthält welche.
// Eigene Sub-Komponenten = explizite Imports; Nuxt-UI (<U…>) = Auto-Import (Template-Scan).
type Node = { name: string; group: string; children: string[]; ui: string[] }
const nodes = data.nodes as Node[]
const uiUsedIn = data.uiUsedIn as Record<string, string[]>

const gruppen = [...new Set(nodes.map(n => n.group))]
const byGroup = (g: string) => nodes.filter(n => n.group === g)
const parentsOf = (name: string) => nodes.filter(n => n.children.includes(name)).map(n => n.name)
const uiSorted = Object.keys(uiUsedIn).sort()
</script>

<template>
  <div class="max-w-5xl space-y-8">
    <header>
      <h1 class="text-xl font-bold text-highlighted">Komponenten-Verwendung</h1>
      <p class="text-sm text-muted">Generiert via <code>pnpm usage</code> (Stand {{ data.generatedAt }}).
        Eigene Sub-Komponenten = explizite Imports; Nuxt-UI-Komponenten sind Auto-Imports und werden per Template-Scan erfasst.</p>
    </header>

    <section v-for="g in gruppen" :key="g">
      <h2 class="text-sm font-semibold uppercase tracking-wide text-primary-700 mb-2">{{ g }}</h2>
      <div class="overflow-x-auto ring-1 ring-default rounded-lg">
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs uppercase text-muted bg-elevated">
              <th class="px-3 py-2 font-medium w-56">Komponente</th>
              <th class="px-3 py-2 font-medium">enthält (eigene)</th>
              <th class="px-3 py-2 font-medium">verwendet in</th>
              <th class="px-3 py-2 font-medium">Nuxt-UI</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-default">
            <tr v-for="n in byGroup(g)" :key="n.name" class="bg-default align-top">
              <td class="px-3 py-2 font-medium text-highlighted">{{ n.name }}</td>
              <td class="px-3 py-2">
                <div class="flex flex-wrap gap-1">
                  <UBadge v-for="c in n.children" :key="c" color="primary" variant="subtle" size="sm">{{ c }}</UBadge>
                  <span v-if="!n.children.length" class="text-dimmed">—</span>
                </div>
              </td>
              <td class="px-3 py-2">
                <div class="flex flex-wrap gap-1">
                  <UBadge v-for="p in parentsOf(n.name)" :key="p" color="neutral" variant="soft" size="sm">{{ p }}</UBadge>
                  <span v-if="!parentsOf(n.name).length" class="text-dimmed">— (Einstieg)</span>
                </div>
              </td>
              <td class="px-3 py-2 text-muted text-xs">{{ n.ui.join(', ') || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section>
      <h2 class="text-sm font-semibold uppercase tracking-wide text-primary-700 mb-2">Nuxt-UI-Komponenten — wo verwendet</h2>
      <div class="overflow-x-auto ring-1 ring-default rounded-lg">
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs uppercase text-muted bg-elevated">
              <th class="px-3 py-2 font-medium w-40">Nuxt-UI</th>
              <th class="px-3 py-2 font-medium w-16">Anz.</th>
              <th class="px-3 py-2 font-medium">verwendet in</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-default">
            <tr v-for="u in uiSorted" :key="u" class="bg-default align-top">
              <td class="px-3 py-2 font-mono text-default">{{ u }}</td>
              <td class="px-3 py-2"><UBadge color="primary" variant="soft" size="sm">{{ uiUsedIn[u].length }}</UBadge></td>
              <td class="px-3 py-2 text-muted text-xs">{{ uiUsedIn[u].join(', ') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
