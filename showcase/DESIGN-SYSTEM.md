# EBZ Design-System — Arbeitsanweisung (Masken, Listen & Komponenten)

Verbindliches Playbook für die Entwicklung von UI (Masken/Listen/Komponenten) im showcase-Monorepo —
für Menschen **und** KI-Assistenten. Ziel: konsistente Oberflächen, **eine Quelle** pro Design-System
und „nichts wird benutzt, was nicht definiert ist" — bei **erhaltenem Bau-Tempo**.

## 0. Grundsatz — Design-System-first (Richtung A)
Storybook ist die **verbindliche Quelle**, nicht nur Doku. Neue UI entsteht DS-first: erst die geteilte
Komponente + Story, dann in der App benutzen. Bestehender App-Code wird **inkrementell** nachgezogen,
nicht auf einen Schlag.

## 1. Landkarte — wo was lebt
| Bereich | Komponenten | Storybook (Schaufenster) |
|---|---|---|
| Intern/Admin (MDM/CRM) | `@crm-ui` = `showcase/crm-ui/src` (gebrandet) | `crm-kernmaske` (:6007) |
| Kunde/Marketing (Shop+Portal) | `@customer-ui` = `showcase/customer-ui/src` (gebrandet: StatusBadge/PreisBadge) | `kunden-kernmaske` (:6008) |
| **Neutrale UI-Infrastruktur** | `@ui-base` = `showcase/ui-base/src` (ListenTabelle/FormFeld/LeerZustand) | `kunden-kernmaske` |
| Invariante Logik (Geld/Datum/Status/Anrede) | `@crm-ui/domain` | — (von allen geteilt) |

**Naht:** **Gebrandete** Admin- und Kunden-Komponenten werden **nicht** über die Naht geteilt.
Geteilt werden nur die **brand-/domain-neutralen** Schichten: `@crm-ui/domain` (Logik) und `@ui-base`
(struktur­elle UI-Infra ohne Branding/Domain). Branding via Tokens, nicht via geforkter Komponenten.

## 2. Entscheidungsregel — vor JEDER Maske/Liste (in dieser Reihenfolge)
1. **Existiert die Komponente?** (Storybook/Shared-Paket suchen) → importieren, **nie kopieren**.
2. **Fast passend?** → geteilte Komponente per **Prop erweitern**, nicht forken.
3. **Neu + wiederverwendbar?** → im geteilten Paket anlegen (prop-rein) → **Story** → dann benutzen.
4. **Echt einmalig/app-spezifisch?** → darf in der App liegen, aber **komponiert aus** geteilten
   Primitiven + Tokens (kein Roh-HTML/Inline-Styling, keine direkten `@nuxt/ui`-Sonderlocken in der Fläche).

## 3. Komponenten-Regeln (für neue geteilte Komponenten)
- **Prop-rein & SSR-safe** — keine App-Bindung (kein API-Client/Router/Store, keine Nuxt-only-Composables);
  muss in Vite-SPA **und** Nuxt-SSR laufen.
- **Logik nur aus `@crm-ui/domain`** (Farben/Status/Format), nie inline nachbauen.
- **Tokens statt Werte** — Farben/Spacing/Radii über Theme-Tokens, keine Hex/px-Hardcodes.
- **Typsichere `defineProps`-Interfaces**, deutsche Fachbegriffe, schlanke Datei-Struktur (verwandtes bündeln).
- **Löschen immer mit Bestätigung.**
- Standard-„Masken & Listen"-Bausteine zuerst seeden: `ListenTabelle`/`MasterDetail`, `FormFeld`/`FormSektion`,
  `StatusBadge`, `AktionsLeiste`, `Leer-/Lade-Zustand`, `LöschenBestätigenDialog`.

## 4. Storybook-Pflicht — „ohne Story nicht fertig"
Jede neue geteilte Komponente bekommt **co-located** `Name.stories.ts` im richtigen Schaufenster, SOTA-Muster:
- `meta` mit `component`; **`argTypes` für JEDEN Prop**; sinnvolle Default-`args`.
- **mehrere Stories** für die relevanten Zustände/Varianten (inkl. Leer/Fehler/Sperre).
- **mind. ein `play()`** als Interaction-Test der Kernaussage.
- `tags:['autodocs']` ist global gesetzt → Doks-Seite automatisch.

