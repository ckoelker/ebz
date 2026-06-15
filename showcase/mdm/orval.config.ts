import { defineConfig } from 'orval';

// Stack B (Codegen): EINE Quelle = /q/openapi des Quarkus-integration-Service. orval erzeugt
//  (1) typsichere axios-Funktionen + TS-Modelle (Enums als echte TS-Enums → Object.values für Dropdowns)
//  (2) zod-Schemas (separater Output, .zod.ts) für die vee-validate-Formulare.
// Same-origin-Aufrufe + Auth laufen über den Custom-Mutator src/api/http.ts.
// `pnpm gen` regeneriert aus der laufenden Spec (Service muss oben sein) oder via OPENAPI_URL.
const target = process.env.OPENAPI_URL || 'http://localhost:8090/q/openapi?format=json';

// Nur die vom Cockpit GENUTZTEN Tags generieren (kleinere src/api + Bundle): Bildung (Pflege),
// Dubletten-Review + Anmeldung-Workflow (HITL). Modelle/zod werden auf die referenzierten Schemas
// gepruned. Andere SPAs/Endpunkte (party/rechnung/outbox/…) bleiben außen vor.
const input = {
  target,
  filters: {
    mode: 'include' as const,
    tags: ['Bildung Resource', 'Dubletten Review Resource', 'Anmeldung Workflow Resource', 'LMS Katalog', 'LMS Einschreibung'],
  },
};

export default defineConfig({
  bildung: {
    input,
    output: {
      mode: 'tags-split',
      target: 'src/api/endpoints',
      schemas: 'src/api/model',
      client: 'axios-functions',
      mock: false, // MSW-Mocks als Folgeschritt (braucht msw + @faker-js/faker)
      override: {
        mutator: { path: 'src/api/http.ts', name: 'http' },
        enumGenerationType: 'enum',
      },
    },
  },
  bildungZod: {
    input,
    output: {
      mode: 'tags-split',
      target: 'src/api/zod',
      client: 'zod',
      fileExtension: '.zod.ts',
    },
  },
});
