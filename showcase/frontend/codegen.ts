import type { CodegenConfig } from '@graphql-codegen/cli';

// Showcase M5 — GraphQL-Codegen (Leitplanke §8a-19).
// Erzeugt typsichere Operationen (TypedDocumentNode) direkt aus dem Vendure-
// Shop-API-Schema — inkl. der Custom Fields (§8a-4). Quelle ist die laufende
// Shop-API (Stack muss oben sein: docker compose up -d). KEIN Backend dazwischen:
// die Storefront spricht die Commerce-Engine direkt an (so im Realisierungsplan
// §2/§8a entschieden).
const config: CodegenConfig = {
  schema: process.env.SHOP_API_SCHEMA || 'http://localhost:3000/shop-api',
  documents: ['src/**/*.vue', 'src/**/*.ts', '!src/gql/**'],
  ignoreNoDocuments: true,
  generates: {
    './src/gql/': {
      preset: 'client',
      // Ohne Fragment-Masking: Felder sind direkt lesbar (passt zu unseren Stores).
      presetConfig: { fragmentMasking: false },
      config: {
        useTypeImports: true,
        // Vendure-Scalars korrekt mappen: Money = Cent-Ganzzahl (§8a-10).
        scalars: {
          Money: 'number',
          DateTime: 'string',
          JSON: 'Record<string, unknown>',
        },
      },
    },
  },
};

export default config;
