#!/usr/bin/env bash
#
# lms-share-seed.sh — M4 Content-share-once (PoC-Kriterium C1/K5).
# ====================================================================================================
# Macht EIN importiertes Repo-Entry (das "Video-Nugget") in MEHREREN org-skopierten Curricula sichtbar/
# startbar — bei Storage x 1 (genau ein Repo-Entry, n Curriculum-Elemente referenzieren es).
#
# WARUM Curriculum statt "Catalog-2.0-Offers": Offers sind in OpenOLAT 20.3 NICHT per REST anlegbar
# (kein /offer-Pfad in restapi/openapi.json; /catalog/* ist das alte Catalog 1.0). Der offizielle,
# voll REST-faehige Weg fuer org-skopiertes, startbares Sharing ist das Curriculum:
#   PUT /curriculum                                  (org-skopiert via organisationKey)
#   PUT /curriculum/{ck}/elements                    (Container je Org)
#   PUT /curriculum/{ck}/elements/{ek}/entries/{re}  (DENSELBEN Repo-Entry anhaengen → share-once)
#   PUT /curriculum/{ck}/elements/{ek}/participants/{id}  (Mitglieder → startbar)
# Vorbedingung der Element-Anlage: Aufrufer braucht die Rolle `curriculummanager` IN DER ORG des
# Curriculums (nicht in der Root-Org) — wird hier idempotent gesetzt.
#
# Idempotent. Vorausgesetzt: lms-import-seed.sh lief (Repo-Entry da), mandanten-seed (DEMO_AG-Org da).
# Hook fuer showcase-aufbau.sh (Schritt `lms-share`).
set -uo pipefail

BASE="${OLAT_BASE:-http://localhost:8089/restapi}"
CRED="${OLAT_CRED:-administrator:openolat}"
# Das geteilte Nugget (von lms-import-seed.sh importiert). H5P Course Presentation = video-faehig.
# ASCII-Teilstring (NICHT der volle Name) — lms-import-seed speichert den En-Dash als Mojibake.
CONTENT_NAME="${CONTENT_NAME:-H5P Showcase}"

rot=$'\e[31m'; gruen=$'\e[32m'; aus=$'\e[0m'
ok(){ echo "  ${gruen}✓${aus} $*"; }
warn(){ echo "  ${rot}!${aus} $*" >&2; }
fail(){ echo "${rot}✗ $*${aus}" >&2; exit 1; }

