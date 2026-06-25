# LMS-Plattformvergleich — OpenOLAT (shared & Instanz/Mandant) vs. Moodle (OSS & Workplace)

> **Anlass:** Die Geschäftsführung präferiert **Moodle**. Diese Matrix vergleicht entlang **43 Kriterien**
> (40 + 2 Content-Verteilung §4b + 1 Enterprise-MT-Verbreitung §10b, ergänzt 2026-06-24 nach Rise-360- und
> Adoptions-Recherche) vier Optionen für die **mandantenfähige Vermarktung von EBZ-eLearning** und spricht
> eine Empfehlung aus.
> **Stand:** 2026-06-24 · **Status:** Entscheidungsvorlage. Lizenz-/Preis- und Feature-Aussagen =
> Recherchestand 2026-06, vor Vertrag final prüfen (Quellen am Ende).

## §0 Was verglichen wird
- **OpenOLAT shared (wie geplant)** — bisheriger Showcase-Weg: **eine** OpenOLAT-Instanz (JVM/Tomcat,
  Apache-2.0), bereits gebaut (WBT-Verkauf L0–L3); Mandantenfähigkeit/Branding/Reporting als **geplanter
  Eigenbau** (Plan T0–T7: Organisations + Keycloak-Brokering + Vendure-Channel + Reporting-Adapter).
- **Moodle OSS (+IOMAD)** — Moodle LMS (PHP, GPL, kostenlos, self-hosted). Mandantenfähigkeit nicht im
  Core; über **IOMAD** (GPL: Companies/Tenants, Per-Tenant-Branding, Seat-Lizenzen) bzw. `tool_mutenancy`.
- **Moodle Workplace** — kommerzielle **Enterprise-Edition** auf Moodle-Basis, **nur über Certified
  Partner** lizenzierbar/partner-gehostet. Native Multi-Tenancy, Per-Tenant-Branding/Login/IdP, Programs,
  **Certifications**, Dynamic Rules, Report-Builder, HRIS.
- **OpenOLAT Instanz-pro-Mandant (API-first)** — **je Mandant eine eigene OpenOLAT-Instanz** (eigene
  DB/Domain/Theme/Keycloak-Realm). EBZ-Inhalte liegen **zentral als SoR** (MDM/integration) und werden
  **per OpenOLAT-REST in jede Instanz verteilt** (API-first). Volle physische Isolation & White-Label,
  OSS/0-Lizenz; Preis = Instanz-Betrieb × Anzahl Mandanten.

**Bewertung:** ✅ stark/nativ · 🟡 teilweise / mit Aufwand bzw. Plugin/Eigenbau · ❌ schwach/fehlt

---

## §1 Mandantenfähigkeit (A)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 1 | Multi-Tenancy nativ | 🟡 Eigenbau (Organisations) | 🟡 nur via IOMAD (Core: nein) | ✅ nativ | ✅ physisch je Instanz |
| 2 | Tenant-Datenisolation | 🟡 org-skopiert | 🟡 logisch (Company) | ✅ virtuell partitioniert | ✅ stärkste (eigene DB/Instanz) |
| 3 | Org-Hierarchien/Struktur | ✅ Org-Baum/Typen | 🟡 Categories; IOMAD-Depts | ✅ Hierarchien+Positionen | ✅ Org-Baum je Instanz |
| 4 | Rollen-Scoping pro Mandant | ✅ Rollen pro Org | 🟡 IOMAD scoped | ✅ Tenant-Rollen | ✅ volle Trennung je Instanz |
| 5 | Mandanten-Admin (Self-Service) | 🟡 Admin org-begrenzt | 🟡 IOMAD Company-Manager | ✅ Tenant-Admin | ✅ eigener Admin je Instanz |
| 6 | Multi-Mandant-User/Switcher | ❌ eine Org-Zugehörigkeit | 🟡 IOMAD Switcher | ✅ User in n Tenants | ❌ getrennt (2 Accounts) |
| 7 | Eigene Tenant-URL/Subdomain | ❌ eine Domain | 🟡 IOMAD per-Tenant-URL | ✅ Custom-URL je Tenant | ✅ eigene Domain je Instanz |

