# Grobkonzept / Entscheidungsvorlage: IT-Konsolidierung Bildungsanbieter Immobilienwirtschaft

> Lieferobjekt: **Entscheidungsvorlage / Grobkonzept** (noch keine technische Detailplanung)
> Bewertung: **ergebnisoffen** (Make / Buy / Integrate)
> Treiber: **Plattform-EOL-Risiken UND Stammdatenfragmentierung — gekoppelt in einer Roadmap**

---

## 1. Context — warum dieses Projekt

Ein seit ~50 Jahren etablierter Anbieter von Bildungsprodukten in der Immobilienwirtschaft betreibt drei Geschäftsbereiche (Berufsschule, Hochschule, Akademie) plus ein Gästehaus. Über die Jahre ist jeder Bereich auf eine **eigene Kernsoftware** abgewandert. Die Systeme sind **datentechnisch auseinandergelaufen**: derselbe Kunde existiert mehrfach und inkonsistent in drei getrennten Stammdatentöpfen. Gleichzeitig laufen **zwei der drei Kernplattformen auf ein Ende ihres tragfähigen Betriebsmodells zu** (UniTop/Dynamics-NAV → Cloud-Zwang; OpenEduCat on-prem → künftig nicht mehr möglich, nicht updatefähig customized).

Daraus entstehen zwei akute, **voneinander abhängige** Schmerzen:
- **Geschäftlich:** Marketing kann keine Kampagnen-Zielgruppen bilden, Controlling hat keinen systemübergreifenden Forecast — weil es keinen Single Source of Truth für Kunden/Unternehmen gibt.
- **Technisch/Risiko:** Zwei Kernplattformen sind betrieblich bzw. updatetechnisch am Limit; jede erzwungene Migration entwertet teures Customizing und droht die Kosten zu sprengen.

**Intendiertes Ergebnis dieses Dokuments:** eine entscheidungsreife Vorlage für die Geschäftsführung, die (a) die Ist-Lage verdichtet, (b) ein Zielbild und Architektur-Leitplanken setzt, (c) die Optionen Make/Buy/Integrate fair bewertet, (d) eine begründete Empfehlung samt gekoppelter Roadmap und Einführungsstrategie gibt, und (e) die noch zu klärenden Punkte und nächsten Schritte benennt.

---

## 2. Ist-Architektur (verdichtet)

| Bereich | Kennzahlen | Kernsystem (System of Record) | Satellitensysteme | Status / Risiko |
|---|---|---|---|---|
| **Berufsschule** | 1.500 Schüler gleichzeitig, 50 Lehrer, Blockunterricht, bundesweit, halbjährl. Gebühren | **Legacy Java/Tomcat-Webanwendung** — nur noch Stammdaten (Unternehmen↔Schüler↔Ausbilder) + Rechnungsstellung | WebUntis (Stundenplan/Stunden), Moodle (Lehrmaterial), Teams (Online-Vorlesung/Kommunikation, je Schüler MS-Konto), Schild-NRW (jährl. Reporting) | Legacy, aber funktional reduziert & stabil; beherrscht N:M Person↔Company + mehrere Debitoren |
| **Hochschule** | 1.200 Studierende, Bachelor/Master, >300 €/Monat je Student | **OpenEduCat (Odoo-Basis)** — fast alle Prozesse, nur Bruchteil der Funktionen genutzt | **Sket** (Prüfungswesen, ausgelagert) | Hoch: ~5.000 h Customizing nicht versionsneutral → schmerzhafte Major-Updates; on-prem-Betrieb laut Hersteller künftig nicht mehr möglich; **Odoo-Datenmodell: Person nur zu 1 Company; keine mehreren Debitoren** → Dubletten nach 3 Jahren |
| **Akademie** | ~400 Seminare/Jahr, Tagungen, Kongresse | **UniTop (Microsoft Dynamics NAV-basiert), on-prem** — fast alle IT-Anforderungen | TarLemon o.ä. (Publizieren/Verkauf von Lehrinhalten), Moodle (Online-Events, **unklar**) | Hoch: NAV-Cloud-Zwang → Customizing entwertet, Kosten explodieren |
| **Gästehaus** | 120 Betten; Mo–Fr v.a. Schüler, Fr–So Hotelgäste/Tagungen | **unklar** (PMS?) | — | Eigene Domäne (Hotellerie), bisher nicht beleuchtet |

