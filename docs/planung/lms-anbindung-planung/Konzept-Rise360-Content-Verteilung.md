# Konzept: Rise-360-Content-Verteilung → N OpenOLAT-Mandanten-Instanzen

> **Status: KONZEPT / PLANUNG — kein Code, nichts gebaut.** Ergänzt
> [Konzept-LMS-Anbindung.md](Konzept-LMS-Anbindung.md) (LMS-Auswahl) und
> [L1-Code-Plan-LMS-Anbindung.md](L1-Code-Plan-LMS-Anbindung.md) (Einschreibung/SSO) um die bisher offene
> Frage: **Wie kommen die in Rise 360 erstellten Lernnuggets in die OpenOLAT-Instanzen — bei
> Mandantenfähigkeit per Instanz?**

## Auftrag / Ausgangslage
Der Kunde erstellt Lernnuggets in **Articulate Rise 360** (web-basiertes Autorentool). Die Inhalte sollen
in unsere OpenOLAT-Landschaft gelangen und dort an die Mandanten verteilt werden, wobei die
**Mandantenfähigkeit per Instanz** geregelt ist (jeder Mandant = eigene OpenOLAT-Instanz, eigene
`openolat`-DB, harte Isolation).

## Kernbefund: Push statt Pull

**Es gibt keinen brauchbaren API-„Pull" der authored Inhalte aus Rise 360.** Articulate bietet **keine
öffentliche Content-Export-API**, mit der man Rise-Nuggets programmatisch herausziehen könnte. Die wenigen
Articulate-APIs hängen an **Reach 360** (deren eigenem LMS) und betreffen User/Einschreibung/Reporting —
**nicht** die Content-Extraktion aus dem Autorentool.

Der vendor-gestützte Handoff ist immer ein **Export-/Publish-Paket**:

| Format | Eignung hier |
|---|---|
| **SCORM 1.2** | **Empfohlen.** OpenOLAT spielt es nativ; identisch zum bestehenden Import-Pfad. |
| SCORM 2004 | OpenOLAT kann nur 1.2 (siehe Auswahl-Konzept) → vermeiden. |
| xAPI (Tin Can) | Nur bei echtem Tracking-Bedarf; braucht eine **LRS**, OpenOLAT-Support schwächer. |
| AICC / Web-HTML / PDF | Nicht relevant. |

→ **Vertragsobjekt der Integration = SCORM-1.2-Paket** (`.zip`). **Auslöser = der Autor, der exportiert**
(Push), **nicht** ein Timer/Job bei uns (kein Pull).

### Was „Ingestion = Push, autoren-getriggert" konkret heißt
- **Pull (verbaut):** *Wir* fragen aktiv/zeitgesteuert bei Rise nach geänderten Nuggets. Setzt eine
  Content-Export-API voraus, die Rise nicht hat.
- **Push (gangbar):** *Der Autor* klickt in Rise auf **Export/Publish (SCORM)** und gibt das `.zip` an uns.
  „Autoren-getriggert" = der Mensch im Autorentool ist der Auslöser. Zwei Spielarten desselben Eingangs:
  - **Portal-Upload** — Seite „Nugget hochladen" im (Mandanten-)Portal; Mensch-im-Loop, am robustesten.
  - **Ingest-Endpunkt** — `POST …/nuggets` (multipart-Zip), maschinelle Annahme; gleicher Eingang ohne Klick,
    falls später ein Skript/CI-Schritt den Export ablegt.

  **Merksatz:** *Rise schiebt zu uns (Push), wir holen nichts (kein Pull); ausgelöst vom Autor beim Export,
  nicht von einem Timer bei uns.*

## Zielarchitektur: Ingest → Content-Outbox → Per-Instanz-Dispatcher

Spiegelt **eins zu eins das bewährte Einschreibungs-Muster** (`EnrollmentDispatcher` + Outbox), nur für
Content statt Teilnehmer:

```
Autor (Rise 360)
   │  Export SCORM 1.2 (.zip)
   ▼
[Push-Ingest]  Portal-Upload  oder  POST /nuggets (multipart)
   │  Paket entgegennehmen, Nugget-ID + Version + Checksumme festhalten
   ▼
[Content-Outbox]  ein Job je (Nugget × Ziel-Instanz)
   │  idempotent, retry-/dead-letter-fähig
   ▼
[Verteil-Dispatcher]  je Mandant: Base-URL + Service-Creds
   │  PUT  /restapi/repo/entries           (multipart: file/filename/resourcename/displayname)
   │  POST /restapi/repo/entries/{key}/status   newStatus=published
   ▼
OpenOLAT-Instanz Mandant A · B · C …   (Lernende starten per Keycloak-SSO)
```

Der OpenOLAT-Import ist **per-Instanz** → „Mandantenfähigkeit per Instanz" ist dafür sogar das **saubere**
Modell: der Verteildienst routet je Mandant gegen dessen `/restapi/repo/entries`. Isolation gratis.

