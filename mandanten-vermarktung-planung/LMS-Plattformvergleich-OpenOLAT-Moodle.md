# LMS-Plattformvergleich — OpenOLAT (shared & Instanz/Mandant) vs. Moodle (OSS & Workplace)

> **Anlass:** Die Geschäftsführung präferiert **Moodle**. Diese Matrix vergleicht entlang **40 Kriterien**
> vier Optionen für die **mandantenfähige Vermarktung von EBZ-eLearning** und spricht eine Empfehlung aus.
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

## §11 Auswertung (indikativ, ungewichtet: ✅=2 · 🟡=1 · ❌=0, max. 80)

| Option | Punkte | Profil |
|---|:--:|---|
| **Moodle Workplace** | **≈ 66** | Stärkste **Feature-Abdeckung** nativ (Mandanten, Branding, Per-Tenant-SSO, Certifications, Reporting). Schwach: **Kosten, Lock-in, Stack-Fit, Souveränität**. |
| **OpenOLAT Instanz/Mandant (API-first)** | **≈ 61** | **Volle Isolation & White-Label** (Domain/Theme/Login/IdP je Instanz), **OSS/0 €/souverän/JVM-Stack**, nutzt Bestehendes. Schwach: **Betriebsaufwand n×**, kein Multi-Tenant-User, TCO skaliert mit Mandantenzahl. |
| **Moodle OSS (+IOMAD)** | **≈ 56** | Bester **Kostenausgleich**: 0 € + native Mandanten via IOMAD + größtes DACH-Ökosystem. Schwach: **PHP-Stack-fremd**, IOMAD-Bus-Faktor. |
| **OpenOLAT shared (wie geplant)** | **≈ 54** | Bester **Stack-Fit + bereits gebaut + 0 €**, geringster Betrieb. Mandanten/Branding/Reporting aber **Eigenbau**. |

> Die Summen liegen eng beieinander; entscheidend ist die **Gewichtung**. Auffällig: **OpenOLAT
> Instanz/Mandant erreicht fast Workplace-Niveau bei Isolation/Branding/SSO — ohne Lizenz, Lock-in und
> Stack-Bruch**, erkauft das aber mit linear steigendem Betriebsaufwand (n Instanzen).

---

## §12 Empfehlung

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

**Konkreter Vorschlag (build-vs-buy mit Evidenz):** zeitlich begrenzter **Doppel-PoC** —
1) **OpenOLAT Instanz-pro-Mandant** für **einen Enterprise-Demokunden** (eigene Domain/Theme/Realm + IdP,
   Inhalte per REST verteilt, Weiterbildungsnachweis), und
2) **Moodle OSS + IOMAD** für **zwei Reseller-Demomandanten** (Branding/Seat-Lizenz/Report).
So werden GF-Präferenz (Moodle) und der souveräne OpenOLAT-Weg **gegeneinander an realen Fällen** validiert,
bevor der teure Mandanten-Layer überhaupt gebaut wird.

---

## §13 Annahmen & offene Punkte (vor Entscheidung prüfen)
- **„Moodle Enterprise" = Moodle Workplace** (Partner-lizenziert) — Definition mit GF bestätigen.
- **Erwartete Mandantenzahl** (E vs. R, wenige vs. viele) — der wichtigste Hebel für Instanz- vs.
  Shared-Architektur; **Break-even** Instanz-Betrieb × n gegen geteilte Tenancy beziffern.
- **IaC für Instanz-pro-Mandant**: automatisiertes Bereitstellen/Patchen/Backups je Instanz; Content-
  Versionierung + idempotente REST-Verteilung an n Instanzen; **Reporting-Aggregation über n Instanzen**.
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
- OpenOLAT: [REST API](https://docs.openolat.org/manual_admin/administration/REST_API/) ·
  [Organisations](https://docs.openolat.org/manual_admin/administration/Modules_Organisations/) ·
  [Installation/Multi-Instance](https://docs.openolat.org/manual_admin/installation/installGuide/)