## 5. Verifikationsschleife (abgestuft — siehe Tempo-Klausel)
- `typecheck` **immer** (billig).
- `build-storybook` **nur**, wenn ein Storybook angefasst wurde.
- App-Seite: `vitest --passWithNoTests` + `typecheck` (mdm/portal) bzw. Nuxt-`build` (storefront).
- **Voller Stack-Lauf** (`showcase-aufbau.sh`) nur an **Meilensteinen**, nicht pro Komponente.
- **Docker-Gotcha:** jede App, die ein geteiltes Paket konsumiert, braucht Build-`context: showcase/`
  + `COPY <paket>` im Dockerfile (sonst bricht das Image bei der `@…`-Auflösung — bereits passiert bei portal/storefront).

## 6. Wann nachfragen/eskalieren
- Neue Komponente **kreuzt die Naht** (Admin↔Kunde) → kurz rückfragen.
- **Branding/Tokens** betroffen (neue Marketing-Palette o. ä.) → CD klären, nicht raten.
- Muster existiert noch **gar nicht** im DS → Vorschlag zeigen, bevor es Standard wird.

## Tempo-Klausel (damit die Regeln Rails sind, keine Schranke)
1. **Generator/Template** für „neue Komponente + Story" nutzen (sobald vorhanden) → spart ~80 % der Story-Kosten.
2. **`build-storybook` nur bei SB-Änderung**, `typecheck` immer, voller Stack-Lauf nur an Meilensteinen.
3. Mehraufwand ist **front-loaded**: dünne Bibliothek = viele neue Komponenten; gefüllte Bibliothek =
   meist **Komposition** → schnell. Reuse-lastige Masken/Listen sind mit Playbook **schneller** als ohne.

## Erledigt (Stand 2026-06-29)
- **Pakete angelegt:** `@customer-ui` (StatusBadge mit `art` einschreibung/rechnung/azubi, PreisBadge) +
  `@ui-base` (ListenTabelle/FormFeld/LeerZustand, prop-rein/dependency-frei) — alle mit Stories in
  `kunden-kernmaske`. Consumer brauchen kein `@nuxt/ui`-Pfad-Mapping (geteilte Komponenten importieren
  bewusst keine `@nuxt/ui`-Typen; Globals + lose `any`-Spalten).
- **Apps migriert:** portal (Rechnungen/Trainings/Azubis), storefront (kasse-Formular→FormFeld),
  mdm (DublettenReview). Alias/`@source`/Dockerfile-`COPY`/`vue`-Pfad je App verdrahtet; typecheck/build grün.
- **`ListenTabelle` typsicher (Variante A, 2026-06-30):** generisch `generic="T"` + `data: T[]` +
  `defineSlots` → Zell-Slot-`row.original` ist im Consumer wieder **`T`** (per Tippfehler-Test in
  MeineRechnungen nachgewiesen: Compiler fängt falsches Feld). `columns: any[]` bleibt als begründete
  Ausnahme (keine `@nuxt/ui`-Typabhängigkeit im dependency-freien `@ui-base`). Storybook-Story: ein
  schmaler, kommentierter Cast im `component`-Feld (generische SFC passt nicht in Storybooks Component-Typ).

## Noch offen — in dieser Reihenfolge
1. **Abdeckung-Rest** (zuerst): storefront Katalog-Preise → `PreisBadge`; mdm 2 paginierte Tabellen
   (`AngeboteListe`/`AnmeldungenBestaetigung` — brauchen Pagination/`ref`, dafür `ListenTabelle` erst
   erweitern). Bewusst bespoke & NICHT zu migrieren: portal Aktivitäten/Nachrichten (Timeline/Chat).
   Danach: echter Docker-Stack-Rebuild (alle `COPY ui-base`/`customer-ui`) als Meilenstein-Validierung.
2. **Erzwingung** (danach): ESLint-Importgrenzen (`@nuxt/ui`/Roh-Komponenten nur im DS-Paket),
   dependency-cruiser auf die Apps, Story-Coverage-Test — als CI-/`showcase-aufbau`-Schritt. Erst damit
   ist „nur was definiert ist" garantiert (statt nur befolgt).
- **Komponenten-Generator** (Tempo-Klausel Punkt 1).

## Entschieden — `ListenTabelle`-Typsicherheit (Variante A, umgesetzt 2026-06-30)
Per Projekt-Policy „typsicher bleiben wo vertretbar" entschieden: **Variante A** (generisch + getypte
Slots, s. „Erledigt"). B (cast im Slot) / C (rohes `<UTable>`) verworfen. Muster für künftige getypte
Tabellen-Wrapper: `generic="T"` + `defineSlots<Record<string, ((p:{ row:{ original: T } }) => any) | undefined>>()`.
