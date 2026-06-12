# TCO-/Kostenaufstellung: Datenkern & Optionen (5-Jahres-Sicht)

> Ergänzung zu [Grobkonzept](Grobkonzept-IT-Konsolidierung.md), [Anbieter-Recherche](Anbieter-Recherche-Softwareprodukte.md) und [Vergleichsmatrix](Anbieter-Vergleichsmatrix.md).
> **Planungs-Schätzwerte (Größenordnung), keine Angebote.** Reale Kosten hängen an Scope, Nutzerzahl und Lizenzdesign. Alle Annahmen sind offengelegt und ersetzbar.

---

## 1. Methodik & Annahmen (alle ersetzbar)

**Betrachtungsgegenstand:** der **strategische Datenkern** = Stammdaten-SSoT mit N:M Person↔Unternehmen + Mehr-Debitoren, Dublettenbereinigung/Golden Record, Integrations-/Sync-Schicht zu den 3 Fachsystemen, Marketing-/Controlling-Auswertung, Self-Service-Portale. **Nicht** enthalten: Ablösung der Fach-Satelliten selbst (separate Projekte) und Gästehaus-PMS.

**Tagessätze (DE, 2026, Annahme):**
| Rolle | Tagessatz |
|---|---|
| Intern Junior (Python/Azubi, vollkostet) | ~350 € |
| Externer Mid-Dev (Python/PHP-Contractor) | ~750 € |
| Externer Senior (Java-DL, Hauskenntnis) | ~950 € |
| Blended-Mix Eigenbau (Annahme) | ~780 € |
| Blended-Mix Plattform (mehr intern/Low-Code) | ~650 € |

**Aufwands-Annahme Build (Vollausbau Datenkern):** 1.500–2.800 Personentage je nach Stack; Plattform deutlich weniger, da Datenmodell/Auth/UI/Connector-Gerüst vorhanden. **Wartung/Weiterentwicklung p.a.** ≈ 16–20 % des Build-Aufwands. **Migration/Datenbereinigung** separat 0,1–0,3 Mio. €.

**Disclaimer:** Build-Aufwände bei Individualsoftware streuen leicht ±40 %. Die **relative Reihenfolge** und die **Kostentreiber** sind belastbarer als die absoluten Zahlen.

---

## 2. Die Optionen

- **P — Plattform (Empfehlung):** MS Dynamics 365 / Dataverse + Power Platform + Azure (Low-Code + Pro-Code durch DL).
- **Py — Eigenbau Python:** Django (Batteries-included: ORM, Admin, Auth) + FastAPI (Integration/APIs).
- **Jv — Eigenbau Java:** Spring Boot (Enterprise; DL-Kernkompetenz) — Alt.: Quarkus/Micronaut. Schärfung als **standardbasierter Integrations-/Kern-Layer** (Camel Quarkus + LangChain4j + Quarkus Flow) statt Monolith: siehe Architektur-Option in der [Capability-Map](Zielarchitektur-Capability-Map.md).
- **Ph — Eigenbau PHP:** Laravel (schnelles CRUD/Admin, Admin-Panel Filament) — Alt.: Symfony/API Platform.
- **Bn — Buy Nischen-Suite:** ANTRAGO/cobra als Referenz (günstig, aber **kein moderner Datenkern**, N:M/Debitor unbestätigt).

---

## 3. Stack-Profil: Frameworks, Runtimes, Lizenz-/Talent-Faktoren

| Faktor | **P** Power Platform/Azure | **Py** Python/Django | **Jv** Java/Spring | **Ph** PHP/Laravel |
|---|---|---|---|---|
| Framework-Eignung Datenkern/CRUD | Datenplattform out-of-the-box (Dataverse) | sehr gut (Django-Admin/ORM) | sehr gut, enterprise (mehr Boilerplate) | sehr gut/schnell (CRUD/Admin) |
| **N:M + Mehr-Debitoren** | **nativ** (Plattform-Standard) | selbst modellieren (frei, aber Aufwand) | selbst modellieren | selbst modellieren |
| Runtime | SaaS (Azure, EU-Region) | CPython, gunicorn/uvicorn, Container | **JVM** (OpenJDK/Temurin) | PHP-FPM |
| **Runtime-Lizenz-Fallstrick** | im Abo enthalten | frei | **Oracle JDK ist kostenpflichtig (per-Employee) → OpenJDK/Temurin/Azul nutzen = 0 €** | frei (Laravel Nova/Forge optional kostenpflichtig, Filament frei) |
| Hosting-Kosten (relativ) | im Lizenzpreis | mittel | höher (JVM-Ressourcen) | am günstigsten |
| **Team-Fit intern** (2 Python-Junioren) | ✓ Low-Code + Python-Pro-Code | ✓✓ direkt | ✗ fremd | ✗ fremd |
| **Team-Fit Java-DL** | ✓ Pro-Code/Azure | ◑ | ✓✓ Kernkompetenz | ✗ |
| Talentmarkt (Nachbesetzung) | groß (MS-Ökosystem) | sehr groß | groß | sehr groß/günstig |
| Updatefähigkeit (L4) | erzwungen (Managed Solutions/ALM) | Eigenverantwortung | Eigenverantwortung | Eigenverantwortung |