## §2 Branding / White-Label (B)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 8 | Per-Tenant-Branding (Logo/Farben) | 🟡 CSS-Klasse pro Org-Typ | ✅ IOMAD: Logo/Theme/CSS | ✅ nativ je Tenant | ✅ volles Theme je Instanz |
| 9 | Per-Tenant-Login-Seite | ❌ nur via Keycloak-Theme | 🟡 IOMAD per-Tenant-Login | ✅ Custom-Login | ✅ eigener Realm/Login je Instanz |
| 10 | Theme-Tiefe (CSS/Templates) | 🟡 ein globales SCSS-Theme | ✅ Theme-System + IOMAD-CSS | ✅ Theme+Templates je Tenant | ✅ volles SCSS, keine Org-Klassen-Grenze |
| 11 | White-Label-Reife (B2B2C) | 🟡 über Storefront-Layer | 🟡 via IOMAD | ✅ dafür gebaut | ✅ volles White-Label inkl. OLAT-UI+Domain |

## §3 Identität & SSO (C)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 12 | OIDC/OAuth2-SSO | ✅ nativ (Keycloak) | ✅ OAuth2 nativ | ✅ nativ | ✅ nativ je Instanz |
| 13 | SAML-SSO | ✅ Shibboleth/SAML | 🟡 Plugin `auth_saml2` | ✅ tenant-aware | ✅ nativ je Instanz |
| 14 | Per-Tenant-IdP (Kunden-IdP) | 🟡 Keycloak-Brokering (Eigenbau) | 🟡 nicht tenant-nativ | ✅ IdP je Tenant | ✅ je Instanz direkt (kein Brokering nötig) |
| 15 | Keycloak-Fit (im Showcase) | ✅ live integriert | ✅ OIDC | ✅ OIDC | ✅ eigener Realm/Client je Instanz |

## §4 Content / SCORM / Authoring (D)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 16 | SCORM 1.2 | ✅ nativ | ✅ nativ | ✅ nativ | ✅ nativ |
| 17 | SCORM 2004 | ❌ nicht unterstützt | 🟡 teilweise | 🟡 teilweise | ❌ nicht unterstützt |
| 18 | xAPI / LRS | 🟡 extern LRS | ✅ internes LRS (Plugin) | ✅ internes LRS | 🟡 extern LRS (n Quellen) |
| 19 | H5P / interaktiv | 🟡 via Plugin/SCORM-Trick | ✅ H5P im Core | ✅ H5P im Core | 🟡 via Plugin/SCORM-Trick |
| 20 | Autorenwerkzeug | ✅ Kurseditor | ✅ stark | ✅ stark | ✅ Kurseditor je Instanz |

## §4b Content-Verteilung & -Pflege über Mandanten (D+, ergänzt 2026-06-24)

> Ergänzt nach der Rise-360-Content-Analyse
> ([../lms-anbindung-planung/Konzept-Rise360-Content-Verteilung.md](../lms-anbindung-planung/Konzept-Rise360-Content-Verteilung.md)).
> **Treiber:** viele Nuggets enthalten **Video** → Content-Gewicht und Verteil-/Storage-Kosten werden
> entscheidungsrelevant. Trennt „**einmal speichern, alle referenzieren**" (geteilte Tenancy) von „**in jede
> Instanz kopieren**" (Instanz-pro-Mandant). Die beiden Kriterien sind korreliert (beide folgen aus der
> Kopie-vs-Referenz-Wahl) → bei video-schweren Inhalten verdienen sie **hohes Gewicht** (vgl. §11/§12.1).

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 41 | Content einmal speichern, alle Mandanten referenzieren (Storage, v. a. Video) | ✅ ein Repo-Entry, alle Orgs referenzieren (Storage × 1) | ✅ IOMAD Shared Courses (Storage × 1) | ✅ zentrale Kursbibliothek/Programs (Storage × 1) | ❌ Kopie je Instanz (Storage × N; bei Video gravierend) |
| 42 | Update-Propagierung / Drift-Freiheit (ein Update → überall) | ✅ Update an einer Stelle, sofort für alle | ✅ Shared-Course-Update zentral | ✅ zentrale Bibliothek | ❌ Re-Import in n Instanzen, Versions-Drift |

## §5 API / Provisionierung / Automatisierung (E)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 21 | REST/Web-Services-Breite | ✅ 422 Endpunkte | ✅ umfangreiche WS | ✅ + Workplace-WS | ✅ gleiche REST-API je Instanz |
| 22 | Bulk-/Auto-Provisionierung | ✅ REST + Dispatcher | ✅ WS + Cohort-Sync | ✅ + Dynamic Rules | 🟡 zentral gegen n Endpunkte (API-first) |
| 23 | Dynamische Enrol-Regeln | 🟡 Curriculum (Eigenbau) | 🟡 Cohort-Sync/Plugins | ✅ Dynamic Rules | 🟡 Eigenbau, je Instanz |
| 24 | Seat-/Lizenzkontingent | ❌ nativ (Eigenbau geplant) | 🟡 IOMAD-Licensing | ✅ Lizenz/Seats nativ | 🟡 pro Instanz einfacher, aber Eigenbau |

