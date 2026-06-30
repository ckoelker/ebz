// Erzeugt dependency-graph.svg aus dependency-cruiser — ohne System-Graphviz,
// via Graphviz-as-WASM (@hpcc-js/wasm-graphviz). Cross-platform.
//   node scripts/graph.mjs           → detaillierter Graph (Reporter "dot")
//   node scripts/graph.mjs --archi   → nach Ordnern verdichtet (Reporter "archi")
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { Graphviz } from '@hpcc-js/wasm-graphviz'

const archi = process.argv.includes('--archi')
const outputType = archi ? 'archi' : 'dot'

// depcruise-JS direkt über Node aufrufen (kein .cmd-Spawn → cross-platform, kein Shell).
const cli = 'node_modules/dependency-cruiser/bin/dependency-cruise.mjs'
const dot = execFileSync(
  process.execPath,
  [cli, 'src', '--include-only', '^src', '--output-type', outputType],
  { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 },
)

const graphviz = await Graphviz.load()
const svg = graphviz.layout(dot, 'svg', 'dot')
const out = 'dependency-graph.svg'
writeFileSync(out, svg)
console.log(`→ ${out} geschrieben (${(svg.length / 1024).toFixed(1)} kB, Reporter: ${outputType})`)