**Wiederverwendung aus dem Bestand:**
- Import-Endpunkt + curl-Sequenz bereits bewiesen in `showcase/openolat/lms-import-seed.sh`
  (`PUT /restapi/repo/entries` + `POST …/status published`; **`-X PUT` erzwingen, sonst 405**).
- Outbox-/Dispatcher-Muster: `lms/service/EnrollmentDispatcher.java` + `lms/model/Kurseinschreibung.java`.
- REST-Client + Basic-Auth-Muster: `lms/openolat/OpenolatApi.java` (Service-Account je Instanz).

## Datenmodell (Skizze, im `mdm`-Schema — keine neue Schema-Freigabe nötig)
- **`LernNugget`** — fachliche Identität des Inhalts: eigene stabile **Nugget-ID**, Titel, aktuelle
  Version, Quelle=`RISE360`, Checksumme des letzten Pakets. **Eigene ID, weil Rise-Re-Exporte keine
  stabile Manifest-ID garantieren** (gleiche Lektion wie HubSpots `ebz_party_id` als idProperty).
- **`NuggetVerteilung`** — Mapping **(Nugget × Instanz) → `repoEntryKey`** + zuletzt verteilte Version +
  Status. Das ist der Schlüssel für **idempotente Updates in place** statt Dubletten.
- **`ContentVerteilAuftrag`** (Outbox) — Job je (Nugget, Instanz, Operation IMPORT/UPDATE/RETIRE), Status
  NEU/IN_ARBEIT/ERLEDIGT/FEHLER/TOT, Versuche, letzter Fehler, Prozess-Baggage (Europe/Berlin).

## Fallstricke (das eigentlich Wichtige)
- **Re-Export = neues Zip ohne stabile Identität.** Update in place braucht das eigene
  Nugget-ID → `(Instanz, repoEntryKey)`-Mapping. Das Seed-Skript behilft sich mit Idempotenz-per-
  `displayname` — für echten Betrieb zu fragil.
- **Update vs. Lernfortschritt.** SCORM-Tracking hängt an der Ressource; frischer Import = frische
  Ressource = verwaiste Versuche. **Entscheidung nötig:** Inhalt im bestehenden repo-entry ersetzen vs.
  als neue Version führen. → Gating-Punkt unten.
- **Fan-out-Konsistenz/Drift.** N Instanzen, Teilausfälle → Retry/Dead-Letter **+ Versions-Drift-Report**
  (welche Instanz hat welche Nugget-Version). Liefert die Outbox.
- **Medienlast.** Rise-SCORM-Zips mit Video/Bildern werden groß → Upload-Limits + Storage **× N Instanzen**.
  Abwägen: SCORM (self-contained, kopiert Medien überallhin) vs. zentrales Hosting + xAPI/LTI-Referenz.
- **Kein Update-Webhook von Rise.** Der Autor muss re-triggern; Handoff mit **Versionsfeld + Checksumme**,
  damit ein No-op-Re-Upload erkannt (und übersprungen) wird.
- **xAPI nur bei echtem Bedarf.** OpenOLATs SCORM-Player ist reif; xAPI/cmi5 schwächer + braucht LRS.
  Default = SCORM 1.2.

## Elegantere Alternativen? Git-Unterstützung?
Zwei Achsen sauber trennen: **Quellseite (Rise)** vs. **Verteilseite (→ Instanzen)**.

### Git
- **An der Quelle: nein.** Rise 360 ist geschlossene SaaS — **kein Git, keine VCS-Anbindung, kein
  Push/Pull**. Der authored Source liegt in Articulates Cloud; weder versionierbar noch heraus-pushbar.
  Der **manuelle Export-Schritt bleibt** unabhängig vom Folgesystem.
- **Git als Verteil-Backbone (GitOps): möglich, hier aber nicht elegant.** Exportierte SCORM-Zips ins Repo
  committen, CI verteilt bei `push`. Mehrwert: **Audit-Trail, Versionierung, Rollback, Release-Diff**.
  Aber: SCORM-Zips sind **große Binär-Blobs** → braucht **Git-LFS** (Git ist schlecht für Media); der Autor
  muss trotzdem manuell exportieren + committen (Push-Arbeit nur verschoben, nicht entfernt). **Marginaler
  Netto-Gewinn** — Revisionssicherheit gegen Binär-in-Git-Schmerz.

