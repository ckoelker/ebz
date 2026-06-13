import { defineConfig } from 'vitest/config';

// Spec-Test 2 (Plan §5): die GENERIERTE zod gegen valid/invalid-Fixtures. Reiner Node-Lauf,
// kein DOM nötig — der Test importiert nur die zod-Schemas aus src/gen und parst.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts'],
  },
});
