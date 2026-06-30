// Ergänzt die von generate.py erzeugten (semantischen) BPMN-Dateien um ein Auto-Layout (DI-
// Koordinaten) via bpmn.io's bpmn-auto-layout und legt die fertigen, in Camunda Modeler/draw.io
// sauber öffnenden .bpmn nach docs/bpmn/ (committet).
import { readdir, readFile, writeFile, mkdir, rm } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { layoutProcess } from 'bpmn-auto-layout';

const root = dirname(fileURLToPath(import.meta.url));
const inDir = join(root, 'out');
const outDir = join(root, '..', 'bpmn');

await mkdir(outDir, { recursive: true });
// Altbestand im Zielordner entfernen (selbstheilend bei umbenannten/entfernten Verfahren/Phasen).
for (const f of (await readdir(outDir)).filter((f) => f.endsWith('.bpmn'))) {
  await rm(join(outDir, f));
}
const dateien = (await readdir(inDir)).filter((f) => f.endsWith('.bpmn')).sort();
if (dateien.length === 0) {
  console.error('Keine .bpmn in', inDir, '- bitte zuerst generate.py laufen lassen.');
  process.exit(1);
}
for (const f of dateien) {
  const xml = await readFile(join(inDir, f), 'utf8');
  if (xml.includes('swimlane-laidout')) {
    // Bereits vom eigenen Swimlane-Layouter (generate.py) layoutet → unverändert übernehmen.
    await writeFile(join(outDir, f), xml, 'utf8');
    console.log('  übernommen (swimlane)', f);
    continue;
  }
  const layoutet = await layoutProcess(xml);
  await writeFile(join(outDir, f), layoutet, 'utf8');
  console.log('  layoutet', f);
}
console.log(`OK: ${dateien.length} BPMN -> ${outDir}`);
