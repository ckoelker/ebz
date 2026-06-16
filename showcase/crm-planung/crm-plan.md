# CRM-Kernmaske (MDM-Einstieg): vollständiges Datenmodell + klickbarer Wireframe

## Context

Die `company:person`-N:M-Pflege wird die **Einstiegsmaske** der EBZ-Mitarbeiter für die MDM-Pflege
und die **Kernfunktion der MDM** (Komponenten später im weiteren Stack wiederverwendbar). EBZ = Bildung
für die Wohnungs-/Immobilienwirtschaft; >90 % der Unternehmen kommen aus der Immobilienbranche. Der
bisherige Party-Kern (`Person`/`Organisation`/`Mitgliedschaft`/`PersonEmail` im `mdm`-Schema) ist
**prototypisch**. Ziel: ein **vollumfängliches, minimales, international-fähiges** Modell zum Erfassen,
Pflegen und DSGVO-konformen Verarbeiten von Kontakten.

**Reihenfolge (Nutzervorgabe):** Zuerst **klickbarer Wireframe** zur Funktionsabstimmung mit dem Kunden,
**dann** Backend. Dieses Dokument = (A) Datenmodell-Vorschlag zur Freigabe, (B) UX-Festlegungen,
(C) Wireframe-Konzept. **In dieser Phase keine Backend-/DB-/JPA-Änderungen, kein Vue-TS-Code.**

> Grundlage: ~40 abgestimmte Entscheidungen (Blöcke 1–16).

### Leitprinzipien (vom Nutzer gesetzt)
- **Pflicht minimal:** Person = `vorname`+`nachname` + 1 Kontaktpunkt; Firma = `name` + 1 Kontaktpunkt/Adresse/Person.
- **Lookup-Tabellen statt Enums:** alle Klassifikationen (Rollen, Verbände, Unternehmenstyp, Schwerpunkte, Beziehungstypen, Branche, Land, Sprache, Lead-Quelle …) als **pflegbare DB-Tabellen**. **Enums nur**, wo Code real verzweigt (z. B. `geschlecht`→Anrede, Status-Workflows, Kontaktpunkt-Typ, Einwilligungs-Status, `loeschStatus`, `richtung`).
- **Lookup-Bauform:** **eigene Tabelle je Kategorie** (echtes FK-Ziel, FK-sicher) mit gemeinsamer JPA-`@MappedSuperclass` (`id`/`code`/`bezeichnung`/`aktiv`/`sortierung`) — **keine** zentrale Discriminator-Tabelle (OTLT-Anti-Pattern, bricht das FK-Prinzip). Mehrfachzuordnungen via Join-Tabelle. Sonderfälle: **Titel** = Freitext + Autocomplete (keine starre Liste); **Land/Sprache** = Tabelle mit ISO-Codes (3166/639); **Branche** = Tabelle mit WZ-2008/NACE-Code.
- **Lookup-Pflege generisch:** Stammdatenpflege der reinen Bezeichnungslisten läuft **tabellengetrieben über EINE parametrierte Resource + eine generische Pflege-Komponente** (Kategorie als Parameter), **nicht** über dutzende quasi-identische CRUD-Funktionen. Erst wenn eine Liste **mehr als die Bezeichnung** trägt (z. B. Verband: Kürzel/Website, Branche: Code), wird deren Pflege ausgelagert/spezialisiert.
- **Echte FKs** wo fachlich möglich; ein `mdm`-Schema; gebündelte DTOs; stabile Codegen-Namen.

---

## Teil A — Datenmodell (Vorschlag, zur Freigabe)

### A1. Person
- **Identität:** Keycloak-`sub` Anker; Login-/Identitäts-E-Mail in eigener `Login`-Tabelle (A1b), **Erfassung ohne Login** (Status `PROVISORISCH`).
- **Pflicht:** `vorname`, `nachname` + 1 Kontaktpunkt. Name minimal (kein Geburtsname/Rufname/Zusatz).
- `geschlecht` (Enum m/w/divers/o.A.) → **Anrede abgeleitet** (kein eigenes Feld); neutrale **Briefanrede „Hallo {Vorname} {Nachname}"** für divers/o.A. und als Fallback.
- `titel` mit **Positionslogik**: DE-Grade vorangestellt (Dr./Prof.), internationale nachgestellt (PhD/MBA).
- Optional: `geburtsdatum`, `geburtsort`, `geburtsland`, `staatsangehoerigkeit1/2`, `korrespondenzsprache`.
- **Volljährigkeit** abgeleitet → bei Minderjährigen Eltern-Einwilligung nötig (A5/A6).
- `foto` optional (sonst Initialen-Fallback).
- **Sperren:** `werbesperre`, `auskunftssperre` — **überstimmen jedes Opt-In**.
- Kein Konfession/Verkehrssprache/Migration.