## §6 Reporting / Nachweis / Compliance (F)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 25 | Completion/Weiterbildungsstunden | ✅ Assessment + `session_time` | ✅ Completion-Tracking | ✅ + Programs | ✅ je Instanz |
| 26 | Zertifikate | ✅ Cert-Modul | ✅ Custom-Cert-Plugin | ✅ nativ | ✅ Cert-Modul je Instanz |
| 27 | Zertifizierung/Recert (§34d/§34c) | 🟡 im Cert | 🟡 via Plugin | ✅ Certifications nativ | 🟡 im Cert |
| 28 | Report-Builder / Custom-Reports | 🟡 Reports/Archiv | ✅ Core-Report-Builder | ✅ erweitert | 🟡 Reports/Archiv je Instanz |
| 29 | Reporting-Export in HR/BI | 🟡 Adapter-Eigenbau | 🟡 Reports/Plugins | ✅ Scheduled-Export+HRIS | 🟡 Aggregation über n Instanzen nötig |

## §7 Vermarktung / Commerce (G)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 30 | Commerce-Entkopplung / Vendure-Fit | ✅ sauber (Vendure = SoR) | 🟡 eigenes Enrol/Payment od. Bridge | 🟡 HRIS-orientiert | ✅ Vendure-Channel je Mandant → Push je Instanz |
| 31 | Payment/Enrolment-on-payment nativ | 🟡 Booking/Access | ✅ Payment-Gateways | ✅ vorhanden | 🟡 Booking/Access |

## §8 Lizenz / Kosten / TCO (H)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 32 | Lizenzkosten / Modell | ✅ Apache-2.0, 0 € | ✅ GPL, 0 € | ❌ ~50–150 k €+/Jahr | ✅ Apache-2.0, 0 € |
| 33 | Anbieter-/Partner-Lock-in | ✅ keiner | ✅ keiner | ❌ nur Certified Partner | ✅ keiner |
| 34 | Gesamt-TCO | ✅ frei + moderater Betrieb | 🟡 frei + PHP + Plugin-Pflege | ❌ Lizenz + Partner-Hosting | ❌ skaliert mit Instanzzahl (n × Betrieb) |

## §9 Betrieb / Stack-Fit / Technologie (I)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 35 | Tech-Stack-Fit (Quarkus/JVM-Haus) | ✅ JVM, integriert | ❌ PHP (fremd) | ❌ PHP + Fremdbetrieb | ✅ JVM, gleicher Stack |
| 36 | Datenbank (Postgres-Fit) | ✅ Postgres empfohlen | 🟡 Postgres ok, MariaDB-zentriert | 🟡 dito, partner-gehostet | ✅ Postgres je Instanz |
| 37 | Betriebsaufwand / Skalierung | 🟡 ein Tomcat + Postgres | 🟡 PHP/Apache + Cache/Cron | ✅ Partner betreibt | ❌ n Tomcats/DBs/Upgrades (IaC Pflicht) |
| 38 | Hosting-Souveränität (self-host/DSGVO) | ✅ voll | ✅ self-host | 🟡 partner-gehostet | ✅ voll, je Kunde isolierbar |

## §10 Ökosystem / Strategie / Risiko (J)

| # | Kriterium | OpenOLAT shared (geplant) | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant (API-first) |
|--|--|--|--|--|--|
| 39 | Verbreitung / Bus-Faktor / DACH | 🟡 solide DACH | ✅ Marktführer DACH (~48 % DE) | ✅ Moodle-Ökosystem + Partner | 🟡 solide DACH |
| 40 | Migrationsaufwand / Sunk-Cost | ✅ bereits gebaut | ❌ Neuaufbau (PHP) | ❌ Neuaufbau + Partner | 🟡 nutzt L0–L3; Instanz-Orchestrierung neu |

---

## §10b Verbreitung & Enterprise-Multi-Tenant-Referenzen (Recherche 2026-06)

> **Datenlage-Vorbehalt:** Harte, getrennte Zahlen *für die Multi-Tenant-Nutzung* sind öffentlich kaum
> verfügbar (Workplace ist partner-gated, IOMAD/OpenOLAT führen keine MT-Zähler). Folgende Einordnung ist
> qualitativer Recherchestand, kein belastbares Marktdatum.

