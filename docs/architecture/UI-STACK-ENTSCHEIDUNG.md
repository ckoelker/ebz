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

## Begriffsklärung: „Vue-DX" (Histoire) betrifft das Story-Format, NICHT die Komponenten
Häufiges Missverständnis: Die **Komponenten** sind in beiden Welten `.vue` (z. B.
`PreisBadge.vue`). Unterschiedlich ist nur die **Story-Datei** daneben:
- **Storybook:** `PreisBadge.stories.ts` — TypeScript-Objekt (CSF: `meta` + benannte Exports + `args`).
- **Histoire:** `PreisBadge.story.vue` — SFC; der Vue-Dev bleibt durchgängig im `<template>`-Format.

Histoires „besseres Vue-DX" = genau dieses durchgängige SFC-Gefühl. **Aber** gerade das typisierte
CSF-Format ist der Grund, warum Storybook seine Addons (a11y, Autodocs, Interaction-Tests) automatisch
ableiten kann — der Tausch fällt zugunsten Storybook aus.

## Greenfield (ohne Sunk Cost) — was sich ändert und was nicht
Auf der grünen Wiese fällt **nur** Argument #5 (Sunk Cost) weg. Es bleiben:
- **Workshop → weiterhin Storybook.** Greenfield macht Histoires eingeschlafene Wartung nicht wett.
- **UI-Lib → echte, knappe Abwägung (~60/40).** Sie hängt an **einer** Frage:
  *Ist die visuelle Design-Identität ein First-Class-Differenzierer, das Design will/kann Komponenten-Code
  selbst besitzen+pflegen?*
  - **Ja (design-geführt):** greenfield ist **shadcn-vue** legitim — „own your components" ist 2025/26 der
    SOTA-Default design-getriebener Teams (Reka-UI-Basis, Tailwind, alles editierbar). SSR-Plumbing zahlt
    man dann bewusst.
  - **Nein / Velocity-first (unser Profil):** **Nuxt UI** — SSR-nativ, batteries-included, ein kohärentes
    System. Schnellster Weg zu Wert.

## Architektur schlägt Lib-Wahl: Naht zuerst (konsensfähig)
Das Klügste — greenfield **vor** der Lib-Entscheidung, bei uns bereits gebaut: eine **dünne, prop-reine,
library-agnostische Primitive-Schicht** (`packages/crm-ui|customer-ui|ui-base`). Dadurch wird die UI-Lib zur
**austauschbaren Implementierung hinter der Naht**:
- Mit Nuxt UI starten (Tempo), einzelne Primitive später auf shadcn-vue/Reka UI umstellen — **ohne Consumer
  anzufassen**.
- Bespoke-Komponenten, die das Design will, punktuell direkt auf Reka UI — **heute schon, ohne Lib-Wechsel**.

Damit ist die Lib-Wahl **weniger irreversibel, als sie klingt**, und der Streit löst sich auf in: „**womit
starten wir hinter einer Naht, die den Wechsel billig hält**" → dort gewinnt Tempo (Nuxt UI), mit shadcn-vue
als jederzeit offener Tür. Niemand verliert.

## Vibecoding-Eignung (Agent-Perspektive)
Da hier viel KI-gestützt gebaut wird, ist *messbar*, womit der Coding-Agent zuverlässiger/schneller ist —
und das fällt klar auf **Storybook + Nuxt UI**, aus mechanischen Gründen (nicht Gewohnheit):
- **Storybook-CSF (`.stories.ts`)** = strukturierter, **typisierter Vertrag** → Typecheck fängt Fehler sofort
  (enger Loop); riesige Trainingsdichte (Histoire ist Nische → mehr Fehlgriffe); **Story = prüfbare Spec**
  (`build-storybook` als Gate — so wurde der generische `ListenTabelle`-Fehler gefunden).
- **Nuxt UI** = stabile, dokumentierte, prop-getriebene APIs + Auto-Import → **weniger fehleranfälliger
  Glue-Code**. shadcn-vue („owned code") = mehr Bespoke-Fläche, die der Agent korrekt halten muss.
- **Eigentlicher Hebel = die Gates** (Typecheck → build-storybook → Naht-Wächter). Typisierte CSF + Nuxt-UI-
  Props **füttern** diese Gates am besten; shadcn/Histoire liefern dafür weniger.
- **Fair benannt, wo es auch jetzt beißt:** generische Komponenten in CSF brauchen Casts
  (`… as unknown as Meta<…>`); Nuxt UIs lose getippte Slots/`any`-Spalten verstecken gelegentlich Typfehler
  vor dem Typecheck → ein Bug rutscht bis Build/Runtime durch.

## Revisit-Trigger
Design-geführte Bespoke-Anforderung wird verbindlich · zweiter UI-Mitwirkender mit Pflege-Kapazität ·
Nuxt UI blockiert ein konkretes Designziel, das auch über Reka-UI-Direktnutzung nicht lösbar ist.