### A1b. Login-Identität (NEU — entkoppelt von Kommunikations-E-Mails)
- Eigene `Login`-Tabelle: `personId` (FK), `loginEmail` (**global unique**), `keycloakSub`, `verifiziert`. Trägt die identitätsstiftende Login-E-Mail (eine Person kann mehrere Login-Adressen bündeln).
- **Folge:** Kontaktpunkt-E-Mails (A3) sind reine **Kommunikationskanäle, nicht unique** — löst den Unique-Konflikt der gemeinsamen Kontaktpunkt-Entity sauber.

### A2. Organisation
- **Pflicht:** `name` + 1 Kontaktpunkt/Adresse/Person.
- Optional: `rechtsform`, `handelsregisternummer`, `registergericht`, `branche`(Lookup), `website`, `ustId`.
- **Firma↔Firma:** einfache Hierarchie (`uebergeordneteOrganisationId`, Mutter/Tochter).
- **Immobilien-Spezifika:** `unternehmenstyp` + `taetigkeitsschwerpunkte` (Lookups, mehrfach: Wohnungsunternehmen/Makler/Verwalter/Genossenschaft/Bauträger/Sachverständiger …); `verbandszugehoerigkeiten` (Lookup, mehrfach: GdW/BFW/IVD/VNW/vdw/Haus&Grund …); `bestandsgroesse` (verwaltete Einheiten); `§34c/§34i-Erlaubnis` (Status/Behörde/Datum); `ausbildungsbetrieb` (Flag → Berufsschul-/Azubi-Prozesse); `ihk_kammer` (Lookup).

### A3. Kontaktpunkt (NEU — gemeinsame Entity)
- `typ` (Enum EMAIL/TELEFON/ADRESSE — Verhalten je Typ). **Besitzer = Person ODER Organisation ODER Mitgliedschaft** (Firmenkontext, dienstlich; A4).
- E-Mail (`email`, `primaer`; **reiner Kommunikationskanal, nicht unique** — Identitäts-E-Mail liegt in A1b) · Telefon (`nummerE164` **normalisiert** fürs Anruf-Matching, `nummerAnzeige`, Festnetz/Mobil/Fax, `primaer`) · Adresse (`strasse`, `hausnummer`, `adresszusatz`, `plz`, `ort`, `region`, `land`(Lookup), `auslandsblock` Freitext, `primaer`).
- **Adressen** strukturiert DE/DACH + Land; außerhalb DACH zusätzlich Region + Freitext-Block. PLZ-Validierung **länderabhängig** (DE/DACH streng).
- **Historie:** `status` AKTIV/EHEMALIG + `gueltigVon/Bis` (Umzug = alter Kanal bleibt EHEMALIG).
- ⚠️ Migration des bestehenden `PersonEmail`: **aufteilen** in `Login`-Identität (A1b, global-unique) und Kommunikations-E-Mail-Kontaktpunkte (nicht unique).

### A4. Mitgliedschaft (Person↔Organisation) — der Kern
- **Mehrere Rollen je Firma** (Rolle als **Lookup-Tabelle**, erweitert um Geschäftsführung/Vorstand/Prokurist, WEG-/Mietverwalter, Objektmanager/Vermietung/Technik — zusätzlich zu Bestand).
- `hauptzugehoerigkeit` (person-seitig, **höchstens eine aktive** = Default-Kanal; Privatperson hat keine) **und** `hauptansprechpartner` (firmen-seitig, **höchstens einer aktiv** je Firma).
- `buchungsberechtigt` + `rechnungsempfaenger`-Flag; `position`/`abteilung`; `gueltigVon/Bis`.
- **Dienstliche Kontaktpunkte** hängen an der Mitgliedschaft (A3).
- Firma ohne Person möglich; Person privat (ohne Mitgliedschaft) möglich.
- **Ausscheiden:** historisieren (`gueltigBis`), aus aktiver Liste ausgeblendet, als „ehemalig" einsehbar.
- **Anlegen beidseitig** (von Firma↔von Person) mit **Bestandssuche zuerst** (Dublettenschutz).

