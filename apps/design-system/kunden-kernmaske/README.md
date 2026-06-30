# EBZ Kunden-/Marketing-Design-System (Storybook)

Best-practice Storybook für die **kundennahen** EBZ-Oberflächen — **Shop** (`storefront`) und
**Außenportal** (`portal`). Gegenstück zum internen CRM-/Admin-Storybook `crm-kernmaske`.

Die Naht ist bewusst getrennt: kundennah/Marketing vs. intern/langläufig. Geteilt wird nur die
**invariante Domain-Logik** (`@crm-ui/domain` — EUR/Datum/Status), **nicht** die Admin-Komponenten
(`@crm-ui/ui`). Kunden-Primitive leben hier in `src/ui` und sind prop-rein (SSR-safe).

## Stack

- Nuxt UI 4 **Vue-only** über `@nuxt/ui/vite` (kein Nuxt-Runtime) — wie `crm-kernmaske`.
- Storybook 10 + `@storybook/vue3-vite`.
- EBZ-Navy-Branding (aktuell identisch zu Shop/MDM; eigene Marketing-Palette = Token-Swap in
  `src/assets/css/main.css`).

## State-of-the-art

- **Interaktive Controls** — jede Story über `args` + `argTypes`.
- **Autodocs** — `tags: ['autodocs']` global (preview.ts) → Doks-Seite je Komponente.
- **a11y** — `@storybook/addon-a11y` (axe-core); Gate über `parameters.a11y.test` (aktuell `todo`,
  auf `error` stellen, sobald axe-sauber).
- **Interaction-Tests** — `play()` mit `storybook/test` (z. B. StatusBadge/PreisBadge).
- **Theme-Toolbar** — Light/Dark.

## Befehle

```bash
pnpm install
pnpm storybook        # Dev-Server auf :6008
pnpm build-storybook  # statischer Build (CI-baubar) → storybook-static/
pnpm typecheck        # vue-tsc
```

## Offen (Roadmap zu „voll SOTA")

- **Visuelle Regression (Chromatic)** — braucht SaaS-Account/Token; auf Zuruf verdrahten.
- **Test-Runner/CI** — `build-storybook` in die CI/`showcase-aufbau` hängen; a11y-Gate auf `error`.
- Weitere Kunden-Primitive extrahieren, sobald Shop/Portal sie real teilen (→ künftiges `customer-ui`).
