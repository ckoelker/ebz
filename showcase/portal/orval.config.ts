import { defineConfig } from 'orval';

// Stack B (Codegen): EINE Quelle = /q/openapi des Quarkus-integration-Service. orval erzeugt
// axios-Funktionen + TS-Modelle (Enums als TS-Enums) und zod-Schemas (separater .zod.ts-Output).
// Same-origin-Aufrufe + Auth laufen über den Custom-Mutator src/api/http.ts.
const input = process.env.OPENAPI_URL || 'http://localhost:8090/q/openapi?format=json';

export default defineConfig({
  party: {
    input: { target: input },
    output: {
      mode: 'tags-split',
      target: 'src/api/endpoints',
      schemas: 'src/api/model',
      client: 'axios-functions',
      mock: false,
      override: {
        mutator: { path: 'src/api/http.ts', name: 'http' },
        enumGenerationType: 'enum',
      },
    },
  },
  partyZod: {
    input: { target: input },
    output: {
      mode: 'tags-split',
      target: 'src/api/zod',
      client: 'zod',
      fileExtension: '.zod.ts',
    },
  },
});