**Kernaussage Stacks:** Bei Eigenbau lösen **alle drei** N:M/Debitor nur durch Eigenleistung (volle Kontrolle, aber dauerhafte Eigenverantwortung). **Python** passt zum internen Team, **Java** zum Dienstleister, **PHP** zu keinem von beiden (neue Talentabhängigkeit), ist aber am billigsten im Betrieb.

---

## 4. 5-Jahres-TCO (Richtwerte, gerundet)

| Option | Build (einmalig) | Migration | Lizenz/Runtime p.a. | Wartung/Weiterentw. p.a. | **5-Jahres-TCO** |
|---|---|---|---|---|---|
| **P — Power Platform/Azure** *(empf.)* | 0,55–0,85 Mio. € | 0,15–0,25 Mio. € | 0,06–0,18 Mio. € | 0,12–0,20 Mio. € | **≈ 1,8–2,6 Mio. €** |
| **Ph — Eigenbau PHP/Laravel** | 1,0–1,4 Mio. € | 0,15–0,25 Mio. € | 0,006–0,018 Mio. € | 0,17–0,24 Mio. € | **≈ 2,0–2,8 Mio. €** |
| **Py — Eigenbau Python/Django** | 1,1–1,6 Mio. € | 0,15–0,25 Mio. € | 0,012–0,036 Mio. € | 0,20–0,28 Mio. € | **≈ 2,3–3,1 Mio. €** |
| **Jv — Eigenbau Java/Spring** | 1,5–2,2 Mio. € | 0,15–0,25 Mio. € | 0,018–0,048 Mio. € (OpenJDK!) | 0,28–0,36 Mio. € | **≈ 3,2–4,2 Mio. €** |
| **Bn — Buy Nischen-Suite** *(Referenz)* | 0,1–0,25 Mio. € | 0,1–0,2 Mio. € | 0,06–0,12 Mio. € | 0,06–0,10 Mio. € | **≈ 0,9–1,4 Mio. €** |

> Lesart: **Bn** ist nominal am billigsten, liefert aber **nicht** den modernen, zukunftssicheren Datenkern (N:M/Debitor unbestätigt, konservative Basis). **Jv** ist mit Abstand am teuersten (höchste Build- und Wartungskosten via Senior-Tagessätze). **P, Ph, Py** liegen in einem ähnlichen Korridor — der Unterschied entscheidet sich über Risiko und Team-Fit (§6), nicht über den Preis allein.

---

## 5. Kostentreiber & Sensitivitäten

- **Lizenzdesign Plattform (größter Hebel bei P):** Der Kunde hat **120 Mitarbeiter insgesamt** — das ist die **harte Obergrenze** der zu lizenzierenden Innen-Nutzer. Realistisch brauchen nur **~60–100** einen aktiven Core-Seat (Verwaltung, Marketing/Controlling, Seminar-/Studien-/Schul-Admins); die **50 Lehrer** arbeiten überwiegend in WebUntis/Moodle/Teams und benötigen oft nur leichten oder keinen Core-Zugriff.
  - **Günstige Route** (Power Apps Premium ~20 €/User/Monat auf Dataverse): 80 Nutzer ≈ **19 k €/Jahr**, alle 120 ≈ **29 k €/Jahr**.
  - **Teure Route** (Dynamics-365-First-Party-Apps ~85–115 €/User/Monat): 80 Nutzer ≈ **91 k €/Jahr**, 120 ≈ **137 k €/Jahr**.
  - Plus Power BI Pro für Auswerter (~10 × 10 €), Dataverse-Storage (~5–15 k €/Jahr), Power Pages-Portalkapazität (~10–40 k €/Jahr je nach Self-Service).
  - **1.500 + 1.200 Lernende NIE als Innen-Nutzer lizenzieren**, sondern über **Power Pages-Portale** (Kapazitätspakete) oder in den Fachsystemen belassen — sonst explodieren die Kosten.
  → Selbst die teure Vollvariante mit allen 120 Mitarbeitern bleibt im niedrigen sechsstelligen Bereich; die **günstige Route ist der Normalfall**. Lizenzmodell-Workshop mit MS-Partner bestätigt die finale Zahl.
