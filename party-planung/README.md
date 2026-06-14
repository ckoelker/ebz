# party-planung

Planung & Status für den **Party-/CRM-Identitätskern** im EBZ-Best-of-Breed-Showcase —
der bewusst **selbst gebaute** Differenzierer (HubSpot bleibt reines Marketing, Entscheidung
des Kunden). Anders als die übrigen `*-planung/`-Ordner ist hier der Kern bereits **gebaut**;
dieses Dokument hält Leitidee, Status und die **optionalen Erweiterungen** fest.

## Leitidee
**Eine Identität, n Bestellkontexte.** Das EBZ hat Hauptkunden (Firmen), die in allen drei
Bereichen (Berufsschule/Hochschule/Akademie) buchen, über mehrere Ansprechpartner; Personen
können zugleich privat bestellen und in mehreren Firmen Rollen halten (n:m). Kernproblem:
*dieselbe E-Mail privat **und** als Firmen-Azubi*.

Lösung:
- **E-Mail ist nicht die Identität**, sondern Auflösungsschlüssel. Identität = `Person`
  (ein Mensch = ein Datensatz = ein Login via Keycloak-`sub`); `PersonEmail` mit globaler
  Unique-Constraint erzwingt „eine E-Mail → eine Person".
- **N:M** über `Mitgliedschaft` (Person × Organisation, Rolle/Gültigkeit/buchungsberechtigt).
- **Kontext pro Vorgang** = `{PRIVAT} ∪ {Organisationen mit aktiver, buchungsberechtigter
  Mitgliedschaft}` — Haupt-/Nebenfirma sind zwei FIRMA-Kontexte.
- **Abrechnung folgt dem Kontext**: `ermittleDebitor` projiziert kontextabhängig auf die
  bestehende Debitoren-Hoheit (Rechnungsstellung R3) statt einer übergebenen Debitor-ID.

## Status — GEBAUT + VERIFIZIERT (2026-06-14)
Backend im `integration`-Service, Package `party`, Schema `party`; `PartyKernTest` 7/7 grün.

- **Identitätskern** (Commit `1818378`): Person/PersonEmail/Organisation/Mitgliedschaft,
  `PartyHoheitService` (selbstRegistrieren/Claim, registriereTeilnehmer, kontexte,
  ermittleDebitor, kandidaten, merge), eine `PartyResource` unter `/party/...`.
- **Buchung im Kontext** (Commit `ab05f54`): `POST /party/buchungen/berufsschule` legt aus
  Identität + Kontext eine `Anmeldung` mit projiziertem Debitor + Provenienz an; der
  bestehende Rechnungslauf (R1) verarbeitet sie unverändert weiter (end-to-end getestet).
- **Token-`sub`-Login + DSGVO-Sichten** (Commit `2d311ed`): `POST /party/personen/login`
  (`@Authenticated`, `sub` aus dem Token, nicht aus dem Body); `GET /party/firmensicht/{orgId}`
  zeigt nur den Org-Kontext (kein Privat, Mitgliedschaft Pflicht sonst 403);
  `GET /party/personen/{id}/buchungen` = interne 360°-Sicht.

## Optionale Erweiterungen (offen, nicht gebaut)
Bewusst zurückgestellt; keine davon ist für den Kern-Showcase nötig.

1. **Weitere Bereiche an die Buchung andocken** — Hochschul-Buchung (Raten/Firmen-Split, R6)
   und die externe Bestellung/Vendure-Quelle (R7) analog zu `bucheBerufsschule` über den
   Kontext-Mechanismus führen (heute nur Berufsschule/R1).
2. **Echte JWT-Claims statt Body** — E-Mail/Name beim Login aus verifizierten Token-Claims
   ziehen (heute aus dem Request-Body; der `sub` kommt bereits aus dem Token).
3. **Provenienz im Anmeldung-DTO lesbar machen** — `teilnehmerPersonId` /
   `bestellerPersonId` / `kontextOrganisationId` über das bestehende Anmeldung-CRUD/`AnmeldungDto`
   exponieren (heute nur persistiert + über die party-Sichten sichtbar) für die 360°-Sicht.
4. **Firmensicht produktiv auf eine Org-Portal-Rolle einschränken** — zusätzlich zur
   Mitgliedschafts-Prüfung eine dedizierte Rolle/Scope (Self-Service-Portal vs. interne 360°).
5. **Identitäts-Match/Merge ausbauen** — stärkere Dublettenschlüssel (Name+Geburtsdatum,
   Fuzzy), Merge-Audit/Undo, Bestands-Import von Personen analog zum Debitoren-Bestandsimport.
6. **HubSpot-Naht (nur Marketing)** — Personen/Organisationen als Marketing-Sicht nach HubSpot
   projizieren (Quelle bleibt der Party-Kern; keine Stammdaten-Hoheit in HubSpot).

> Status: **Party-Kern GEBAUT + VERIFIZIERT** (Identität, n Kontexte, Kontext→Debitor,
> Buchungs-Naht, Token-`sub`, DSGVO-Sichten). Die Punkte oben sind optionale Erweiterungen.