**Sonderfall „Bochum-Prüfung"** (Berufsschule): Prüfungsvorbereitung wie ein Seminar buchbar + Prüfung mit Gebühr + eigenes Zertifikat → fachlich nah an Akademie-/Prüfungslogik.

**Ressourcen:**
- **Interne IT:** 2 Python-Entwickler + 3 Azubis (Junior-Niveau, keine Erfahrung mit großen Systemarchitekturen).
- **Externer Dienstleister:** 10 Entwickler (3 Senior), Java-Backend, **kennt das Haus**, hat die ursprüngliche Tomcat-App gebaut, bietet aktiv „Re-Synchronisierung" an.
- **Marketing + Controlling** sind organisatorisch zusammengelegt, arbeiten heute über manuelle Exporte.

---

## 3. Kernprobleme (verdichtet)

1. **Kein Single Source of Truth für Stammdaten.** Drei getrennte Kunden-/Unternehmenstöpfe; viele Kunden in allen drei Bereichen (typischer Lebenszyklus: Ausbildung → Studium → Seminare) → Dubletten, Inkonsistenz, kein 360°-Kundenbild.
2. **Verlorene Beziehungsfähigkeit.** N:M Person↔Company und „mehrere Debitoren je Company/Person" existierten in der Java-App, fehlen in Odoo. Dies ist ein **fachliches Modellierungsproblem**, das jede Zielarchitektur abbilden muss.
3. **Plattform-EOL / Betriebsmodell-Risiko.** UniTop-NAV (Cloud-Zwang) und OpenEduCat (on-prem-Ende, nicht updatefähig) zwingen ohnehin zu Entscheidungen — beide mit Customizing-Entwertung und Kostenrisiko.
4. **Kein systemübergreifendes Marketing/Controlling.** Keine Kampagnenbasis, kein Forecast, keine gemeinsamen Metriken; alles per Export zusammengesucht.
5. **Organisatorische Tragfähigkeit.** Interne IT kann große Systeme nicht eigenständig bauen/betreiben; Abhängigkeit vom externen Dienstleister (mit Interessenkonflikt: er bietet die Lösung an, die er selbst liefern würde).

---

## 4. Zielbild & Architektur-Leitplanken

Unabhängig von Make/Buy/Integrate sollten folgende Prinzipien gelten (sie strukturieren die spätere Detailentscheidung):

- **L1 — Single Source of Truth für Stammdaten.** Eine zentrale, führende Quelle für **Personen, Unternehmen, Beziehungen (N:M) und Debitoren**. Fachsysteme konsumieren/abonnieren, statt eigene Kunden-Wahrheiten zu pflegen.
- **L2 — Domänentrennung statt Monolith.** Berufsschule, Hochschule, Akademie und Gästehaus sind **fachlich verschiedene Domänen** (verschiedene Prozesse, Pflichten, Reporting). Ein einziges „alles"-System ist unrealistisch; Ziel ist ein **föderiertes Modell mit gemeinsamem Stammdaten-Kern**.
- **L3 — Integration als entkoppelte Schicht.** Datenaustausch über einen **Integration-/Sync-Layer (Events/APIs)**, nicht über Punkt-zu-Punkt-Bastellösungen. Reduziert die Abhängigkeit von einzelnen Produkten.
- **L4 — Customizing-Disziplin.** Versionsneutralität/Updatefähigkeit als harte Vorgabe (die Hauptlektion aus dem OpenEduCat-Debakel). Standard nutzen, Anpassung über unterstützte Erweiterungspunkte.
- **L5 — Kunde im Mittelpunkt (CRM/MDM).** Marketing & Controlling erhalten ihre Datenbasis aus dem zentralen Kern, nicht aus den Fachsystemen direkt.
- **L6 — Pflicht-/Spezialsysteme bleiben Satelliten.** Schild-NRW, WebUntis, Moodle, Teams, Sket sind etablierte Spezial-/Pflichtsysteme — **nicht ersetzen, sondern anbinden** (Reporting/LMS/Prüfung).
- **L7 — Tragfähiger Betrieb & Governance.** Architektur muss zur realen Mannschaft passen (interne Junioren + externer DL); klare Rollen, kein Single-Vendor-Lock-in beim Stammdaten-Kern.
- **L8 — Moderne Basis ist Pflicht.** Jede Ziellösung muss auf einem modernen, gepflegten technischen Stack laufen. **Entscheidungsregel Betriebsmodell:** Cloud/SaaS **nur** mit state-of-the-art Weboberfläche (moderne, responsive, performante Web-UX); ist die Cloud-/Web-Oberfläche nicht erstklassig, ist eine **moderne On-Premise-Lösung** vorzuziehen. Veralteter Stack oder EOL-Pfad (wie UniTop/NAV on-prem) ist ausgeschlossen.

