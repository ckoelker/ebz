#!/usr/bin/env python3
"""
Lightdash-Bootstrap (Showcase M4) — richtet eine FRISCHE Lightdash-Instanz vollständig per API ein,
damit der Nutzer nach dem SSO-Login (Keycloak `staff`) sofort ein fertiges Controlling-Dashboard sieht.
Idempotent + wiederholbar: legt nur an, was fehlt (Org/Projekt/Space/Charts/Dashboard).

Hintergrund: Passwort-Login ist deaktiviert (SSO-Zwang), daher fährt der Bootstrap denselben
OIDC-Authorization-Code-Flow wie ein Browser (Lightdash → Keycloak-Login `staff/staff` → Callback).
Der erste SSO-Nutzer wird Org-Admin (L21). Aufruf aus `showcase-aufbau.sh` (Schritt `lightdash`),
nachdem dbt die Marts ins Schema `analytics` gebaut hat.

Kein externer Zustand nötig — alles über die öffentliche Lightdash-API. ENV-Defaults passen zu
showcase/.env und docker-compose.yml.
"""
import os, re, html, sys, json, time, socket
try:
    sys.stdout.reconfigure(encoding="utf-8"); sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

LD   = os.environ.get("LIGHTDASH_URL", "http://localhost:8084")
KC_USER = os.environ.get("LIGHTDASH_BOOTSTRAP_USER", "staff")
KC_PASS = os.environ.get("LIGHTDASH_BOOTSTRAP_PASS", "staff")
ORG_NAME      = os.environ.get("LIGHTDASH_ORG_NAME", "EBZ")
PROJECT_NAME  = os.environ.get("LIGHTDASH_PROJECT_NAME", "EBZ Controlling")
SPACE_NAME    = os.environ.get("LIGHTDASH_SPACE_NAME", "Controlling")
DASHBOARD_NAME= os.environ.get("LIGHTDASH_DASHBOARD_NAME", "EBZ Controlling-Cockpit")
DBT_VERSION   = os.environ.get("LIGHTDASH_DBT_VERSION", "v1.8")

WH = {
    "type": "postgres",
    "host": os.environ.get("LIGHTDASH_WH_HOST", "postgres"),
    "port": int(os.environ.get("LIGHTDASH_WH_PORT", "5432")),
    "dbname": os.environ.get("CONTROLLING_DB", "controlling"),
    "user": os.environ.get("CONTROLLING_USER", "controlling"),
    "password": os.environ.get("CONTROLLING_PASSWORD", "controlling"),
    "schema": "analytics",
    "sslmode": "disable",
    "threads": 4,
}
DBT = {"type": "dbt", "target": "dev", "environment": [],
       "project_dir": "/usr/app/dbt", "profiles_dir": "/usr/app/dbt"}

# *.localhost wird von Python (anders als curl/Browser) nicht aufgelöst → auf 127.0.0.1 mappen,
# damit der OIDC-Redirect auf keycloak.localhost:8080 erreichbar ist.
_orig_gai = socket.getaddrinfo
def _gai(host, *a, **k):
    return _orig_gai("127.0.0.1" if str(host).endswith(".localhost") else host, *a, **k)
socket.getaddrinfo = _gai

try:
    import requests
except ImportError:
    sys.exit("FEHLT: python-Paket 'requests' (im dbt-venv vorhanden).")

JAR = {}  # name -> value; Cookie-Namen sind über die beiden Hosts (lightdash/keycloak) eindeutig.

def _grab(r):
    for sc in r.raw.headers.getlist("Set-Cookie"):
        kv = sc.split(";", 1)[0]
        if "=" in kv:
            n, v = kv.split("=", 1); JAR[n.strip()] = v.strip()

def req(method, path, **kw):
    url = path if path.startswith("http") else LD + path
    kw.setdefault("allow_redirects", False); kw.setdefault("timeout", 120)
    h = kw.pop("headers", {})
    h["Cookie"] = "; ".join(f"{n}={v}" for n, v in JAR.items())
    h.setdefault("User-Agent", "ebz-lightdash-bootstrap")
    r = requests.request(method, url, headers=h, **kw); _grab(r); return r

def ok(r):
    try: return r.json()["results"]
    except Exception: raise SystemExit(f"Unerwartete Antwort {r.status_code}: {r.text[:300]}")

