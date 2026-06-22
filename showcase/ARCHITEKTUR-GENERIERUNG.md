# Architektur-Übersicht — Generier-Anleitung (versioniert)

> **Was das ist:** die *Anweisung*, mit der Claude die Architektur-Grafiken jederzeit
> **aktualisiert neu erzeugt**. Nur diese Datei ist versioniert. Die erzeugten Artefakte
> (MD + PDF) landen **unversioniert** in `./tmp/` (per `.gitignore` ausgeschlossen).
>
> Aufruf: „Regeneriere die Architektur-Übersicht nach `showcase/ARCHITEKTUR-GENERIERUNG.md`."

Es gibt **zwei Themen**, je in einer *mit-*-(Produkt/Status/Technik) und einer
*beschreibenden* (produkt-/technologieneutralen) Fassung:
1. **IST-/Showcase-Architektur** (`ARCHITEKTUR*`) — was heute im `showcase/`-Monorepo läuft.
2. **Zielarchitektur / Enterprise-Stack** (`ZIELARCHITEKTUR*`) — die konsolidierte Soll-Bebauung,
   aufbauend auf der IST-Architektur und **ergänzt um alles, was noch fehlt**.

## Ziel-Artefakte (alle nach `./tmp/`, unversioniert)

| Datei | Inhalt |
|---|---|
| `tmp/ARCHITEKTUR.md` | IST, **mit Status** (🟢 gebaut · 🟡 in Arbeit · ⚪ geplant), farblich; **intern** — darf Container/Produkte/Ports nennen |
| `tmp/ARCHITEKTUR-beschreibend.md` | IST, **ohne Status**, **technologie-/produktneutral** (zum Zeigen, Regeln unten) |
| `tmp/ZIELARCHITEKTUR.md` | SOLL/Enterprise, **mit Produkten** + Planungs-Status; **intern**. Quelle: `enterprise-stack-planung/` |
| `tmp/ZIELARCHITEKTUR-beschreibend.md` | SOLL/Enterprise, **produkt-/technologieneutral** (zum Zeigen, Regeln unten) |
| `tmp/*.pdf` | PDF-Render je MD (Pipeline unten) |

## Inhalt & Quellen (Single Source of Truth)

Beide MD-Dokumente enthalten **zwei getrennte Sichten**:

1. **Fachliche Sicht — Bausteine & Domänen.** Was wir wofür bauen; `integration` (Quarkus) als
   fachliches Drehkreuz. Quelle: die fachlichen Bausteine unten + Memorys (`*-showcase.md`).
2. **Technische Sicht — Container & Schnittstellen.** Container/Ports/Protokolle.
   **Quelle = [docker-compose.yml](docker-compose.yml)** (immer dagegen abgleichen, nicht raten).

Dazu je eine **Wofür-Tabelle** (Baustein → Zweck → [Status] → Technik) und eine **Ports/Profile-Tabelle**.

### Fachliche Bausteine (Stand pflegen aus Code/Memory)