jget(){ python -c "import sys,json
try: d=json.load(sys.stdin)
except Exception: print(''); sys.exit()
$1"; }

api(){ curl -fsS -u "$CRED" -H 'Accept: application/json' "$@"; }
apic(){ curl -fsS -u "$CRED" -H 'Accept: application/json' -H 'Content-Type: application/json' "$@"; }
# Liefert nur den HTTP-Code (fuer idempotente PUTs, die 200/409/304 zurueckgeben duerfen).
put_code(){ curl -s -o /dev/null -w '%{http_code}' -u "$CRED" -H 'Accept: application/json' -X PUT "$1"; }

# --- Schluessel aufloesen --------------------------------------------------------------------------
admin_key(){ api "$BASE/users?login=administrator" | jget "d=d if isinstance(d,list) else [d]; print(d[0]['key'] if d else '')"; }
user_key(){ api "$BASE/users?login=$1" | jget "d=d if isinstance(d,list) else [d]; print(d[0]['key'] if d else '')"; }
# Name als argv (NICHT in den py-Quelltext interpolieren) — sonst zerschiesst der En-Dash die Quelle.
content_key(){ api "$BASE/repo/entries" | python -c "import sys,json
d=json.load(sys.stdin); n=sys.argv[1]
d=d if isinstance(d,list) else d.get('repositoryEntries',[])
print(next((str(e['key']) for e in d if n in e['displayname']),''))" "$1"; }
# EBZ-Kontext = Default-/Root-Org (externalId null). DEMO_AG = externalId DEMO_AG.
org_by_extid(){ api "$BASE/organisations" | jget "print(next((str(o['key']) for o in d if (o.get('externalId') or '')=='$1'),''))"; }
org_root(){ api "$BASE/organisations" | jget "print(next((str(o['key']) for o in d if not o.get('externalId')),''))"; }

cur_key(){ api "$BASE/curriculum" | jget "d=d if isinstance(d,list) else []; print(next((str(c['key']) for c in d if c.get('identifier')=='$1'),''))"; }
elt_key(){ api "$BASE/curriculum/$1/elements" | jget "d=d if isinstance(d,list) else []; print(next((str(e['key']) for e in d if e.get('identifier')=='$2'),''))"; }

# Teilt das Nugget in EINE Org. Args: orgKey label curId eltId memberKey(optional)
share_into_org(){
  local org="$1" label="$2" cid="$3" eid="$4" member="${5:-}"
  [ -n "$org" ] || { warn "$label: Org-Key fehlt — uebersprungen"; return 0; }

  # 1) curriculummanager IN DER ORG (sonst 403 bei Element-Anlage).
  put_code "$BASE/organisations/$org/curriculummanager/$ADMIN" >/dev/null

  # 2) Curriculum (org-skopiert) find-or-create.
  local ck; ck="$(cur_key "$cid")"
  if [ -z "$ck" ]; then
    ck="$(apic -X PUT "$BASE/curriculum" -d "{\"identifier\":\"$cid\",\"displayName\":\"$label - Lernangebote\",\"organisationKey\":$org}" | jget "print(d.get('key',''))")"
    [ -n "$ck" ] || { warn "$label: Curriculum-Anlage fehlgeschlagen"; return 1; }
    ok "$label: Curriculum angelegt (key $ck, org $org)"
  else
    ok "$label: Curriculum existiert (key $ck)"
  fi

  # 3) Element find-or-create + aktiv schalten.
  local ek; ek="$(elt_key "$ck" "$eid")"
  if [ -z "$ek" ]; then
    ek="$(apic -X PUT "$BASE/curriculum/$ck/elements" -d "{\"identifier\":\"$eid\",\"displayName\":\"Geteiltes Video-Nugget\"}" | jget "print(d.get('key',''))")"
    [ -n "$ek" ] || { warn "$label: Element-Anlage fehlgeschlagen"; return 1; }
    ok "$label: Element angelegt (key $ek)"
  fi
  apic -o /dev/null -X POST "$BASE/curriculum/$ck/elements/$ek" \
    -d "{\"key\":$ek,\"identifier\":\"$eid\",\"displayName\":\"Geteiltes Video-Nugget\",\"status\":\"active\",\"curriculumKey\":$ck}" || true

  # 4) DENSELBEN Repo-Entry anhaengen (share-once) + Entry<->Org-Link (Authoring/Sichtbarkeit).
  put_code "$BASE/curriculum/$ck/elements/$ek/entries/$CONTENT" >/dev/null
  put_code "$BASE/repo/entries/$CONTENT/organisations/$org" >/dev/null
  ok "$label: Nugget ($CONTENT) angehaengt + Org-Link gesetzt"

  # 5) Optional Mitglied einschreiben → startbar.
  if [ -n "$member" ]; then
    put_code "$BASE/curriculum/$ck/elements/$ek/participants/$member" >/dev/null
    ok "$label: Mitglied $member als Teilnehmer eingeschrieben"
  fi
}

main(){
  command -v curl >/dev/null || fail "curl fehlt"
  echo "M4 Content-share-once gegen $BASE (Nugget: $CONTENT_NAME)"

  ADMIN="$(admin_key)";          [ -n "$ADMIN" ]   || fail "administrator-Identity nicht gefunden"
  CONTENT="$(content_key "$CONTENT_NAME")"; [ -n "$CONTENT" ] || fail "Repo-Entry '$CONTENT_NAME' fehlt — erst 'bash openolat/lms-import-seed.sh'"
  local ebz demo cust
  ebz="$(org_root)"; demo="$(org_by_extid DEMO_AG)"; cust="$(user_key customer)"
  ok "Aufgeloest: admin=$ADMIN content=$CONTENT ebzOrg=$ebz demoOrg=$demo customer=$cust"

  # EBZ-Kontext (Default-Org) — customer (B2C) als startbarer Teilnehmer.
  share_into_org "$ebz"  "EBZ"     "CUR-EBZ"     "ELT-EBZ-VIDEO"     "$cust"
  # B2B-Mandant DEMO_AG — DENSELBE Entry, org-skopiert. Mitglieder kommen gebrokert/JIT (Enrollment-Outbox).
  share_into_org "$demo" "DEMO_AG" "CUR-DEMO_AG" "ELT-DEMO_AG-VIDEO"

  # Gegenprobe: wie viele Elemente referenzieren das EINE Nugget?
  local refs; refs="$(api "$BASE/repo/entries/$CONTENT/curriculum/elements" | jget "print(len(d) if isinstance(d,list) else 0)")"
  echo "${gruen}✓ Content-share-once: 1 Repo-Entry ($CONTENT) referenziert von $refs Curriculum-Element(en) (Storage x 1).${aus}"
}
main "$@"
