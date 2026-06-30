# Showcase „Shop-Anbindung" — Recherche Headless-Shop-Systeme

> Eigenständiger **Showcase-Strang** zum Best-of-Breed-Ansatz (vgl. [Capability-Map](../enterprise-stack-planung/Zielarchitektur-Capability-Map.md), Fähigkeit #7 „Shop"). Ziel: den Shop-Baustein praktisch evaluieren, statt ihn nur auf dem Papier zu planen.
> **Stand Juni 2026, Vorauswahl-Niveau** auf Basis öffentlicher Doku/Vergleichsportale — **nicht getestet**. K.-o.-Kriterien im PoC verifizieren.

---

## 1. Anforderungs-Kurzprofil (Showcase)

**Architektur-Setzung:** Headless-Shop-**Backend** (nicht selbst bauen) + **selbst entwickeltes Vue-Frontend**. Frontend selbst gebaut, weil sich die Prozesse je **Warengruppe** stark unterscheiden und **Zusatzdaten** (Berufsschul-/Studien-/Seminaranmeldung) erfasst werden müssen, die ein Standard-Shop nicht kennt.

**Betrieb:** Optimal **on-premise / self-host**, **Docker**, **PostgreSQL**. Open Source oder kostengünstig.

**Auswahl-Wunschkriterien:** gut dokumentiert · kostengünstig/Lizenz · **Vue-Unterstützung** (fertige Komponenten/SDK/Composables) · saubere, typisierte API.

**Sieben Warengruppen, die das Backend tragen muss:**

| # | Warengruppe | Commerce-Charakter | Knackpunkt fürs Shop-System |
|---|---|---|---|
| W1 | Physische Ware (Versand) | klassischer Warenkorb + Fulfillment | Standard — jedes System kann das |
| W2 | Digitalprodukt / Download | digitales Gut, kein Versand | Entitlement/Download-Auslieferung |
| W3 | Tickets für Tagungen | Event/Ticketing (Kontingent, Termin) | Bestand je Termin, ggf. Sitzplatz |
| W4 | Tages-/Wochenseminar-Anmeldung | Event-Buchung + **Teilnehmerdaten** | Zusatzfelder, Teilnehmer ≠ Besteller |
| W5 | Abo wiederkehrender Veranstaltungen | **Subscription** | wiederkehrende Abrechnung |
| W6 | Berufsschul-Anmeldung | **Halbjahresrechnung** | wiederkehrende Rechnung (Intervall ½ Jahr) |
| W7 | Studiengang | **Monatsrate über 3 Jahre** | langlaufender Ratenplan / Subscription mit Enddatum |

> **Zentrale Erkenntnis:** W1–W4 sind reiner Commerce-Standard. **Der wahre Differenzierer ist W5–W7 — wiederkehrende/ratierliche Abrechnung.** Kein Standard-Shop kennt „Berufsschul-Halbjahresrechnung" oder „36 Monatsraten" als Produkt; das ist **Subscription-/Recurring-Billing**. Die Auswahl entscheidet sich daher primär an der **Recurring-Fähigkeit**, nicht am Katalog-/Warenkorb-Standard. W4/W6/W7 brauchen außerdem **Custom Fields** (Zusatzdaten) — zweites Auswahlkriterium.

---

## 2. Kandidaten (Open Source / self-host, Docker + Postgres)

Vier ernsthafte Kandidaten erfüllen „Headless + self-host + Postgres-fähig + dokumentiert":

| Kriterium | **Vendure** | **Medusa** | **Saleor** | **Sylius** |
|---|---|---|---|---|
| Stack | NestJS/TypeScript + GraphQL | Node/TypeScript + REST | Python/Django + GraphQL | PHP/Symfony + API Platform |
| **PostgreSQL** | ✓ nativ | ✓ nativ | ✓ nativ | ◑ läuft, aber MySQL-first |
| **Docker / self-host** | ✓ | ✓ | ✓ (schwer: + Redis + Celery → oft K8s) | ✓ (LAMP-Stack) |
| **API für Vue** | GraphQL Shop-API, **stark typisiert** (TS-Codegen) | REST + **JS-SDK** (framework-agnostisch) | GraphQL-only | REST **+** GraphQL (API Platform) |
| **Vue-Unterstützung konkret** | **offizielle Vue-Storefront-Integration** (Composables auf Shop-API) | Vue-Storefront-UI-Tutorial; SDK ideal für Vue | keine Vue-spezifische | generisch (JS-Storefront) |
| **Custom Fields / Zusatzdaten (W4/W6/W7)** | ✓✓ **First-Class Custom Fields** auf allen Entitäten | ✓ Module/Custom-Models (v2) | ◑ Metadata-Felder | ◑ über Symfony-Erweiterung |
| **Subscription / Recurring (W5–W7)** | ✓ **Pinelab Stripe-Subscription-Plugin** mit **eigener `SubscriptionStrategy`** | ◑ kein Core; **Reorder** (OSS) o. Custom/Stripe | ✗ nicht nativ | ◑ Plugin/Custom, schwächer |
| **Digital/Download (W2)** | ✓ (Plugin/Custom) | ✓ digitale Varianten ohne Versand | ✓ | ✓ |
| Lizenz | **GPLv3** (Core) + kommerzielle Optionen | **MIT** (permissivst) | BSD-3 (Cloud-fokussiert) | **MIT** |
| Managed-Cloud-Option | nein (rein self-host) | ja (ab ~29 $/M) — für uns irrelevant | ja (Cloud-getrieben) | nein |
| Betriebsaufwand | mittel | mittel | **hoch** (Redis/Celery/K8s) | mittel (PHP-Stack) |
| EU/DSGVO self-host | ✓ (eigenes Hosting) | ✓ | ✓ | ✓ |

---

## 3. Tiefenblick auf die drei entscheidenden Punkte

### 3.1 Recurring/Subscription (W5–W7) — das eigentliche K.-o.-Kriterium
- **Vendure:** Das **Pinelab Stripe-Subscription-Plugin** erlaubt eine **eigene `SubscriptionStrategy`** — d. h. Abrechnungsintervall, Startzeitpunkt, Laufzeit und Anzahl der Raten sind frei in Code definierbar. Damit lassen sich **W6 (Halbjahresintervall)** und **W7 (36 Monatsraten, festes Enddatum)** als unterschiedliche Strategien direkt abbilden. Bestes Profil der vier.
- **Medusa:** Kein Core-Subscription; **Reorder** (OSS) deckt Renewals, Dunning (Zahlungsausfall), Kündigung/Retention ab — oder Eigenbau im Backend (volle Kontrolle, mehr Aufwand). Tragfähig, aber W6/W7 wären Eigenentwicklung.
- **Saleor / Sylius:** keine überzeugende native Recurring-Story → für diesen Showcase nachrangig.

> **Wichtige Abgrenzung:** „Subscription über Stripe" heißt, die **Abrechnungslogik liegt teils bei Stripe**. Für rein interne Halbjahres-/Ratenrechnungen (ohne Kartenzahlung, z. B. SEPA-Lastschrift/Rechnung über DATEV) kann eine **eigene SubscriptionStrategy ohne Stripe-Capture** nötig sein — oder die Recurring-Logik wandert in eine **dedizierte Billing-Engine** (vgl. Kill Bill/Billwerk in der [Vergleichsmatrix](../enterprise-stack-planung/Anbieter-Vergleichsmatrix.md)). Das ist die zentrale Designfrage des Showcase: **macht der Shop das Recurring, oder nur die Bestellung — und ein Billing-Layer den Rest?**

### 3.2 Vue-Anbindung
- Da das **Frontend ohnehin komplett selbst** in Vue gebaut wird (unterschiedliche Prozesse je Warengruppe), zählt weniger „fertige Shop-UI" als eine **saubere, typisierte API + SDK**:
  - **Vendure:** GraphQL-Shop-API mit TypeScript-Codegen → im Vue-Projekt End-to-End-Typen; zusätzlich **offizielle Vue-Storefront-Composables** als Starthilfe.
  - **Medusa:** offizielles **JS-SDK** (`@medusajs/js-sdk`), framework-agnostisch → in Vue direkt nutzbar; Vue-Storefront-UI-Komponenten als optionaler Baukasten.
- **Fazit Vue:** Vendure (Typisierung) und Medusa (SDK) sind beide stark; „fertige Vue-Komponenten" sind im Eigenbau-Frontend ein **Bonus, kein Muss** — die API-Qualität ist wichtiger.

### 3.3 Zusatzdaten erfassen (W4/W6/W7)
- **Vendure Custom Fields** sind First-Class: pro Bestellung/Position/Kunde frei definierbare Felder (Schüler-/Studierendendaten, Ausbildungsbetrieb, Seminarteilnehmer) — **ohne Core-Modifikation** (deckt sich mit Leitplanke L4 „konfigurieren statt customizen").
- Medusa v2 löst das über **eigene Module/Models** — flexibel, aber mehr Eigenbau.

---

## 4. Vorläufige Empfehlung für den Showcase

**Primärkandidat: Vendure.**
- Trifft alle Hart-Kriterien (TypeScript, **Postgres nativ**, Docker, EU-self-host).
- **Einziger mit einer fertigen, anpassbaren Recurring-Mechanik** (`SubscriptionStrategy`) → adressiert W5–W7, den eigentlichen Differenzierer, ohne von null zu bauen.
- **First-Class Custom Fields** für W4/W6/W7 (Zusatzdaten) ohne Core-Bruch.
- **Typisierte GraphQL-API + offizielle Vue-Integration** → bester Fit fürs Vue-Eigenbau-Frontend.
- Lizenz GPLv3 ist für **self-host/internen Betrieb unkritisch**.

**Starke Alternative: Medusa.**
- **MIT-Lizenz** (permissivst), modulare Architektur, exzellentes JS-SDK für Vue.
- Schwächer nur bei W5–W7: Recurring über **Reorder**/Eigenbau statt fertiger, frei parametrierbarer Strategy.
- Wählen, wenn **MIT-Lizenz** strategisch gewünscht ist oder das Team Node/REST gegenüber GraphQL bevorzugt.

**Nachrangig:** **Saleor** (mächtig, aber schwergewichtiger Betrieb Redis/Celery/K8s + kein natives Recurring → Overkill für den Showcase), **Sylius** (solide EU/PHP-Option, aber Postgres zweitrangig und Recurring schwächer).

---

## 5. Offene Punkte für den Realisierungsplan (nächster Schritt)

1. **Recurring-Hoheit:** Shop-Subscription (Vendure-Strategy) **vs.** dedizierte Billing-Engine? Und Zahlweg: Stripe-Karte **vs.** SEPA/Rechnung über DATEV/E-Rechnung? → bestimmt die Architektur des Showcase.
2. **Scope des Showcase:** alle 7 Warengruppen oder ein repräsentativer Schnitt (z. B. W1 physisch + W4 Seminar + W7 Studienrate) als „1 von jeder Klasse"?
3. **Datenmodell-Brücke:** wie hängt der Shop-Kunde am künftigen Stammdaten-Kern (vgl. Connector **S13** in der [Schnittstellenliste](../enterprise-stack-planung/Soll-Bebauungsplan-und-Schnittstellen.md))? Im Showcase ggf. zunächst lokal in der Shop-DB.
4. **Vue-Setup:** Nuxt vs. reines Vue-3-SPA; GraphQL-Codegen (Vendure) vs. REST-SDK (Medusa).
5. **Payment/Tax (DE):** USt, Rechnungs-/E-Rechnungs-Anforderung, Zahlungsanbieter (Stripe/Mollie/SEPA).

→ Sobald **Punkt 1 + 2** entschieden sind, erstelle ich den **konkreten Realisierungsplan** (Docker-Compose-Topologie, Backend-Setup, Subscription-Strategy-Skizze, Vue-Anbindung, Showcase-Datenfälle).

---

## Quellen
- Vergleich OSS-Headless 2026: [PkgPulse — Medusa vs Saleor vs Vendure](https://www.pkgpulse.com/blog/medusa-vs-saleor-vs-vendure-headless-ecommerce-2026) · [Linearloop](https://www.linearloop.io/blog/medusa-js-vs-saleor-vs-vendure) · [YNS — Best Headless Platforms](https://yournextstore.com/blog/best-headless-commerce-platforms)
- Medusa vs Vendure (Subscriptions/Self-host): [PowerGate](https://powergatesoftware.com/tech-blog/medusajs-vs-vendure/) · [Build with Matija](https://www.buildwithmatija.com/blog/medusa-vs-vendure-open-source-commerce) · [Medusa Pricing 2026](https://www.buildwithmatija.com/blog/medusajs-pricing-cloud-self-host-costs-2026)
- Vendure Subscriptions: [Vendure Docs — Stripe Subscription](https://docs.vendure.io/plugins/stripe-subscription) · [Pinelab Plugin](https://plugins.pinelab.studio/plugin/vendure-plugin-stripe-subscription/)
- Medusa Subscriptions: [Medusa Docs — Subscriptions Recipe](https://docs.medusajs.com/resources/recipes/subscriptions) · [Reorder (OSS)](https://www.reorderjs.com/)
- Vue-Integration: [Vendure + Vue Storefront](https://vendure.io/blog/2022/01/vendure-vue-storefront-integration-v1-0) · [Medusa + Vue Storefront UI](https://medusajs.com/blog/create-vue-js-ecommerce-store-with-medusa-vue-storefront-ui/) · [Alokai/Vue Storefront](https://github.com/vuestorefront/vue-storefront)
- Sylius: [github.com/Sylius/Sylius](https://github.com/Sylius/Sylius) · [sylius.com](https://sylius.com/)
