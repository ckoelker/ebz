# PoC: Vollumfängliche Mandanten-Integration auf OpenOLAT shared

> **Status:** PLANUNG — **kein Code freigegeben.** Dieser Plan setzt die in
> [LMS-Plattformvergleich §12.2](LMS-Plattformvergleich-OpenOLAT-Moodle.md) zugespitzte Empfehlung um:
> **OpenOLAT shared als Primärwette** mit **eingeschränkter Mandanten-CI** (eine Instanz, Organisation je
> Mandant). **Moodle Workplace** ist aus Kostengründen geparkt; den **IOMAD-Vergleichs-Spike übernehmen
> Kollegen** und ist hier **explizit out-of-scope**. **Stand:** 2026-06-25.
>
> Dieser PoC ist die **fokussierte, gebaute Teilmenge** von
> [Plan-Mandanten-Vermarktung-OpenOLAT.md](Plan-Mandanten-Vermarktung-OpenOLAT.md) (Typ E, Phasen
> T-Spike/T0–T3) — ohne Reseller/Vendure-Channel (Typ R) und ohne die Reporting-Ausspielwege #3–#10.

---

## 1. Zweck & Leitfrage

**Leitfrage (das eigentliche Risiko aus §12.2):** *Wie schwer ist der Eigenbau der Mandanten-Schicht auf
OpenOLAT shared wirklich?* IOMAD würde diese Schicht nativ schenken (um den Preis PHP-Stack + Fork-Risiko);
OpenOLAT verlangt sie als Eigenleistung. Dieser PoC **misst diesen Aufwand an einem realen Durchstich** und
liefert die Evidenz für die Build-vs-Buy-Entscheidung **OpenOLAT shared ↔ Moodle+IOMAD**.

**Erfolg = nicht nur „läuft grün", sondern eine belastbare Aufwands-/Reife-Aussage:** Welche Teile der
Mandanten-Schicht sind trivial (REST-Mapping), welche zäh (Branding-Injection, Seat-Cap ohne nativen Gate,
IdP-Brokering-Mapper)? → fließt als **Aufwands-Fazit** zurück in den Plattformvergleich.

## 2. Scope

**In Scope — vollumfängliche Mandanten-Integration (Typ E) auf EINER OpenOLAT-Instanz:**
1. **Organisation je Mandant** — OpenOLAT-Organisation per REST anlegen; Lernende beim SSO-Login JIT der
   richtigen Org zuordnen; Repo-/Curriculum-Zugang org-skopiert.
2. **Seat-Cap** — Lizenz-/Seat-Limit je Mandant in MDM/integration durchsetzen (OpenOLAT hat **keinen**
   nativen harten Gate) + Drift-/Überschreitungs-Report/HITL.
3. **Per-Tenant-CSS** — Mandanten-Marke post-auth über **CSS-Klasse pro Org-Typ** im **einen** globalen
   `ebz`-Theme (Logo-Swap + Markenfarben).
4. **Keycloak-Login-Theme + Identity Brokering** — pre-auth Mandanten-Login (Realm/Theme) und **Kunden-IdP
   föderiert** (Home-IdP-Discovery per E-Mail-Domäne, `mandant`-Claim → Org-Zuordnung).
5. **Content-share-once** — **eine** Lernressource (video-schweres Nugget) **einmal** importiert, von **n
   Mandanten-Orgs referenziert/sichtbar** (Beweis: Storage × 1, keine Kopie je Mandant).
