/**
 * dependency-cruiser — Abhängigkeits-/Verwendungs-Baum der CRM-Kernmaske.
 * Zeigt die expliziten Importe (eigene Sub-Komponenten). Nuxt-UI-Komponenten
 * (<UButton> …) sind Auto-Imports von @nuxt/ui/vite und erscheinen hier NICHT.
 *
 * Nutzung:  pnpm graph        → dependency-graph.svg (detailliert)
 *           pnpm graph:archi  → dependency-graph.svg (nach Ordnern verdichtet)
 *           pnpm depcruise    → nur Regeln prüfen (CI-tauglich)
 */
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      comment: 'Zyklische Abhängigkeiten vermeiden.',
      severity: 'warn',
      from: {},
      to: { circular: true },
    },
    {
      name: 'ui-rein',
      comment: 'ui/-Primitives dürfen NICHT aus features/ oder data/ importieren (nur vue + domain). Stories ausgenommen.',
      severity: 'error',
      from: { path: '^src/ui/', pathNot: '\\.stories\\.' },
      to: { path: '^src/(features|data)/' },
    },
    {
      name: 'domain-rein',
      comment: 'domain/ ist die unterste Schicht — kein Import aus ui/features/data.',
      severity: 'error',
      from: { path: '^src/domain/' },
      to: { path: '^src/(ui|features|data)/' },
    },
    {
      name: 'schichtung-keine-shell-importe',
      comment: 'Bausteine/Dialoge/Tabs/Cockpit sollen die Shell nicht importieren (untere Schicht zieht nicht die obere).',
      severity: 'warn',
      from: { path: '^src/features/(bausteine|dialoge|tabs|cockpit)/' },
      to: { path: '^src/features/shell/' },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    includeOnly: '^src',
    tsConfig: { fileName: 'tsconfig.json' },
    enhancedResolveOptions: {
      extensions: ['.ts', '.js', '.vue', '.json'],
    },
    reporterOptions: {
      dot: {
        collapsePattern: 'node_modules/(@[^/]+/[^/]+|[^/]+)',
        theme: {
          graph: { rankdir: 'TB', splines: 'true', bgcolor: 'white' },
          node: { shape: 'box', style: 'rounded,filled', fillcolor: '#e9eff7', color: '#0b3a6f', fontname: 'Segoe UI' },
          edge: { color: '#2f66a3' },
          modules: [
            { criteria: { source: 'stories' }, attributes: { fillcolor: '#fbf0d9' } },
            { criteria: { source: 'src/components/shell' }, attributes: { fillcolor: '#c9d9ec' } },
          ],
        },
      },
      archi: {
        collapsePattern: '^src/(components/[^/]+|mock|assets)',
      },
    },
  },
}