### A5. Person↔Person: Beziehung (NEU)
- `typ` (Lookup: Erziehungsberechtigt/Notfallkontakt/…), `personA`, `personB`. Trägt Eltern-Einwilligung bei Minderjährigen.

### A6. Einwilligung / Marketing-Opt-In (NEU)
- `personId`, optional `organisationId` → **global vs. Firmenkontext**; `kanal`, `zweck`, `status` (Default **AUSSTEHEND**), `rechtsgrundlage` (Art. 6), `quelle`, Datumsfelder.
- **Voller Double-Opt-In:** Bestätigungsmail + `nachweis` (Token/IP/Zeit).
- **Marketing-Anbahnung im Rahmen:** jeder Kontakteingang erzeugt Einwilligung `AUSSTEHEND` + erfasst **Lead-Quelle** (A8); Sperren (A1) gehen vor.

### A7. Recht auf Vergessen
- **Sofort sperren** (Art. 18) + **geplante Anonymisierung** nach Aufbewahrungsfrist (GoBD/§147 AO 10 J.) per Job. ⚠️ **Envers-Purge** der `_AUD`-Historie mit-bereinigen. Auslösen nur Rolle **`crm-datenschutz`**.

### A8. Lead-Quelle (Felder an Person/Org)
- `quelle` (Lookup) + Datum, **pflichtnah** bei Neuanlage → Marketing-Attribution + A6.

### A9. Aktivität / Kontakthistorie (NEU)
- `typ` (Lookup), `richtung`, `betreff`, `inhaltHtml` (**Rich-Text**: fett/kursiv/Listen/Links) + **Datei-Anhänge**, `bearbeiterMitarbeiterId`, Bezug, `zeitpunkt`, `dauer`.

### A10. Wiedervorlage (NEU)
- `betreff`, `faelligAm`, `erledigt`, zugewiesen an **Mitarbeiter ODER Gruppe**, Bezug, `prioritaet`. **Erinnerung nur In-App**.

### A11. Mitarbeiter & Gruppen, Quicklinks (NEU)
- `Mitarbeiter` (schlank, Keycloak-`sub`), `MitarbeiterGruppe` (Team/Abteilung **und** Zuweisungsziel), N:M.
- `LetzteAufrufe` (Quicklinks) **server-seitig** pro Mitarbeiter, Top-N.

### A12. Änderungs-Tracking — **Hibernate Envers** auf Kern-Entities (wer/wann); Versionskompatibilität in Build-Phase prüfen; Envers-Purge bei Anonymisierung (A7).

### A13. Anruf-/CTI-Log (NEU)
- `nummerE164`, `richtung`, `zeitpunkt`, `mitarbeiterId`, Bezug, `dauer`, `status`. **Event-Quelle anbieter-neutral** (generisches WebSocket-Event). Unbekannte Nummer → **KI/Web-Suche mit Schnellauswahl**.

### A14. Externe IDs (NEU) — generische Alias-Tabelle `(besitzer, quelle, externeId)` (HubSpot/HsTAG/SchILD/LMS/Debitor).

### A15. Firmen-Online-Anreicherung (Backend-Phase) — **live erlaubt:** VIES + Impressum/Website-Fetch + **LLM-Extraktion** (`quarkus-langchain4j`, Phoenix-Tracing); Mitarbeiter bestätigt. Im Wireframe gemockt.

### A16. Dubletten — **Live-Prüfung beim Anlegen** (matchSchluessel + KI) → Vorschlag mergen/verknüpfen; nutzt bestehende HITL-Review.

### A17. Quick-Capture — Speichern mit Status **`unvollständig/Nachpflege`** erlaubt (Telefonat) → Nachpflege-To-do. Datenqualität = **nur „unvollständig"-Flag**.

### A18. 360°-Sicht — Kontakt-Detail zeigt **Buchungen/Rechnungen/Anmeldungen read-only** + **Verlinkung** in Dubletten-Review/Anmeldungen. **DSGVO:** aus Firmenkontext nur firmenbezogene Daten (private verborgen).

