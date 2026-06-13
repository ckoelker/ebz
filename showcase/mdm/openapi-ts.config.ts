import { defineConfig } from '@hey-api/openapi-ts';

// Stack-B-Generierung (Plan §5): EINE Quelle = /q/openapi des Quarkus-bildung-Service.
// Erzeugt TS-Typen + Fetch-Client + zod-Schemas. `pnpm gen` regeneriert aus der laufenden Spec
// (Service muss oben sein). Per-Typ-Schemas (SeminarDto …) — kein oneOf/discriminatedUnion (§11.2).
export default defineConfig({
  input: process.env.OPENAPI_URL || 'http://localhost:8090/q/openapi?format=json',
  output: {
    path: 'src/gen',
  },
  plugins: [
    '@hey-api/client-fetch',
    '@hey-api/typescript',
    '@hey-api/sdk',
    'zod',
  ],
});