---

## 5. Strategische Optionen (ergebnisoffen bewertet)

Bewertungskriterien: Stammdaten-Konsolidierung · EOL-Risiko-Entschärfung · Time-to-Value · Kostenrisiko · Abhängigkeit/Lock-in · Betreibbarkeit durch eigene Mannschaft.

### Option A — **Integrate**: Zentrale Stammdaten-/CRM-Drehscheibe vorschalten
Ein zentrales **MDM/CRM** wird als SSoT eingeführt (mit korrektem N:M- und Mehr-Debitoren-Modell). Fachsysteme bleiben zunächst, werden über den Sync-Layer angebunden. Dubletten werden durch Matching/Golden-Record konsolidiert.
- ✅ Löst das **wichtigste** geschäftliche Problem (Stammdaten/Marketing/Controlling) **zuerst und unabhängig** von den EOL-Entscheidungen.
- ✅ Niedrigstes Risiko, schnellster sichtbarer Nutzen, gut etappierbar.
- ➖ Löst die EOL-Plattformrisiken **nicht** — die bleiben parallel zu adressieren (siehe Roadmap).
- ➖ Betreibt zwischenzeitlich mehr Systeme (Kern + Fachsysteme).

### Option B — **Buy**: Konsolidierung auf 1 Standard-/Branchenplattform
Möglichst alle Bereiche auf eine Plattform (z. B. ein Campus-/Education-Management-Suite oder ein CRM/ERP mit Bildungs-Add-ons) zusammenführen, Altsysteme ablösen.
- ✅ Langfristig ein Datenmodell, weniger Schnittstellen.
- ➖ **Hohes Risiko & lange Time-to-Value**; drei sehr unterschiedliche Domänen + Pflichtsysteme (Schild-NRW etc.) selten von einem Produkt abgedeckt → erneut Customizing-Falle.
- ➖ Großes „Rip-and-Replace"-Risiko, Big-Bang-Charakter, hohe Kosten.

### Option C — **Make**: Eigen-/Dienstleisterentwicklung (Java-DL baut zentralen Kern neu)
Der bekannte Java-Dienstleister entwickelt den zentralen Stammdaten-/CRM-Kern (Wiederaufleben der N:M-Fähigkeit der alten App) und bindet Fachsysteme an bzw. löst sie schrittweise ab.
- ✅ Passgenaues Datenmodell, Domänen-Know-how, Hauskenntnis.
- ➖ **Lock-in & Interessenkonflikt** (DL bewertet/empfiehlt seine eigene Leistung); Schlüsselpersonenrisiko; interne Mannschaft kann es nicht mittragen.
- ➖ Baut Eigenentwicklung dort, wo Standard existiert (CRM/MDM) → langfristige Wartungslast.

### Empfehlung (zur Entscheidung, nicht gesetzt)
**Hybrid „Integrate-first, dann selektiv Buy/Make je Domäne":**
1. **Zuerst Option A** — zentralen Stammdaten-/CRM-Kern als SSoT etablieren. Das entschärft sofort den größten Geschäftsschmerz, ist risikoarm und schafft die Grundlage, auf der alle EOL-Migrationen später sauber andocken.
2. **Parallel** die EOL-Plattformen **je Domäne** bewerten und nacheinander modernisieren (Buy bevorzugt, wo guter Standard existiert; Make/DL gezielt dort, wo Domänenlogik einzigartig ist) — **aber erst, nachdem der Stammdaten-Kern steht**, damit Migrationen nicht erneut auf isolierte Datentöpfe laufen.
3. Den **Java-Dienstleister in eine definierte Rolle** bringen (Integration-Layer / Stammdaten-Kern, wo seine Hauskenntnis zählt), aber **Architektur-Hoheit und Produktauswahl unabhängig** halten (Interessenkonflikt entschärfen).

