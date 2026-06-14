import { defineConfig } from '@hey-api/openapi-ts';

// Stack-B-Generierung: EINE Quelle = /q/openapi des Quarkus-integration-Service. Erzeugt TS-Typen +
// Fetch-Client + zod-Schemas. `pnpm gen` regeneriert aus der laufenden Spec (Service muss oben sein)
// oder aus einem Schema-File via OPENAPI_URL (z. B. target/schema/openapi.json aus `mvn package`).
export default defineConfig({
  input: process.env.OPENAPI_URL || 'http://localhost:8090/q/openapi?format=json',
  output: {
    path: 'src/gen',
  },
  plugins: ['@hey-api/client-fetch', '@hey-api/typescript', '@hey-api/sdk', 'zod'],
});