| Baustein | Zweck | Status | Technik | Memory |
|---|---|---|---|---|
| Shop (Vendure) | Verkauf physisch/digital/Seminar/WBT + Ratenpläne | 🟢 (M5 🟡) | server, worker, postgres(vendure), storefront | showcase-shop-vendure |
| Party-Kern | 1 Identität / n Bestellkontexte | 🟢 | integration, postgres(controlling) | party-kern-showcase |
| Formularverwaltung/MDM | Bildungsangebote pflegen → Shop-Projektion | 🟢 | mdm-SPA, integration | formularverwaltung-showcase |
| Anmeldung Berufsschule | Self-Service Azubi (HITL+KI → Vertrag → Rechnung) | 🟡 Backend A–G | portal, integration, keycloak | anmeldung-berufsschule-showcase |
| Rechnungsstellung/E-Rechnung | ZUGFeRD, Debitoren-Hoheit, GoBD-WORM | 🟡 R1–R7 | integration, minio, postgres | rechnungsstellung-showcase |
| Kunden-Rechnungsabruf | Rechnungen im Außenportal abrufen | 🟢 | portal, integration | kunden-rechnungsabruf-portal |
| E-Rechnung → DATEV | Übergabe an Buchhaltung | ⚪ Weg offen | integration → DATEV | rechnungsstellung-showcase |
| LMS-Anbindung | WBT-Verkauf via OpenOLAT, SSO-Launch, „Meine Trainings" | 🟢 L0–L3 | openolat, integration, portal | lms-anbindung-showcase |
| Outbox-Provisionierung | zuverlässige Drittsystem-Sync (WebUntis-Mock) | 🟢 | integration | outbox-drittsystem-provisionierung |
| Controlling | Seminar-Profitabilität, HubSpot, GuV-Forecast | 🟢 M1–M3 | integration(dlt/dbt), lightdash, minio, OpenAI | controlling-showcase |
| Prozessdoku | BPMN aus OTel-Spans (Living Docs) | ⚪ geplant | otel-collector, jaeger, phoenix | prozessdoku-showcase |
| SSO | 2 Realms (Kunde/Staff), OIDC | 🟢 | keycloak | spa-auth-oidc-client-ts |

> Bei Regeneration **Status & Kanten gegen den aktuellen Stand** (Code, git, Memory) aktualisieren.

## Styling-Konventionen (für die *mit-Status*-Version)

```
classDef gebaut  fill:#d4edda,stroke:#28a745,color:#093;
classDef arbeit  fill:#fff3cd,stroke:#f0ad4e,color:#860;
classDef geplant fill:#e9ecef,stroke:#adb5bd,color:#555,stroke-dasharray:5 4;
```

Für die *beschreibende* Version: neutrale `classDef` (z.B. blau `#eef3fb/#5b8def`), keine
Statusfarben, Status-Spalte aus der Wofür-Tabelle entfernen, geplante Kanten als normale Linien.

## Regeln für die *beschreibende* Version (WICHTIG — produkt-/technologieneutral)

Diese Fassung wird **Externen/Kollegen gezeigt**. Sie soll das *Konzept* erklären, ohne zu
verraten, mit welchen Programmen/Technologien es umgesetzt ist (keine Ideen-Übernahme).
Bei jeder Regeneration strikt einhalten:

- **Keine Produkt-/Herstellernamen** — weder Open Source noch kommerziell. Stattdessen den
  übergreifenden fachlichen Begriff (= die *Schicht*). Beispiele:
  Vendure → „Online-Shop / Vertrieb" · OpenOLAT → „Lernplattform" · Keycloak → „Single Sign-On /
  Identitäten" · HubSpot → „Vertriebs-Pipeline" · DATEV → „Buchhaltung (extern)" ·
  Lightdash → „Auswertung (BI)" · MinIO → „Objektspeicher" · ZUGFeRD → „E-Rechnung" ·
  GoBD-WORM → „revisionssicheres Archiv" · WebUntis/Lemon → „Fachsystem" ·
  Jaeger/Phoenix/OTel → „Prozess-Telemetrie / Observability". Auch Stack-Namen
  (Quarkus, Camel, LangChain4j, Vue, nginx, Postgres, OpenAI …) **weglassen**.
- **Keine konkreten Container-Namen** (`server`, `worker`, `integration`, `mdm`, `portal`, `pg`,
  `minio` …). Stattdessen logische Rollen: „Shop-Dienst", „Integrations-Dienst",
  „Verwaltungs-Cockpit", „Außenportal", „Datenbank", „Archiv".
- **Auch die Mermaid-Knoten-IDs neutralisieren**, nicht nur die sichtbaren Labels! Eine ID wie
  `moodle["Lernplattform"]` oder `hubspot[…]` ist im PDF unsichtbar, steht aber im MD-Quelltext —
  wer die `.md` öffnet, liest den Produktnamen. IDs generisch wählen (`lms`, `marketing`,
  `stundenplan`, `berufsschule`, `hochschule` …). Dieselbe Regel gilt für `class …`-Zeilen.
