# -*- coding: utf-8 -*-
# Relevanz-gewichtete Neubewertung des LMS-Plattformvergleichs (EBZ-Szenario), PoC-belegt.
# Score-Skala 0..4 (0 fehlt · 1 schwach/Plugin · 2 mittel/Eigenbau-geplant · 3 gut/Eigenbau-gebaut+verifiziert
# bzw. solide · 4 nativ/erstklassig). Gewicht G 1..3 = Relevanz fuers beschriebene EBZ-Szenario.
# Kandidaten: OLs=OpenOLAT shared, MI=Moodle OSS+IOMAD, WP=Moodle Workplace, OLi=OpenOLAT Instanz/Mandant.

# (id, Kurzname, Bereich, G, OLs, MI, WP, OLi)  — S-Werte aus den emoji-Tabellen, OLs mit PoC-Override.
K = [
 (1,"Multi-Tenancy nativ","A",3, 2,2,4,4),
 (2,"Tenant-Datenisolation","A",3, 2,2,4,4),
 (3,"Org-Hierarchien/Struktur","A",2, 4,2,4,4),
 (4,"Rollen-Scoping pro Mandant","A",2, 4,2,4,4),
 (5,"Mandanten-Admin (Self-Service)","A",2, 2,2,4,4),
 (6,"Multi-Mandant-User/Switcher","A",1, 0,2,4,0),
 (7,"Eigene Tenant-URL/Subdomain","A",2, 0,2,4,4),
 (8,"Per-Tenant-Branding (Logo/Farben)","B",3, 3,4,4,4),   # OLs PoC: addBodyCssClass-Extension gebaut
 (9,"Per-Tenant-Login-Seite","B",2, 2,2,4,4),
 (10,"Theme-Tiefe (CSS/Templates)","B",1, 2,4,4,4),
 (11,"White-Label-Reife (B2B2C)","B",2, 2,2,4,4),
 (12,"OIDC/OAuth2-SSO","C",2, 4,4,4,4),
 (13,"SAML-SSO","C",1, 4,2,4,4),
 (14,"Per-Tenant-IdP (Kunden-IdP)","C",3, 3,2,4,4),        # OLs PoC: Keycloak-Brokering gebaut+verifiziert
 (15,"Keycloak-Fit","C",2, 4,4,4,4),
 (16,"SCORM 1.2","D",2, 4,4,4,4),
 (17,"SCORM 2004","D",1, 0,2,2,0),
 (18,"xAPI / LRS","D",1, 2,4,4,2),
 (19,"H5P / interaktiv","D",2, 2,4,4,2),
 (20,"Autorenwerkzeug","D",2, 4,4,4,4),
 (21,"REST/Web-Services-Breite","E",2, 4,4,4,4),
 (22,"Bulk-/Auto-Provisionierung","E",2, 4,4,4,2),
 (23,"Dynamische Enrol-Regeln","E",2, 2,2,4,2),
 (24,"Seat-/Lizenzkontingent","E",3, 3,2,4,2),             # OLs PoC: Seat-Cap (weich+HITL) gebaut+live-E2E
 (25,"Completion/Weiterbildungsnachweis","F",3, 3,4,4,3),  # OLs PoC: Soll-Stunden-Fakt-Seam gebaut+live
 (26,"Zertifikate","F",2, 4,4,4,4),
 (27,"Zertifizierung/Recert (§34d/§34c)","F",2, 2,2,4,2),
 (28,"Report-Builder / Custom-Reports","F",2, 2,4,4,2),
 (29,"Reporting-Export in HR/BI","F",2, 2,2,4,2),
 (30,"Commerce-Entkopplung / Vendure-Fit","G",3, 4,2,2,4),
 (31,"Payment/Enrolment-on-payment nativ","G",1, 2,4,4,2),
 (32,"Lizenzkosten / Modell","H",3, 4,4,0,4),
 (33,"Anbieter-/Partner-Lock-in","H",2, 4,4,0,4),
 (34,"Gesamt-TCO","H",3, 4,2,0,0),
 (35,"Tech-Stack-Fit (Quarkus/JVM-Haus)","I",3, 4,0,0,4),
 (36,"Datenbank (Postgres-Fit)","I",2, 4,2,2,4),
 (37,"Betriebsaufwand / Skalierung","I",2, 2,2,4,0),
 (38,"Hosting-Souveränität (DSGVO)","I",3, 4,4,2,4),
 (39,"Verbreitung / Bus-Faktor / DACH","J",2, 2,4,4,2),
 (40,"Migrationsaufwand / Sunk-Cost","J",3, 4,0,0,2),       # OLs: M0-M6 + L0-L3 bereits gebaut
 (41,"Content einmal speichern (Storage, Video)","D+",3, 3,4,4,0),  # OLs PoC: Storage x1 via Curriculum gebaut
 (42,"Update-Propagierung / Drift-Freiheit","D+",3, 3,4,4,0),
 (43,"Belegte Enterprise-MT-Referenzen","J",3, 2,2,4,2),
 # --- Neue Kriterien aus dem PoC (2026-06-26) ---
 (44,"Eigenbau-Aufwand Mandanten-Schicht (K7; hoeher=leichter)","PoC",3, 3,4,4,2),
 (45,"Integrations-Robustheit/Auth-Reife (PoC-Bug-Trail)","PoC",2, 2,3,4,3),
]