# ───────────────────────── SSO-Login (OIDC Authorization Code) ─────────────────────────
def login():
    r = req("GET", "/api/v1/login/oidc")
    if r.status_code not in (301, 302, 303, 307, 308):
        raise SystemExit(f"OIDC-Start unerwartet: {r.status_code} {r.text[:200]}")
    u = r.headers["location"]
    for _ in range(5):
        r = req("GET", u)
        if r.status_code in (301, 302, 303, 307, 308): u = r.headers["location"]; continue
        break
    m = re.search(r'id="kc-form-login"[^>]*action="([^"]+)"', r.text)
    if not m:
        raise SystemExit("Keycloak-Login-Formular nicht gefunden (schon eingeloggt? falscher Realm?).")
    action = html.unescape(m.group(1))
    r = req("POST", action, data={"username": KC_USER, "password": KC_PASS, "credentialId": ""},
            headers={"Content-Type": "application/x-www-form-urlencoded"})
    loc = r.headers.get("location")
    for _ in range(6):
        if not loc: break
        r = req("GET", loc); loc = r.headers.get("location")
    me = req("GET", "/api/v1/user")
    if me.status_code != 200:
        raise SystemExit(f"SSO-Login fehlgeschlagen: {me.status_code} {me.text[:200]}")
    return me.json()["results"]

def ensure_org():
    me = req("GET", "/api/v1/user").json()["results"]
    org = req("GET", "/api/v1/org")
    if org.status_code == 200:
        return org.json()["results"]
    # Account noch ohne Org → anlegen (erster SSO-Nutzer wird Admin).
    r = req("PUT", "/api/v1/org", json={"name": ORG_NAME})
    if r.status_code != 200:
        raise SystemExit(f"Org anlegen fehlgeschlagen: {r.status_code} {r.text[:200]}")
    return req("GET", "/api/v1/org").json()["results"]

def ensure_project():
    projs = ok(req("GET", "/api/v1/org/projects"))
    p = next((x for x in projs if x["name"] == PROJECT_NAME), None)
    if p:
        pid = p["projectUuid"]
        print(f"  Projekt vorhanden: {pid}")
    else:
        body = {"name": PROJECT_NAME, "type": "DEFAULT", "dbtVersion": DBT_VERSION,
                "dbtConnection": DBT, "warehouseConnection": WH}
        r = req("POST", "/api/v1/org/projects", json=body)
        if r.status_code != 200:
            raise SystemExit(f"Projekt anlegen fehlgeschlagen: {r.status_code} {r.text[:400]}")
        pid = ok(r)["project"]["projectUuid"]
        print(f"  Projekt angelegt: {pid}")
    # dbt compile → Explores erzeugen (immer, damit neue Marts erscheinen).
    job = ok(req("POST", f"/api/v1/projects/{pid}/refresh", json={})).get("jobUuid")
    print("  dbt-Refresh:", end=" ", flush=True)
    for _ in range(80):
        time.sleep(3)
        st = ok(req("GET", f"/api/v1/jobs/{job}")).get("jobStatus")
        print(st, end=" ", flush=True)
        if st in ("DONE", "ERROR"): break
    print()
    if st != "DONE":
        raise SystemExit("dbt-Compile in Lightdash fehlgeschlagen.")
    return pid

def ensure_space(pid):
    spaces = ok(req("GET", f"/api/v1/projects/{pid}/spaces"))
    s = next((x for x in spaces if x["name"] == SPACE_NAME), None)
    if s:
        return s["uuid"]
    r = req("POST", f"/api/v1/projects/{pid}/spaces", json={"name": SPACE_NAME, "isPrivate": False})
    return ok(r)["uuid"]

# ───────────────────────── Chart-/Dashboard-Helfer ─────────────────────────
def chart(pid, sid, name, table, xfield, yspecs,
          sort_field=None, sort_desc=True, limit=500, description=""):
    """Ein kartesischer Chart. `yspecs` = Liste von (metric, kind, stackgroup|None);
    gleiche stackgroup ⇒ gestapelte Reihen. `sort_field` (Default: erste Metrik) steuert die Ordnung."""
    x = f"{table}_{xfield}"
    ys = [f"{table}_{m}" for (m, _k, _s) in yspecs]
    sf = f"{table}_{sort_field}" if sort_field else ys[0]
    series = []
    for (m, kind, stackgrp) in yspecs:
        yf = f"{table}_{m}"
        s = {"type": kind, "yAxisIndex": 0,
             "encode": {"xRef": {"field": x}, "yRef": {"field": yf}}}
        if stackgrp:
            s["stack"] = stackgrp
        series.append(s)
    body = {
        "name": name, "description": description, "tableName": table,
        "spaceUuid": sid, "dashboardUuid": None,
        "metricQuery": {
            "exploreName": table, "dimensions": [x], "metrics": ys, "filters": {},
            "sorts": [{"fieldId": sf, "descending": bool(sort_desc)}],
            "limit": limit, "tableCalculations": [], "additionalMetrics": [],
        },
        "tableConfig": {"columnOrder": [x] + ys},
        "chartConfig": {"type": "cartesian", "config": {
            "layout": {"xField": x, "yField": ys},
            "eChartsConfig": {"series": series},
        }},
    }
    r = req("POST", f"/api/v1/projects/{pid}/saved", json=body)
    if r.status_code not in (200, 201):
        raise SystemExit(f"Chart '{name}' fehlgeschlagen: {r.status_code} {r.text[:400]}")
    return ok(r)["uuid"]