- **Keine Ports, keine Compose-Profile, keine Datei-/Schema-/DB-Namen** (kein `docker-compose.yml`,
  kein `vendure`/`controlling`/`lightdash`). Die **Ports/Profile-Tabelle entfällt**; an ihre Stelle
  tritt eine **Schichten-Tabelle** (Schicht → Aufgabe).
- Sicht 2 heißt hier **„Logische Architektur — Schichten & Zusammenspiel"** (nicht „Container"),
  abstrahiert auf: Oberflächen · Dienste · Daten & Speicher · Fachsysteme/Querschnitt.
- **Nicht** auf die mit-Status-Version verweisen.
- Erlaubt bleiben generische Fach-/Standardbegriffe (WBT, BPMN, GuV, Debitoren, SSO, BI, E-Rechnung,
  Mensch-in-der-Schleife) sowie die EBZ-eigenen Domänenbegriffe
  (Seminar/Tagung/Berufsschuljahr/Studiengang).

> Gegenprobe vor Abgabe: die beschreibende MD per grep nach Eigennamen durchsuchen (auch IDs!) —
> kommt einer noch vor, ersetzen. Beispiel:
> ```bash
> grep -oiE 'vendure|keycloak|quarkus|camel|hubspot|dataverse|power ?bi|lightdash|openolat|minio|moodle|webuntis|schild|sket|teams|entra|datev|business central|shopware|shopify|odoo|openeducat|unitop|trainex|semco|kill ?bill|stripe|qloapps|zugferd|xrechnung|postgres|tomcat|nav' tmp/<datei>.md | sort -u
> ```

## Zielarchitektur (`ZIELARCHITEKTUR*`) — Inhalt & Quellen

**Quelle:** `enterprise-stack-planung/` — v.a. `Zielarchitektur-Capability-Map.md`,
`Soll-Bebauungsplan-und-Schnittstellen.md` (Schnittstellen **S1–S15**) und
`Review-2026-06-Showcase-vs-Planung.md` (Showcase vs. Planung = **was schon erprobt / was fehlt**).

Beide Zielarchitektur-Fassungen enthalten:
1. **Ziel-Bebauung (Capability-Mesh):** Kern (Differenzierer: MDM/Golden-Record · CRM · Controlling) +
   Querschnitt (SSO · Integrations-Layer) + Altsysteme (ablösen/schrumpfen) + Satelliten (Commodity).
2. **Schnittstellen-Map S1–S15:** Integrations-Layer im Zentrum, alle Systeme als Connectoren mit
   Richtung (⬅ liest / ➡ schreibt / ↔) und Priorität.
3. **„Was steht — was fehlt"-Tabelle:** Reifegrad heute (erprobt/teilweise/offen) + konkrete Lücke,
   abgeleitet aus dem Review. Das ist der Kern des Auftrags „ergänze, was noch fehlt".
4. **Roadmap-Phasen** (Integrate-first: Klären → Kern → Integration → EOL-Ablösung → Legacy-Abbau).

**Planungs-Status-Marker** (mit-Produkt-Fassung): 🟢 behalten · 🟡 erweitern · 🟠 ersetzen (EOL) ·
🔵 neu · ⚪ klären · 🔒 gesetzt · ✅ im Showcase erprobt. Bei Regeneration **gegen den aktuellen
Planungsstand** (`enterprise-stack-planung/`) und den Showcase-Fortschritt abgleichen.
Die **beschreibende** Fassung folgt denselben Neutralitäts-Regeln wie oben (Produktnamen → fachliche
Schicht-Begriffe, IDs neutral); Status bleibt erlaubt, aber als neutrale Worte (vorhanden / abzulösen /
neu / zu klären) bzw. Reifegrad (erprobt / teilweise / offen).