| Option | Verbreitung (gesamt) | Enterprise-Multi-Tenant-Praxis | Einordnung |
|---|---|---|---|
| **Moodle-Familie (Basis)** | **#1 LMS weltweit:** 148.000+ registrierte Sites, **500 Mio.+ Nutzer**, ~15 % Markt; DACH-Marktführer (~48 % DE). Marken: IBM, Allianz, Shell, Vodafone, NHS, Roche; 606 Orgs mit 10.001+ MA | — (gilt für die ganze Familie) | größtes Ökosystem/Talentpool/Partner-Netz |
| **Moodle Workplace** | Teil der Moodle-Familie, **kommerziell/partner-only** | ✅ **dafür gebaut**: native MT bei Konzernen/Multi-Division/Franchise; Referenzen über Certified Partner | stärkste **belegte** Enterprise-MT-Basis, Zahlen aber partner-gated |
| **Moodle OSS + IOMAD** | IOMAD = **verbreitetste *freie* MT-Lösung** für Moodle; Deployments mit **50+ Orgs aus einer Instanz** dokumentiert | 🟡 Corporate/Franchise real genutzt, aber weniger prominent belegt als Workplace; kleiner Maintainer-Kreis (Bus-Faktor) | bester freier MT-Kompromiss; Reife/Release-Lag prüfen |
| **OpenOLAT shared (Organisations-MT)** | stark in **DACH-Hochschule/Public** (frentix/UZH-Spin-off; Uni Innsbruck, CAU Kiel, HS Furtwangen; OLAT-Vorgänger UZH+ETH 50.000+ Nutzer) | 🟡 **Organisations als *Multi-Tenant*-Modell jung/dünn belegt** — große Installs sind eher *ein* großer Mandant je Instanz, nicht n Tenants in einer | solide Plattform, MT-in-einer-Instanz wenig referenziert |
| **OpenOLAT Instanz-pro-Mandant** | **Instanz-pro-Org ist in OpenOLAT der Normalfall** (jede Hochschule betreibt ihre eigene Instanz) | 🟡 als *Muster* allgegenwärtig, aber „**ein Anbieter orchestriert N Instanzen als Managed-MT-Angebot**" bespoke/kaum als Produkt belegt | bewährt als Einzelinstanz, MT-Orchestrierung = Eigenleistung |

**Lesart (ehrlich, auch gegen die OpenOLAT-Präferenz):** Nach *belegter* Enterprise-Multi-Tenant-Praxis führt
**Moodle Workplace** klar, gefolgt von **IOMAD** (freie MT mit echten Mehr-Org-Deployments). **Beide
OpenOLAT-Wege** sind als Plattform solide, aber **im Multi-Tenant-Betrieb am dünnsten referenziert** —
OpenOLAT läuft überwiegend *ein-mandantig je Instanz*. Das ist ein **Reife-/Risiko-Argument gegen die
OpenOLAT-MT-Eigenbauten** (shared wie instanz-orchestriert) und relativiert deren Punktevorsprung zusätzlich.

| # | Kriterium | OpenOLAT shared | Moodle OSS (+IOMAD) | Moodle Workplace | OpenOLAT Instanz/Mandant |
|--|--|--|--|--|--|
| 43 | Belegte Enterprise-Multi-Tenant-Referenzen | 🟡 dünn (Organisations-MT jung) | 🟡 IOMAD: 50+ Orgs/Instanz dokumentiert | ✅ dafür gebaut, Konzern-Referenzen (partner) | 🟡 Muster verbreitet, Managed-MT bespoke |

---

## §11 Auswertung (indikativ, ungewichtet: ✅=2 · 🟡=1 · ❌=0, max. 86)

| Option | Punkte (40 Krit.) | **+ §4b/§10b** | **Gesamt /86** | Profil |
|---|:--:|:--:|:--:|---|
| **Moodle Workplace** *(geparkt — Kosten)* | ≈ 66 | +6 | **≈ 72** | Höchster Score, aber **aus Lizenzkosten (~50–150 k €+/Jahr, Partner-only) geparkt** (§12) → fällt aus der engeren Wahl. Stärkste Feature-Abdeckung + beste belegte Enterprise-MT-Praxis; Schwach: Kosten, Lock-in, Stack-Fit, Souveränität. |
| **OpenOLAT Instanz/Mandant (API-first)** | ≈ 61 | **+1** | **≈ 62** | **Volle Isolation & White-Label**, **OSS/0 €/souverän/JVM-Stack**, nutzt Bestehendes. Schwach: **Betriebsaufwand n×**, TCO **und Content-Storage** skalieren mit Mandantenzahl, **Managed-MT-Orchestrierung kaum referenziert**. |
| **Moodle OSS (+IOMAD)** | ≈ 56 | +5 | **≈ 61** | Bester **Kostenausgleich**: 0 € + native Mandanten via IOMAD + Shared-Courses + **verbreitetste freie MT** + größtes DACH-Ökosystem. Schwach: **PHP-Stack-fremd**, IOMAD-Bus-Faktor. |
| **OpenOLAT shared (wie geplant)** | ≈ 54 | +5 | **≈ 59** | Bester **Stack-Fit + bereits gebaut + 0 €**, geringster Betrieb, **Content einmal halten**. Schwach: Mandanten/Branding/Reporting **Eigenbau**, **Organisations-MT jung/dünn belegt**. |