### A19. Weiterbildungspflicht (Immobilien, NEU) — je Person **Stundenkonto + Nachweise (auch fremde) + 3-Jahres-Zeitraum mit Frist-Ampel** (§34c GewO / §15b MaBV, 20 Std./3 J.). Starker Vertriebs-/Bindungshebel.

### A20. DSGVO-Erfassungs-Checkliste (UI, z. B. Telefonanfrage)
Minimaldaten → Zweck/Rechtsgrundlage (Anfrage = Art. 6.1.b) → Info-Pflicht Art. 13 (mündlich + Link/Mail) → Marketing separat (Opt-In AUSSTEHEND, §7 UWG) → Double-Opt-In → Quelle/Bearbeiter/Zeit protokollieren.

---

## Teil B — UX-Festlegungen
- **Grundlayout:** Master-Detail (Liste links, Detail rechts, per-Kontakt-Tabs oben, Quicklinks links), **responsive**.
- **Globale Sofortsuche** über Personen+Firmen (Name/Telefon/Firma/USt-Id), per Shortcut fokussierbar.
- **Erfassung:** Inline-Edit im Detail + **schlankes Schnell-Modal**, **gestuft (progressive disclosure)** in fester Reihenfolge:
  1. **Pflichtfelder im Fokus** — Person: Vorname, Nachname, 1 Kontaktkanal · Firma: Name, 1 Kontaktkanal/Adresse/Person.
  2. **Marketingrelevant** — Lead-Quelle, Marketing-Opt-In (Kanal/Zweck, global vs. Firmenkontext), Werbe-/Auskunftssperre, Tags/Segmente (B2B: Verband/Unternehmenstyp).
  3. **Weitere Kontaktdaten** — zusätzliche E-Mail/Telefon/Adressen, Firmenzugehörigkeit/Funktion.
  4. **Alles andere (eingeklappt)** — Geburtsdatum/-ort/-land, Staatsangehörigkeit(en), Titel, Korrespondenzsprache, Foto, externe IDs, Beziehungen.
- **Effizienz hoch:** Shortcuts (Suche, neue Notiz, neue Wiedervorlage, speichern), optimierte Tab-Reihenfolge.
- **Dichte:** kompakt mit aufklappbaren Sekundär-Details.
- **Fehler-Feedback:** on-blur je Feld + Sammel-Hinweis beim Speichern (analog vee-validate/400).
- **Anruf-UX:** dezenter **Toast unten rechts** mit Trefferliste, 1-Klick zum Kontakt (bzw. Web-Such-Schnellauswahl bei unbekannt), Notiz/Anruf-Log sofort startbar — unterbricht nicht.
- **Optik:** an **ebz-akademie.de** angelehnt — Navy/Dunkelblau primär, helle Blau-/Grautöne, viel Weißraum, Sans-Serif, seriös-bildungsorientiert (B2B Immobilienwirtschaft).

---

## Teil C — Klickbarer Wireframe (jetzt zu bauen)
- **Technologie:** statisches **HTML/CSS + Vanilla-JS**, EBZ-Optik. Kein Build/TS, wegwerfbar, schnellste Iteration. **Umfang: alle Funktionen** in erster Iteration.
- **Ort:** `showcase/crm-wireframe/` (getrennt von `showcase/mdm/`).
- **Dateien:** `index.html`, `crm.css`, `crm.js` (Verhalten + simuliertes WebSocket-Tick), `mock-data.js` (Personen privat + mehrere Firmenzugehörigkeiten inkl. Haupt-/Ansprechpartner-Flags, Firmen mit/ohne Personen + Immobilien-Attribute, Einwilligungen, Aktivitäten, Anrufe, Weiterbildungsstunden, Loginversuche/Buchungen für Tab-Bubbles).
- **Funktionen:** N:M Firma↔Person (Einstieg, beidseitig, Mehrfachrollen, Haupt-/Ansprechpartner, dienstl. Kanäle, Firmenhierarchie, ehemalige) · Person/Firma anlegen (Quick-Modal, **gestuft: Pflicht→Marketing→weitere Kontaktdaten→Rest eingeklappt**, DSGVO-Checkliste, Opt-In global/Firmenkontext, Sperren, Lead-Quelle, Live-Dublettenhinweis) · Firma „Daten online ziehen" (gemockt) · Rich-Text-Notiz + Wiedervorlage (In-App) · eingehender Anruf (Toast + Web-Suche) · globale Sofortsuche + Quicklinks + Shortcuts · Mitarbeiter/Gruppen + Rollen `crm-pflege`/`crm-lesen`/`crm-datenschutz` · 360°-Sicht + per-Kontakt-Tabs (Bubbles: **Loginversuche + Produktbuchungen**, gemockt) · Weiterbildungspflicht-Ampel · Recht-auf-Vergessen.

