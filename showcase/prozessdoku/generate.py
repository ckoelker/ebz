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
    lane_y = {a: i * LANE_H for i, a in enumerate(lanes)}
    pool_h = len(lanes) * LANE_H
    pos = {}
    for n in nodes:
        w, h = nodes[n]["w"], nodes[n]["h"]
        pos[n] = (x0 + spalte[n] * COL_W, lane_y[lane_of[n]] + (LANE_H - h) / 2, w, h)

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
               f'        <omgdc:Bounds x="{POOL_LBL}" y="{lane_y[a]}" width="{total_w - POOL_LBL}" height="{LANE_H}"/>',
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


def main() -> None:
    OUT.mkdir(exist_ok=True)
    df = lade_log()

    # 1) Subprozess je Phase (Detailsicht: die einzelnen Schritte)
    erzeugt = []
    for phase in PHASE_LABEL:
        teil = df[df["phase"] == phase]
        if teil.empty:
            continue
        pfad = OUT / f"sub-{phase.lower()}.bpmn"
        schreibe_bpmn(entdecke(teil, "name"), pfad)
        name_zu_akteur = dict(zip(teil["name"], teil["akteur"]))
        name_zu_system = dict(zip(teil["name"], teil["system"]))
        roh = pfad.read_text(encoding="utf-8")
        if LANES_AKTIV:
            # echte Swimlanes (Lane = Person), System im Task-Label — fertiges Layout, layout.mjs überspringt es.
            pfad.write_text(_swimlane(roh, name_zu_akteur, name_zu_system, PHASE_LABEL[phase], pfad.stem),
                            encoding="utf-8")
        else:
            # Fallback: „Person · System" je Schritt ins Task-Label, Layout via bpmn-auto-layout.
            pfad.write_text(_label_anreichern(roh, name_zu_akteur, name_zu_system), encoding="utf-8")
        erzeugt.append(pfad.name)

    # 2) Übersicht: je Fall die Phasen-Sequenz (konsekutive Duplikate je Fall zusammenfassen)
    ueb = df.copy()
    ueb["phase_label"] = ueb["phase"].map(PHASE_LABEL)
    erster_der_phase = ueb["phase"].ne(ueb.groupby("fall")["phase"].shift())
    ueb = ueb[erster_der_phase]
    schreibe_bpmn(entdecke(ueb, "phase_label"), OUT / "uebersicht.bpmn")
    erzeugt.append("uebersicht.bpmn")

    print(f"OK: {len(erzeugt)} BPMN-Dateien in {OUT}")
    for name in erzeugt:
        print("  -", name)


if __name__ == "__main__":
    main()
