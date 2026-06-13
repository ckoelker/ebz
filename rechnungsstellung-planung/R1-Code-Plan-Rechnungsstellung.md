# R1 — Code-Plan Rechnungsstellung (im `integration`-Service)

> Konkreter Implementierungsplan auf Basis der [Fachklärung](R1-Fachklaerung-Rechnungsstellung.md).
> **R1-Slice = Berufsschule end-to-end** (interne Rechnung). **ZUGFeRD = R2, Debitoren-Match/Merge =
> R3, DATEV = R4, Hochschule = R6, Vendure-Quelle = R7.** Stand 2026-06-13. Noch kein Code.

## Architektur-Rollen
- **Vendure** = Commerce/Zahlung-SoR (Webshop, Karte/Stripe, SEPA, Subscriptions, Self-Service).
- **`integration` (Quarkus)** = MDM + Rechnung/Beleg/Buchung-SoR.
- **Naht:** bezahlte Vendure-Order → **Abrechnungsbasis** (quellen-agnostisch, neben `Anmeldung`).

## Verortung
- Package `de.netzfactor.ebz.controlling.integration.rechnung` (+ Unterpakete `model`/`web`/`service`).
- **Eigenes DB-Schema `rechnung`** in DB `controlling` (analog `bildung`; explizit `@Table(schema="rechnung")`).
- Reuse vorhandener Bausteine: Bean-Validation→smallrye-openapi (Stack B), `quarkus-oidc` RBAC,
  Panache. **R1 fügt keine neuen Maven-Deps hinzu** (Mustang/PDFBox erst R2).

## Datenmodell (R1)
Geldbeträge durchgängig **Integer Cent**. Enums als String-Spalten (kein DB-Check, vgl. bildung).

**`Debitor`** — zentrale, idempotente Vergabe (Match/Merge = R3).
`id · version · debitorNr (unique, aus Nummernkreis je Bereich) · bereich (BERUFSSCHULE|HOCHSCHULE|AKADEMIE|SHOP) · rolle (FIRMA|PRIVAT) · name · strasse · plz · ort · land · ustId? · iban? (SEPA) · email? · aliasNummern? (Migration R3)`

**`Anmeldung`** — Abrechnungsbasis der Vertrags-Ströme (R1: Berufsschule; Hochschule-Felder vorbereitet, Logik R6).
`id · version · typ (BERUFSSCHULE|HOCHSCHULE) · teilnehmerName · teilnehmerEmail? · bildungsangebotId (→ bildung.Bildungsangebot) · zahlungspflichtigerDebitorId (→ Debitor) · status (AKTIV|ABGEBROCHEN|ABGESCHLOSSEN)`
- BS: `schuljahr ("JJJJ/JJJJ") · halbjahr (1|2) · zimmerart (KEINE|DOPPEL|EINZEL)`
- HS (R6): `semester · semesterbetragCent · zahlmodell (KOMPLETT|RATEN) · ratenAnzahl? · ratenIntervallMonate? · splitFirmaDebitorId? · splitFirmaAnteilCent?`

**`Rechnung`** (Kopf).
`id · version · belegart (RECHNUNG|GUTSCHRIFT|STORNO|NACHBERECHNUNG) · bereich · nummer? (null bis Ausstellung; unique) · debitorId · zeitraumBezeichnung ("Schuljahr 2025/2026, 2. Halbjahr") · ausstellungsdatum? · zahlungszielTage (Default 14) · status (ENTWURF|AUSGESTELLT|BEZAHLT|STORNIERT) · originalRechnungId? (Pflicht bei GUTSCHRIFT/STORNO/NACHBERECHNUNG) · positionen (1:n)`

**`RechnungPosition`**.
`id · rechnungId · teilnehmerName? (Zeilenkontext der Sammelrechnung) · beschreibung · menge (Default 1) · einzelbetragCent · steuerfall (BEFREIT|STANDARD|ERMAESSIGT) · steuersatz · befreiungsgrund? · leistungsart (UNTERRICHT|UEBERNACHTUNG|KORREKTUR|SONSTIGE → R4-Erlöskonto) · herkunft (AUTO|MANUELL)`

**`Nummernkreis`** — lückenlose, atomare Vergabe je **Bereich × Belegart**.
`id · bereich · belegart · praefix ("RE-BS-2026-") · naechsteNummer`

**`Tarif`** — **konfigurierbare Beträge (Variablen, KEINE Code-Konstanten).** Die genannten Werte
(Unterricht 1.500, Doppelzimmer ~1.300, Einzelzimmer ~1.260) sind **pflegbare Stammdaten**, nicht
hartkodiert. `id · bereich · schuljahr · unterrichtCent · doppelzimmerCent · einzelzimmerCent` (BS);
gilt für den Rechnungslauf des jeweiligen Zeitraums. (Später erweiterbar je Bildungsangebot/Staffel.)