---

## Nicht in dieser Phase
Keine Backend-/JPA-/DB-Änderungen, kein Vue-TS, kein orval, keine echten externen Calls. Tab-Daten & konkrete HsTAG/SchILD-Feldmappings später. **Die Listenfeld-/Stammdaten-Pflege (Lookup-CRUD) wird in diesem Step NICHT realisiert** — Lookups erscheinen im Wireframe nur als (gemockte) Auswahlwerte; die generische Pflege folgt in einer späteren Phase.

## Fallstricke & Risiken (vor Bau bedacht)
**Modell/Architektur:** (1) ✅ **Gelöst:** Login-/Identitäts-E-Mail in eigener `Login`-Tabelle (A1b, global unique); Kontaktpunkt-E-Mails sind nicht-unique Kommunikationskanäle. (2) Hauptzugehörigkeit/Hauptansprechpartner = **höchstens eine/einer aktiv** (nicht „genau eine"). (3) **Default-Kanal-Auflösung** zwischen person-privatem Primär-Kanal und dienstlichem Kanal der Hauptzugehörigkeit als feste Präzedenz-Regel. (4) **Envers-Purge** für Recht-auf-Vergessen nur via gezieltes Custom-SQL (`_AUD` append-only), Quarkus/Native + Live-Daten-Verträglichkeit prüfen. (5) Migration `Mitgliedschaft.rolle` Enum→Lookup ändert Unique `(person,org,rolle_id)` + Datenmigration.
**Recht/Sicherheit (Backend-Phase):** (6) **SSRF** bei „Daten online ziehen" → Allow-Liste/Timeouts/keine internen Redirects. (7) **Telefon-Rückwärtssuche** in DE eingeschränkt → eher Namens-/Firmensuche statt Telefonbuch-Rückwärts. (8) ✅ Anrede divers/o.A. + Fallback = **Briefanrede „Hallo {Vorname} {Nachname}"**; optionaler Override später. (9) Minderjährigen-Gating hängt an optionalem `geburtsdatum` → Default volljährig + Hinweis, wenn DOB fehlt.
**Kosten/Performance & Prozess:** (10) ✅ Live-KI (Dubletten/Anreicherung) **on-demand**, nicht pro Tastendruck — vorab billiger `matchSchluessel`-Abgleich (debounced). (11) Rich-Text im Wireframe via `contenteditable` nur fürs Klickbild — **nicht** in die echte Vue-Komponente (dort Editor-Lib). (12) Wireframe-Scope bewusst Low-Fidelity halten (Wegwerf), nicht über-investieren.

## Verifikation
`showcase/crm-wireframe/index.html` direkt im Browser öffnen — alle Flows durchklickbar; Review-Runde mit Kunde → schnelle HTML-Iteration. Datenmodell (Teil A) als Grundlage für separate Backend-Freigabe.

## Roadmap (nach Freigabe)
0. **Zuerst:** diese Plan-Doku nach `showcase/crm-planung/` in den Repo übernehmen, **committen + pushen** (auf Feature-Branch, da `main`). Erst danach Wireframe-Bau starten.
1. Wireframe + Kundenabstimmung (diese Phase). 2. Datenmodell-Freigabe → JPA-Entities + **Lookup-Tabellen** + Envers + Service-Validierungen + `PersonEmail`→Kontaktpunkt-Migration. 3. REST-Resource (flach) + orval/zod + neue Rollen. 4. Echte Vue-CRM-Komponente (mock→live), wiederverwendbare Bausteine. 5. Anreicherung (VIES/Impressum/LLM), CTI-WebSocket, Anruf-Matching, Double-Opt-In-Versand, MaBV-Stundenkonto.
