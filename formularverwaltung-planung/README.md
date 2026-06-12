# Formularverwaltung-Showcase

Best-of-Breed-Showcase für **Verwaltungsmasken eines Stammdaten-/MDM-Kerns**: typsichere,
spezifikations­getriebene, testbare CRUD-Masken aus **einer** Quelle — generiert statt
handgeschrieben. Beispiel-Domäne: die **operative Seminarverwaltung** (Durchführung/Dozent/
Raum/Teilnehmer).

> Dieser Ordner (`formularverwaltung-planung/`) enthält **nur die Doku**. Code käme später ins
> bestehende Monorepo **`../showcase/`** (neuer Quarkus-`seminar`-Service + Vue-Cockpit, beide
> als eigene Compose-Container). **Noch nichts gebaut** — reine Planung.
>
> Ausgegliedert aus dem [Controlling-Showcase](../controlling-planung/) (2026-06-12). Shop-Doku:
> [`../shop-planung/`](../shop-planung/), Enterprise-Planung: [`../enterprise-stack-planung/`](../enterprise-stack-planung/).

> **Plan & verbindliche Versionen:** [Showcase-Realisierungsplan-Formularverwaltung.md](Showcase-Realisierungsplan-Formularverwaltung.md)
> (These §1, Architektur §2, Generator-Spine §4, **Validierungskette Stack B §5**, Naht zum Controlling §7, Milestones §8, Leitplanken §9).

## Der entschiedene Stack (eine Zeile)

> Hibernate Validator → smallrye-openapi (`/q/openapi`) → `@hey-api/openapi-ts` (Typen+Client+zod)
> → vee-validate + `@vee-validate/zod` → PrimeVue — Backend per `rest-data-panache`/Panache/oidc/
> flyway, alles als **getrennte Docker-Container**.

## Kriterien (in dieser Reihenfolge entschieden)

1. **typsicher** (durchgängig, `z.infer` = Form- und API-Typ)
2. **über die Spezifikation testbar** (rest-assured auf `/q/openapi` + vitest auf generierte zod)
3. **generierbar** statt handgeschrieben (kein bespoke Glue)

## Verbote (entschieden)

kein Quinoa · kein Orval · kein Hibernate Envers (erst einmal) · **kein hand-zod / kein Ajv-Adapter**.

## Naht

Vendure bleibt Buchungs-/Geld-SoR; Quarkus = operativer Seminar-/MDM-Kern (Schema `seminar`).
Bindung über `order_line.seminarRunId`. Die **Kostendeckung je Durchführung** liefert der
[Controlling-Showcase](../controlling-planung/) (dieser Showcase = Stammdaten + Datenvertrag).