- **Build-Aufwand Eigenbau (größter Hebel bei Py/Jv/Ph):** ±40 % Streuung. Jeder reduzierte Scope (MVP statt Vollausbau) senkt Build **und** Folgewartung.
- **Java-Tagessätze:** Jv ist teuer v. a. wegen Senior-DL-Sätzen in Build **und** Dauerwartung — Lock-in an den DL.
- **Oracle-JDK-Falle (Jv):** Bei Oracle JDK fallen **per-Employee-Gebühren fürs gesamte Unternehmen** an → zwingend OpenJDK/Temurin/Azul, dann 0 € Runtime-Lizenz.
- **Wartungslast bleibt bei Eigenbau dauerhaft intern/DL** — bei P trägt Microsoft Plattform-Patches/Upgrades.

---

## 6. Kosten ist notwendig, nicht hinreichend — Risiko-/Eignungs-Overlay

| Option | Time-to-Value | Risiko Junior-Team | Lock-in | Modernität/Zukunft | Gesamturteil |
|---|---|:--:|---|:--:|---|
| **P — Power Platform** | schnell (Plattform-Gerüst) | **niedrig** (Low-Code) | MS | ✓✓ | **Bestes risikoadjustiertes Verhältnis** |
| **Py — Python/Django** | mittel | mittel (Junioren brauchen Senior-Architektur) | gering, aber Eigenverantwortung | ✓ | Gut, wenn intern Senior-Kapazität aufgebaut wird |
| **Ph — PHP/Laravel** | mittel/schnell (CRUD) | **hoch** (kein Team-/DL-Fit → neue Talentabhängigkeit) | gering | ✓ | Billig im Betrieb, aber organisatorisch verwaist |
| **Jv — Java/Spring** | langsam | **hoch** (nur DL kann es) | **hoch (DL)** | ✓ | Teuerste + größtes Key-Person-Risiko → wiederholt die Historie |
| **Bn — Buy Nischen** | sehr schnell | niedrig | Anbieter | ✗ (kein moderner Kern) | Sparvariante ohne strategische Lösung |

---

## 7. Szenario B: „KI-gestützt / automations-first" (revidierte Build-Aufwände)

> **Annahme:** Entwicklung KI-gestützt (Copilot/Claude & Co.), Architektur **automations-/API-first** statt formular-zentriert (Power Automate/Copilot Studio bzw. eigene Agenten/Events). Damit kollabiert der teuerste Eigenbau-Posten — handgebaute UI/Formulare/Boilerplate.

**Was sich ändert:**
- **Build sinkt** (UI/CRUD/Glue/Tests ~−35–40 %); Formulare teils überflüssig, wenn Funktionen automatisch/agentengetrieben ablaufen.
- **Was bleibt (von KI nicht wegrationalisiert):** die schweren **Design-Teile** (Datenmodell N:M/Debitor, Dublettenmatching, Integrationssemantik über 3 Legacy-Systeme, Migration, Security) **und Betrieb/Haftung** (Patching, CVEs, DSGVO, Ops, On-Call) — KI schreibt Code, betreibt ihn nicht.
- **KI hebt beide Seiten:** auch die Plattform profitiert (Copilot Studio/Power Automate) → der **relative Abstand schrumpft, kehrt sich aber nicht um**.

**Revidierte 5-Jahres-TCO (KI-Szenario, Richtwerte):**

