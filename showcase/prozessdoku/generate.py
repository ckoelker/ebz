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

# Lanes je Akteur in die Subprozesse injizieren? Aktuell aus: bpmn-auto-layout layoutet Lanes/Pools
# noch nicht (würde ohne DI rendern). Erst aktivieren, wenn ein lane-fähiges Layout vorliegt.
LANES_AKTIV = False

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


def _mit_lanes(xml_text: str, name_zu_akteur: dict, titel: str, stamm: str) -> str:
    """
    Hüllt den Prozess in eine Collaboration mit einem Pool + Lanes je Akteur (= „von welcher Person").
    Jeder Knoten wird der Lane seines Akteurs zugeordnet; Start/Ende/Gateways erben vom Nachbarn.
    Das Layout (inkl. Lane-Boxen) erzeugt anschließend bpmn-auto-layout.
    """
    for praefix, uri in (("bpmn", BPMN_NS), ("bpmndi", DI_NS),
                         ("omgdc", "http://www.omg.org/spec/DD/20100524/DC"),
                         ("omgdi", "http://www.omg.org/spec/DD/20100524/DI"),
                         ("xsi", "http://www.w3.org/2001/XMLSchema-instance")):
        ET.register_namespace(praefix, uri)
    root = ET.fromstring(xml_text)
    proc = root.find(f"{{{BPMN_NS}}}process")

    nach, von, knoten = {}, {}, {}
    for el in list(proc):
        tag = el.tag.split("}", 1)[1]
        if tag == "sequenceFlow":
            nach[el.get("sourceRef")] = el.get("targetRef")
            von[el.get("targetRef")] = el.get("sourceRef")
        else:
            knoten[el.get("id")] = (tag, el.get("name"))

    def akteur(nid: str, tiefe: int = 0) -> str:
        tag, name = knoten[nid]
        if name in name_zu_akteur:
            return name_zu_akteur[name]
        if tiefe > 20:
            return AKTEUR_ORDER[0]
        nachbar = von.get(nid) if tag == "endEvent" else nach.get(nid, von.get(nid))
        return akteur(nachbar, tiefe + 1) if nachbar in knoten else AKTEUR_ORDER[0]

    mitglieder: dict[str, list[str]] = {}
    for nid in knoten:
        mitglieder.setdefault(akteur(nid), []).append(nid)

    laneset = ET.Element(f"{{{BPMN_NS}}}laneSet", {"id": f"laneset_{stamm}"})
    for a in AKTEUR_ORDER:
        if a not in mitglieder:
            continue
        lane = ET.SubElement(laneset, f"{{{BPMN_NS}}}lane",
                             {"id": f"lane_{stamm}_{_slug(a)}", "name": a})
        for nid in mitglieder[a]:
            ET.SubElement(lane, f"{{{BPMN_NS}}}flowNodeRef").text = nid
    proc.insert(0, laneset)

    collab = ET.Element(f"{{{BPMN_NS}}}collaboration", {"id": f"collab_{stamm}"})
    ET.SubElement(collab, f"{{{BPMN_NS}}}participant",
                  {"id": f"participant_{stamm}", "name": titel, "processRef": proc.get("id")})
    root.insert(list(root).index(proc), collab)

    return ET.tostring(root, encoding="unicode")


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
        # „von welcher Person, in welchem System" je Schritt ins Task-Label (robust in jedem Viewer).
        name_zu_akteur = dict(zip(teil["name"], teil["akteur"]))
        name_zu_system = dict(zip(teil["name"], teil["system"]))
        pfad.write_text(_label_anreichern(pfad.read_text(encoding="utf-8"), name_zu_akteur, name_zu_system),
                        encoding="utf-8")
        # Optional echte Swimlanes (bpmn-auto-layout layoutet sie noch nicht; siehe LANES_AKTIV).
        if LANES_AKTIV:
            pfad.write_text(
                _mit_lanes(pfad.read_text(encoding="utf-8"), name_zu_akteur, PHASE_LABEL[phase], pfad.stem),
                encoding="utf-8")
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