## Lebenszyklus, Festschreibung, Nummernvergabe
- **ENTWURF**: aus Rechnungslauf; Positionen editierbar/ergänzbar; **keine Nummer**.
- **Ausstellen**: vergibt **lückenlose Nummer** (atomar, s. u.), setzt `ausstellungsdatum`, Status
  `AUSGESTELLT`; danach **unveränderbar** (Festschreibung ab Ausstellung). Nummer erst hier → keine
  Lücken durch verworfene Entwürfe.
- **Nach Festschreibung** nur Korrekturbeleg: `GUTSCHRIFT` (negativ) / `STORNO` (Vollumkehr) /
  `NACHBERECHNUNG` (positiv) — je mit `originalRechnungId` (Pflicht) und eigenem Nummernkreis.
- **Atomare Nummer:** `NummernkreisService` per pessimistischem Lock (`SELECT … FOR UPDATE` /
  Panache `LockModeType.PESSIMISTIC_WRITE`) auf der Nummernkreis-Zeile — kein DB-Sequence-Loch.

## Services
- **`NummernkreisService`** — `String vergib(Bereich, Belegart)` (atomar, lückenlos).
- **`RechnungslaufService`** — `List<Rechnung> erzeugeEntwuerfe(bereich, schuljahr, halbjahr)`:
  alle aktiven `Anmeldung`en des Zeitraums → **gruppiert je `zahlungspflichtigerDebitor`** (=
  Sammelrechnung) → je Gruppe eine `Rechnung(ENTWURF)` mit **Standardpositionen aus dem `Tarif`**
  (Unterricht + Zimmer-Halbjahresbetrag je `zimmerart`) — **Beträge variabel aus Stammdaten, nicht im
  Code**; alle `BEFREIT`. Idempotent (kein Doppel-Entwurf je Debitor+Zeitraum).
- **`RechnungService`** — `ausstellen(id)` · `storno(id)` · `gutschrift(id, positionen)` ·
  `nachberechnung(id, positionen)` · `addManuellePosition(id, …)` (nur `ENTWURF`).
- **(R2)** `ZugferdService` · **(R4)** `DatevUebergabe` (Interface) · **(R3)** `DebitorService` (Match/Merge).

## REST-Endpunkte (`/rechnung`, RBAC-Rolle `rechnung-pflege` an Schreib-Ops)
- `GET/POST /rechnung/anmeldungen`, `GET/PUT/DELETE /rechnung/anmeldungen/{id}`
- `POST /rechnung/laeufe` `{bereich, schuljahr, halbjahr}` → Entwürfe (Liste)
- `GET /rechnung/rechnungen?status&bereich` · `GET /rechnung/rechnungen/{id}`
- `POST /rechnung/rechnungen/{id}/positionen` (manuell, nur ENTWURF)
- `POST /rechnung/rechnungen/{id}/ausstellen`
- `POST /rechnung/rechnungen/{id}/storno` · `…/gutschrift` · `…/nachberechnung`
- *(R2)* `GET /rechnung/rechnungen/{id}/zugferd` (application/pdf)
- DTO + Bean-Validation als Single Source (Stack B) → erscheint im `/q/openapi`.

## Steuer & Leistungsart
- Berufsschule R1: alle Positionen `steuerfall=BEFREIT` (§4), `befreiungsgrund` gesetzt.
- `leistungsart` je Position vorhalten → **R4** mappt (Bereich × Leistungsart × Steuerfall) →
  Erlöskonto + BU-Schlüssel (SKR, dem StB gehörige Tabelle).

## Teststrategie (rest-assured + Unit, Muster wie bildung)
- Rechnungslauf erzeugt **Sammelrechnung je Firma** (mehrere Teilnehmer = Positionen).
- Ausstellen vergibt **lückenlose Nummer**; zweimal Ausstellen idempotent/abgelehnt.
- ENTWURF editierbar, **AUSGESTELLT nicht** (Position-Add → 409).
- Gutschrift/Storno/Nachberechnung tragen `originalRechnungId`; eigener Nummernkreis.
- Unit: `NummernkreisService` lückenlos & nebenläufig (Lock).
- RBAC: Schreib-Ops ohne Rolle → 403 (`@TestSecurity`).

## Abgrenzung (was NICHT in R1)
ZUGFeRD/PDF (R2) · Debitoren-Match/Merge + Migration (R3) · DATEV-Übergabe + Erlöskonten/SKR (R4) ·
Gutschrift-Automatik/Rabatte (R5/6 – Rabatt zurückgestellt) · Hochschule-Lauf + Split + Raten (R6) ·
Vendure-Order→Abrechnungsbasis + Shop/Seminare (R7).

## Offen (Kundenfragen, nicht R1-blockierend)
SKR03/04 (R4) · konkrete **Tarif-Werte** (Unterricht/Zimmer — sind **Stammdaten/Variablen**, werden
gepflegt, nicht im Code) · Debitoren-Bestand/Migration A vs B (R3) · SEPA-Mandatsquelle.