6. **Weiterbildungsnachweis (Durchstich-Minimal)** — Completion/`session_time` eines Mandanten-Lernenden als
   kanonischer Fakt lesbar (nur als Beleg, dass der Nachweis-Seam steht; Ausspielwege #1–#10 NICHT hier).

**Out of Scope (bewusst):**
- **IOMAD / Moodle** jeglicher Art (Kollegen-Spike, separat).
- **Typ R (Reseller/B2B2C):** Vendure-Channel/Seller, gebrandete Storefront, Revenue-Share.
- **Reporting-Ausspielwege #3–#10** (HR-Konnektoren, OData, Webhook, xAPI-Export, DWH-Push …).
- **Instanz-pro-Mandant** (durch Content-Gewicht in die Nische gefallen).
- **Echte GDPR-/Lösch-Lebenszyklen** über das hinaus, was der Identitäts-/Org-Pfad ohnehin braucht.

## 3. Erfolgskriterien (messbar)

- **K1 Org-Isolation:** Mandant-A-Lernender sieht **nur** Katalog/Kurse seiner Org; Mandant-B-Inhalte
  **nicht**. Cross-Tenant-Leak = Fehlschlag.
- **K2 Seat-Cap:** N+1-ter Lernender über `seatLimit` wird **abgelehnt/HITL**; Report zeigt Belegung je
  Mandant korrekt.
- **K3 Branding:** Mandant-A-Nutzer sieht A-Logo/-Farben (post-auth) **und** A-Login (pre-auth); EBZ-Default
  bleibt unverändert. **Klassenname/Injection-Punkt an der laufenden Instanz im DOM verifiziert.**
- **K4 IdP-Föderation:** Login mit `@mandant-a.de` landet über den gebrokerten Kunden-IdP automatisch in
  Org A (`mandant`-Claim gesetzt); JIT-User angelegt.
- **K5 Content-share-once:** dasselbe video-schwere Nugget ist in **≥ 2** Mandanten-Orgs sichtbar/startbar,
  liegt aber **einmal** im OpenOLAT-Repo (kein Re-Import je Org). Update am Nugget → in beiden Orgs sichtbar.
- **K6 Nachweis-Seam:** Completion eines A-Lernenden ist als kanonischer `LernleistungsFakt` lesbar.
- **K7 (Meta) Aufwands-Fazit:** dokumentierte Einschätzung je Baustein (trivial / mittel / zäh) inkl. der
  Stolpersteine → Entscheidungs-Input.

## 4. Wiederverwendung (Bestand) & was neu ist

**Bestehend, wird genutzt/erweitert:**
- [`lms/openolat/OpenolatApi.java`](../showcase/integration/src/main/java/de/netzfactor/ebz/controlling/integration/lms/openolat/OpenolatApi.java)
  — REST-Client (Basic-Auth je Aufruf). **Erweitern:** `createOrganisation`, `addOrganisationMember`,
  Curriculum/Catalog-/Repo-Org-Scope. **Pfade/Payloads zwingend gegen `/restapi/openapi.json` verifizieren**
  (war bei `PUT /repo/entries` schon der Stolperstein), nicht raten.
- [`lms/service/EnrollmentDispatcher.java`](../showcase/integration/src/main/java/de/netzfactor/ebz/controlling/integration/lms/service/EnrollmentDispatcher.java)
  + `Kurseinschreibung` — **Outbox-/Dispatcher-Muster** (idempotent, Retry/Dead-Letter/HITL, BPMN
  `SERVICE_TASK`) als Vorlage für die Mandanten-Provisionierung.
- [`openolat/lms-import-seed.sh`](../showcase/openolat/lms-import-seed.sh) — Content-Import
  (`PUT /repo/entries` + publish) als Basis für **Content-share-once** (ein Repo-Entry, n Orgs grant).
- [`openolat/theme/ebz/theme.scss`](../showcase/openolat/theme/ebz/theme.scss) — globales Theme → CSS-Klasse
  pro Org-Typ ergänzen (§7.1 des Parent-Plans).
- **Keycloak** (`ebz-customers`/`ebz-staff`) — Identity Brokering + Login-Theme; Provisionierung über
  **Quarkiverse `quarkus-keycloak-admin-rest-client`** ([[prefer-quarkus-quarkiverse-extension]]),
  token-gated, Mock ohne Token.

**Neu (Datenmodell + Backend — wie Parent-Plan §5/§6, auf PoC reduziert):** Package
`…integration.mandant`, **Schema `mdm`, echte FKs, keine neue Schema-Freigabe** nötig
([[no-new-db-schema-without-approval]], [[prefer-manytoone-real-fks]]):
- **`Mandant`** (`organisation`→FK, `schluessel` unique, `anzeigeName`, `vertragsTyp=ENTERPRISE_FLAT`,
  `status`, `openolatOrganisationKey?`, Branding `logoUrl/primaerFarbe`).
- **`IdpFoederation`** (`mandant`→FK, `idpAlias`, `emailDomains`, `protokoll`, `status`).
- **`Lizenzvertrag`** (`mandant`→FK, `seatLimit`, Laufzeit, `katalogUmfang`) — für Seat-Cap (PoC: ohne
  Rechnungslauf).
- **`Kurseinschreibung.mandant`** (bestehende Entity, neue nullable FK) — Delivery kennt die Ziel-Org.
- **`LernleistungsFakt`** (minimal: `mandant`/`person`/`wbtKurs`→FK, `abgeschlossenAm`, `lernzeitMinuten`).
- **Services:** `MandantService` (CRUD, `aktiviere()` reiht Outbox ein), `MandantProjektion`
  (Outbox-Dispatcher: Org anlegen → Katalog-Grant → IdP/Mapper), `KeycloakFederationService` (Mock ohne
  Token), `SeatLimitService` (zählt Org-Mitglieder gegen `seatLimit`). `web/MandantResource`
  (`@RolesAllowed` ebz-staff; CRUD, „Aktivieren", Seat-Report, HITL-Retry).

## 5. Bau-Schritte (M0–M6) — jeweils Build grün + Verifikation

> **Reihenfolge nach Risiko:** das Unbekannteste zuerst (Branding-Injection, REST-Org-Pfade), damit der
> Aufwand früh sichtbar wird. Iterativ via `quarkus:dev`/Continuous Testing ([[dev-iterate-fast]]), gezielte
> Tests ([[run-only-needed-tests]]), `mvn -f` über Bash ([[use-bash-not-powershell]]).

- **M0 — Branding-Spike (zuerst, höchstes Unbekanntes, kein Backend):** an der **laufenden** OpenOLAT-Instanz
  Org-Typ + „layout valid for this organization type via CSS class" **im DOM verifizieren**; skopierter Block
  in `theme/ebz/theme.scss` (Logo-Swap + 2–3 Markenfarben) → kompilieren (Docker-Build); Gegenprobe:
  Mandanten-User sieht Marke, EBZ-Default unverändert. **→ K3 (post-auth).** Klärt die größte Branding-
  Unbekannte vorab; Aufwands-Notiz.
- **M1 — Datenmodell + CRUD:** `Mandant`/`IdpFoederation`/`Lizenzvertrag` + `Kurseinschreibung.mandant`;
  `MandantResource`-CRUD; CHECK-Constraints bei neuen Enum-Werten ([[jpa-enum-check-constraints]]). rest-assured.
- **M2 — Org-Projektion (Kern-Seam):** `OpenolatApi` um `createOrganisation`/`addOrganisationMember`
  erweitern (**gegen `openapi.json` verifiziert**); `MandantProjektion`-Outbox legt Org an →
  `openolatOrganisationKey`. rest-assured gegen `FakeOpenolatProvisioning` + **live** (compose). **→ K1-Basis.**
- **M3 — Identity Brokering + Login-Theme:** Kunden-IdP als gebrokerter IdP in Keycloak (Quarkiverse-Admin-
  Client, Mock ohne Token) + Home-IdP-Discovery (E-Mail-Domäne) + `mandant`-Claim-Mapper; JIT-Login → in Org;
  Keycloak-Login-Theme für den Demo-Realm. **→ K4 + K3 (pre-auth).**
- **M4 — Content-share-once:** ein video-schweres Nugget einmal importieren (`lms-import-seed.sh`-Muster) und
  **per Org-/Curriculum-Grant ≥ 2 Mandanten-Orgs** zuordnen; Update-Propagierung prüfen. **→ K5.**
- **M5 — Seat-Cap:** `SeatLimitService` zählt aktive Org-Mitglieder (OpenOLAT-REST) gegen `seatLimit`;
  N+1 → Ablehnung/HITL; Belegungs-Report. **→ K2.**
- **M6 — Nachweis-Seam (minimal) + E2E:** Completion/`session_time` eines A-Lernenden → `LernleistungsFakt`
  lesbar (Completion-Read-Pfad **gegen `openapi.json` verifizieren** — war offener L4-Punkt); **E2E-Durchstich
  mit Prozessspur/Baggage** (Akteur durchgängig, [[generate-bpmn-on-demand]]): Mitarbeiter via eigenem IdP →
  Org A → video-Nugget (shared) → Launch → Completion → Fakt. **→ K6.** Abschließend **K7-Aufwands-Fazit**
  schreiben.

## 6. Verifikation

- **rest-assured** (`%test`, Mock-Senken `FakeOpenolatProvisioning`/Keycloak-Mock): Aktivierung legt **genau
  eine** Org an (idempotent); Login `@mandant-a.de` → Org-A-Mitglied + Katalog sichtbar; 101. Seat → HITL;
  Fremd-Mandant sieht fremden Katalog **nicht**; `/mandant` ohne `ebz-staff` → 403.
- **Live (compose, Mock-Modus default):** 1 Enterprise-Demo-Mandant (zweites Keycloak-Realm als Demo-Kunden-
  IdP gebrokert) + ≥ 2 Orgs für den Content-share-once-Beweis; Branding-Gegenprobe im Browser.
- **Aufwands-Fazit (K7):** je M-Schritt trivial/mittel/zäh + Stolpersteine → Rückfluss in
  [LMS-Plattformvergleich §12.2](LMS-Plattformvergleich-OpenOLAT-Moodle.md).

## 7. Risiken / Gotchas (PoC-spezifisch)

- **OpenOLAT-REST Org/Curriculum/Completion:** Pfade/Payloads **gegen `openapi.json` verifizieren** (nicht
  raten) — das ist das wahrscheinlichste Zeitfresser-Risiko.
- **Branding nicht runtime-fähig:** ein globales SCSS-Theme → neuer Mandant = `theme.scss` + Neukompile
  (Docker-Build), kein Self-Service; **Klassenname/Injection-Punkt am DOM verifizieren** (frentix dokumentiert
  das Detail nicht). Login-Seite geht **nur** über Keycloak-Theme, nicht via Org-CSS.
- **Seat-Cap nicht nativ:** kein harter Echtzeit-Gate im Login-Pfad ohne tiefere Hooks → in MDM durchsetzen +
  Drift-Report (bewusst weicher Gate im PoC).
- **Brokering-Mapper:** `mandant`-Claim muss zuverlässig gesetzt werden, sonst falsche/keine Org.
- **Content-share-once-Grant:** verifizieren, dass Org-/Curriculum-Zuordnung **ohne Re-Import** mehreren Orgs
  Sichtbarkeit gibt (sonst kippt der Storage-×-1-Beweis).
- **Kein echtes Drittsystem in CI:** Mock default; Real-Adapter (Keycloak-Token/OpenOLAT-live) token-/profil-
  gated.

## 8. Abhängigkeiten / offene Punkte (vor Bau)

- **Freigabe zum Bau** (dieser PoC ist Planung; Tabellen in bestehendes `mdm`-Schema bestätigen).
- **Erwartete Mandantenzahl** (fließt in Seat-/Org-Annahmen; eigentlicher Break-even-Hebel).
- **Ein video-schweres Demo-Nugget** als Testinhalt (Größe realistisch, für den share-once-Beweis).
- **Demo-Kunden-IdP** = zweites Keycloak-Realm (gebrokert) für M3.
- **IOMAD-Spike der Kollegen** liefert die Gegenseite des Vergleichs — Schnittstelle = das **K7-Aufwands-
  Fazit** beider Seiten in §12.2 zusammenführen.
