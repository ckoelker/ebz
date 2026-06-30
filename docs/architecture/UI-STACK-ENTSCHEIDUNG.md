# UI-Stack: Nuxt UI + Storybook (Decision Record)

**Status:** bestätigt (2026-06-30). **Kontext:** Disput Design ↔ Entwicklung über die UI-Tooling-Wahl.
**Frage:** Wäre mit **shadcn-vue + Histoire** „dasselbe" möglich wie mit unserem **Nuxt UI + Storybook**, und
welche Variante passt besser zu *diesem* Projekt?

## TL;DR
Es sind **zwei unabhängige Entscheidungen** (mischbar): (1) UI-Bibliothek, (2) Component-Workshop.
**Empfehlung: beides behalten — Nuxt UI + Storybook.** shadcn-vue ist die prinzipientreuere Wahl *nur*,
wenn volle Komponenten-Code-**Ownership** ein harter Design-Anspruch ist UND das Team die Pflege übernimmt.
Histoire scheidet für einen Mehrjahres-Stack wegen **eingeschlafener Wartung** aus.

**Wichtigster, oft übersehener Fakt:** Nuxt UI *und* shadcn-vue bauen beide auf **Reka UI** (a11y-Primitive,
ex-Radix-Vue) + Tailwind. Der Unterschied ist **nicht** Qualität/Barrierefreiheit, sondern
**„gemanagtes Design-System (Nuxt UI)" vs. „eigener, einkopierter Komponenten-Code (shadcn-vue)".**

## Entscheidung 1 — UI-Bibliothek

| Kriterium | **Nuxt UI** (Status quo) | **shadcn-vue** |
|---|---|---|
| Fundament | Reka UI + Tailwind v4 | Reka UI + Tailwind (**gleich**) |
| Modell | Dependency (gemanagt) | Copy-in (**Ownership**) |
| Nuxt-SSR (Storefront!) | first-class: SSR-safe, Auto-Import, Color-Mode, i18n | Plumbing **selbst** bauen |
| Batteries (Forms/Table/Overlays/Toasts) | inklusive | selbst assemblieren |
| Bespoke Pixel-Kontrolle | über Theme-API/Tokens; tief = gegen Abstraktion | **voll** (Code editierbar) |
| Updates | `pnpm up` | manuelles Re-Diffen |
| Kohärenz über 3 SPAs | hoch, eine Sprache | du baust sie selbst |

**Mittelweg (unser stärkstes Argument):** kein Alles-oder-Nichts. Für die wenigen wirklich bespoke
Komponenten können wir **direkt auf Reka UI** runtergehen (= das shadcn-Prinzip) und den Rest bei Nuxt UI
lassen. **Und unsere geteilten Primitive (`packages/crm-ui|customer-ui|ui-base`) sind bereits prop-rein/
type-neutral** → einzelne Implementierungen später austauschbar, ohne Consumer anzufassen. Die Architektur
hat die Entscheidung damit **gehedged**.

## Entscheidung 2 — Component-Workshop

| Kriterium | **Storybook** (Status quo) | **Histoire** |
|---|---|---|
| Engine | builder-agnostisch (Vite-Builder ok) | Vite-nativ, für Vue gebaut |
| DX (Vue-SFC) | gut | **schöner** (`*.story.vue`) |
| a11y / Interaction-Tests / Visual-Regression | reifes Ökosystem (Addon, Test-Runner, Chromatic) | schwach / DIY |
| Autodocs aus argTypes | ja | begrenzt |
| Wartung / Zukunft | aktiv, Standard | **faktisch eingeschlafen → Risiko** |
| Bestand | 2 Storybooks + Stories vorhanden | Migration nötig |

Für ein **>1-Jahr-Vorhaben** mit künftigem BFSG/a11y- und CI-Bedarf wiegt Storybooks Ökosystem schwerer
als Histoires (reales) besseres DX.

## Begründung für *dieses* Projekt
Profil (vgl. `STRUKTUR.md`, Projekt-Memories): Nuxt-SSR-Storefront, **3 SPAs schon auf Nuxt UI 4**,
geteilte prop-reine Primitive, **solo/Vibecoding**, Produktiv **erst >1 Jahr**, Fokus **Tempo + Sauberkeit**,
schwere Zeremonie „erst mit Team". Das favorisiert den **maximalen Hebel pro Stunde** (Nuxt UI) auf einem
**ökosystem-sicheren** Workshop (Storybook).

## Zitierbare Argumente (fürs Gespräch)
1. **Gleiches Fundament:** beide auf Reka UI → weder a11y noch Kontrolle verloren; Nuxt UI *kapselt* nur.
2. **Der SSR-Shop entscheidet:** shadcn-vue hieße SSR/Color-Mode/Forms/Table selbst bauen — Wochen Arbeit ohne Designnutzen.
3. **Nicht eingesperrt:** bespoke Teile punktuell auf Reka UI; Primitive prop-rein → einzeln austauschbar.
4. **Histoire = Wartungsrisiko:** schöneres DX, aber eingeschlafen; Storybook liefert a11y/Visual-Regression/Interaction-Tests, die wir ohnehin brauchen.
5. **Sunk cost real, aber zweitrangig:** Wechsel = Wochen Migration für null Nutzen *heute*.

## Wo das Design recht hat (fair benannt)
- Geht es um ein **design-geführtes, pixel-bespokes** System **und** das Team pflegt Komponenten-Code selbst,
  ist **shadcn-vue** die prinzipientreuere Wahl (Ownership > Abstraktion).
- **Histoire** hat das angenehmere Vue-DX.

## Entscheidungs-Hebel
Ist **volle Code-Ownership der Komponenten** ein *harter* Design-Anspruch (+ Pflege-Commitment)?
→ dann shadcn-vue — **aber weiterhin Storybook**, nicht Histoire.
Sonst (Tempo, Konsistenz, allein wartbar) → **Status quo: Nuxt UI + Storybook.**

## Revisit-Trigger
Design-geführte Bespoke-Anforderung wird verbindlich · zweiter UI-Mitwirkender mit Pflege-Kapazität ·
Nuxt UI blockiert ein konkretes Designziel, das auch über Reka-UI-Direktnutzung nicht lösbar ist.