Begründung: Beide Treiber sind gekoppelt — aber der Stammdaten-Kern ist die **Voraussetzung**, ohne die jede Plattformmigration die Fragmentierung nur fortschreibt. „Integrate-first" liefert frühen Nutzen und senkt das Risiko der nachgelagerten, teureren Plattformentscheidungen.

---

## 6. Gekoppelte Roadmap (Phasen)

**Phase 0 — Assessment & Entscheidungsreife (kurz).**
- Detail-Aufnahme der Datenmodelle/Volumina je System; Dubletten-/Überschneidungsanalyse (wie viele Kunden in ≥2 Bereichen real).
- Klärung der offenen Punkte (§9): Gästehaus-PMS, Fehltage-/Krankmeldungs-System, Moodle-Akademie, Sket-Schnittstellen, TarLemon-Rolle, EOL-Termine & Vertragslage (UniTop-Cloud, OpenEduCat).
- Anforderungen Marketing/Controlling konkretisieren (welche Metriken, welche Kampagnenlogik).
- Bewertung externer Standardprodukte (Buy-Kandidaten) je Domäne; DL-Angebot fachlich einordnen.

**Phase 1 — Stammdaten-Kern / SSoT (Treiber: Daten).**
- Auswahl/Aufbau zentrales MDM/CRM mit **N:M Person↔Company + mehrere Debitoren**.
- Golden-Record-/Matching-Regeln, Dublettenbereinigung; initiale Migration aus Java-App, OpenEduCat, UniTop.
- **Read-only-Konsolidierung zuerst** (360°-Sicht für Marketing/Controlling), dann schrittweise Schreibhoheit.

**Phase 2 — Integration-Layer & erste Nutzeneffekte.**
- Sync von Stammdaten in die Fachsysteme; Marketing-/Controlling-Auswertung auf dem Kern.
- Damit ist der **größte Geschäftsschmerz vor den Plattformmigrationen gelöst**.

**Phase 3 — Plattform-Modernisierung je Domäne (Treiber: EOL), nacheinander.**
- Priorisierung nach Risiko/Termin: i. d. R. **UniTop/NAV** (Cloud-Zwang) und **OpenEduCat** (on-prem-Ende) zuerst.
- Je Domäne Make/Buy-Entscheidung gegen das Zielbild; jede Migration dockt an den bestehenden Stammdaten-Kern an (keine neuen Inseln).
- Customizing-Disziplin (L4) als Abnahmekriterium.

**Phase 4 — Legacy-Abbau & Konsolidierung.**
- Java-App auf Stammdaten/Rechnung schrittweise ablösen, sobald Kern + Domänensysteme tragen.
- Gästehaus-Domäne (PMS) integrieren.

---

## 7. Einführungsstrategie

**Empfehlung: schrittweise / inkrementell, kein Big Bang.**
- Begründung: drei Live-Geschäftsbereiche mit laufendem Betrieb (1.500 Schüler, 1.200 Studierende, 400 Seminare/Jahr), schwache interne Mannschaft, hohe Pflicht-/Reporting-Abhängigkeiten → Big-Bang-Risiko zu hoch.
- **Strangler-Pattern:** neuer Stammdaten-Kern wächst neben den Altsystemen; Funktion für Funktion wird verlagert; Altsysteme schrumpfen, bis sie abschaltbar sind (so wie die Java-App heute schon nur noch Stammdaten/Rechnung macht — dieses Muster fortsetzen).
- **Pilot zuerst:** Stammdaten-Konsolidierung + Marketing-/Controlling-Sicht als erster abgeschlossener, messbarer Nutzenblock.

---

## 8. Governance, Rollen & Ressourcen

- **Architektur-/Produkt-Hoheit unabhängig vom liefernden DL** (Interessenkonflikt: er bewertet seine eigene Lösung). Empfehlung: neutrale Architektur-/PM-Instanz (intern verantwortet, ggf. punktuell extern begleitet).
- **Externer Java-DL:** einsetzen für Integration-Layer / Stammdaten-Kern / Migration (Hauskenntnis, Senior-Kapazität) — mit klaren, updatefähigen Standards und Exit-/Übergabefähigkeit.
- **Interne IT (Python-Junioren + Azubis):** Betrieb, Auswertungen, kleinere Erweiterungen, Wissensaufbau — **nicht** als alleinige Bauträger großer Systeme.
- **Fachbereiche** (Berufsschule/Hochschule/Akademie/Marketing) als Product Owner ihrer Domäne einbinden.