| Option | Build (KI-gestützt) | Migration | Lizenz/Runtime p.a. | Wartung p.a. | **5-Jahres-TCO** |
|---|---|---|---|---|---|
| **P — Power Platform/Azure** | 0,40–0,65 Mio. € | 0,15–0,25 | 0,06–0,18 | 0,10–0,17 | **≈ 1,4–2,4 Mio. €** |
| **Ph — PHP/Laravel** | 0,65–0,95 | 0,15–0,25 | ~0,01 | 0,14–0,19 | **≈ 1,5–2,2 Mio. €** |
| **Py — Python/Django+FastAPI** | 0,70–1,05 | 0,15–0,25 | 0,012–0,036 | 0,16–0,23 | **≈ 1,7–2,6 Mio. €** |
| **Jv — Java/Quarkus+Spring** | 1,00–1,50 | 0,15–0,25 | 0,018–0,048 | 0,23–0,29 | **≈ 2,4–3,4 Mio. €** |

> **Kernergebnis:** Der Kostenabstand zwischen **Plattform und KI-gestütztem PHP-/Python-Eigenbau schließt sich praktisch.** Java bleibt am teuersten (Senior-Sätze + DL-Lock-in). **Build ist nicht mehr der Differenzierer.**

**Verschobene Entscheidungsachse** (weil die Build-Kosten nivelliert sind):
1. **Besitzen vs. Mieten** des Automations-/Datensubstrats — maximale Kontrolle + **volle Ops-/DSGVO-Haftung** vs. betrieben/gemanagt.
2. **Governance-Reife:** Kann das Team **KI-generierten Produktionscode** dauerhaft kohärent, sicher und updatefähig halten? Das ist der **neue kritische Erfolgsfaktor** — und die OpenEduCat-Customizing-Falle in neuem Gewand (Code billig zu *erzeugen*, teuer *kohärent zu halten*).
3. **Senior-Review reintroduziert Kosten:** KI-Code für Finanz-/Personendaten ohne Senior-Architektur-Review ist ein DSGVO-/Qualitätsrisiko → der eingesparte Senior kommt in der Review-/Governance-Rolle zurück.

---

## 8. Empfehlung

> **Aktualisiert nach Szenario B:** Da KI-gestützte Entwicklung den Build-Vorsprung der Plattform einebnet, stützt sich die Empfehlung **nicht mehr primär auf Build-Kosten**, sondern auf **Betrieb/Haftung, Governance-Reife und Team-Fit**.

1. **Datenkern auf P (Power Platform/Azure)** — nicht primär wegen Kosten (im KI-Szenario nivelliert, siehe §7), sondern wegen **niedrigstem Betriebs-/Governance-Risiko**, bestem Team-Fit (intern Low-Code + DL Pro-Code), nativem N:M/Debitor-Modell und erzwungener Updatefähigkeit. Erst der **Lizenzdesign-Workshop** (Staff-Seats vs. Portal-Nutzer) macht die TCO endgültig belastbar — das ist die entscheidende offene Variable.
2. **Falls Eigenbau gewollt** (z. B. bewusst raus aus MS-Lock-in): **Python/Django** wegen internem Team-Fit — **nicht** Java (teuerste + DL-abhängig) und **nicht** PHP (organisatorisch verwaist). Voraussetzung: interne Senior-Architektur-Kapazität aufbauen.
3. **Buy-Nischen-Suite (Bn)** nur als **Sparvariante**, wenn das Plattform-Budget nicht tragbar ist — mit dem bewussten Verzicht auf einen modernen Datenkern.
4. **Java-DL** bleibt wertvoll — aber in der Rolle **Pro-Code/Integration auf P**, nicht als Java-Monolith-Bauträger (verlagert sonst die Kosten in die teuerste/riskanteste Spalte).

**Nächster Schritt zur Belastbarkeit:** (a) Lizenzdesign-Workshop mit MS-Partner (Seats/Portale), (b) Scope-Cut MVP vs. Vollausbau definieren, (c) Festpreis-/T&M-Indikation vom Java-DL für die Pro-Code-Anteile auf P einholen.

---

## Annahmen-Quellen
- Power Apps/Dataverse-Preise: [Power Apps Pricing](https://www.microsoft.com/en-us/power-platform/products/power-apps/pricing/) · [Lizenzüberblick](https://learn.microsoft.com/en-us/power-platform/admin/pricing-billing-skus) · [EU data residency](https://learn.microsoft.com/en-us/dynamics365/get-started/availability)
- Java-Runtime-Lizenz (Kontext OpenJDK vs. Oracle JDK): allgemeiner Branchenstand — vor Entscheidung anwaltlich/Lizenz prüfen.
- Tagessätze/Aufwände: Planungsannahmen (DE-Markt 2026), im RFI/Angebot zu bestätigen.
