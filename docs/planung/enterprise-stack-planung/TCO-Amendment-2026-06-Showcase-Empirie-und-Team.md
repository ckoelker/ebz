# TCO-Amendment: Neubewertung §6–§8 nach Showcase-Empirie & realer Teamstruktur

> **Lieferobjekt:** Amendment (Ergänzung/Revision) zur [TCO-Kostenaufstellung](TCO-Kostenaufstellung.md),
> §6 (Risiko-/Eignungs-Overlay), §7 (KI-Szenario) und §8 (Empfehlung) — **ergänzt um die geschätzten
> Kosten der empfohlenen Lösung und die interne Personal-Einsparung durch die optimierte
> Softwarelandschaft (§I–§K)**. Baut auf dem
> [Review Showcase vs. Planung](Review-2026-06-Showcase-vs-Planung.md) und der
> [Capability-Map](Zielarchitektur-Capability-Map.md) auf.
> **Stand:** 2026-06-22. **Status:** Entscheidungs-Input für den GF-Workshop — **keine gesetzte
> Entscheidung**. Ändert die TCO-Hauptdatei nicht; die Korrektur gehört in einen GF-Beschluss.
> **Geltungsbereich:** nur der strategische **Datenkern + Integrations-Layer** (wie TCO §1), nicht die
> Satelliten-Ablösungen selbst.

---

## A. Anlass — was sich seit der TCO-Aufstellung geklärt hat

Drei neue, belastbare Fakten verschieben die TCO-Bewertung — **nicht** primär die Zahlen, sondern die
in §8 ohnehin entscheidende Risiko-/Eignungsachse:

1. **Showcase-Empirie.** Der `showcase/`-Stack hat den Best-of-Breed-Mesh mit JVM-Kern (Camel Quarkus +
   LangChain4j) + Vue + OSS-Commodity (Vendure/Lightdash/Keycloak/OpenOLAT) gebaut und grün/live verifiziert
   — inzwischen über die ursprüngliche Stichprobe hinaus deutlich breiter: Controlling **M1–M4** (inkl.
   reproduzierbarem BI-Dashboard), Formularverwaltung P1, Rechnung **R1–R7 + Cockpit**, Party-/CRM-Kern,
   Anmeldung **A–I**, LMS **L0–L3**, **HubSpot In-/Outbound-Sync** (Consent/Art. 17/Marketing-Merkmale +
   signierter Webhook-Rückkanal, realer Adapter live), Kundenkommunikation (Event-Spine) und ein
   **EIN-Kommando-Voll-Aufbau** (`showcase-aufbau.sh`: reset→seed→Tests→Telemetrie/BPMN→BI). Das ist der
   **PoC der in der [Capability-Map](Zielarchitektur-Capability-Map.md) ausdrücklich „nicht gesetzten"
   JVM-Kern-Option** — die Velocity-Hypothese ist damit über mehrere Domänen belegt, nicht an einem Modul.
2. **Präzisierung Liefergegenstand.** Vergeben würde **nicht der Showcase-Code**, sondern ein
   **Produktiv-Build nach dem Vorgehen, das der Showcase als tragfähig bewiesen hat**. Der Showcase ist
   **Blueprint/Entrisikungs-Artefakt**, nicht das ausgelieferte Produkt.