def existing_charts(pid):
    # Charts liegen in Spaces; sammle Name->uuid über die Space-Inhalte.
    out = {}
    for s in ok(req("GET", f"/api/v1/projects/{pid}/spaces")):
        det = ok(req("GET", f"/api/v1/projects/{pid}/spaces/{s['uuid']}"))
        for q in det.get("queries", []):
            out[q["name"]] = q["uuid"]
    return out

def main():
    print("Lightdash-Bootstrap →", LD)
    user = login(); print(f"  SSO ok: {user['email']}")
    org = ensure_org(); print(f"  Org: {org['name']} ({org['organizationUuid']})")
    pid = ensure_project()
    sid = ensure_space(pid); print(f"  Space: {SPACE_NAME} ({sid})")

    have = existing_charts(pid)
    # (name, table, x, [(metric, kind, stackgroup)], sort_field, sort_desc, beschreibung)
    specs = [
        ("Kunden nach Umsatz", "fct_revenue_by_customer", "debitor_name",
         [("sum_customer_revenue", "bar", None)], None, True,
         "Top-Kunden nach fakturiertem Netto-Umsatz (Debitor/Golden-Record)."),
        ("Umsatz je Bereich", "fct_revenue_billed", "bereich",
         [("sum_billed_revenue", "bar", None)], None, True,
         "Fakturierter Netto-Erlös je Erlösbereich."),
        ("Fakturierter Erlös je Monat", "fct_revenue_billed", "month",
         [("sum_billed_revenue", "bar", None)], "month", False,
         "Fakturierter Netto-Erlös je Monat (Ausstellungsdatum)."),
        # Seminar-P&L je Variante: Umsatz, DB II und Ergebnis nebeneinander (M4-Ursprungsauswertung).
        ("Seminar-Deckungsbeitrag", "fct_seminar_db", "product_variant_id",
         [("total_revenue", "bar", None), ("total_db2", "bar", None), ("total_result", "bar", None)],
         "total_revenue", True,
         "Seminar-P&L je Variante: Umsatz, DB II und Ergebnis (DB II − Gemeinkosten-Umlage)."),
        # Unternehmens-Forecast (M4-Ursprung): Ist/gesichert/gewichtete Pipeline GESTAPELT je Monat,
        # plus Ergebnis-Forecast als Linie.
        ("Unternehmens-Forecast", "fct_forecast", "month",
         [("sum_actual", "bar", "fc"), ("sum_contracted", "bar", "fc"), ("sum_pipeline", "bar", "fc"),
          ("sum_result_forecast", "line", None)], "month", False,
         "Monats-Forecast: Ist + gesichert + gewichtete Pipeline (gestapelt), Ergebnis-Forecast als Linie."),
    ]
    chart_uuids = []
    for name, table, xf, yspecs, sortf, sortd, desc in specs:
        if name in have:
            print(f"  Chart vorhanden: {name}"); chart_uuids.append(have[name]); continue
        cu = chart(pid, sid, name, table, xf, yspecs, sort_field=sortf, sort_desc=sortd, description=desc)
        print(f"  Chart angelegt: {name}"); chart_uuids.append(cu)

    # Dashboard mit allen Charts (36-Spalten-Grid, 2 Spalten à w=18, h=9; Forecast volle Breite).
    dashes = ok(req("GET", f"/api/v1/projects/{pid}/dashboards"))
    if any(d["name"] == DASHBOARD_NAME for d in dashes):
        print(f"  Dashboard vorhanden: {DASHBOARD_NAME}")
        return
    layout = [(0, 0, 18, 9), (18, 0, 18, 9), (0, 9, 18, 9), (18, 9, 18, 9), (0, 18, 36, 9)]
    tiles = []
    for (x, y, w, h), cu in zip(layout, chart_uuids):
        tiles.append({"type": "saved_chart", "x": x, "y": y, "w": w, "h": h,
                      "properties": {"savedChartUuid": cu}})
    body = {"name": DASHBOARD_NAME,
            "description": "Controlling-Cockpit: Kunden-/Bereichsumsatz, fakturierter Erlös, Seminar-DB, Forecast.",
            "spaceUuid": sid, "tiles": tiles, "tabs": []}
    r = req("POST", f"/api/v1/projects/{pid}/dashboards", json=body)
    if r.status_code not in (200, 201):
        raise SystemExit(f"Dashboard fehlgeschlagen: {r.status_code} {r.text[:500]}")
    print(f"  Dashboard angelegt: {DASHBOARD_NAME} ({ok(r)['uuid']})")
    print("FERTIG.")

if __name__ == "__main__":
    main()