> Die Zusatzkriterien (§4b Content-einmal-halten/Drift + §10b belegte Enterprise-MT) geben **+5/+6 an die
> geteilten/Moodle-Optionen** und **+1 an Instanz-pro-Mandant** → dessen ungewichteter Vorsprung schrumpft von
> 5–7 auf **1–2 Punkte**. **Zwei gegenläufige Befunde:** (a) **Content-Gewicht (Video)** drückt Richtung
> *geteilter* Tenancy; (b) **Adoptions-Evidenz** drückt innerhalb der geteilten Optionen Richtung *Moodle*
> (Workplace/IOMAD), weil OpenOLATs MT am wenigsten battle-tested ist. Entscheidend bleibt die **Gewichtung**
> (siehe §12.1).
>
> **Hinweis (2026-06-25):** Der Spitzenreiter **Moodle Workplace ist aus Kostengründen geparkt** (§12) → die
> **engere Wahl ist der Zweikampf OpenOLAT shared ↔ Moodle OSS+IOMAD**; Instanz-pro-Mandant nur noch Nische
> (Content-Gewicht). Zugespitzte Empfehlung: **§12.2**.

---

## §12 Empfehlung

> **⚠️ Aktualisierte Entscheidungsgrundlage (2026-06-25): Moodle Workplace ist aus Kostengründen geparkt.**
> Die ~50–150 k €+/Jahr Lizenz (Partner-only) sind aktuell **kein gangbarer Weg**. Damit fällt die einzige
> „fertig kaufen, vendor-bewiesene" Option weg; das Feld besteht nur noch aus **OSS-/0 €-Wegen**, bei denen
> die B2B-Mandanten-Schicht so oder so selbst integriert wird. **Die maßgebliche, zugespitzte Empfehlung
> steht in §12.2**; die ursprüngliche Drei-Wege-Logik (§12 unten) bleibt als Historie/Begründung stehen.

**Timing-Befund:** Der Mandanten-Layer ist **geplant, aber noch nicht gebaut** (nur WBT-Basis L0–L3 steht)
→ **jetzt** ist der günstigste Entscheidungszeitpunkt.

**Schlüsselerkenntnis:** Es gibt keinen Einzelsieger — die beste Wahl hängt am **Mandanten-Typ** und an der
**erwarteten Mandantenzahl**:

- **Typ E — Enterprise-Flatrate (wenige, große Kunden, eigene Marke/Domain/IdP, Festpreis):**
  → **OpenOLAT Instanz-pro-Mandant (API-first)**. Liefert **volles White-Label inkl. eigener Domain, eigenem
  Login und direkt konfiguriertem Kunden-IdP** — auf Workplace-Niveau, aber **OSS, 0 € Lizenz, voll
  souverän, im JVM-Stack** und unter **Wiederverwendung** der vorhandenen OpenOLAT-Basis. Der Betriebsaufwand
  (n Instanzen) ist bei wenigen Enterprise-Kunden vertretbar und per IaC automatisierbar. Inhalte bleiben
  zentral SoR und werden per REST verteilt.
- **Typ R — Reseller/B2B2C (viele kleine Mandanten):** Instanz-pro-Mandant skaliert hier **nicht**
  (Instanz-Sprawl). → **geteilte Multi-Tenancy**: **Moodle OSS + IOMAD** (erfüllt den GF-Wunsch, OSS/0 €,
  native Per-Tenant-Branding/Seat-Lizenzen, größtes DACH-Ökosystem) **oder** OpenOLAT shared (Eigenbau).
- **Wenn „eine Plattform für alles, nativ, mit Hersteller-Support" und Budget vorhanden:**
  → **Moodle Workplace** — geringster Bauaufwand, deckt E **und** R nativ ab; Preis: Lizenz, Partner-Lock-in,
  PHP-Fremdbetrieb, geringere Souveränität (widerspricht der OSS-/Kein-Lock-in-Linie des Showcase).