---

## 9. Offene Punkte (in Phase 0 zu klären)

- **Gästehaus:** eingesetztes PMS/Hotelsystem? In Scope der Konsolidierung (mind. Stammdaten/Debitoren-Anbindung)?
- **Berufsschule:** wo werden **Fehltage/Krankmeldungen** geführt — Schild-NRW oder WebUntis?
- **Akademie:** wird **Moodle** tatsächlich genutzt, wofür?
- **Hochschule:** Schnittstellen-/Datenlage **Sket** (Prüfungswesen) — bleibt eigenständig, nur anbinden?
- **Akademie:** Rolle/Abgrenzung **TarLemon** (Publizieren/Verkauf Lehrinhalte) — bleibt Satellit?
- **EOL-Fakten:** belastbare Termine & Vertrags-/Lizenzlage zu **UniTop-Cloud-Zwang** und **OpenEduCat on-prem-Ende** (bestimmt die Dringlichkeit von Phase 3).
- **Datenqualität:** reales Ausmaß der Dubletten/Überschneidungen (Mengengerüst) — bestimmt Aufwand Phase 1.
- **Compliance:** DSGVO/Datenschutz bei zentralem Kundenkern; ggf. bildungsrechtliche Vorgaben (Schild-NRW, Hochschul-Reporting).
- **Budget-/Zeitrahmen & Risikoappetit** der Geschäftsführung (bestimmt Tempo Make vs. Buy).

---

## 10. Risiken & Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| Customizing-Falle (wie OpenEduCat) wiederholt sich | L4-Disziplin als Abnahmekriterium; Standard vor Anpassung; Versionsneutralität vertraglich |
| Lock-in / Interessenkonflikt externer DL | Architektur-Hoheit intern; Exit-/Übergabefähigkeit; offene Schnittstellen |
| Big-Bang-/Migrationsausfall im Live-Betrieb | Strangler-Pattern, Domäne-für-Domäne, Read-only-Konsolidierung zuerst |
| Interne Mannschaft überfordert | Realistische Rollenverteilung; Wissensaufbau; externer Senior-Support |
| EOL-Termine überholen das Projekt | Phase 0 klärt Termine; Phase 3 risikobasiert priorisiert; ggf. Brückenbetrieb |
| Datenqualität/Dubletten schlechter als erwartet | Frühe Dublettenanalyse in Phase 0; Matching-/Golden-Record-Regeln |

---

## 11. Nächste Schritte (konkret)

1. **Phase-0-Assessment beauftragen** (Datenmodell-/Volumen-Aufnahme, Dublettenanalyse, EOL-Fakten, offene Punkte §9).
2. **Anforderungskatalog Marketing/Controlling** + Zielbild Stammdaten-Kern (N:M, Mehr-Debitoren) festschreiben.
3. **Make/Buy-Marktscreening** für den Stammdaten-/CRM-Kern und je EOL-Domäne; DL-Angebot fachlich einordnen.
4. **Entscheidungs-Workshop Geschäftsführung** auf Basis dieser Vorlage: Grundrichtung (Empfehlung Hybrid/Integrate-first) bestätigen, Budget-/Zeitrahmen setzen, Governance festlegen.
5. Darauf aufbauend **detaillierten Umsetzungs- und Einführungsplan** (Phase 1) erstellen.

---

## 12. Erfolgs-/Verifikationskriterien

- **Stammdaten:** definierter Golden Record je Kunde; messbarer Rückgang der Dubletten (Zielwert in Phase 0 festlegen); N:M- und Mehr-Debitoren-Fälle korrekt abbildbar.
- **Marketing/Controlling:** Kampagnen-Zielgruppen und systemübergreifender Forecast **ohne manuelle Exporte** erzeugbar.
- **EOL-Risiko:** UniTop- und OpenEduCat-Abhängigkeit mit dokumentiertem Migrationspfad entschärft; keine Customizing-Entwertung im Zielzustand.
- **Betrieb:** Lösung durch die reale Mannschaft (intern + definierte DL-Rolle) betreibbar; keine Single-Person-/Single-Vendor-Abhängigkeit beim Kern.
- **Vorgehen:** jede Phase liefert einen eigenständig nutzbaren Block (inkrementell verifizierbar), Altsysteme nachweislich schrumpfend (Strangler).
