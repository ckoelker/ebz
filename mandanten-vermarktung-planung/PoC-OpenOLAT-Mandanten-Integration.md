# PoC: Vollumfängliche Mandanten-Integration auf OpenOLAT shared

> **Status:** PLANUNG — **Bau noch nicht freigegeben** (Review dieses Plans → dann separate Bau-Freigabe).
> Setzt die in [LMS-Plattformvergleich §12.2](LMS-Plattformvergleich-OpenOLAT-Moodle.md) zugespitzte
> Empfehlung um: **OpenOLAT shared als Primärwette**, eingeschränkte Mandanten-CI, **eine** Instanz.
> **Moodle Workplace** geparkt (Kosten); **IOMAD-Vergleichs-Spike machen Kollegen** → hier **out-of-scope**.
> **Stand:** 2026-06-25 (inkl. Entscheidungen aus dem 40-Fragen-Q&A, §0/§1).
>
> Fokussierte, gebaute Teilmenge von
> [Plan-Mandanten-Vermarktung-OpenOLAT.md](Plan-Mandanten-Vermarktung-OpenOLAT.md) (Typ E, ohne
> Reseller/Vendure-Channel und ohne Reporting-Ausspielwege #3–#10).

---

## 0. Entscheidungen aus dem Q&A (2026-06-25) — verbindlich

| # | Entscheidung |
|---|---|
| **A1/A2** | **Native Keycloak Organizations** (v26 GA) statt `home-idp-discovery`-Plugin; **ein** Realm (`ebz-customers`). |
| **A3** | **MDM ist Source-of-Truth** fürs Domain→Mandant-Mapping, projiziert nach Keycloak. OpenOLAT-Domain-Mapping **nicht** parallel. |
| **A4** | **EBZ-Kernmandant = Default** für direkte Logins; **föderierte** Logins ohne `mandant`-Claim → **fail-closed**. |
| **A5** | Keycloak **vor M3 auf v26+** anheben (freigegeben). |
| **EBZ** | **Zwei EBZ-Mandanten:** *EBZ-Customer* (B2C/Shop) + *EBZ-Staff-Intern* (internes Training). Siehe §1. |
| **B1** | Tenant-Isolation = **weicher PoC-Gate** (K1 getestet + Lecks dokumentiert, kein Pass/Fail-Block). Harte Iso bleibt B2B-Vertriebspflicht. |
| **B2** | Gegen **OpenOLAT 20.1**-`openapi.json` bauen (Version gepinnt). |
| **B3** | **Ein Mandant = eine Top-Org** (Sub-Orgs später). |
| **B4** | **M2 startet mit `openapi.json`-Audit** der Organisations/Member-Endpunkte. |
| **B5** | Multi-Tenant-User (eine Person, mehrere Mandanten) **ausgeklammert** (Annahme: eine Identität = ein Mandant). |
| **C1** | Content-Sharing über **Catalog-2.0-Offers pro Organisation** (ein Repo-Entry, n org-skopierte Offers). |
| **C3** | **SCORM-embedded Video** im PoC (Storage × 1 via Offers); externes Streaming/host-once-per-LTI nur Skalierungs-Option. |
| **C5** | Update-Propagierung im PoC **nur Sichtbarkeit**; Update-in-place/Progress-Erhalt = offener Punkt. |
| **D1/H2** | **M0 Branding-Spike läuft parallel zu M1** (kein harter Gate); M0 bleibt Gate für den **Branding-Anspruch** (D4/K3) mit Fallback D5. |
| **D2** | Theme-**Recompile je Mandant akzeptabel** (≤ 20, kein Sprawl). |
| **D3** | Login-Branding über **Firmen-IdP-Redirect** (B2B) bzw. **EBZ-Realm-Theme** (B2C) — **keine** per-Org-Keycloak-Themes. |
| **D4** | Branding-Tiefe **Stufe 1 zuerst** (Logo + Farben + IdP-Login); **Stufe 2** (volle White-Label) später — **direkt Stufe 2**, falls Stufe 1 kontraproduktiv. |
| **E1** | Seat-Gate **bei Provisionierung/Org-Add**, nicht im Login-Hot-Path. |
| **E2** | Seat-Zählung = **aktive Org-Mitglieder**. |
| **E4** | Überschreitung: **weich überbuchen + verpflichtende HITL-Meldung** (intern bestätigen); **jeder** weitere überschreitende Nutzer löst die Meldung **erneut** aus. |
| **F1** | Weiterbildungsstunden aus **Soll-Stunden je Kurs** (rechtliche Zählung); `session_time` nur informativ. |
| **F2** | **`WbtKurs.sollStundenAnrechenbar`** ergänzen (Anrechnung §34d/§34c/§34i separat final prüfen). |
| **F4** | Nachweis im PoC = **minimaler Fakt-Seam** (Completion → kanonischer Fakt); Zertifikat/PAdES out. |
| **G1** | **Eigene `MandantProjektion`-Outbox-Tabelle**; nur das Dispatcher-*Muster* mit `EnrollmentDispatcher`/HubSpot teilen. |
| **G3** | **Live-Compose gegen echtes OpenOLAT + Keycloak**; Mock nur für Unit/rest-assured. |
| **H1** | PoC-Erfolg = **K7-Aufwands-Fazit** (je M-Schritt trivial/mittel/zäh) → Schnittstelle zum IOMAD-Kollegen-Spike. |
| **H3** | **≤ 20 Mandanten** in der Endausbaustufe (+ die 2 EBZ-Kontexte). |
| **H4** | Demo-Content = **Platzhalter-Video-SCORM** (kein Kunden-Rise-Export nötig). |
| **H5** | **mdm-Schema-Tabellen + Keycloak-v26-Bump freigegeben.** Bau-Freigabe nach Plan-Review. |

---

## 1. Tenant-Landschaft (tragendes Prinzip: EBZ ist Kernmandant)

EBZ ist **nicht nur Betreiber, sondern selbst Mandant**. Die Landschaft ist **additiv** — die bestehende
Ein-Mandanten-LMS-Strecke (L0–L3, EBZ-Shop → Einschreibung) **läuft unverändert weiter** als
EBZ-Customer-Pfad.

| Mandant | Population | Login-Weg | `mandant`-Claim | Org / Branding |
|---|---|---|---|---|
| **EBZ-Customer** (Kernmandant) | B2C-Shop-Kunden (z. B. Carla Kundin) | `ebz-customers` direkt | keiner (erwartet) | EBZ-Org · **globales `ebz`-Default-Theme** |
| **EBZ-Staff-Intern** | EBZ-Mitarbeiter als Lernende | `ebz-staff` | (realm-basiert) | EBZ-Intern-Org · EBZ-Branding |
| **B2B-Mandant × (≤ 20)** | Mitarbeiter des Kunden | Kunden-IdP **gebrokert** | `mandant=X` | Org X · X-Branding (Stufe-1-CSS) |
| *(Fehler)* | gebrokert, Claim fehlt | — | fehlt | **fail-closed → abgewiesen** |

**Landing-Regel (A4):** realm-/claim-basiert. Direkter `ebz-customers`-Login ohne Claim ist legitim
(= EBZ-Customer); `ebz-staff` → EBZ-Staff-Intern; gebrokerte B2B-Logins per `mandant`-Claim → Org X; ein
gebrokerter Login **ohne** erwarteten Claim wird abgewiesen (kein Cross-Tenant-Leak ins EBZ-Branding).

**Branding-Risiko entschärft:** Der Kernmandant läuft auf dem **globalen Default-Theme** → braucht **keine**
per-Org-CSS. Scheitert per-Org-Type-CSS (Risiko D1), ist EBZ unberührt; nur die B2B-Mandanten bekommen dann
reduziertes Branding (Fallback D5).

## 2. Zweck & Leitfrage

**Leitfrage (das Risiko aus §12.2):** *Wie schwer ist der Eigenbau der Mandanten-Schicht auf OpenOLAT shared
wirklich?* IOMAD würde sie nativ schenken (Preis: PHP-Stack + Fork-Risiko); OpenOLAT verlangt sie als
Eigenleistung. Der PoC **misst diesen Aufwand an einem realen, live verifizierten Durchstich** (G3) und
liefert das **K7-Aufwands-Fazit** als Build-vs-Buy-Evidenz gegen den IOMAD-Kollegen-Spike.

## 3. Scope

**In Scope (Typ E + EBZ-Kernmandant, eine Instanz):**
1. **Org je Mandant** + realm-/claim-basierte Landing (EBZ-Customer / EBZ-Staff-Intern / B2B-Orgs).
2. **Seat-Cap** je B2B-Mandant (weich + HITL, E1/E2/E4). EBZ-Customer B2C = **kein** Seat-Limit.
3. **Per-Tenant-CSS** Stufe 1 (Logo + Farben) für B2B; EBZ auf Default-Theme.
4. **Keycloak Organizations + Identity Brokering** (Kunden-IdP, Domain-Routing, `mandant`-Claim).
5. **Content-share-once** über **Catalog-2.0-Offers**: ein Repo-Entry (video-schweres SCORM), n org-skopierte
   Offers — inkl. EBZ-Kontext in EBZ-Branding.
6. **Weiterbildungsnachweis (minimal)**: Completion → kanonischer `LernleistungsFakt` (Soll-Stunden).

**Out of Scope:** IOMAD/Moodle (Kollegen-Spike) · Typ R (Reseller/Vendure-Channel/Storefront/Revenue-Share)
· Reporting-Ausspielwege #3–#10 · Zertifikat/eIDAS-PAdES · Instanz-pro-Mandant · Multi-Tenant-User ·
volle White-Label (Stufe 2) · Content-Update-in-place mit Progress-Erhalt.

## 4. Erfolgskriterien (messbar)

- **K1 Isolation (weicher Gate):** Mandant-A sieht nur eigene Orgs/Kataloge; Lecks werden **getestet und
  dokumentiert** (nicht als Block) → fließt ins K7-Fazit + B2B-Risikovermerk.
- **K2 Seat-Cap:** N+1 über `seatLimit` wird **durchgelassen, aber als HITL-Meldung** erzeugt (pro
  Überschreitung erneut); Belegung je Mandant korrekt.
- **K3 Branding:** B2B-Mandant-A-Nutzer sieht A-Logo/-Farben (post-auth, Stufe 1) **und** A-Login via
  eigenem IdP; EBZ-Default unverändert. **Klassenname/Injection-Punkt am DOM verifiziert.**
- **K4 IdP-Föderation:** Login `@mandant-a.de` → über gebrokerten Kunden-IdP automatisch in Org A
  (`mandant`-Claim); fail-closed bei fehlendem Claim.
- **K5 Content-share-once:** dasselbe video-SCORM ist in **≥ 2** Orgs (inkl. EBZ) sichtbar/startbar, liegt
  aber **einmal** im Repo (kein Re-Import/Kopie je Org); Update → in allen Orgs sichtbar.
- **K6 Nachweis-Seam:** Completion eines A-Lernenden → `LernleistungsFakt` (Soll-Stunden) in MDM lesbar.
- **K7 (Meta) Aufwands-Fazit:** je M-Schritt trivial/mittel/zäh + Stolpersteine → Entscheidungs-Input.

## 5. Wiederverwendung (Bestand) & was neu ist

**Bestehend, genutzt/erweitert:**
- [`lms/openolat/OpenolatApi.java`](../showcase/integration/src/main/java/de/netzfactor/ebz/controlling/integration/lms/openolat/OpenolatApi.java)
  — **erweitern:** `createOrganisation`, `addOrganisationMember`, **Offer/Share-Endpunkte (Catalog 2.0)**,
  Completion-Read. **Alle Pfade gegen `20.1/openapi.json` verifizieren** (B2/B4).
- [`lms/service/EnrollmentDispatcher.java`](../showcase/integration/src/main/java/de/netzfactor/ebz/controlling/integration/lms/service/EnrollmentDispatcher.java)
  — Outbox-/Dispatcher-**Muster** für `MandantProjektion` (eigene Tabelle, G1).
- [`openolat/lms-import-seed.sh`](../showcase/openolat/lms-import-seed.sh) — Content-Import als Basis; M4
  ergänzt **org-skopierte Offers** statt nur Import.
- [`openolat/theme/ebz/theme.scss`](../showcase/openolat/theme/ebz/theme.scss) — globales Theme = EBZ-Default;
  per-Org-Type-CSS (Stufe 1) für B2B ergänzen.
- **Keycloak** `ebz-customers`/`ebz-staff` — **Organizations** (v26) + Brokering; Provisionierung über
  **Quarkiverse `quarkus-keycloak-admin-rest-client`**, token-gated, Mock ohne Token.

**Neu** (Package `…integration.mandant`, **Schema `mdm`, echte FKs — freigegeben H5**):
- **`Mandant`** (`organisation`→FK, `schluessel`, `anzeigeName`, `vertragsTyp` {EBZ_CUSTOMER, EBZ_STAFF,
  ENTERPRISE_FLAT}, `status`, `openolatOrganisationKey?`, Branding `logoUrl/primaerFarbe`).
- **`IdpFoederation`** (`mandant`→FK, `idpAlias`, `emailDomains`, `protokoll`, `status`) — Quelle der
  Keycloak-Organizations-Provisionierung.
- **`Lizenzvertrag`** (`mandant`→FK, `seatLimit`, Laufzeit) — nur für `ENTERPRISE_FLAT`; B2C ohne Limit.
- **`Kurseinschreibung.mandant`** (bestehende Entity, neue nullable FK).
- **`WbtKurs.sollStundenAnrechenbar`** (F2) + **`LernleistungsFakt`** (`mandant`/`person`/`wbtKurs`→FK,
  `abgeschlossenAm`, `sollStunden`, `session_time?` informativ).
- **`MandantProjektion`-Outbox** (`HubSpotSyncAuftrag`-artig) + Services `MandantService`,
  `KeycloakOrganizationsService` (Mock ohne Token), `SeatLimitService`, `web/MandantResource`
  (`@RolesAllowed` ebz-staff).

## 6. Bau-Schritte (M0–M6) — Build grün + Live-Verifikation (G3)

> Risiko zuerst; iterativ via `quarkus:dev`/Continuous Testing ([[dev-iterate-fast]]), gezielte Tests
> ([[run-only-needed-tests]]), `mvn -f` über Bash ([[use-bash-not-powershell]]).

- **M0 — Branding-Spike** *(parallel zu M1, D1/H2)*: an der **laufenden** Instanz Org-Typ + „CSS class" **im
  DOM verifizieren**; Stufe-1-Block in `theme.scss` (Logo + 2–3 Farben) → Recompile; Gegenprobe: B2B-Mandant
  sieht Marke, **EBZ-Default unverändert**. Trägt per-Org-CSS nicht → **Fallback D5** (nur IdP-Login-Branding),
  EBZ unberührt. **→ K3.** Aufwands-Notiz (K7).
- **M1 — Datenmodell + CRUD:** `Mandant`/`IdpFoederation`/`Lizenzvertrag` + `Kurseinschreibung.mandant` +
  `WbtKurs.sollStundenAnrechenbar`; `MandantResource`-CRUD; CHECK-Constraints bei neuen Enums
  ([[jpa-enum-check-constraints]]); rest-assured.
- **M2 — Org-Projektion** *(Kern-Seam, startet mit `openapi.json`-Audit, B4)*: `OpenolatApi`-Erweiterung;
  `MandantProjektion`-Outbox legt Org an → `openolatOrganisationKey`; rest-assured (`FakeOpenolatProvisioning`)
  **+ live**. **→ K1-Basis.**
- **M3 — Keycloak Organizations + Brokering** *(Keycloak v26+ vorausgesetzt, A5)*: Organization je Mandant
  (Domain), Kunden-IdP gebrokert, `mandant`-Claim-Mapper; Landing-Regel (A4); JIT in Org. **→ K4** (+ K3
  IdP-Login).
- **M4 — Content-share-once** *(Offers, C1)*: ein video-SCORM einmal importieren, **org-skopierte Offers für
  ≥ 2 Orgs (inkl. EBZ)**; Storage-once messen (K5); Offer-per-REST gegen `openapi.json` (C2, sonst
  Admin-Fallback). **→ K5.**
- **M5 — Seat-Cap** *(E1/E2/E4)*: `SeatLimitService` zählt aktive Org-Mitglieder gegen `seatLimit`;
  Überschreitung **durchlassen + HITL-Meldung** (pro Überschreitung erneut); Belegungs-Report. **→ K2.**
- **M6 — Nachweis-Seam + E2E** *(startet mit Completion-Read-`openapi.json`-Audit, F3)*: Completion/
  Soll-Stunden → `LernleistungsFakt`; **E2E-Durchstich mit Prozessspur/Baggage** (Akteur durchgängig,
  [[generate-bpmn-on-demand]]): Mitarbeiter via IdP → Org A → video-Nugget (shared Offer) → Launch →
  Completion → Fakt. **→ K6.** Danach **K7-Aufwands-Fazit** schreiben.

## 7. Verifikation

- **Live-Compose (G3, default-Pfad):** echtes OpenOLAT 20.1 + Keycloak v26; 1 B2B-Demo-Mandant (zweites
  Realm/IdP gebrokert) + EBZ-Customer + EBZ-Staff-Intern; ≥ 2 Orgs für den Content-share-once-Beweis;
  Branding-Gegenprobe im Browser.
- **rest-assured** (`%test`, Mock-Senken): Aktivierung legt **genau eine** Org an (idempotent); Login
  `@mandant-a.de` → Org-A-Mitglied + Katalog; Seat-Überschreitung → HITL-Meldung; Fremd-Mandant sieht
  fremden Katalog **nicht** (K1, dokumentiert); `/mandant` ohne `ebz-staff` → 403.
- **K7-Aufwands-Fazit** je M-Schritt → Rückfluss in [§12.2](LMS-Plattformvergleich-OpenOLAT-Moodle.md).

## 8. Risiken / Gotchas (recherche-gestützt, 2026-06)

- **🔴 Branding-Update-Instabilität:** OpenOLAT-Doku — CSS-Klassen/Element-IDs sind **nicht zwischen Updates
  garantiert**, DOM ändert sich; **keine dokumentierte native per-Organisation-CSS-Differenzierung**. → M0
  beweist Tragfähigkeit zuerst; Klassenname/Injection-Punkt am DOM verifizieren; Fallback D5. EBZ-Default
  bleibt davon unberührt.
- **OpenOLAT-REST-Abdeckung:** Organisations/Offers/Completion-Pfade **gegen `openapi.json` verifizieren,
  nicht raten** (war bei `PUT /repo/entries` der Stolperstein). Offers (Catalog 2.0) evtl. nur UI → C2-Audit,
  Admin-Fallback.
- **🔴 SCORM `session_time` unzuverlässig:** write-only, „kein Beweis echter Lernzeit", oft nicht korrekt
  gespeichert → **Soll-Stunden** als Rechtsgrundlage (F1), `session_time` nur informativ.
- **Tenant-Isolation:** OpenOLAT-Organisations-Isolation nicht hart dokumentiert (#6); K1 testet Lecks, im
  PoC weicher Gate — **für den B2B-Vertrieb bleibt harte Isolation Pflicht**.
- **Keycloak per-Org-Login-Theme:** Themes sind realm-/client-, nicht org-nativ → **keine** per-Org-Themes
  bauen; B2B sieht ohnehin den **eigenen IdP** (Domain-Redirect), B2C die EBZ-Realm-Login (D3).
- **Video im SCORM:** kein OpenOLAT-Transcoding/adaptives Streaming, große Pakete (aber Storage × 1 via
  Offers). Externes Streaming/host-once-per-LTI als Skalierungs-Option offenhalten.
- **Seat-Cap nicht nativ:** weicher Gate bei Provisionierung + HITL; Count in der Geschäfts-Tx +
  Drift-Report; kein Echtzeit-Login-Gate.
- **Keycloak-Version:** Organizations ist erst ab **v26 GA** → Version vor M3 prüfen/anheben (A5).
- **Kein echtes Drittsystem in CI:** Mock default; Real-Adapter token/profil-gated.

## 9. Abhängigkeiten / offene Punkte

- **Bau-Freigabe** (nach Review dieses Plans). mdm-Tabellen + Keycloak-v26-Bump **bereits freigegeben** (H5).
- **Platzhalter-Video-SCORM** als Demo-Content bereitstellen (H4).
- **Demo-Kunden-IdP** = zweites Keycloak-Realm (gebrokert) für M3.
- **Rechtliche Stundenzählung** §34d/§34c/§34i (Soll-Stunden-Anrechnung) final prüfen.
- **Content-Update-in-place** (Progress-Erhalt) — bewusst offener Punkt (C5, Rise-Doc).
- **Schnittstelle zum IOMAD-Kollegen-Spike:** das K7-Aufwands-Fazit beider Seiten in §12.2 zusammenführen.

## 10. Quellen (Recherche 2026-06)

- Keycloak Organizations (GA v26):
  [Ankündigung](https://www.keycloak.org/2024/06/announcement-keycloak-organizations) ·
  [Multitenancy mit Organizations](https://skycloak.io/blog/multitenancy-in-keycloak-using-the-organizations-feature/)
- OpenOLAT Content/Offers (Catalog 2.0, ab v17):
  [Catalog/Share](https://docs.openolat.org/manual_how-to/catalog/catalog/) ·
  [Access configuration](https://docs.openolat.org/manual_user/learningresources/Access_configuration/)
- OpenOLAT Organisations (Domain-Mapping):
  [Modules Organisations](https://docs.openolat.org/manual_admin/administration/Modules_Organisations/) ·
  [REST API](https://docs.openolat.org/manual_admin/administration/REST_API/)
- OpenOLAT Theming-Caveat:
  [CSS How-to](https://docs.openolat.org/manual_how-to/css/css/) ·
  [themes.README](https://github.com/OpenOLAT/OpenOLAT/blob/master/src/main/webapp/static/themes/themes.README)
- OpenOLAT Video:
  [Video Upload](https://docs.openolat.org/manual_user/learningresources/Video_Upload/) ·
  [Reduce storage](https://docs.openolat.org/manual_how-to/reduce_storage_consumption/reduce_storage_consumption/)
- SCORM `session_time`:
  [SCORM Run-Time Reference](https://scorm.com/scorm-explained/technical-scorm/run-time/run-time-reference/)