### §12.1 Re-Bewertung: Content-Gewicht (Video) als dritter Hebel

Die Empfehlung hing bisher an zwei Hebeln (**Mandanten-Typ**, **Mandantenzahl**). Die Rise-360-Analyse fügt
einen **dritten** hinzu: **Content-Gewicht**. Bei **video-schweren Nuggets** kostet „Instanz-pro-Mandant"
nicht nur n× Betrieb, sondern n× **Storage + laufende Medien-Verteilung + Drift-Risiko** (Kriterien 41/42).
Das erodiert genau den Punktevorsprung, mit dem Instanz-pro-Mandant bei Isolation/Branding führt:

- **Ungewichtet** rücken die geteilten Optionen auf (Instanz 61 vs. shared 58/60 — Abstand von 5–7 auf 1–3
  Punkte geschrumpft).
- **Gewichtet** man Content-Verteilung hoch (was Video rechtfertigt), kippt es zugunsten **geteilter
  Tenancy** — also **„eine OpenOLAT-Instanz mit eingeschränkter Mandanten-CI"** (Org-je-Mandant + CSS-Klasse
  pro Org-Typ + Keycloak-Login-Theme), exakt die tragende Architektur aus
  [Plan-Mandanten-Vermarktung §3](Plan-Mandanten-Vermarktung-OpenOLAT.md). Damit löst sich die scheinbare
  Spannung zwischen den beiden Planungsdokumenten **zugunsten der Single-Instance-Architektur**, sobald
  Content-Gewicht mitzählt.

**Präzisierte Empfehlung (drei Hebel):**
- **Wenige Enterprise-Kunden · eigene Domain/Marke zwingend · eher leichte Inhalte:** Instanz-pro-Mandant
  bleibt vertretbar (Isolation/White-Label gewinnt; Storage × N verschmerzbar).
- **Video-schwere Inhalte und/oder mehr als eine Handvoll Mandanten:** **geteilte Instanz** — OpenOLAT shared
  mit eingeschränkter CI (Stack-Fit, bereits gebaut, 0 €) **oder** Moodle OSS+IOMAD (GF-Wunsch). Content
  einmal halten, alle referenzieren.
- **Brücke „beides":** Will man Instanz-pro-Mandant aus **Branding**-Gründen, aber Video **nicht n× kopieren**
  → **„host-once-per-LTI"** (Rise-Doc, §Alternativen): Inhalt zentral hosten, je Instanz per LTI referenzieren.

**Vierter Hebel — Adoptions-Evidenz (§10b):** Die Verbreitungs-Recherche zieht *innerhalb* der geteilten
Optionen in eine andere Richtung: **Moodle Workplace/IOMAD** haben die **belegtere Enterprise-Multi-Tenant-
Praxis**, während OpenOLATs Multi-Tenancy (shared wie instanz-orchestriert) **am dünnsten referenziert** ist
(OpenOLAT läuft meist ein-mandantig je Instanz). Daraus folgt eine ehrliche Spannung:
- **Content-Gewicht** sagt „geteilte Tenancy" (gut für *OpenOLAT shared*).
- **Adoptions-Evidenz** sagt „wenn schon geteilt, dann eher *Moodle* (Workplace/IOMAD)" — reifer für MT.
- **OpenOLAT shared** gewinnt nur, wenn **Stack-Fit/OSS/Souveränität/bereits-gebaut** hoch und das
  **MT-Reife-Risiko** akzeptiert wird (Eigenbau, wenig Referenzen).

**Fazit zur Ausgangsfrage:** Ja — video-schwere Nuggets sprechen für **eine geteilte Instanz mit
eingeschränkter Mandanten-CI** statt Instanz-pro-Mandant; das bestätigt die §3-Setzung des Plans, die
§12-Empfehlung (Instanz-pro-Mandant für Typ E) gilt nur noch für den **branding-kritischen, content-leichten**
Sonderfall. **Ob diese geteilte Instanz OpenOLAT (Stack-Fit/souverän, aber MT-Eigenbau & wenig referenziert)
oder Moodle+IOMAD (reifere, belegtere MT, aber PHP-fremd) ist, bleibt der offene Build-vs-Buy-Punkt** — genau
das validiert der Doppel-PoC unten.

### §12.2 Zugespitzte Empfehlung — ohne Moodle Workplace (maßgeblich, 2026-06-25)

