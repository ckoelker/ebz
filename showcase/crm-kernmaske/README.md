# EBZ CRM-Kernmaske — Storybook (Design-Abnahme)

Abnahme-Artefakt **vor** FE/BE-Bau: das Komponenten-Inventar der CRM-Kernmaske
(Unternehmen↔Personen-N:M als MDM-Einstieg, Betriebs-Cockpit) als Storybook.
Abgeleitet aus dem abgenommenen Wireframe `../crm-wireframe/` und `../crm-planung/crm-plan.md`.

## Stack (Entscheidungen 2026-06-17)

- **Komponenten-Bibliothek: Nuxt UI 4** (Pro ist in v4 in `@nuxt/ui` gemerged, MIT).
- **Vue-only-Einbindung** (kein Nuxt): `@nuxt/ui/vite`-Plugin + `@nuxt/ui/vue-plugin`,
  passt zum bestehenden Vite+Vue-Showcase-Stack (`../mdm/`).
- **Branding:** Nuxt-Default-Theme, nur die Primärfarbe = EBZ-Navy `#0b3a6f`
  (Scale `navy` in `src/assets/css/main.css`, gesetzt in `vite.config.ts`).
- **Storybook:** `@storybook/vue3-vite` (Vite-8-Builder). Abnahme **lokal vorführen**.

## Befehle

```bash
pnpm install
pnpm storybook        # http://localhost:6007  (Abnahme)
pnpm build-storybook  # statischer Export nach storybook-static/
pnpm typecheck        # vue-tsc --noEmit
pnpm dev              # minimaler Vite-Einstieg (nur Hinweis-Seite)

pnpm graph            # dependency-graph.svg (Komponenten-Baum, dependency-cruiser)
pnpm graph:archi      # dito, nach Ordnern verdichtet
pnpm depcruise        # nur Architektur-Regeln prüfen (CI-tauglich)
```

### Komponenten-Baum / Verwendungsnachweis

`pnpm graph` erzeugt `dependency-graph.svg` (zoom-/scrollbar im Browser) über
[dependency-cruiser](https://github.com/sverweij/dependency-cruiser) — SVG wird via
Graphviz-as-WASM (`@hpcc-js/wasm-graphviz`) gerendert, **kein** System-Graphviz nötig.
Zeigt die expliziten `import`s (eigene Sub-Komponenten). **Hinweis:** Nuxt-UI-Komponenten
(`<UButton>` …) sind Auto-Imports von `@nuxt/ui/vite` und erscheinen im Graph **nicht** —
die bekommt man nur über einen Template-Scan.

## Inventar (7 Gruppen, 30 Stories) — alle gegen Mock-Daten

- **Tokens** — Design-Tokens (Navy-Scale, Severity, Buttons, Badges)
- **Shell** — Topbar (Suche/CTI/User), Rail (Nav+Quicklinks), CockpitShell (komplett)
- **Cockpit** — ProzessStatusTabelle, EingriffeKarten (HITL), SonderfaelleKarten, AnrufToast
- **Kundenstamm** — KundenMasterListe, MasterListItem, KontaktDetailHeader, TabBar, Master-Detail-Flow
- **Bausteine** — FeldRenderer, StammdatenForm (Person/Firma inline), KanalEdit, MitgliedschaftEdit (N:M),
  VerknuepfungDialog (Bestand zuerst), LookupCheckboxListe
- **Tabs** — KontakthistorieListe, EinwilligungTabelle (DSGVO), WeiterbildungStundenkonto (§34c-Ampel)
- **Dialoge** — NeuePerson (gestuft + Dublettenwarnung), NeueFirma (Daten ziehen), Notiz, Wiedervorlage,
  RechtAufVergessen (DSGVO Art. 17)

> Wegwerf-/Abnahme-Code. Nach Design-Sign-off: echte SPA (Phase 3) + Backend-Endpunkt-Lücken (Phase 4),
> siehe `~/.claude/plans/smooth-purring-marble.md`.
