# Soll-Bebauungsplan & Schnittstellenliste

> Ergänzung zu [Grobkonzept](Grobkonzept-IT-Konsolidierung.md), [Capability-Map](Zielarchitektur-Capability-Map.md), [Anbieter-Recherche](Anbieter-Recherche-Softwareprodukte.md), [Vergleichsmatrix](Anbieter-Vergleichsmatrix.md) und [TCO](TCO-Kostenaufstellung.md).
> Löst den in der [Capability-Map](Zielarchitektur-Capability-Map.md) genannten *„Nächsten sinnvollen Schritt"* ein: **Ist-Bestand gegen die Soll-Fähigkeiten abgleichen, je System „behalten / ersetzen / neu" markieren** → daraus **Soll-Bebauung** + **Schnittstellenliste** für den Integrations-Layer.
> **Planungsstand Juni 2026, Vorauswahl-Niveau.** Produkt-/Eignungsaussagen sind im RFI/PoC zu verifizieren (K.-o.-Kriterien: N:M/Mehr-Debitoren, Web-UX/L8, EU-Hosting). Status-Setzungen sind **Empfehlungen zur Entscheidung**, nicht gesetzt — **mit Ausnahme der unter §0 verbindlich gesetzten Entscheidungen**.

---

## 0. Gesetzte Entscheidungen (Decision-Log)

> Anders als die Status-Empfehlungen unten sind dies **verbindlich gesetzte** Festlegungen der Geschäftsführung/Projektleitung — nicht mehr im RFI/PoC zur Disposition.

| Datum | Entscheidung | Geltungsbereich | Konsequenz |
|---|---|---|---|
| 2026-06-12 | **HubSpot = Quelle für Marketing + Sales-Pipeline** 🔒 | Fähigkeit #3 (Marketing) + Sales-Pipeline/Opportunities; Schnittstelle S14 | HubSpot ist System of Record für **Kontakte/Kampagnen *und* Deals/Pipeline**. Der **Golden Record/Kundenstamm bleibt im Kern** (#1/#2) — HubSpot **synchronisiert** dorthin (S14) und speist die **Pipeline-/Forecast-Basis** des Controllings (S14→S15). Marketing-Alternativen (Customer Insights / Mautic / Brevo) damit **verworfen**. Erstmals realisiert + erprobt im **Controlling-Showcase** (Anbindung via Airbyte→Postgres analytisch + n8n/Webhooks operativ). |

---

## Lesehilfe — Status-Marker

| Marker | Bedeutung |
|---|---|
| 🟢 **behalten** | bleibt unverändert, wird nur an den Kern **angebunden** (Pflicht-/Spezial-/funktionierendes System, Leitplanke L6) |
| 🟡 **behalten + anbinden/erweitern** | bleibt, braucht aber neue Schnittstelle oder Zusatzfunktion (Entitlement, SSO) |
| 🟠 **ersetzen (EOL/Risiko)** | abzulösen wegen Plattform-EOL oder Modellbruch — nicht sofort, sondern in priorisierter Reihenfolge |
| 🔵 **neu aufbauen** | im Ist nicht vorhanden, im Soll erforderlich (Kern, Integration, Abo-Billing …) |
| ⚪ **klären (Phase 0)** | Ist-Lage unklar → erst Bestand erheben, dann Status setzen (offene Punkte §9 Grobkonzept) |
| 🔒 **gesetzt** | verbindlich entschieden (Decision-Log §0) — keine Empfehlung mehr |

**Migrationsdringlichkeit:** 🔴 hoch (EOL/akut) · 🟠 mittel · 🟢 niedrig/opportunistisch.

---

## 1. Bebauungsplan je Fähigkeit (Ist → Soll)

> Spiegelt die [Capability-Map](Zielarchitektur-Capability-Map.md) gegen den Ist-Bestand aus [Grobkonzept §2](Grobkonzept-IT-Konsolidierung.md). „Soll-System" nennt die **empfohlene Ziel-Belegung** (Plattform-Variante; OSS-/Buy-Alternativen siehe Vergleichsmatrix).