Mit geparktem Workplace bleibt **kein** vendor-bewiesenes B2B-MT-Produkt im Budget; alle Wege sind OSS und
verlangen Eigenintegration der Mandanten-Schicht. Die Frage verschiebt sich von „kaufen vs. bauen" zu
**„welchen Stack baue ich — und wie viel davon schenkt mir das System".**

**Das Feld schrumpft real auf zwei.** **OpenOLAT Instanz-pro-Mandant** scheidet durch das Content-Gewicht
(Video × N Instanzen = Storage × N + Drift) aus — Restnische: wenige Enterprise-Kunden mit eigener Domain
**und** content-leichten Inhalten. Bleibt der **Zweikampf OpenOLAT shared ↔ Moodle OSS + IOMAD**, ungewichtet
fast gleichauf (59 ↔ 61); zwei Hebel entscheiden:

| Hebel | OpenOLAT shared | Moodle OSS + IOMAD |
|---|---|---|
| Stack | ✅ JVM/Postgres = euer Haus | ❌ **PHP** = Fremdkörper im Quarkus-Haus |
| Mandanten-Schicht | ❌ **Eigenbau** (Org + Seat + Branding) | ✅ **nativ via IOMAD** (Companies/Branding/Seat) |
| Content share-once (Video) | ✅ ein Repo-Entry, n Orgs | ✅ Shared Courses |
| Bereits gebaut | ✅ L0–L3 stehen | ❌ Neuaufbau |
| MT-Reife belegt | 🟡 Organisations-MT jung | 🟡 IOMAD real, aber **Fork-Lag + Bus-Faktor** |

**Der eigentliche Trade-off:** *IOMAD schenkt genau die Mandanten-Schicht, die OpenOLAT-shared zum Eigenbau
macht — um den Preis eines PHP-Stacks (zweite Runtime/Patch-/Security-Kultur) und des IOMAD-Fork-Risikos.*
Umgekehrt behält **OpenOLAT-shared Stack-Kohärenz + die investierte Arbeit + die Hoheit über die B2B-Logik**.

**Empfehlung: OpenOLAT shared als Primärwette, IOMAD als Hedge.** Für ein konsequentes Quarkus/JVM-Haus mit
Souveränitäts-Linie ist ein PHP-System **kein „kostenloser" Gewinn** — die bei Workplace gesparten Lizenz-
kosten kämen bei Moodle teils als **laufende Betriebs-/Skill-Steuer** für einen Fremdstack zurück; das wiegt
schwerer als der einmalige Tenant-Layer-Bau bei OpenOLAT. **Wahrnehmung** spielt fair: IOMAD rebrandet je
Mandant (Endkunde sieht *seine* Marke), aber intern bleibt's „wir fahren Moodle"; OpenOLAT ist im B2B ein
**weißer Fleck** — für ein *premium* White-Label eher Vorteil als Makel.

**Das eine ernste Risiko ist benannt und früh testbar:** *Wie schwer ist der Mandanten-Schicht-Eigenbau bei
OpenOLAT wirklich?* → erster PoC-Sprint. Fällt er leicht → OpenOLAT klar. Wird er zäh → **IOMAD ziehen** und
den PHP-Preis bewusst zahlen.

**Konkreter Vorschlag (build-vs-buy mit Evidenz):** **fokussierter PoC auf dem Primärweg, IOMAD als
Vergleichs-Spike** —
1) **OpenOLAT shared** (eine Instanz, eingeschränkte Mandanten-CI): an **einem Demo-Mandanten** die
   Mandanten-Schicht bauen — **Organisation + Seat-Cap + Per-Tenant-CSS-Klasse + Keycloak-Login-Theme** +
   Content einmal halten/n Orgs referenzieren + Weiterbildungsnachweis. **Misst den Eigenbau-Aufwand** (das
   Kernrisiko).
2) **Moodle OSS + IOMAD** als **schlanker Vergleichs-Spike** für **zwei Reseller-Mandanten**
   (Branding/Seat-Lizenz/Report) — nur so tief, dass der **native MT-Komfort gegen den PHP-Betriebspreis**
   ehrlich abwägbar wird.

So wird **am realen Aufwand** entschieden, nicht am Markenbild — und der GF-Moodle-Wunsch bekommt seinen
fairen Vergleichslauf, ohne dass die teure Workplace-Variante nötig ist.

---