## Render-Pipeline (MD → PDF, offline-fähig)

PDF-Render läuft über Chromium (`--print-to-pdf`) mit **lokal** abgelegtem mermaid/marked
(kein CDN zur Render-Zeit). Voraussetzung: Playwright-Chromium (`ms-playwright/chromium-*`).

```bash
cd <repo>/tmp
# 1) Libs einmalig holen (offline-fähig danach)
curl -sL -o mermaid.min.js https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js
curl -sL -o marked.min.js  https://cdn.jsdelivr.net/npm/marked@12/marked.min.js
# 2) render.mjs erzeugt self-contained HTML (siehe unten), dann Chromium druckt PDF
CHROME="$LOCALAPPDATA/ms-playwright/chromium-1223/chrome-win64/chrome.exe"   # Version anpassen
for d in ARCHITEKTUR ARCHITEKTUR-beschreibend; do
  node render.mjs "$d.md" "$d.html"
  "$CHROME" --headless=old --disable-gpu --no-sandbox --virtual-time-budget=20000 \
    --run-all-compositor-stages-before-draw --print-to-pdf-no-header \
    --print-to-pdf="$(pwd -W)/$d.pdf" "file:///$(pwd -W)/$d.html"
done
```

`render.mjs` (in `./tmp/`, unversioniert — bei Bedarf neu anlegen):

```js
import { readFileSync, writeFileSync } from 'node:fs';
const [,, mdPath, htmlPath] = process.argv;
const md = readFileSync(mdPath, 'utf8');
const html = `<!DOCTYPE html><html lang="de"><head><meta charset="utf-8"><style>
 body{font-family:'Segoe UI',Roboto,Arial,sans-serif;max-width:980px;margin:0 auto;padding:8px 24px;color:#24292e;line-height:1.55;}
 table{border-collapse:collapse;width:100%;margin:12px 0;} th,td{border:1px solid #d0d7de;padding:6px 10px;text-align:left;font-size:12.5px;vertical-align:top;} th{background:#f6f8fa;}
 code{background:#f6f8fa;padding:1px 4px;border-radius:4px;font-size:90%;} blockquote{color:#57606a;border-left:4px solid #d0d7de;margin:8px 0;padding:2px 12px;}
 h1{font-size:24px;} h2{font-size:19px;margin-top:24px;} h1,h2,h3{border-bottom:1px solid #eaecef;padding-bottom:4px;}
 .mermaid{margin:16px 0;text-align:center;page-break-inside:avoid;} .mermaid svg{max-width:100%;height:auto;} @page{size:A4;margin:14mm;}
</style></head><body><div id="content"></div>
<script src="marked.min.js"></script><script src="mermaid.min.js"></script>
<script id="src" type="text/markdown"></script><script>
 document.getElementById('src').textContent = SRC;
 document.getElementById('content').innerHTML = marked.parse(document.getElementById('src').textContent);
 document.querySelectorAll('code.language-mermaid').forEach(c=>{const pre=c.closest('pre');const d=document.createElement('div');d.className='mermaid';d.textContent=c.textContent;pre.replaceWith(d);});
 mermaid.initialize({startOnLoad:false,theme:'default',flowchart:{htmlLabels:true}});
 mermaid.run().then(()=>{document.title='READY';}).catch(e=>{document.title='ERR '+e;});
</script></body></html>`.replace('SRC', JSON.stringify(md));
writeFileSync(htmlPath, html);
```

## Mermaid-Gotchas (sonst „Syntax error in text")

- **Keine geraden Anführungszeichen `"` INNERHALB eines `["…"]`-Knotenlabels** — sie beenden den
  String vorzeitig. Stattdessen einfache `'…'` oder typografische `„…"` verwenden.
- Verifikation: nach dem Render einen Chromium-`--screenshot` der HTML ziehen und prüfen, dass
  keine Grafik „Syntax error in text" zeigt (häufig nur EINE der beiden Grafiken betroffen).
