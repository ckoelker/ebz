"""
Prozessdoku-Generator (Living Documentation).

Liest den im E2E-Test erzeugten OpenTelemetry-Span-Log (spans.jsonl) und entdeckt mit PM4py
(Inductive Miner) das Prozessmodell, das als BPMN gespeichert wird:

  - sub-<phase>.bpmn  : ein Subprozess je fachlicher Phase (Detailsicht)
  - uebersicht.bpmn   : Phasen-Ablauf (Grobsicht; Lanes/Call-Activities ergänzt layout.mjs)

Mehrere Test-Szenarien (Fälle) im Log → der Miner erzeugt automatisch Gateways/Verzweigungen.
Eingabe-Schema je Zeile: {fall, name, startEpochNanos, akteur, system, typ, phase}.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pandas as pd
import pm4py
from pm4py.objects.bpmn.exporter import exporter as bpmn_exporter

BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"

ROOT = Path(__file__).resolve().parent
SPANS = ROOT.parent / "integration" / "target" / "prozess-log" / "spans.jsonl"
OUT = ROOT / "out"

# Echte Swimlanes (Lane = Akteur) in den Subprozessen? True → eigener Swimlane-Layouter (_swimlane,
# komplettes DI); False → „Person · System" nur im Task-Label, Layout via bpmn-auto-layout.
LANES_AKTIV = True

# Reihenfolge + Klartext der Phasen (Übersicht/Dateinamen).
PHASE_LABEL = {
    "ANFRAGE_DUBLETTEN": "Anfrage & Dublettenprüfung",
    "EINLADUNG": "Login-Einladung",
    "AZUBI_ANMELDUNG": "Azubi-Anmeldung",
    "EBZ_BESTAETIGUNG": "EBZ-Bestätigung",
    "VERTRAG": "Vertragsbestätigung",
    "PROVISIONIERUNG": "Provisionierung Drittsysteme",
    "RECHNUNGSLAUF": "Rechnungslauf",
}


def lade_log() -> pd.DataFrame:
    if not SPANS.exists():
        sys.exit(f"Span-Log nicht gefunden: {SPANS}\nBitte zuerst die E2E-Tests laufen lassen.")
    zeilen = [json.loads(z) for z in SPANS.read_text(encoding="utf-8").splitlines() if z.strip()]
    if not zeilen:
        sys.exit(f"Span-Log ist leer: {SPANS}")
    df = pd.DataFrame(zeilen)
    # Unkorrelierte Spans (kein Szenario-Baggage, z. B. aus Test-Setup) ignorieren.
    df = df[df["fall"] != "unbekannt"]
    if df.empty:
        sys.exit("Keine korrelierten Prozess-Spans (prozess.fall) im Log.")
    df["timestamp"] = pd.to_datetime(df["startEpochNanos"], unit="ns", utc=True)
    return df.sort_values(["fall", "startEpochNanos"]).reset_index(drop=True)


def entdecke(df: pd.DataFrame, aktivitaet_spalte: str):
    """Formatiert den Log auf PM4py-Standardspalten und entdeckt ein BPMN (Inductive Miner)."""
    formatiert = pm4py.format_dataframe(
        df.rename(columns={aktivitaet_spalte: "_aktivitaet"}),
        case_id="fall",
        activity_key="_aktivitaet",
        timestamp_key="timestamp",
    )
    return pm4py.discover_bpmn_inductive(formatiert)


def _slug(text: str) -> str:
    text = (text or "").lower()
    for a, b in (("ä", "ae"), ("ö", "oe"), ("ü", "ue"), ("ß", "ss"), ("&", "und")):
        text = text.replace(a, b)
    return re.sub(r"[^a-z0-9]+", "_", text).strip("_") or "x"


def _stabile_ids(xml_text: str, stamm: str) -> str:
    """
    Ersetzt PM4pys zufällige UUID-Element-Ids durch deterministische, sprechende Ids
    (z. B. {@code task_azubi_anmeldung}, {@code flow_start__task_...}), damit die committeten
    .bpmn bei gleichem Prozess byte-identisch bleiben (CI-`git diff`-Verify).
    """
    root = ET.fromstring(xml_text)
    prozess = root.find(f"{{{BPMN_NS}}}process")
    mapping: dict[str, str] = {}
    benutzt: set[str] = set()

    def eindeutig(basis: str) -> str:
        kandidat, i = basis, 2
        while kandidat in benutzt:
            kandidat, i = f"{basis}_{i}", i + 1
        benutzt.add(kandidat)
        return kandidat

    # 1) Knoten (Tasks, Events, Gateways) — Reihenfolge-unabhängig über sprechende Ids
    for el in prozess:
        tag = el.tag.split("}", 1)[1]
        eid = el.get("id")
        if eid is None or tag == "sequenceFlow":
            continue
        if tag == "startEvent":
            basis = "start"
        elif tag == "endEvent":
            basis = "end"
        elif tag == "task":
            basis = "task_" + _slug(el.get("name"))
        else:
            basis = _slug(tag) + "_" + _slug(el.get("name"))
        mapping[eid] = eindeutig(basis)

    # 2) Sequenzflüsse — über die (bereits stabilen) Quell-/Ziel-Ids benannt
    for el in prozess:
        if el.tag.split("}", 1)[1] != "sequenceFlow":
            continue
        quelle = mapping.get(el.get("sourceRef"), "x")
        ziel = mapping.get(el.get("targetRef"), "x")
        mapping[el.get("id")] = eindeutig(f"flow_{quelle}__{ziel}")

    # 3) Prozess/Diagramm/Plane
    mapping[prozess.get("id")] = f"process_{stamm}"
    diagram = root.find("{http://www.omg.org/spec/BPMN/20100524/DI}BPMNDiagram")
    if diagram is not None:
        mapping[diagram.get("id")] = f"diagram_{stamm}"
        plane = diagram.find("{http://www.omg.org/spec/BPMN/20100524/DI}BPMNPlane")
        if plane is not None and plane.get("id"):
            mapping[plane.get("id")] = f"plane_{stamm}"

    # Globales Token-Replace (DI nutzt id="<eid>_gui" + bpmnElement="<eid>" → folgt automatisch).
    # Längste Ids zuerst, damit keine Teil-Treffer entstehen.
    for alt in sorted(mapping, key=len, reverse=True):
        xml_text = xml_text.replace(alt, mapping[alt])
    return xml_text


def _ist_parallelphase(teil: pd.DataFrame) -> bool:
    """
    Generische Erkennung einer **nebenläufigen** Phase: ALLE Schritte tragen denselben
    {@code parallelgruppe}-Marker (gesetzt z. B. von der Outbox-Provisionierung je Zielsystem) und es
    gibt mindestens zwei verschiedene Aktivitäten. Dann werden sie als parallele Zweige gerendert —
    unabhängig vom Phasen-Namen und von der Zahl der Ziele (skaliert auf WebUntis, Suite8, Moodle …).
    Gemischte Phasen (teils markiert) fallen auf das normale Mining zurück.
    """
    if "parallelgruppe" not in teil.columns:
        return False
    markiert = teil["parallelgruppe"].notna()
    return bool(markiert.all()) and teil["name"].nunique() >= 2


def _parallel_semantik(teil: pd.DataFrame, stamm: str) -> str:
    """
    Baut deterministisch ein semantisches BPMN für eine nebenläufige Phase:
    {@code start → parallelGateway(split) → [Task je Aktivität] → parallelGateway(join) → end}.
    Aktivitätsnamen/Systeme stammen aus den Spans (also weiterhin „aus Telemetrie"); nur die parallele
    Struktur wird gesetzt. Format = {@code bpmn:}-Präfix wie die pm4py-Ausgabe, damit Swimlane-Layouter,
    Einbettung und Stable-IDs die Datei unverändert weiterverarbeiten.
    """
    erst = teil.groupby("name")["startEpochNanos"].min().sort_values()
    namen = list(erst.index)
    benutzt: set[str] = set()
    tasks, flows = [], []
    for nm in namen:
        tid, i = "task_" + _slug(nm), 2
        while tid in benutzt:
            tid, i = f"task_{_slug(nm)}_{i}", i + 1
        benutzt.add(tid)
        tasks.append(f'<bpmn:task id="{tid}" name="{_xml_attr(nm)}"/>')
        flows.append((f"flow_gw_split__{tid}", "gw_split", tid))
        flows.append((f"flow_{tid}__gw_join", tid, "gw_join"))
    flows = [("flow_start__gw_split", "start", "gw_split"), *flows, ("flow_gw_join__end", "gw_join", "end")]
    elemente = [
        '<bpmn:startEvent id="start" name="Start"/>',
        '<bpmn:parallelGateway id="gw_split" gatewayDirection="Diverging"/>',
        *tasks,
        '<bpmn:parallelGateway id="gw_join" gatewayDirection="Converging"/>',
        '<bpmn:endEvent id="end" name="Ende"/>',
        *[f'<bpmn:sequenceFlow id="{fid}" sourceRef="{s}" targetRef="{t}"/>' for fid, s, t in flows],
    ]
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<bpmn:definitions xmlns:bpmn="{BPMN_NS}" targetNamespace="http://ebz/prozessdoku">\n'
            f'  <bpmn:process id="process_{stamm}" isExecutable="false">\n    '
            + "".join(elemente)
            + f'\n  </bpmn:process>\n</bpmn:definitions>\n')


def schreibe_bpmn(bpmn, pfad: Path) -> None:
    """Serialisiert semantisches BPMN (ohne Graphviz-Layout) — das Layout/DI ergänzt layout.mjs (bpmn.io)."""
    bpmn_exporter.apply(bpmn, str(pfad))
    pfad.write_text(_stabile_ids(pfad.read_text(encoding="utf-8"), pfad.stem), encoding="utf-8")


def _label_anreichern(xml_text: str, name_zu_akteur: dict, name_zu_system: dict) -> str:
    """Ergänzt jeden Task-Namen um „ — <Akteur> · <System>" (wer/wo je Schritt, viewer-unabhängig)."""
    def ersetze(m: re.Match) -> str:
        task_id, name = m.group(1), m.group(2)
        akteur = name_zu_akteur.get(name)
        system = name_zu_system.get(name)
        if not akteur and not system:
            return m.group(0)
        return f'<bpmn:task id="{task_id}" name="{name} — {akteur} · {system}"'

    return re.sub(r'<bpmn:task id="([^"]+)" name="([^"]+)"', ersetze, xml_text)


# Lane-Reihenfolge (oben→unten) anhand der Akteur-Labels aus Prozess.java.
AKTEUR_ORDER = [
    "Interessent (anonym)", "Firma (Ansprechpartner)", "Azubi", "EBZ-Sachbearbeitung", "System",
]
DI_NS = "http://www.omg.org/spec/BPMN/20100524/DI"


def _xml_attr(s: str) -> str:
    return (s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def _swimlane(xml_text: str, name_zu_akteur: dict, name_zu_system: dict, titel: str, stamm: str) -> str:
    """
    Eigener **Swimlane-Layouter** (bpmn-auto-layout kann keine Lanes): erzeugt eine Collaboration mit
    Pool + einer Lane je Akteur. Spalte = Reihenfolge (Longest-Path), Zeile = Akteur-Lane; Knoten,
    Lane-Boxen und orthogonale Kanten bekommen eigenes DI. Start/Ende/Gateways erben die Lane vom
    Nachbarn. Eingabe ist das semantische BPMN mit reinen Aktivitätsnamen; das System wird ans Label
    gehängt (die Person zeigt die Lane). Marker `swimlane-laidout` → layout.mjs überspringt die Datei.
    """
    root = ET.fromstring(xml_text)
    proc = root.find(f"{{{BPMN_NS}}}process")
    procid = proc.get("id")

    nodes, order, flows, adj, radj = {}, [], [], {}, {}
    for el in list(proc):
        tag = el.tag.split("}", 1)[1]
        eid = el.get("id")
        if tag == "sequenceFlow":
            s, t = el.get("sourceRef"), el.get("targetRef")
            flows.append((eid, s, t))
            adj.setdefault(s, []).append(t)
            radj.setdefault(t, []).append(s)
        else:
            nodes[eid] = {"tag": tag, "name": el.get("name") or ""}
            order.append(eid)
    for info in nodes.values():
        t = info["tag"]
        info["w"], info["h"] = (36, 36) if t.endswith("Event") else (50, 50) if "Gateway" in t else (100, 80)

    # Spalten via Longest-Path (Kahn-Topologie; azyklische Modelle aus den Traces).
    from collections import deque
    indeg = {n: len(radj.get(n, [])) for n in nodes}
    spalte = {n: 0 for n in nodes}
    q = deque([n for n in nodes if indeg[n] == 0])
    while q:
        u = q.popleft()
        for v in adj.get(u, []):
            spalte[v] = max(spalte[v], spalte[u] + 1)
            indeg[v] -= 1
            if indeg[v] == 0:
                q.append(v)

    def akteur(nid, seen=None):
        seen = seen or set()
        if nid in seen:
            return AKTEUR_ORDER[0]
        seen.add(nid)
        if nodes[nid]["name"] in name_zu_akteur:
            return name_zu_akteur[nodes[nid]["name"]]
        nb = (radj.get(nid) or adj.get(nid) or [None])[0]
        return akteur(nb, seen) if nb in nodes else AKTEUR_ORDER[0]

    lane_of = {n: akteur(n) for n in nodes}
    lanes = [a for a in AKTEUR_ORDER if a in set(lane_of.values())]

    LANE_H, COL_W, POOL_LBL, LANE_LBL, PAD = 110, 180, 30, 30, 40
    maxspalte = max(spalte.values()) if spalte else 0
    x0 = POOL_LBL + LANE_LBL + PAD
    total_w = x0 + maxspalte * COL_W + 140
    # Mehrere Knoten in gleicher Lane+Spalte (z. B. parallele Zweige WebUntis ∥ Suite8) vertikal
    # stapeln, sonst überlagern sie sich auf identischen Koordinaten. Lane-Höhe = größter Stapel.
    from collections import defaultdict
    bucket = defaultdict(list)
    for n in order:
        bucket[(lane_of[n], spalte[n])].append(n)
    stapel = {a: max([len(bucket[(a, c)]) for c in range(maxspalte + 1)] or [1]) for a in lanes}
    lane_h = {a: max(1, stapel[a]) * LANE_H for a in lanes}
    lane_y, _y = {}, 0
    for a in lanes:
        lane_y[a], _y = _y, _y + lane_h[a]
    pool_h = _y
    pos = {}
    for (a, c), ns in bucket.items():
        for k, n in enumerate(ns):
            w, h = nodes[n]["w"], nodes[n]["h"]
            pos[n] = (x0 + c * COL_W, lane_y[a] + k * LANE_H + (LANE_H - h) / 2, w, h)

    # laneSet
    lane_xml = [f'    <bpmn:laneSet id="laneset_{stamm}">']
    for a in lanes:
        lane_xml.append(f'      <bpmn:lane id="lane_{stamm}_{_slug(a)}" name="{_xml_attr(a)}">')
        lane_xml += [f"        <bpmn:flowNodeRef>{n}</bpmn:flowNodeRef>" for n in order if lane_of[n] == a]
        lane_xml.append("      </bpmn:lane>")
    lane_xml.append("    </bpmn:laneSet>")

    # Prozess-Innenleben übernehmen; System ans Task-Label hängen (Person = Lane).
    inner = re.search(r"<bpmn:process\b[^>]*>(.*)</bpmn:process>", xml_text, re.S).group(1).strip()

    def system_suffix(m):
        tid, nm = m.group(1), m.group(2)
        sysn = name_zu_system.get(ET.fromstring(f"<x a=\"{nm}\"/>").get("a"))
        return f'<bpmn:task id="{tid}" name="{nm} · {sysn}"' if sysn else m.group(0)

    inner = re.sub(r'<bpmn:task id="([^"]+)" name="([^"]+)"', system_suffix, inner)

    # DI: Pool, Lanes, Knoten, orthogonale Kanten
    di = [f'      <bpmndi:BPMNShape id="participant_{stamm}_di" bpmnElement="participant_{stamm}" isHorizontal="true">',
          f'        <omgdc:Bounds x="0" y="0" width="{total_w}" height="{pool_h}"/>',
          "      </bpmndi:BPMNShape>"]
    for a in lanes:
        di += [f'      <bpmndi:BPMNShape id="lane_{stamm}_{_slug(a)}_di" bpmnElement="lane_{stamm}_{_slug(a)}" isHorizontal="true">',
               f'        <omgdc:Bounds x="{POOL_LBL}" y="{lane_y[a]}" width="{total_w - POOL_LBL}" height="{lane_h[a]}"/>',
               "      </bpmndi:BPMNShape>"]
    for n in order:
        x, y, w, h = pos[n]
        di += [f'      <bpmndi:BPMNShape id="{n}_di" bpmnElement="{n}">',
               f'        <omgdc:Bounds x="{x:.0f}" y="{y:.0f}" width="{w}" height="{h}"/>',
               "      </bpmndi:BPMNShape>"]
    for fid, s, t in flows:
        sx, sy, sw, sh = pos[s]
        tx, ty, tw, th = pos[t]
        x1, y1, x2, y2 = sx + sw, sy + sh / 2, tx, ty + th / 2
        mx = (x1 + x2) / 2
        di += [f'      <bpmndi:BPMNEdge id="{fid}_di" bpmnElement="{fid}">',
               f'        <omgdi:waypoint x="{x1:.0f}" y="{y1:.0f}"/>',
               f'        <omgdi:waypoint x="{mx:.0f}" y="{y1:.0f}"/>',
               f'        <omgdi:waypoint x="{mx:.0f}" y="{y2:.0f}"/>',
               f'        <omgdi:waypoint x="{x2:.0f}" y="{y2:.0f}"/>',
               "      </bpmndi:BPMNEdge>"]

    nl = "\n"
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<!-- swimlane-laidout: eigener Layouter (showcase/prozessdoku/generate.py) -->\n'
            f'<bpmn:definitions xmlns:bpmn="{BPMN_NS}" xmlns:bpmndi="{DI_NS}"'
            f' xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"'
            f' xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="http://ebz/prozessdoku">\n'
            f'  <bpmn:collaboration id="collab_{stamm}">\n'
            f'    <bpmn:participant id="participant_{stamm}" name="{_xml_attr(titel)}" processRef="{procid}"/>\n'
            f'  </bpmn:collaboration>\n'
            f'  <bpmn:process id="{procid}" isExecutable="false">\n'
            f'{nl.join(lane_xml)}\n    {inner}\n  </bpmn:process>\n'
            f'  <bpmndi:BPMNDiagram id="diagram_{stamm}">\n'
            f'    <bpmndi:BPMNPlane id="plane_{stamm}" bpmnElement="collab_{stamm}">\n'
            f'{nl.join(di)}\n'
            f'    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n</bpmn:definitions>\n')


# Geometrie-Konstanten für die eingebetteten Phasen-Blöcke (Swimlanes).
_EVT, _GW, _TW, _TH = 36, 50, 100, 80
_COLW, _PAD, _LANE_H, _LANE_LBL, _GAP = 170, 20, 110, 24, 80


def _phase_block(phase: str, semantik: str, name_akteur: dict, name_system: dict) -> dict:
    """Layoutet eine Phase als Swimlane-Block (relativ zu (0,0)): Lanes je Akteur, Spalte =
    Reihenfolge. Liefert Geometrie + fertige laneSet-/flowElement-XML (Ids je Phase präfixiert)."""
    from collections import deque
    proc = ET.fromstring(semantik).find(f"{{{BPMN_NS}}}process")
    pref = phase.lower() + "__"
    nodes, order, flows, adj, radj = {}, [], [], {}, {}
    for el in list(proc):
        tag = el.tag.split("}", 1)[1]
        eid = pref + el.get("id")
        if tag == "sequenceFlow":
            s, t = pref + el.get("sourceRef"), pref + el.get("targetRef")
            flows.append((eid, s, t))
            adj.setdefault(s, []).append(t)
            radj.setdefault(t, []).append(s)
        else:
            nodes[eid] = {"tag": tag, "name": el.get("name") or ""}
            order.append(eid)
    for info in nodes.values():
        t = info["tag"]
        info["w"], info["h"] = (_EVT, _EVT) if t.endswith("Event") else (_GW, _GW) if "Gateway" in t else (_TW, _TH)
    indeg = {n: len(radj.get(n, [])) for n in nodes}
    col = {n: 0 for n in nodes}
    q = deque([n for n in nodes if indeg[n] == 0])
    while q:
        u = q.popleft()
        for v in adj.get(u, []):
            col[v] = max(col[v], col[u] + 1)
            indeg[v] -= 1
            if indeg[v] == 0:
                q.append(v)

    def akteur(nid, seen=None):
        seen = seen or set()
        if nid in seen:
            return AKTEUR_ORDER[0]
        seen.add(nid)
        if nodes[nid]["name"] in name_akteur:
            return name_akteur[nodes[nid]["name"]]
        nb = (radj.get(nid) or adj.get(nid) or [None])[0]
        return akteur(nb, seen) if nb in nodes else AKTEUR_ORDER[0]

    lane_of = {n: akteur(n) for n in nodes}
    lanes = [a for a in AKTEUR_ORDER if a in set(lane_of.values())]
    maxcol = max(col.values()) if col else 0
    # Knoten in gleicher Lane+Spalte (parallele Zweige) vertikal stapeln; Lane-Höhe = größter Stapel.
    from collections import defaultdict
    bucket = defaultdict(list)
    for n in order:
        bucket[(lane_of[n], col[n])].append(n)
    stapel = {a: max([len(bucket[(a, c)]) for c in range(maxcol + 1)] or [1]) for a in lanes}
    lane_h = {a: max(1, stapel[a]) * _LANE_H for a in lanes}
    lane_y, _yy = {}, 0
    for a in lanes:
        lane_y[a], _yy = _yy, _yy + lane_h[a]
    content_w = _LANE_LBL + _PAD + maxcol * _COLW + _TW + _PAD
    content_h = _yy
    rel = {}
    for (a, c), ns in bucket.items():
        for k, n in enumerate(ns):
            rel[n] = (_LANE_LBL + _PAD + c * _COLW, lane_y[a] + k * _LANE_H + (_LANE_H - nodes[n]["h"]) / 2,
                      nodes[n]["w"], nodes[n]["h"])

    fe = []
    for n in order:
        info = nodes[n]
        nm = info["name"]
        label = f"{nm} · {name_system[nm]}" if info["tag"] == "task" and nm in name_system else nm
        ins = "".join(f"<bpmn:incoming>{f[0]}</bpmn:incoming>" for f in flows if f[2] == n)
        outs = "".join(f"<bpmn:outgoing>{f[0]}</bpmn:outgoing>" for f in flows if f[1] == n)
        attr = f'id="{n}"' + (f' name="{_xml_attr(label)}"' if nm else "")
        fe.append(f"<bpmn:{info['tag']} {attr}>{ins}{outs}</bpmn:{info['tag']}>")
    fe += [f'<bpmn:sequenceFlow id="{fid}" sourceRef="{s}" targetRef="{t}"/>' for fid, s, t in flows]

    pl = phase.lower()
    ls = [f'<bpmn:laneSet id="laneset_{pl}">']
    for a in lanes:
        ls.append(f'<bpmn:lane id="lane_{pl}_{_slug(a)}" name="{_xml_attr(a)}">')
        ls += [f"<bpmn:flowNodeRef>{n}</bpmn:flowNodeRef>" for n in order if lane_of[n] == a]
        ls.append("</bpmn:lane>")
    ls.append("</bpmn:laneSet>")

    return {"phase": phase, "spid": f"sp_{pl}", "name": PHASE_LABEL[phase], "order": order, "flows": flows,
            "lanes": lanes, "lane_y": lane_y, "lane_h": lane_h, "rel": rel, "content_w": content_w,
            "content_h": content_h, "laneset": "".join(ls), "fe": "".join(fe)}


def _block_di(b: dict, ox: float, oy: float) -> list:
    """DI (Lane- + Knoten-Shapes + orthogonale Kanten) eines Phasen-Blocks ab Ursprung (ox,oy)."""
    pl = b["phase"].lower()
    out = []
    for a in b["lanes"]:
        out.append(f'<bpmndi:BPMNShape id="lane_{pl}_{_slug(a)}_di" bpmnElement="lane_{pl}_{_slug(a)}"'
                   f' isHorizontal="true"><omgdc:Bounds x="{ox + _LANE_LBL:.0f}" y="{oy + b["lane_y"][a]:.0f}"'
                   f' width="{b["content_w"] - _LANE_LBL:.0f}" height="{b["lane_h"][a]:.0f}"/></bpmndi:BPMNShape>')
    for n in b["order"]:
        rx, ry, w, h = b["rel"][n]
        out.append(f'<bpmndi:BPMNShape id="{n}_di" bpmnElement="{n}"><omgdc:Bounds x="{ox + rx:.0f}"'
                   f' y="{oy + ry:.0f}" width="{w}" height="{h}"/></bpmndi:BPMNShape>')
    for fid, s, t in b["flows"]:
        sx, sy, sw, sh = b["rel"][s]
        tx, ty, tw, th = b["rel"][t]
        x1, y1, x2, y2 = ox + sx + sw, oy + sy + sh / 2, ox + tx, oy + ty + th / 2
        mx = (x1 + x2) / 2
        out.append(f'<bpmndi:BPMNEdge id="{fid}_di" bpmnElement="{fid}"><omgdi:waypoint x="{x1:.0f}"'
                   f' y="{y1:.0f}"/><omgdi:waypoint x="{mx:.0f}" y="{y1:.0f}"/><omgdi:waypoint x="{mx:.0f}"'
                   f' y="{y2:.0f}"/><omgdi:waypoint x="{x2:.0f}" y="{y2:.0f}"/></bpmndi:BPMNEdge>')
    return out


def _embedded(sub_semantik: dict, name_akteur: dict, name_system: dict, collapsed: bool) -> str:
    """
    EINE Datei mit je Phase einem eingebetteten {@code bpmn:subProcess} (mit Swimlanes), linear verkettet.
    {@code collapsed=False}: aufgeklappt inline (Gesamt-Sicht). {@code collapsed=True}: eingeklappt mit
    eigener DI-Plane je Phase → im Camunda Modeler per Drilldown aufklappbar (Übersicht).
    """
    phases = [p for p in PHASE_LABEL if p in sub_semantik]
    blocks = [_phase_block(p, sub_semantik[p], name_akteur, name_system) for p in phases]

    kette = ["of_start"] + [b["spid"] for b in blocks] + ["of_end"]
    flows = [(f"oflow_{i}", kette[i], kette[i + 1]) for i in range(len(kette) - 1)]
    fout = {s: [f for f, a, _ in flows if a == s] for s in kette}
    fin = {s: [f for f, _, z in flows if z == s] for s in kette}

    # Prozess-XML
    pe = ['    <bpmn:startEvent id="of_start" name="Start">'
          + "".join(f"<bpmn:outgoing>{f}</bpmn:outgoing>" for f in fout["of_start"]) + "</bpmn:startEvent>"]
    for b in blocks:
        pe.append(f'    <bpmn:subProcess id="{b["spid"]}" name="{_xml_attr(b["name"])}">'
                  + "".join(f"<bpmn:incoming>{f}</bpmn:incoming>" for f in fin[b["spid"]])
                  + "".join(f"<bpmn:outgoing>{f}</bpmn:outgoing>" for f in fout[b["spid"]])
                  + b["laneset"] + b["fe"] + "</bpmn:subProcess>")
    pe.append('    <bpmn:endEvent id="of_end" name="Ende">'
              + "".join(f"<bpmn:incoming>{f}</bpmn:incoming>" for f in fin["of_end"]) + "</bpmn:endEvent>")
    pe += [f'    <bpmn:sequenceFlow id="{f}" sourceRef="{s}" targetRef="{t}"/>' for f, s, t in flows]

    def shape(eid, x, y, w, h, expanded=False):
        ex = ' isExpanded="true"' if expanded else ""
        return (f'      <bpmndi:BPMNShape id="{eid}_di" bpmnElement="{eid}"{ex}>'
                f'<omgdc:Bounds x="{x:.0f}" y="{y:.0f}" width="{w:.0f}" height="{h:.0f}"/></bpmndi:BPMNShape>')

    def oedge(fid, a, z, box):
        ax, ay, aw, ah = box[a]
        zx, zy, zw, zh = box[z]
        x1, y1, x2, y2 = ax + aw, ay + ah / 2, zx, zy + zh / 2
        mx = (x1 + x2) / 2
        return (f'      <bpmndi:BPMNEdge id="{fid}_di" bpmnElement="{fid}"><omgdi:waypoint x="{x1:.0f}"'
                f' y="{y1:.0f}"/><omgdi:waypoint x="{mx:.0f}" y="{y1:.0f}"/><omgdi:waypoint x="{mx:.0f}"'
                f' y="{y2:.0f}"/><omgdi:waypoint x="{x2:.0f}" y="{y2:.0f}"/></bpmndi:BPMNEdge>')

    nl = "\n"
    if collapsed:
        # Übersicht: kleine eingeklappte Kästen in einer Reihe; Detail je Phase in eigener Plane.
        COLLW, COLLH, ROWY = 150, 90, 80
        box = {}
        cur = 40
        box["of_start"] = (cur, ROWY + COLLH / 2 - _EVT / 2, _EVT, _EVT)
        cur += _EVT + _GAP
        for b in blocks:
            box[b["spid"]] = (cur, ROWY, COLLW, COLLH)
            cur += COLLW + _GAP
        box["of_end"] = (cur, ROWY + COLLH / 2 - _EVT / 2, _EVT, _EVT)
        root = [shape("of_start", *box["of_start"])]
        root += [shape(b["spid"], *box[b["spid"]]) for b in blocks]
        root.append(shape("of_end", *box["of_end"]))
        root += [oedge(f, s, t, box) for f, s, t in flows]
        diagrams = [f'  <bpmndi:BPMNDiagram id="diagram_root"><bpmndi:BPMNPlane id="plane_root"'
                    f' bpmnElement="process_uebersicht">\n{nl.join(root)}\n'
                    f'  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>']
        for b in blocks:
            diagrams.append(f'  <bpmndi:BPMNDiagram id="diagram_{b["phase"].lower()}">'
                            f'<bpmndi:BPMNPlane id="plane_{b["phase"].lower()}" bpmnElement="{b["spid"]}">\n'
                            f'{nl.join(_block_di(b, 0, 0))}\n  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>')
        proc_id, diagram_xml = "process_uebersicht", nl.join(diagrams)
    else:
        # Gesamt: alle Phasen aufgeklappt inline auf einer Plane.
        OY = 60
        maxh = max(b["content_h"] for b in blocks)
        cy = OY + maxh / 2
        box = {}
        di = []
        cur = 40
        box["of_start"] = (cur, cy - _EVT / 2, _EVT, _EVT)
        di.append(shape("of_start", *box["of_start"]))
        cur += _EVT + _GAP
        for b in blocks:
            box[b["spid"]] = (cur, OY, b["content_w"], b["content_h"])
            di.append(shape(b["spid"], cur, OY, b["content_w"], b["content_h"], expanded=True))
            di += _block_di(b, cur, OY)
            cur += b["content_w"] + _GAP
        box["of_end"] = (cur, cy - _EVT / 2, _EVT, _EVT)
        di.append(shape("of_end", *box["of_end"]))
        di += [oedge(f, s, t, box) for f, s, t in flows]
        proc_id = "process_gesamt"
        diagram_xml = (f'  <bpmndi:BPMNDiagram id="diagram_gesamt"><bpmndi:BPMNPlane id="plane_gesamt"'
                       f' bpmnElement="process_gesamt">\n{nl.join(di)}\n'
                       f'  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>')

    # process_id im subProcess-Container muss zur Plane passen → Prozess-Id setzen
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<!-- swimlane-laidout: eingebettete Subprozesse ({"eingeklappt/drilldown" if collapsed else "aufgeklappt"}) -->\n'
            f'<bpmn:definitions xmlns:bpmn="{BPMN_NS}" xmlns:bpmndi="{DI_NS}"'
            f' xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"'
            f' xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="http://ebz/prozessdoku">\n'
            f'  <bpmn:process id="{proc_id}" isExecutable="false">\n{nl.join(pe)}\n  </bpmn:process>\n'
            f'{diagram_xml}\n</bpmn:definitions>\n')