## §13 Annahmen & offene Punkte (vor Entscheidung prüfen)
- **„Moodle Enterprise" = Moodle Workplace** (Partner-lizenziert) — Definition mit GF bestätigen.
- **Erwartete Mandantenzahl** (E vs. R, wenige vs. viele) — der wichtigste Hebel für Instanz- vs.
  Shared-Architektur; **Break-even** Instanz-Betrieb × n gegen geteilte Tenancy beziffern.
- **IaC für Instanz-pro-Mandant**: automatisiertes Bereitstellen/Patchen/Backups je Instanz; Content-
  Versionierung + idempotente REST-Verteilung an n Instanzen; **Reporting-Aggregation über n Instanzen**.
- **Content-Storage/Egress beziffern (Video)**: erwartete Nugget-Größe × Mandantenzahl bei Instanz-pro-
  Mandant (Storage × N + Verteil-Egress je Update) gegen geteilte Tenancy (Storage × 1). Treibt den
  Break-even mit; ggf. „host-once-per-LTI" (Rise-Doc) als Mittelweg prüfen.
- **IOMAD-Reife/Release-Lag** zur aktuellen Moodle-Version + Wartungszusage (Bus-Faktor).
- **Workplace-Preis** nur über Certified Partner (kein Listenpreis) → Angebot; EU-Partner + AVV/Residenz.
- **PHP-Betrieb im JVM-Haus**: wer betreibt/patcht Moodle (Skill/Betrieb)?
- **SCORM-Version der Bestandskurse** (1.2 vs. 2004) — bei 2004 hat Moodle Vorteil ggü. beiden OpenOLAT-Wegen.
- **Vendure-Rolle**: bleibt Commerce-SoR (Entkopplung) oder übernimmt Moodle Enrol/Payment?
- **Rechtliche Stundenzählung** (§34d/§34c/§34i) plattformunabhängig final prüfen.

## §14 Quellen (Recherche 2026-06)
- Moodle Workplace: [Multi-tenancy](https://docs.moodle.org/502/en/Multi-tenancy) ·
  [Branding/Tenants](https://moodle.com/news/moodle-workplace-4-multi-tenancy/) ·
  [Per-Tenant-Auth](https://docs.moodle.org/405/en/Multi-tenancy_authentication) ·
  [Workplace vs LMS / Lizenz](https://support.moodle.com/support/solutions/articles/80001074804-difference-between-moodle-workplace-and-moodle-lms) ·
  [Pricing](https://raccoongang.com/blog/moodle-pricing/)
- IOMAD: [Multi-Tenancy](https://www.iomad.org/multi-tenancy/) · [`tool_mutenancy`](https://moodle.org/plugins/tool_mutenancy)
- Moodle Core: [PostgreSQL](https://docs.moodle.org/502/en/PostgreSQL) ·
  [xAPI](https://moodledev.io/docs/4.5/apis/subsystems/xapi) · [Logstore xAPI](https://moodle.org/plugins/logstore_xapi) ·
  [SCORM FAQ](https://docs.moodle.org/400/en/SCORM_FAQ) · [OAuth2 API](https://docs.moodle.org/dev/OAuth_2_API) ·
  [Enrolment on payment](https://docs.moodle.org/501/en/Enrolment_on_payment)
- Markt/DACH: [LMS-Marktanteil DE 2024 (Statista)](https://www.statista.com/statistics/1415031/market-share/) ·
  [Moodle Europe](https://www.elearnmagazine.com/marketplace/europe-report-moodle-market-share-leader-almost-everywhere/)
- Verbreitung (§10b, Recherche 2026-06):
  [Moodle 500 Mio. Nutzer](https://moodle.com/news/500-million-users-on-registered-moodle-sites/) ·
  [stats.moodle.org](https://stats.moodle.org/) ·
  [Moodle-Marktanteil (W3Techs)](https://w3techs.com/technologies/details/cm-moodle) ·
  [Enterprise-Marken/Workplace-MT](https://www.learningplatforms.net/brands-using-moodle/) ·
  [IOMAD Multi-Tenancy](https://www.iomad.org/multi-tenancy/) ·
  [IOMAD 50+ Orgs/Instanz](https://infranext.co/moodle-multi-tenancy-iomad-configuration/) ·
  [OpenOLAT (Wikipedia: frentix/UZH, Hochschul-Installs)](https://en.wikipedia.org/wiki/OpenOLAT)
- OpenOLAT: [REST API](https://docs.openolat.org/manual_admin/administration/REST_API/) ·
  [Organisations](https://docs.openolat.org/manual_admin/administration/Modules_Organisations/) ·
  [Installation/Multi-Instance](https://docs.openolat.org/manual_admin/installation/installGuide/)