3. **Reale Teamstruktur** (neu gegenüber der generischen TCO-Annahme „2 Python-Junioren"):
   - **Intern (MS / Python):** 2 Junior-Entwickler + 3 Azubis; **Senior-Kapazität muss zugekauft** werden.
   - **Extern (JVM-Ansatz):** ein **seit 20 Jahren bekannter Partner**, der den **Java/Tomcat-Kern der
     Berufsschule selbst programmiert hat** (= Quelle des „verlorenen" N:M-/Debitoren-Datenmodells, S1),
     will direkten Zugang zu den fachlichen Ansprechpartnern und setzt **Java/Quarkus + Vue seit 5 Jahren
     produktiv** in zahlreichen Projekten ein.

---

## B. Liefergegenstand-Klärung — „Ansatz", nicht „Showcase"

Der TCO-Effekt des Showcase ist **die Senkung der Unsicherheit, nicht des Build-Mittelwerts.** Die
[TCO §1](TCO-Kostenaufstellung.md) nennt **±40 % Build-Streuung** als größten Eigenbau-Hebel — genau die
teuersten Unbekannten sind durch das bewiesene Vorgehen bereits **retired**:

**Was der Ansatz beweist (für jeden Builder nutzbar):**
- Machbarkeit des Best-of-Breed-Mesh (ein integrierter Stack, ein Postgres).
- Erreichbare **Velocity** mit KI-gestützter Entwicklung + Standard-Specs (Camel-EIP, CNCF Serverless
  Workflow) — stützt die [TCO §7](TCO-Kostenaufstellung.md)-Hypothese empirisch.
- Das regulatorisch Härteste — **E-Rechnung (ZUGFeRD/XRechnung) + GoBD-WORM** — technisch gelöst (S11).
- **Bidirektionale Drittsystem-Integration an einer realen Cloud-API** (HubSpot): Outbound-Sync mit
  **DSGVO-Tiefe** — Consent/Werbesperre, **Recht auf Vergessen (Art. 17)**, Marketing-Merkmale und ein
  **signatur-verifizierter Webhook-Rückkanal** — gegen das echte System live verifiziert (nicht nur Mock).
  Damit ist die heikelste Integrations-Spielart (Schreibrichtung + Compliance + Rückkanal) belegt (S14).
- **Reproduzierbarkeit als Eigenschaft, nicht Glück**: EIN Kommando baut den ganzen Mesh inkl. BI-Dashboard
  deterministisch neu auf (Tests grün, Telemetrie/BPMN, Dashboard-as-Code) — stützt die Run/Betrieb-Achse.
- Die **Make/Buy-Linie**: Debitoren-Hoheit + E-Rechnungs-/DATEV-Gateway = KERN-nah bauen; klassische
  FiBu = kaufen (DATEV hinter Interface). Vgl. [Review §4.A](Review-2026-06-Showcase-vs-Planung.md).

**Was der Showcase bewusst vereinfacht hat — im Produktiv-Build noch zu bezahlen:**

| Showcase-Vereinfachung | Im Produktiv-Build zu leisten |
|---|---|
| Mocks (DATEV-Cloud, Fixture-Daten) | echte Anbindungen + Härtung |
| Vendure als „Stammdatenquelle" | **S1: reale Legacy-Migration + N:M Person↔Firma** (gar nicht angefasst) |
| ein Postgres/Monorepo, geteilte Test-DB | echte Isolation, Betrieb, Monitoring, On-Call |

⇒ Diese Restliste ist **nicht** vom Ansatz wegrationalisiert. Sie ist das Feld, in dem **Domänen-/
Legacy-Wissen** die verbleibende Unsicherheit in **risikoarme Ausführung** verwandelt.

---

## C. Effekt auf den Build — Varianz kollabiert, Mittelwert bleibt

| | TCO §4 (Basis) | TCO §7 (KI-Szenario) | **Nach Showcase-Empirie** |
|---|---|---|---|
| Jv Build (Kern) | 1,5–2,2 Mio. € | 1,0–1,5 Mio. € | **~0,8–1,2 Mio. €, enge Streuung** (Architektur + E-Rechnung + Velocity bereits belegt) |
| Build-Unsicherheit | ±40 % | ±40 % | **deutlich < ±40 %** für die JVM-Variante |

**Kernaussage:** Der bewusste Wert des Showcase ist **Risiko-/Streuungssenkung mit hoher Konfidenz**,
nicht ein niedrigerer Build-Mittelwert. „Build ist nicht mehr der Differenzierer" ([TCO §7](TCO-Kostenaufstellung.md))
ist damit **belegt statt vermutet**.

---

## D. Reale Teamstruktur → Neubewertung des Risiko-Overlays (§6)

Die §6-Mali für „Java" (Team-Fit, Lock-in, Key-Person, Time-to-Value) waren **generisch**. Mit den
realen Personen sehen sie anders aus:

| Achse | **Intern MS Power Platform** (2 Jun + 3 Azubi, Senior zugekauft) | **Intern Python/Django** (dito) | **Extern JVM/Quarkus+Vue** (20-J-Partner, hat BS-Kern gebaut) |
|---|---|---|---|
| Team-Fit Bau | ◑ Low-Code passt Junioren; Senior **zugekauft** | ✗ 2 Jun + 3 Azubi auf strateg. Finanz-/Personendaten-Kern = dünn | ✓✓ 5 J Quarkus+Vue produktiv, Stamm-Team |
| **Kritischer Pfad S1 (N:M/Debitoren)** | ✗ Reverse-Engineering | ✗ dito | **✓✓ selbst gebautes Datenmodell** |
| Time-to-Value | mittel | langsam (Senior-Aufbau) | **schnell** (Domäne + Tech vorhanden) |
| Governance / KI-Code kohärent ([§7.3](TCO-Kostenaufstellung.md)) | Senior-Review kommt zurück | **strukturell hoch** (Junior+Azubi) | Tagesgeschäft des Partners |
| Run/Betrieb | MS trägt Plattform-Patches ✓ | self-run auf dünnem Team ✗ | beim Partner, stabil, **dauerhaft extern** |
| Lock-in | MS | gering, aber Eigenverantwortung | **DL — aber 20-J-Beziehung, bekannt/stabil** |

**Folge für die Kosten:**
- Der **„Java teuer"-Effekt** schrumpft weiter: hoher Tagessatz (~950 €), aber **weniger effektive Tage**
  (Domänenwissen + 5-J-Pattern-Wiederverwendung + belegte Velocity). Teuer × wenige Tage ≈ mittel.
- Der **„intern billig"-Schein** trügt: niedrige Junior-/Azubi-Sätze, aber **Rework + zugekaufte
  Senior-Reviews ([§7.3](TCO-Kostenaufstellung.md)) + S1-Reverse-Engineering + längere Laufzeit** holen
  die Ersparnis zurück — hier **strukturell**, nicht punktuell.

---

## E. Revidierte 5-Jahres-Sicht — Build und Run getrennt (Review §6.4)

| Linie | Bewertung nach diesem Amendment |
|---|---|
| **Build** | JVM-Variante zentral ~§7-Untergrund mit **enger Varianz**; risiko-adjustiert **Parität** zu P/Py/Ph für den Kern. |
| **Run/Maintain** | **unverändert konservativ** — der Ansatz beweist Bau, nicht 5-Jahre-Betrieb. **Entscheidende Achse.** |
| **S1 / Migration (riskantestes Stück)** | beim Partner **vorab entschärft** (eigenes Datenmodell) — bei intern ein **offenes Großrisiko**. |

**Netto:** Der ohnehin kleine 5-Jahres-Abstand aus [TCO §7](TCO-Kostenaufstellung.md) **verschwindet
risiko-adjustiert für Kern + S1** — und kann dort zugunsten der externen JVM-Route kippen, weil das
größte Einzelrisiko des gesamten Plans (S1, das der Showcase aussparte) genau beim Partner am kleinsten
ist. **Die Entscheidung verlagert sich endgültig von „welcher Stack billiger" auf Betrieb, Governance,
Domänen-Risiko und Souveränität.**

---

## F. Lock-in & Souveränität — die ehrlichen Gegenfragen

1. **Lock-in-Déjà-vu.** Abgelöst wird ein Java/Tomcat-Legacy, das **derselbe Partner** gebaut hat — mit
   neuem Java/Quarkus desselben Partners. Ist das *Raus aus der Abhängigkeit* oder *rein in dieselbe mit
   neuer Technik*? **Entschärfbar** durch:
   - **nur Standards** (Quarkus/Camel/Vue, keine Hausmagie),
   - **Code-/IP-Eigentum + vollständige Doku + Quellcode-Hinterlegung (Escrow)**,
   - **Exit-/Transition-Klausel** im Vertrag.
   Dann bleibt die 20-Jahre-Bindung eine **Wahl, kein Käfig**. *Wenn* das strategische Ziel
   Anbieter-Unabhängigkeit ist, trägt die externe Route es nur mit diesen Sicherungen; *wenn* es „raus
   aus EOL/Customizing-Falle" ist, trägt sie es ohnehin.
2. **Capability-Souveränität.** Den **Differenzierer** dauerhaft auszulagern heißt, intern nie
   Eigentümerschaft daran aufzubauen. Aber: **2 Junioren + 3 Azubis tragen einen strategischen
   Datenkern auf Jahre nicht selbst** — unabhängig vom Stack. Das ist die Realität hinter dem „internen"
   Weg und der Grund für das Split-Modell (§G).

---

## G. Empfehlung — Split nach Risiko, gleicher Ansatz

> Aktualisiert [TCO §8](TCO-Kostenaufstellung.md) für den Fall der hier beschriebenen Teamstruktur.
> §8 bleibt gültig, wo es um *generische* Teams geht; dies ist die team-spezifische Schärfung.

1. **Kern + S1-Migration → externer JVM-Partner.** Das domänen­gebundene, riskanteste Stück gehört dorthin,
   wo das N:M-/Debitoren-Modell **herkommt**. Risiko-adjustiert der günstigste Weg für genau den Punkt,
   an dem der ganze Plan hängt. Voraussetzung: die Lock-in-Sicherungen aus §F **vertraglich** fixieren.
2. **Satelliten / Portale / BI / Self-Service → internes Team**, nach **demselben bewiesenen Ansatz**
   (KI-gestützt, Standards, Commodity kaufen). Low-Code/MS-getragen, risikoarm — baut interne Muskeln
   ohne den Kern-Risikoträger zu sein. Das ist [TCO §8.4](TCO-Kostenaufstellung.md) („Java-DL für
   Pro-Code/Integration") — hier invertiert, weil der DL der Inkumbent mit dem Datenmodell ist.
3. **Make/Buy-Linie wie im Showcase getroffen** beibehalten: Debitoren-Hoheit + E-Rechnungs-Gateway
   bauen, klassische FiBu kaufen (DATEV) — als **Amendment zur [Capability-Map #6/#17](Zielarchitektur-Capability-Map.md)**
   bestätigen ([Review §6.2](Review-2026-06-Showcase-vs-Planung.md)).

---

## H. Was die TCO §6–§8 konkret zu ändern hätte (Deltas) + nächste Schritte

**Deltas:**
- **§6:** „Java = Time-to-Value langsam / Junior-Risiko hoch / Key-Person hoch" gilt **nicht** für einen
  domänen­kundigen 20-J-Partner mit 5-J-Quarkus/Vue-Praxis → für *diesen* Fall neu bewerten (Overlay §D).
- **§7:** Build-Streuung der JVM-Variante von ±40 % auf **eng** senken (Empirie); Mittelwert ~Untergrund.
- **§8:** team-spezifischer Split (§G) als zusätzliche, **nicht** generische Empfehlung aufnehmen;
  Kern-Frage explizit auf **Betrieb/Governance/Souveränität** statt Build-Kosten stützen.

**Nächste Schritte zur Belastbarkeit:**
1. **S1-Spike vom Partner** gegen einen echten Berufsschul-Export rechnen/durchführen lassen — validiert
   das Kern-Risiko mit dem Team, das es am besten kann (speist [Soll-Bebauungsplan §5](Soll-Bebauungsplan-und-Schnittstellen.md)).
2. **Build-vs-Run mit realen Sätzen** neu rechnen: interner Junior-/Azubi-Satz + zugekaufte Senior-Tage
   (inkl. Review/Governance) **vs.** DL-Festpreis/T&M für Kern + S1.
3. **Lock-in-Sicherungen** (IP/Doku/Escrow/Exit, Standards-only) als **Angebots-/Vertragsbedingung**
   formulieren — Voraussetzung dafür, dass die externe Route souveränitätsverträglich ist.

---

## I. Geschätzte Kosten der empfohlenen Lösung (Split-Modell, 5 Jahre)

> Geltungsbereich wie [TCO §1](TCO-Kostenaufstellung.md): **strategischer Datenkern + Integrations-Layer +
> Self-Service-Portale**. Die **Commodity-SaaS-Abos** der Satelliten (Shop, Billing, Abo, PMS) fallen in
> **jeder** Variante an und sind hier nicht eingerechnet (separate Beschaffung, [Capability-Map](Zielarchitektur-Capability-Map.md)).

| Posten | 5-Jahres-Schätzung | Träger |
|---|---|---|
| Build Kern + Integrations-Layer (nach bewiesenem Ansatz) | **0,8–1,2 Mio. €** | externer Partner |
| Migration S1 (Berufsschul-Datenmodell, N:M/Debitoren — initial) | **0,15–0,25 Mio. €** | externer Partner |
| Run/Maintain Kern (0,23–0,29 Mio. € p.a. × 5) | **1,15–1,45 Mio. €** | externer Partner |
| Satelliten/Portale/BI nach gleichem Ansatz (zugekaufte Senior-Begleitung; Basis-Team ist bestehende Payroll) | **0,10–0,25 Mio. €** | internes Team + zugekauft |
| **Summe Kern-Investition (5 J)** | **≈ 2,2–3,1 Mio. €** | |

> Annualisiert inkl. Betrieb ≈ **0,45–0,65 Mio. €/Jahr**. Risiko-adjustiert für Kern + S1 die günstigste
> robuste Variante (§E), da das teuerste Einzelrisiko beim Partner am kleinsten ist.

## J. Einsparung interner Personalkosten durch die optimierte Landschaft

**Ausgangslage (Ist):** praktisch keine durchgängigen Prozesse — Stammdaten verteilt über mehrere
Programme (Doppelpflege), interne Abstimmung über E-Mail (Medienbrüche), Kundenkommunikation
**ausschließlich händisch**. Das ist ein **hohes** Automatisierungs-/Konsolidierungspotenzial.

**Modell (alle Annahmen offengelegt & ersetzbar):**

| Größe | Annahme (Band) | zentral |
|---|---|---|
| betroffene Verwaltungs-/Sachbearbeitungs-FTE (Kundenangabe: nur so viele arbeiten überhaupt mit den aktuellen Software-Systemen) | 25–35 | 30 |
| durch Konsolidierung + Automation + Self-Service **freisetzbarer Zeitanteil** | 15–25 % | 20 % |
| vollkostete Verwaltungs-FTE p.a. | 55–65 k € | 60 k € |
| **freigesetzte interne Kapazität p.a.** | **≈ 0,2–0,5 Mio. €** | **≈ 0,35 Mio. €** |
| **über 5 Jahre** | **≈ 1,0–2,5 Mio. €** | **≈ 1,8 Mio. €** |

**Woher die Einsparung kommt (Hebel ↔ Architektur):**

| Hebel | Beseitigt | Anteil (ca.) |
|---|---|:--:|
| **Stammdaten-Konsolidierung** (Golden Record, ein Stamm) | Doppelpflege, Suchen/Abgleichen über Programme | 25–30 % |
| **Automatisierte Kundenkommunikation** (Anmeldebestätigung, E-Rechnung, Erinnerungen) | händische Korrespondenz | 25–30 % |
| **Self-Service-Portale** (Anmeldung, Rechnungsabruf, „Meine Trainings") | Inbound-Anfragen, manuelle Erfassung | ~20 % |
| **Integration + SSO + Workflows** | E-Mail-Abstimmung, Medienbrüche, Re-Keying, Mehrfach-Logins | 15–20 % |
| **Dubletten-/Fehlerreduktion** | Nacharbeit (falsche Rechnungen, Doppelkontakte) | ~10 % |

> **Ehrlich eingeordnet:** „Einsparung" heißt hier primär **Kapazitätsfreisetzung + vermiedene
> Neueinstellungen bei Wachstum + Tempo/Qualität** — **nicht** automatisch Stellenabbau. Cash-wirksam
> wird sie nur durch eine **bewusste Redeployment-/Einstellungsstopp-Entscheidung**. Gegenzurechnen ist
> ein **einmaliger Change-/Adoptions-Aufwand** (Prozessdefinition, Schulung — da heute kaum Prozesse
> existieren: nennenswert), den der Partner-Build/die Begleitung mit abdeckt.

### J-2. Wegfallende direkte IT-Kosten der heutigen Landschaft (Ist)

Zusätzlich zur Kapazitätsfreisetzung (§J, „weiche" Einsparung) entfallen mit der Konsolidierung
**harte, cash-wirksame Ist-Kosten** der heute betriebenen/abzulösenden Systeme:

| Heutige direkte IT-Kosten (Ist) | p.a. | nach Konsolidierung |
|---|---|---|
| Anpassungen/Customizing der Altsysteme | ~50 k € | **entfällt** |
| Software-Lizenzen der abzulösenden Systeme | ~50 k € | **entfällt** (mittelfristig) |
| Administration/Betrieb (DevOps) der Systeme | (Ist-Basis) | **≈ −50 % (Halbierung geplant)** |
| **direkt wegfallend (Lizenz + Anpassung)** | **≈ 100 k €/Jahr** | **≈ 0,5 Mio. € / 5 J** |

> Diese **~0,10 Mio. €/Jahr** sind — anders als die Kapazitätsfreisetzung — **sofort cash-wirksam**
> (keine Redeployment-Entscheidung nötig). Der **halbierte DevOps-/Administrationsaufwand** senkt
> zusätzlich die laufenden Betriebskosten der neuen Lösung (§I) und ist im Netto unten konservativ
> noch nicht voll angesetzt.

## K. Business Case — Kosten vs. Nutzen (5 Jahre)

| | 5-Jahres-Größenordnung | annualisiert |
|---|---|---|
| **Kern-Investition** (§I, inkl. Betrieb) | 2,2–3,1 Mio. € | 0,45–0,65 Mio. €/Jahr |
| **Freigesetzte interne Kapazität** (§J) | 1,0–2,5 Mio. € | 0,2–0,5 Mio. €/Jahr |
| **Wegfallende direkte IT-Kosten** (§J-2, Lizenz + Anpassung) | ~0,5 Mio. € | ~0,10 Mio. €/Jahr |
| **Nutzen gesamt** | 1,5–3,0 Mio. € | 0,3–0,6 Mio. €/Jahr |
| **Netto (Nutzen − Investition)** | von **−1,6 Mio. €** (konservativ) bis **+0,8 Mio. €** (optimistisch) | |

**Lesart:** Mit der kleineren Anwenderbasis (**30 statt 55 FTE**) trägt die freigesetzte Kapazität
allein die Investition nicht; **zusammen mit den ~0,10 Mio. €/Jahr direkt wegfallenden IT-Kosten**
(Lizenzen + Anpassungen) und dem halbierten DevOps-Aufwand rückt der Break-even aber wieder in
Reichweite: im **zentralen** Fall nahezu kostendeckend über fünf Jahre, im **optimistischen** klar
positiv. Die rein monetäre Rechnung bleibt knapp — den Ausschlag geben die nicht monetarisierten
Effekte:

- **Compliance:** E-Rechnungs-Pflicht ist ohnehin verpflichtend (kein „Nice-to-have") — der Kern löst
  sie als Nebenprodukt (S11, im Showcase technisch nachgewiesen).
- **Skalierung ohne Kopfzahl-Wachstum:** steigende Teilnehmer-/Fallzahlen ohne proportional mehr
  Verwaltung — der eigentliche strategische Hebel bei „heute alles händisch".
- **Kundenerlebnis/Tempo & Fehlerquote** (schnellere Antworten, korrekte Rechnungen, weniger Dubletten).

### Sensitivität — freisetzbarer Zeitanteil (der entscheidende Hebel)

Basis: **30 betroffene Verwaltungs-FTE × 60 k € vollkostet** (= 1,8 Mio. €/Jahr im Scope).
Kern-Investition zentral ≈ **2,7 Mio. €** über 5 J (Upfront Build+Migration ~1,2 Mio. + Betrieb ~0,30 Mio. €/Jahr).

| Freisetzbarer Anteil | Einsparung p.a. | Einsparung 5 J | **Netto 5 J** (− Invest. ~2,7 Mio.) | Payback |
|---|---|---|---|---|
| **10 %** (sehr konservativ) | ~0,18 Mio. € | ~0,9 Mio. € | **~ −1,8 Mio. €** | > 5 J (deckt nicht einmal den Betrieb) |
| **20 %** (zentral) | ~0,36 Mio. € | ~1,8 Mio. € | **~ −0,9 Mio. €** | > 5 J (deckt Betrieb + Teil der Investition) |
| **30 %** (optimistisch) | ~0,54 Mio. € | ~2,7 Mio. € | **~ ±0 Mio. €** | **~ Jahr 5 (Break-even)** |

> Die Tabelle zeigt **nur die weiche Kapazitätsfreisetzung**. Hinzu kommen die **~0,10 Mio. €/Jahr
> direkt wegfallenden IT-Kosten** (§J-2: Lizenzen + Anpassungen) → **+0,5 Mio. € über 5 J in jeder
> Zeile**, womit sich Netto/Payback verbessern: 10 % → ~ −1,3 Mio. €; 20 % → ~ −0,4 Mio. €
> (Payback ~Jahr 4–5); 30 % → ~ +0,5 Mio. € (Payback ~Jahr 4). Der **halbierte DevOps-Aufwand** wirkt
> obendrauf. Die **rein monetäre** Rechtfertigung bleibt knapp — tragend werden die nicht
> monetarisierten Effekte (Pflicht-Compliance E-Rechnung, Skalierung ohne Kopfzahl-Wachstum,
> Tempo/Qualität). Sowohl der *freisetzbare Anteil* als auch die *betroffene FTE-Zahl* sind im
> Verwaltungs-Workshop seriös vor Ort zu kalibrieren.

---

## Vorbehalte
- Schätz-/Größenordnungen, keine Angebote; **relative Reihenfolge** belastbarer als absolute Zahlen
  ([TCO §1](TCO-Kostenaufstellung.md)).
- **Einsparungs-Modell (§J/§K) ist eine Annahmen-Rechnung** — FTE-Zahl, freisetzbarer Anteil und
  Vollkostensatz (§J) sowie die Ist-Kostenpositionen (Lizenzen/Anpassungen/DevOps-Aufwand, §J-2) sind
  im Workshop mit der Verwaltungs-/IT-Leitung zu verifizieren; die Kapazitäts-Einsparung ist
  Kapazitätsfreisetzung, nicht zwingend Cash/Stellenabbau (die §J-2-Positionen dagegen sofort
  cash-wirksam). Change-/Adoptionsaufwand gegenrechnen.
- Die Run/Maintain- und Governance-Risiken bleiben **bewusst konservativ** — der Showcase beweist sie nicht.
- Dies ist **Entscheidungs-Input**, kein Beschluss; die Setzung gehört in den GF-Workshop
  ([Grobkonzept §11](Grobkonzept-IT-Konsolidierung.md)).