def main() -> None:
    OUT.mkdir(exist_ok=True)
    df = lade_log()

    name_akteur = dict(zip(df["name"], df["akteur"]))
    name_system = dict(zip(df["name"], df["system"]))

    # 1) Subprozess je Phase (Detailsicht: die einzelnen Schritte)
    erzeugt = []
    sub_semantik = {}  # phase -> semantisches BPMN (reine Ids/Namen) für die Gesamt-Sicht
    for phase in PHASE_LABEL:
        teil = df[df["phase"] == phase]
        if teil.empty:
            continue
        pfad = OUT / f"sub-{phase.lower()}.bpmn"
        name_zu_akteur = dict(zip(teil["name"], teil["akteur"]))
        name_zu_system = dict(zip(teil["name"], teil["system"]))
        if _ist_parallelphase(teil):
            # Nebenläufige Phase (Marker an allen Schritten): parallele Zweige statt geminter Sequenz.
            roh = _parallel_semantik(teil, pfad.stem)
        else:
            schreibe_bpmn(entdecke(teil, "name"), pfad)
            roh = pfad.read_text(encoding="utf-8")
        sub_semantik[phase] = roh
        if LANES_AKTIV:
            # echte Swimlanes (Lane = Person), System im Task-Label — fertiges Layout, layout.mjs überspringt es.
            pfad.write_text(_swimlane(roh, name_zu_akteur, name_zu_system, PHASE_LABEL[phase], pfad.stem),
                            encoding="utf-8")
        else:
            # Fallback: „Person · System" je Schritt ins Task-Label, Layout via bpmn-auto-layout.
            pfad.write_text(_label_anreichern(roh, name_zu_akteur, name_zu_system), encoding="utf-8")
        erzeugt.append(pfad.name)

    # 2) Übersicht: eingebettete, EINGEKLAPPTE Phasen-Subprozesse — im Modeler je Phase per Drilldown
    #    aufklappbar (eigene DI-Plane je Phase, mit Swimlanes).
    (OUT / "uebersicht.bpmn").write_text(
        _embedded(sub_semantik, name_akteur, name_system, collapsed=True), encoding="utf-8")
    erzeugt.append("uebersicht.bpmn")

    # 3) Gesamt-Sicht: alle Phasen AUFGEKLAPPT inline, je Phase mit Swimlanes (Lane = Person).
    (OUT / "gesamt.bpmn").write_text(
        _embedded(sub_semantik, name_akteur, name_system, collapsed=False), encoding="utf-8")
    erzeugt.append("gesamt.bpmn")

    print(f"OK: {len(erzeugt)} BPMN-Dateien in {OUT}")
    for name in erzeugt:
        print("  -", name)


if __name__ == "__main__":
    main()