| # | Fähigkeit | Ebene | Ist-Beleg (heute) | Status | Soll-System (Empfehlung) | Dringlichkeit |
|---|---|---|---|:--:|---|:--:|
| 1 | **Single Point of Truth / MDM** | KERN | — (fragmentiert in 3 Töpfen) | 🔵 neu | **Dataverse** (Golden Record, N:M, Mehr-Debitoren) · Alt.: EspoCRM/Salesforce | 🔴 |
| 2 | **Kundenstamm / CRM** | KERN | je Fachsystem eigener Stamm | 🔵 neu | **Dynamics/Dataverse** (konsolidiert) | 🔴 |
| 3 | **Marketing/Kampagnen + Sales-Pipeline** | KERN-nah | manuelle Exporte | 🔒 **gesetzt** | **HubSpot** — System of Record für Marketing **und** Deals/Pipeline; Golden Record bleibt im Kern (#1/#2), HubSpot synct dorthin (§0) | 🟠 |
| 4 | **Controlling/BI** | KERN-nah | manuelle Exporte | 🔵 neu | **Power BI** auf dem Kern · Alt.: Metabase/Superset | 🟠 |
| 5 | **SSO / Identity** | QUERSCHNITT | fragmentierte Logins je Fachsystem | 🔒 **gesetzt** | **Keycloak** — anbieter-neutrales OIDC, 2 Realms (Kunde/Staff), im Showcase erprobt; über alle Satelliten ausrollen (ein Login), externe Konten (M365 o. ä.) bei Bedarf föderieren | 🟢 |
| 6 | **Integration / Sync-Layer** | QUERSCHNITT | Punkt-zu-Punkt / manuell | 🔵 neu | **Power Automate / iPaaS** (Event-/API-Bus) · Alt.: n8n/Locoia | 🔴 |
| 7 | **Hochschul-Verwaltung** | DOMÄNE | **OpenEduCat (Odoo)** — EOL on-prem, Customizing-Falle | 🟠 ersetzen | **TraiNex** o. Eigenbau auf Dataverse/Education Accelerator | 🔴 |
| 8 | **Prüfungswesen Hochschule** | Satellit | **Sket (ausgelagert, vorhanden)** | 🟢 behalten | **Sket** anbinden (L6) | 🟢 |
| 9 | **Akademie/Seminar/Kongress** | DOMÄNE | **UniTop (NAV, on-prem)** — Cloud-Zwang, EOL | 🟠 ersetzen | **SEMCO** o. **TCmanager** (+LMS) · Alt.: ANTRAGO `seminar` | 🔴 |
| 10 | **Publizieren/Verkauf Lehrinhalte** | Satellit | **TarLemon o.ä.** (Akademie) | ⚪ klären | Rolle/Abgrenzung in Phase 0 — behalten o. in Shop/Akademie-System aufgehen | ⚪ |
| 11 | **Berufsschul-Verwaltung** | DOMÄNE | **Legacy Java/Tomcat** (nur noch Stammdaten+Rechnung, stabil) | 🟡 behalten/schrumpfen | **Java-App** als Stammdaten-Quelle anbinden, schrittweise an Kern abgeben (Strangler); Modernisierung opportunistisch (OrgaEasy/ATLANTIS/ANTRAGO `berufsbildung`) | 🟢 |
| 12 | **Stundenplan/Anwesenheit** | Satellit | **WebUntis (vorhanden)** | 🟢 behalten | **WebUntis** anbinden | 🟢 |
| 13 | **Landes-Reporting Schule** | Satellit/**Pflicht** | **Schild-NRW / SVWS (vorhanden)** | 🟢 behalten | **Schild-NRW/SVWS-API** (Pflicht, bleibt) | 🟢 |
| 14 | **LMS / Lehrmaterial** | Satellit | **Moodle (vorhanden)** | 🟢 behalten | **Moodle** anbinden (+ Entitlement-Anbindung, s. #16) | 🟢 |
| 15 | **Kollaboration/Online-Vorlesung** | Satellit | **MS Teams / M365 (vorhanden)** | 🟢 behalten | **Teams/M365** (per SSO/Keycloak angebunden) | 🟢 |
| 16 | **Abo / Dauerzugriff Lehrmaterial** | Satellit | — (heute keine echte Abo-Logik) | 🔵 neu | **Abo-/Entitlement-Billing** (Kill Bill/Billwerk/Stripe) + Moodle-Entitlement | 🟠 |
| 17 | **Rechnungsstellung / FiBu** | Satellit | je Domäne verteilt (Java-App, UniTop) | 🟠 konsolidieren | **Business Central/DATEV** o. je Domäne; **E-Rechnung-Pflicht** beachten | 🟠 |
| 18 | **Shop (Lehrmaterial-Verkauf)** | Satellit | (unklar/in TarLemon?) | ⚪ klären | **Shopware/Shopify** falls echter Shop nötig (nie selbst bauen) | 🟢 |
| 19 | **PMS Gästehaus** | DOMÄNE | **unklar** (PMS?) | ⚪ klären | **ASA/3RPMS/igumbi** o. QloApps; Debitoren an Kern anbinden; **E-Rechnung** | ⚪ |
| 20 | **„Bochum-Prüfung" (Berufsschule)** | Sonderfall | in Java-App/Akademie-nah | ⚪ klären | als buchbares Seminar im Akademie-/Seminar-System abbilden (Buchung+Gebühr+Zertifikat) | 🟢 |

**Quintessenz des Plans:** **3 Systeme neu/ersetzen mit hoher Dringlichkeit** (Kern+Integration #1/2/6, OpenEduCat #7, UniTop #9). **Der Großteil der Satelliten bleibt** (Moodle, Teams, WebUntis, Schild-NRW, Sket) und wird nur angebunden — das ist die Risiko-minimierende Strangler-Linie aus dem [Grobkonzept §7](Grobkonzept-IT-Konsolidierung.md). **5 Punkte sind ⚪ Phase-0-Klärungen**, bevor ihr Status fixiert werden kann.

---

## 2. Sicht je Domäne (verdichtet)

| Domäne | behalten 🟢/🟡 | ersetzen 🟠 | neu 🔵 | klären ⚪ |
|---|---|---|---|---|
| **Querschnitt/Kern** | — | — | SSO (Keycloak, gesetzt), MDM/CRM, Marketing, BI, Integration-Layer | — |
| **Berufsschule** | Java-App (schrumpfend), WebUntis, Schild-NRW, Moodle, Teams | — | Anbindung an Kern | „Bochum-Prüfung", Fehltage/Krankmeldung (Schild vs. WebUntis) |
| **Hochschule** | Sket | OpenEduCat (🔴 EOL) | Anbindung an Kern | — |
| **Akademie** | Moodle (Rolle?) | UniTop (🔴 EOL) | Abo-/Entitlement, Anbindung | TarLemon-Rolle, Moodle-Nutzung, Shop |
| **Gästehaus** | — | — | PMS-Anbindung an Kern | eingesetztes PMS überhaupt? |

---

## 3. Schnittstellenliste (Integrations-Layer, Leitplanke L3)

> Das ist **das eigentliche Projekt** (vgl. [Capability-Map](Zielarchitektur-Capability-Map.md)): der Sync-/Event-Layer, der den Kern mit den Satelliten verbindet. Jede Zeile = ein zu bauender/konfigurierender Connector. **Richtung** aus Sicht des Kerns: ⬅️ Kern liest (inbound), ➡️ Kern schreibt (outbound), ↔️ bidirektional. **Muster:** Event = ereignisgetrieben, Batch = periodisch, API = on-demand.

### A) Kern ↔ Domänen-/Fachsysteme (Stammdaten-Sync)

| # | Quelle ↔ Ziel | Datenobjekte | Richtung | Muster | Hinweis / K.-o. | Prio |
|---|---|---|---|---|---|:--:|
| S1 | **Java-App (Berufsschule)** ↔ Kern | Personen, Unternehmen, **N:M-Beziehungen, Debitoren**, Rechnungen | ↔️ | Event/Batch | **Referenzquelle** für das verlorene Datenmodell — als erstes migrieren/spiegeln | 🔴 |
| S2 | **OpenEduCat (Hochschule)** ⬅️ Kern | Studierende, Verträge, Debitoren | ⬅️ initial, dann ➡️ | Batch→Event | Dubletten-Matching gegen S1; nach Ablösung (#7) auf Nachfolger umhängen | 🔴 |
| S3 | **UniTop (Akademie)** ⬅️ Kern | Seminarteilnehmer, Firmen, Debitoren | ⬅️ initial | Batch | NAV-Export; nach Ablösung (#9) auf Nachfolger umhängen | 🔴 |
| S4 | **PMS Gästehaus** ↔ Kern | Gäste (= teils dieselben Kunden), Debitoren | ↔️ | API/Batch | erst nach Phase-0-Klärung #19 | ⚪ |

### B) Kern → Satelliten / Pflichtsysteme (Konsum, anbinden statt ersetzen)

| # | Quelle ↔ Ziel | Datenobjekte | Richtung | Muster | Hinweis | Prio |
|---|---|---|---|---|---|:--:|
| S5 | Kern ↔ **Keycloak (SSO)** | Identitäten, Gruppen, Rollen | ↔️ | OIDC/SAML | **gesetzt** — SSO über alle Satelliten (ein Login), externe Konten (M365 o. ä.) föderierbar; größter Komfort-Hebel | 🔒 |
| S6 | Kern ⬅️ **WebUntis** | Stunden, Anwesenheit | ⬅️ | API/Batch | für Controlling/Reporting | 🟢 |
| S7 | Kern ↔ **Schild-NRW / SVWS** | Schülerstamm, Landes-Reporting | ↔️ | **SVWS-API** | **Pflicht** — Format-/Compliance-genau | 🟠 |
| S8 | Kern ⬅️ **Sket** | Prüfungsergebnisse, Zertifikate | ⬅️ | API/Batch | bleibt eigenständig (L6) | 🟢 |
| S9 | Kern ↔ **Moodle** | Kursbelegung, **Entitlement/Zugriff** | ↔️ | API (Webservices) | Abo-Status (#16) steuert Zugriff | 🟠 |
| S10 | Kern ⬅️ **Teams/M365** | Kollaborations-/Nutzungssignale (optional) | ⬅️ | Graph-API | niedrige Prio, optionaler Mehrwert | 🟢 |

### C) Kern ↔ kaufmännische / Commodity-Satelliten

| # | Quelle ↔ Ziel | Datenobjekte | Richtung | Muster | Hinweis | Prio |
|---|---|---|---|---|---|:--:|
| S11 | Kern → **Billing/FiBu** (BC/DATEV) | Rechnungen, Debitoren, Zahlungen | ➡️ | Event/Batch | **E-Rechnungs-Pflicht** (ab 2025/26) — Format ZUGFeRD/XRechnung | 🟠 |
| S12 | Kern ↔ **Abo-/Entitlement-Billing** | Abos, Laufzeiten, Entitlement-Status | ↔️ | Event | speist S9 (Moodle-Zugriff); nur falls Abo-Modell #16 gebaut wird | 🟠 |
| S13 | Kern ↔ **Shop** (falls #18) | Bestellungen, Kunden | ↔️ | Event/API | nur falls echter Shop nötig | 🟢 |
| S14 | Kern ↔ **HubSpot** (Marketing + Sales-Pipeline) | Kontakte, Segmente, Kampagnen-Events, **Deals/Opportunities** | ↔️ | Event/Batch | 🔒 gesetzt (§0): Zielgruppen aus Golden Record; **Deals/Pipeline → S15 (Forecast-Basis)**. Sync via **Airbyte** (analytisch) + **n8n/Webhooks** (operativ) | 🟠 |
| S15 | Kern → **Power BI / Controlling** | aggregierte Kennzahlen, Forecast-Basis | ➡️ | Direkt/Dataflow | löst den manuellen-Export-Schmerz | 🟠 |

**Schnittstellen-Summe:** ~**15 Connectoren**, davon **4 mit hoher Dringlichkeit** (S1–S3 + S5/S7 mittel). **S1 ist der kritische Pfad** — die Java-App ist die einzige verlässliche Quelle des N:M-/Debitoren-Modells und damit Startpunkt der Golden-Record-Bildung.

---

## 4. Empfohlene Reihenfolge (an Roadmap-Phasen, [Grobkonzept §6](Grobkonzept-IT-Konsolidierung.md))

1. **Phase 0:** die 5 ⚪-Punkte klären (PMS, TarLemon, Moodle-Akademie, Fehltage-System, Shop) → offene Status fixieren.
2. **Phase 1 (Kern):** #1/#2 aufbauen, **S1** (Java-App) als Referenzquelle spiegeln, Dubletten-Matching, Golden Record (read-only zuerst).
3. **Phase 2 (Integration & Nutzen):** **S2/S3** (Studierende/Seminare einlesen), **S14/S15** (Marketing/Controlling) → größter Geschäftsschmerz vor den Migrationen gelöst.
4. **Phase 3 (EOL-Ablösung):** #7 (OpenEduCat) und #9 (UniTop) ersetzen, Connectoren **S2/S3** auf die Nachfolger umhängen; **S5/S7/S9** für SSO/Pflicht/Entitlement.
5. **Phase 4 (Legacy-Abbau):** Java-App auf reine Restfunktion schrumpfen (#11), **S4** (Gästehaus) integrieren.

---

## 5. Nächster sinnvoller Schritt

Aus diesem Plan wird der **RFI-/PoC-Kriterienkatalog** konkret: je 🟠-Ablösung (OpenEduCat, UniTop) und je 🔵-Neubau (Kern, Integration) die **Muss-Kriterien** ([Anbieter-Recherche §0](Anbieter-Recherche-Softwareprodukte.md)) und die zugehörigen **Connectoren** (S1–S15) als Abnahme-/Angebotsumfang an die Anbieter und den Java-DL geben. Damit ist die Brücke von der Architektur zur **Beschaffung** geschlagen — der letzte offene Baustein vor dem Entscheidungs-Workshop der Geschäftsführung ([Grobkonzept §11](Grobkonzept-IT-Konsolidierung.md)).
