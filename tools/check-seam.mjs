#!/usr/bin/env node
// Naht-Wächter (Design-System). Verhindert cross-seam-Imports, die der TypeScript-Compiler NICHT sieht
// (ein falscher Paket-Import kompiliert fehlerfrei) — das ist die einzige Architektur-Invariante, die
// der typecheck/build-Loop nicht abdeckt. Reiner Text-Scan über Import-Specifier in .vue + .ts/.js,
// keine neuen Abhängigkeiten, kein .vue-Parser nötig.
//
// Regeln (siehe DESIGN-SYSTEM.md → Landkarte/Naht):
//  - Kundennahe Apps (portal, storefront) dürfen KEINE gebrandeten Admin-Komponenten (@crm-ui/ui)
//    importieren. Erlaubt: @crm-ui/domain (geteilte Logik), @customer-ui, @ui-base.
//  - Admin-App (mdm) darf KEINE gebrandeten Kunden-Komponenten (@customer-ui) importieren.
//    Erlaubt: @crm-ui (alles), @ui-base, @crm-ui/domain.
//  - @ui-base muss neutral/dependency-frei bleiben: kein @nuxt/ui, kein gebrandetes Paket
//    (genau das macht es über die Naht hinweg teilbar).
//
// Lauf: `node tools/check-seam.mjs` (exit 1 bei Verstoß). Auch als Schritt `naht` in tools/stack.sh.
import { readdir, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SKIP = new Set(['node_modules', 'dist', '.nuxt', '.output', 'storybook-static', '.git', '.cache']);
const EXT = new Set(['.vue', '.ts', '.mts', '.cts', '.js', '.mjs', '.cjs', '.tsx', '.jsx']);

/** Erlaubt Präfix-Treffer auf den Import-Specifier (z. B. @crm-ui/ui ⇒ @crm-ui/ui/PartyAvatar.vue). */
const matcher = (spec) => {
  const e = spec.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`(?:from|import\\s*\\()\\s*['"]${e}`);
};

const RULES = [
  {
    name: 'portal (kundennah) ↛ @crm-ui/ui',
    roots: ['apps/portal'],
    forbid: ['@crm-ui/ui'],
    hint: 'Kundennahe App: gebrandete Admin-Komponenten verboten. Erlaubt: @crm-ui/domain, @customer-ui, @ui-base.',
  },
  {
    name: 'storefront (kundennah) ↛ @crm-ui/ui',
    roots: ['apps/storefront'],
    forbid: ['@crm-ui/ui'],
    hint: 'Kundennahe App: gebrandete Admin-Komponenten verboten. Erlaubt: @crm-ui/domain, @customer-ui, @ui-base.',
  },
  {
    name: 'mdm (admin) ↛ @customer-ui',
    roots: ['apps/mdm'],
    forbid: ['@customer-ui'],
    hint: 'Admin-App: gebrandete Kunden-Komponenten verboten. Erlaubt: @crm-ui, @ui-base, @crm-ui/domain.',
  },
  {
    name: '@ui-base muss neutral bleiben',
    roots: ['packages/ui-base/src'],
    forbid: ['@nuxt/ui', '@crm-ui', '@customer-ui'],
    hint: '@ui-base ist die dependency-freie, über die Naht geteilte Schicht: keine @nuxt/ui-Typen, kein gebrandetes Paket.',
  },
];

async function* walk(dir) {
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const ent of entries) {
    if (ent.name.startsWith('.') && ent.name !== '.storybook') continue;
    if (SKIP.has(ent.name)) continue;
    const full = path.join(dir, ent.name);
    if (ent.isDirectory()) yield* walk(full);
    else if (EXT.has(path.extname(ent.name))) yield full;
  }
}

const violations = [];
for (const rule of RULES) {
  const tests = rule.forbid.map((spec) => ({ spec, re: matcher(spec) }));
  for (const root of rule.roots) {
    for await (const file of walk(path.join(ROOT, root))) {
      const lines = (await readFile(file, 'utf8')).split(/\r?\n/);
      lines.forEach((line, i) => {
        for (const { spec, re } of tests) {
          if (re.test(line)) {
            violations.push({ rule, spec, file: path.relative(ROOT, file), line: i + 1, text: line.trim() });
          }
        }
      });
    }
  }
}

if (violations.length === 0) {
  console.log('✓ Naht-Wächter: keine cross-seam-Imports gefunden.');
  process.exit(0);
}

console.error(`✗ Naht-Wächter: ${violations.length} Verstoß/Verstöße gegen die Design-System-Naht:\n`);
let lastRule = null;
for (const v of violations) {
  if (v.rule !== lastRule) {
    console.error(`  ▸ ${v.rule.name}\n    ${v.rule.hint}`);
    lastRule = v.rule;
  }
  console.error(`      ${v.file}:${v.line}  (importiert ${v.spec})\n        ${v.text}`);
}
console.error('\nNaht siehe docs/architecture/DESIGN-SYSTEM.md (Landkarte/Naht).');
process.exit(1);