cands = ["OLs","MI","WP","OLi"]
names = {"OLs":"OpenOLAT shared","MI":"Moodle OSS+IOMAD","WP":"Moodle Workplace","OLi":"OpenOLAT Instanz/Mandant"}
idx = {"OLs":4,"MI":5,"WP":6,"OLi":7}

# Gesamt gewichtet + Max + Prozent
tot = {c:0 for c in cands}; mx = 0
for row in K:
    G = row[3]; mx += G*4
    for c in cands: tot[c] += G*row[idx[c]]

print("## Gewichtete Gesamtwertung\n")
print("| Kandidat | Gewichtete Punkte | von max %d | Erfüllungsgrad |" % mx)
print("|---|:--:|:--:|:--:|")
for c in sorted(cands, key=lambda x:-tot[x]):
    print("| **%s** | **%d** | %d | **%.0f %%** |" % (names[c], tot[c], mx, 100*tot[c]/mx))

# Bereichs-Subtotals (gewichtet, normiert je Bereich)
bereiche = {}
for row in K:
    bereiche.setdefault(row[2], []).append(row)
ber_label = {"A":"§1 Mandantenfähigkeit","B":"§2 Branding/White-Label","C":"§3 Identität & SSO",
 "D":"§4 Content/SCORM/Authoring","D+":"§4b Content-Verteilung (Video)","E":"§5 API/Provisionierung",
 "F":"§6 Reporting/Nachweis/Compliance","G":"§7 Vermarktung/Commerce","H":"§8 Lizenz/Kosten/TCO",
 "I":"§9 Betrieb/Stack-Fit","J":"§10 Ökosystem/Strategie/Risiko","PoC":"§PoC Eigenbau-Evidenz (M0–M6)"}

print("\n## Gewichtete Subtotals je Bereich (Erfüllungsgrad %)\n")
print("| Bereich | Gewicht Σ | OpenOLAT shared | Moodle+IOMAD | Workplace | OpenOLAT Instanz |")
print("|---|:--:|:--:|:--:|:--:|:--:|")
order = ["A","B","C","D","D+","E","F","G","H","I","J","PoC"]
for b in order:
    rows = bereiche[b]; gw = sum(r[3] for r in rows); bmax = gw*4
    cells = []
    for c in cands:
        s = sum(r[3]*r[idx[c]] for r in rows)
        cells.append("%.0f %%" % (100*s/bmax))
    print("| %s | %d | %s | %s | %s | %s |" % (ber_label[b], gw, cells[0], cells[1], cells[2], cells[3]))

# Detailtabelle je Kriterium
print("\n## Kriterien-Detail (G = Gewicht 1–3 · Score 0–4)\n")
print("| # | Kriterium | G | OLs | MI | WP | OLi |")
print("|--|--|:--:|:--:|:--:|:--:|:--:|")
for row in K:
    print("| %d | %s | %d | %d | %d | %d | %d |" % (row[0],row[1],row[3],row[4],row[5],row[6],row[7]))