### „Einmal hosten, mehrfach referenzieren" statt „in N kopieren"
Die eigentlich **elegantere Verteil-Architektur:** Inhalt **einmal zentral hosten**, jede OpenOLAT-Instanz
per **LTI** (oder xAPI) **referenzieren** statt das Paket in jede Instanz zu importieren. Updates landen an
**einer** Stelle, sofort überall sichtbar → **kein Fan-out, keine Drift, Storage × 1**. OpenOLAT ist
LTI-Consumer (LTI 1.1/1.3, „externes Tool"-Kurselement) → passt.

**Haken:** Rise ist selbst **kein LTI-Provider** (exportiert SCORM/xAPI/Web) → eine **Hosting-Schicht
dazwischen** nötig, die das Paket hält und LTI spricht:
- **SCORM Cloud (Rustici)** — hostet SCORM/xAPI zentral **und ist LTI-Provider**: Rise-Export einmal hoch,
  jede Instanz launcht per LTI. Genau die „host-once"-Eleganz. **Aber:** kommerzielle SaaS + **weiterer
  US-Auftragsverarbeiter** (AVV/Datenresidenz — gleiche Sorge wie HubSpot) + **Tracking-Hoheit wandert
  dorthin**, nicht OpenOLAT.
- **xAPI + eigene LRS + CDN** — elegant für „eine Quelle, viele Consumer", aber OpenOLATs xAPI/cmi5-Reife
  ist schwächer als sein SCORM-Player.

**Trade-off:** SCORM-Kopie-in-N = robust, offline-fest, self-contained, Tracking lokal — aber Drift +
Storage × N. Host-once-per-LTI = driftfrei, zentral aktualisierbar — aber Zusatzkomponente + (SCORM Cloud)
Kosten/AVV + Tracking-Hoheit extern.

**Empfehlung:** Default bleibt **SCORM-Kopie-in-N** (deckt sich mit Bestand/Auswahl, keine Fremd-SaaS,
Tracking lokal). „Host-once-per-LTI" als **dokumentierte Option**, falls Drift/Storage über viele Mandanten
und häufige Updates zum echten Schmerz werden — dann ist die zentrale Hosting-Schicht (inkl. AVV-Klärung)
gerechtfertigt. Reine Git-GitOps-Verteilung: **nicht empfohlen** (Binär-Last ohne die Drift-Lösung).

> **Topologie-Rückwirkung:** Genau diese Storage-×-N-/Drift-Last bei video-schweren Nuggets straft die
> **„Instanz-pro-Mandant"**-Variante ab und spricht für **eine geteilte OpenOLAT-Instanz mit eingeschränkter
> Mandanten-CI**. Das ist als Kriterien **41/42** in die 4er-Vergleichsmatrix eingeflossen und dort neu
> bewertet → siehe
> [../mandanten-vermarktung-planung/LMS-Plattformvergleich-OpenOLAT-Moodle.md §4b/§12.1](../mandanten-vermarktung-planung/LMS-Plattformvergleich-OpenOLAT-Moodle.md).

## Offene Gating-Punkte (vor jeder Realisierung mit dem Kunden klären)
1. **Articulate-Plan?** Hat der Kunde **Reach 360 (Pro)**? Ändert nichts am Content-Export (bleibt
   SCORM/xAPI), könnte aber bedeuten, dass Reach 360 als konkurrierendes Auslieferungs-LMS im Spiel ist →
   **SoR der Auslieferung festlegen**, nicht doppelt ausliefern.
2. **Update-Lebenszyklus:** Sollen Nugget-Updates **bestehenden Lernfortschritt erhalten** oder als **neue
   Version** laufen? Treibt Mapping- und Ersetzungs-Design (repo-entry ersetzen vs. neu).
3. **SCORM-Version** der Rise-Exporte (1.2 → OpenOLAT; 2004 → wäre ein Problem, siehe Auswahl-Konzept).
4. **Eigene Tabellen** (`LernNugget`/`NuggetVerteilung`/`ContentVerteilAuftrag`) ins bestehende `mdm`-Schema
   — keine neue Schema-Freigabe nötig, aber zu bestätigen.
5. **Verteil-Architektur:** SCORM-Kopie-in-N (Default) vs. „host-once-per-LTI" (zentrale Hosting-Schicht,
   z. B. SCORM Cloud) — abhängig von Mandantenzahl, Update-Frequenz und AVV-Bereitschaft. Siehe Abschnitt
   „Elegantere Alternativen".

## Phasen-Skizze (analog L0–L5)
- **RC0 — Ingest:** Push-Eingang (Portal-Upload **oder** `POST /nuggets`), Paket validieren (SCORM 1.2),
  `LernNugget` + Checksumme/Version anlegen. Noch ohne Verteilung.
- **RC1 — Verteilung an EINE Instanz:** `ContentVerteilAuftrag` + Dispatcher → `PUT /repo/entries` +
  publish; `NuggetVerteilung`-Mapping setzen; idempotenter Re-Import (Checksumme).
- **RC2 — Update in place:** geklärten Lebenszyklus umsetzen (ersetzen vs. neue Version), Fortschritts-Frage.
- **RC3 — Fan-out auf N Mandanten:** je-Instanz-Routing, Drift-Report, Dead-Letter/Cockpit-Sicht.

## Quellen / zu verifizieren
- Articulate 360 / Reach-360-API-Umfang (Pull-Befund: kein Content-Export) — beim Kunden/Plan gegenprüfen.
- OpenOLAT 20.1 `restapi/openapi.json`: `PUT /repo/entries`, `POST /repo/entries/{key}/status` (im Seed
  bereits live verifiziert).
