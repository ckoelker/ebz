/* CRM-Wireframe — Verhalten (Vanilla JS, Wegwerf-Prototyp).
   Kein Build, keine echte Persistenz. Demonstriert Funktionen & Flows. */

// ---------- Helpers ----------
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = (s) => (s == null ? '' : String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c])));
const orgById = (id) => ORGANISATIONEN.find(o => o.id === id);
const personById = (id) => PERSONEN.find(p => p.id === id);
const DE_TITEL = ['Dr.', 'Prof.', 'Prof. Dr.', 'Dipl.-Ing.', 'Dr. mult.'];

function initials(name) { return name.split(/\s+/).filter(Boolean).slice(0, 2).map(w => w[0]).join('').toUpperCase(); }
function personName(p) {
  const base = `${p.vorname} ${p.nachname}`.trim();
  if (!p.titel) return base;
  return DE_TITEL.includes(p.titel) ? `${p.titel} ${base}` : `${base}, ${p.titel}`;
}
function anrede(p) { return p.geschlecht === 'M' ? 'Herr' : p.geschlecht === 'W' ? 'Frau' : ''; }
function briefanrede(p) {
  const a = anrede(p);
  return a ? `${a} ${p.nachname}` : `Hallo ${p.vorname} ${p.nachname}`; // neutraler Fallback (divers/o.A.)
}
function volljaehrig(p) {
  if (!p.geburtsdatum) return null; // unbekannt
  const d = new Date(p.geburtsdatum); const now = new Date();
  let age = now.getFullYear() - d.getFullYear();
  if (now < new Date(now.getFullYear(), d.getMonth(), d.getDate())) age--;
  return age >= 18;
}
function mitglFuerPerson(pid, inklEhemalig = true) {
  return MITGLIEDSCHAFTEN.filter(m => m.personId === pid && (inklEhemalig || !m.gueltigBis));
}
function mitglFuerOrg(oid, inklEhemalig = true) {
  return MITGLIEDSCHAFTEN.filter(m => m.orgId === oid && (inklEhemalig || !m.gueltigBis));
}
function primKanal(kps, typ) { return (kps || []).find(k => k.typ === typ && k.status !== 'EHEMALIG'); }
function kanalText(k) {
  if (k.typ === 'EMAIL') return k.email;
  if (k.typ === 'TELEFON') return k.nummerAnzeige;
  if (k.typ === 'ADRESSE') return `${k.strasse} ${k.hausnummer || ''}, ${k.plz} ${k.ort}` + (k.land && k.land !== 'DE' ? ` (${laenderName(k.land)})` : '');
  return '';
}
// Label eines Kanals (Person-Adresse als „Privatadresse" auszeichnen)
function kpLabel(k) { if (k.typ === 'ADRESSE') return k.kontext === 'privat' ? 'Privatadresse' : 'Adresse'; return k.typ; }
// Identifikations-Infos (Liste + globale Suche)
function primAdresse(kps) { return (kps || []).find(k => k.typ === 'ADRESSE' && k.status !== 'EHEMALIG'); }
function ortOf(owner) { const a = primAdresse(owner && owner.kontaktpunkte); return a ? (a.ort || '') : ''; }
function hauptMitglied(p) { const a = mitglFuerPerson(p.id, false); return a.find(m => m.hauptzugehoerigkeit) || a[0] || null; }
function hauptFirmaFuer(p) { const m = hauptMitglied(p); return m ? orgById(m.orgId) : null; }
function hauptRolleFuer(p) { const m = hauptMitglied(p); return m && m.rollen.length ? m.rollen[0] : ''; }
function buchungsKontaktFuer(o) { const ms = mitglFuerOrg(o.id, false); const m = ms.find(x => x.buchungsberechtigt) || ms.find(x => x.hauptansprechpartner) || ms[0]; return m ? personById(m.personId) : null; }

// ---------- State ----------
const S = { view: 'cockpit', ckNav: 'cockpit', seg: 'alle', selType: null, selId: null, tab: null, editStamm: false, kpEdit: null, mgEdit: null, mgAdd: false };
function resetEdit() { S.editStamm = false; S.kpEdit = null; S.mgEdit = null; S.mgAdd = false; }
// simulierte WebSocket-Ereignisse (neue Items je Person → Tab-Bubbles)
const wsNew = {}; // pid -> { login: n, buchung: n }
function bubble(pid, key) { return (wsNew[pid] && wsNew[pid][key]) || 0; }

// ---------- Navigation (eine Shell; ckNav schaltet den Hauptbereich) ----------
function heute() { return new Date().toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }); }
function hubspotStatus(p) {
  if (p.werbesperre || p.auskunftssperre) return { badge: 'b-err', text: 'Sync gestoppt (Sperre aktiv)' };
  if ((p.einwilligungen || []).some(e => e.status === 'ERTEILT')) return { badge: 'b-ok', text: 'synchronisiert' };
  return { badge: 'b-warn', text: 'ausstehend (kein Opt-In)' };
}

// ---------- Quicklinks & Sidebar ----------
function renderQuicklinks(animate) {
  $$('.ql-host').forEach(cont => {
    // FLIP: Positionen der bestehenden Einträge vor dem Re-Render merken
    const prev = {};
    if (animate) $$('.quicklink', cont).forEach(e => { prev[e.dataset.key] = e.getBoundingClientRect().top; });
    cont.innerHTML = QUICKLINKS.map(q => {
      const key = q.typ + ':' + q.id;
      if (q.typ === 'person') { const p = personById(q.id); return `<div class="quicklink" data-key="${key}" data-t="person" data-id="${p.id}"><span class="avatar" style="width:24px;height:24px;font-size:10px">${initials(p.vorname + ' ' + p.nachname)}</span>${esc(personName(p))}</div>`; }
      const o = orgById(q.id); return `<div class="quicklink" data-key="${key}" data-t="org" data-id="${o.id}"><span class="avatar org" style="width:24px;height:24px;font-size:10px">${esc(initials(o.name))}</span>${esc(o.name)}</div>`;
    }).join('');
    $$('.quicklink', cont).forEach(e => e.onclick = () => select(e.dataset.t, e.dataset.id));
    // FLIP: von alter zu neuer Position nach oben sliden; neue Einträge sanft einblenden
    if (animate) $$('.quicklink', cont).forEach(e => {
      const oldTop = prev[e.dataset.key], newTop = e.getBoundingClientRect().top;
      if (oldTop != null) {
        const dy = oldTop - newTop;
        if (dy) { e.style.transition = 'none'; e.style.transform = `translateY(${dy}px)`; requestAnimationFrame(() => { e.style.transition = 'transform .35s cubic-bezier(.2,.7,.3,1)'; e.style.transform = ''; }); }
        if (newTop < oldTop) e.classList.add('ql-flash');
      } else { e.classList.add('ql-enter'); requestAnimationFrame(() => e.classList.remove('ql-enter')); }
    });
  });
}
function renderSideWv() {
  const host = $('#sideWv'); if (!host) return; // Wiedervorlagen leben jetzt unter „Sonderfälle"
  const mine = WIEDERVORLAGEN.filter(w => !w.erledigt);
  host.innerHTML = mine.map(w => `<div class="quicklink" data-id="${w.id}"><span class="badge ${w.prioritaet === 'hoch' ? 'b-err' : 'b-warn'}">${esc(w.faelligAm.slice(5))}</span> ${esc(w.betreff)}</div>`).join('') || '<div class="muted" style="padding:4px 10px">keine offenen</div>';
  $$('#sideWv .quicklink').forEach(e => e.onclick = () => { const w = WIEDERVORLAGEN.find(x => x.id === e.dataset.id); if (w && w.personId) select('person', w.personId); });
}

// ---------- Master list ----------
function pushQuicklink(typ, id) {
  const i = QUICKLINKS.findIndex(q => q.typ === typ && q.id === id);
  if (i >= 0) QUICKLINKS.splice(i, 1);
  QUICKLINKS.unshift({ typ, id });
  QUICKLINKS.splice(6);
  renderQuicklinks(true);
}
function renderList() {
  if (!$('#list')) return; // nur auf der Kundenstamm-Seite vorhanden
  const items = [];
  if (S.seg === 'alle' || S.seg === 'person') PERSONEN.forEach(p => items.push({ typ: 'person', obj: p }));
  if (S.seg === 'alle' || S.seg === 'org') ORGANISATIONEN.forEach(o => items.push({ typ: 'org', obj: o }));
  $('#list').innerHTML = items.map(it => {
    if (it.typ === 'person') {
      const p = it.obj; const aktiv = mitglFuerPerson(p.id, false);
      const hf = hauptFirmaFuer(p); const rolle = hauptRolleFuer(p);
      const firma = hf ? hf.name : 'Privat';
      const extra = aktiv.length > 1 ? ` <span class="chip" style="font-size:10px;padding:0 6px" title="${aktiv.length} Firmenzugehörigkeiten">+${aktiv.length - 1}</span>` : '';
      const ort = ortOf(p); const detail = [p.geburtsdatum ? '*' + p.geburtsdatum : '', ort].filter(Boolean).join(' · ');
      return `<div class="ml-item ${S.selType === 'person' && S.selId === p.id ? 'active' : ''}" data-t="person" data-id="${p.id}">
        <span class="avatar">${esc(initials(p.vorname + ' ' + p.nachname))}</span>
        <div style="min-width:0"><div class="name">${esc(personName(p))} ${p.unvollstaendig ? '<span class="badge b-warn">unvollständig</span>' : ''} ${p.werbesperre ? '<span class="badge b-err">Werbesperre</span>' : ''}</div>
        <div class="sub">${esc(firma)}${rolle ? ' · ' + esc(rolle) : ''}${extra}</div>
        ${detail ? `<div class="sub2">${esc(detail)}</div>` : ''}</div></div>`;
    }
    const o = it.obj; const ort = ortOf(o); const bk = buchungsKontaktFuer(o); const n = mitglFuerOrg(o.id, false).length;
    return `<div class="ml-item ${S.selType === 'org' && S.selId === o.id ? 'active' : ''}" data-t="org" data-id="${o.id}">
      <span class="avatar org">${esc(initials(o.name))}</span>
      <div style="min-width:0"><div class="name">${esc(o.name)}</div>
      <div class="sub">${esc(o.unternehmenstyp || '')}${ort ? ' · ' + esc(ort) : ''} · ${n} Personen</div>
      ${bk ? `<div class="sub2">Buchungskontakt: ${esc(personName(bk))}</div>` : ''}</div></div>`;
  }).join('');
  $$('#list .ml-item').forEach(e => e.onclick = () => select(e.dataset.t, e.dataset.id));
}

// ---------- Selection ----------
function select(typ, id) {
  S.selType = typ; S.selId = id; S.tab = null; resetEdit();
  if (typ === 'person') { wsNew[id] = { login: 0, buchung: 0 }; } // "gelesen"
  pushQuicklink(typ, id);
  document.body.classList.add('show-detail');
  // Kundenstamm-Seite ist Teil der Cockpit-Shell (kein Vollbild-Wechsel)
  if (S.ckNav !== 'kunden' || !$('#list')) { S.ckNav = 'kunden'; renderCkNav(); renderKundenPage(); }
  else { renderList(); renderDetail(); }
}

// ---------- Detail ----------
function renderDetail() {
  const d = $('#detail');
  if (!d) return; // nur auf der Kundenstamm-Seite vorhanden
  if (!S.selId) { d.innerHTML = '<p class="muted">Wähle links einen Kontakt oder lege über „+ Person/Firma anlegen" einen neuen an.</p>'; return; }
  if (S.selType === 'person') renderPerson(personById(S.selId), d);
  else renderOrg(orgById(S.selId), d);
}

function tabBar(tabs) {
  const active = S.tab || tabs[0].key;
  S.tab = active;
  return `<div class="tabs">${tabs.map(t => `<button class="tab ${t.key === active ? 'active' : ''}" data-tab="${t.key}">${esc(t.label)}${t.bubble ? `<span class="bubble">${t.bubble}</span>` : ''}</button>`).join('')}</div><div id="tabBody"></div>`;
}
function wireTabs(render) {
  $$('#detail .tab').forEach(e => e.onclick = () => { S.tab = e.dataset.tab; resetEdit(); render(); });
}

function renderPerson(p, d) {
  const vj = volljaehrig(p);
  const tabs = [
    { key: 'stamm', label: 'Stammdaten' },
    { key: 'zugeh', label: 'Zugehörigkeiten' },
    { key: 'komm', label: 'Kommunikation' },
    { key: 'dsgvo', label: 'Einwilligung / DSGVO' },
    { key: 'wb', label: 'Weiterbildung' },
    { key: 'v360', label: '360°' },
    { key: 'login', label: 'Loginversuche', bubble: bubble(p.id, 'login') },
    { key: 'buchung', label: 'Buchungen', bubble: bubble(p.id, 'buchung') },
  ];
  d.innerHTML = `
    <div class="dt-head">
      <button class="btn sm back-btn" id="backBtn">‹ Liste</button>
      <span class="avatar">${esc(initials(p.vorname + ' ' + p.nachname))}</span>
      <div>
        <h1 class="dt-title">${esc(personName(p))}</h1>
        <div class="dt-meta">
          <span class="badge ${p.status === 'AKTIV' ? 'b-ok' : 'b-grey'}">${esc(p.status)}</span>
          ${p.unvollstaendig ? '<span class="badge b-warn">unvollständig · Nachpflege</span>' : ''}
          ${p.werbesperre ? '<span class="badge b-err">Werbesperre</span>' : ''}
          ${p.auskunftssperre ? '<span class="badge b-err">Auskunftssperre</span>' : ''}
          ${vj === false ? '<span class="badge b-warn">minderjährig</span>' : ''}
          <span>Anrede: ${esc(briefanrede(p))}</span>
        </div>
      </div>
      <div class="dt-actions">
        <button class="btn" id="aNote">📝 Notiz</button>
        <button class="btn" id="aWv">⏰ Wiedervorlage</button>
        <button class="btn" id="aLink">🔗 Firma verknüpfen</button>
      </div>
    </div>
    ${tabBar(tabs)}`;
  $('#backBtn').onclick = () => document.body.classList.remove('show-detail');
  $('#aNote').onclick = () => openNote(p);
  $('#aWv').onclick = () => openWv(p);
  $('#aLink').onclick = () => openVerknuepfen('person', p.id);
  wireTabs(() => renderPerson(p, d));
  const body = $('#tabBody');

  if (S.tab === 'stamm') {
    const pe = primKanal(p.kontaktpunkte, 'EMAIL'), pt = primKanal(p.kontaktpunkte, 'TELEFON');
    const rerender = () => renderPerson(p, d);
    if (S.editStamm) {
      body.innerHTML = `<div class="card"><h3>Stammdaten bearbeiten</h3>
        <div class="grid2"><div class="field"><label>Vorname <span class="req">*</span></label><input id="s_vn" value="${esc(p.vorname)}" /></div>
        <div class="field"><label>Nachname <span class="req">*</span></label><input id="s_nn" value="${esc(p.nachname)}" /></div></div>
        <div class="grid2"><div class="field"><label>Titel</label><input id="s_titel" value="${esc(p.titel || '')}" placeholder="Dr. (DE vorn) / MBA (int. hinten)" /></div>
        <div class="field"><label>Geschlecht <span class="req">*</span></label><select id="s_g">${geschlechtOptions(p.geschlecht, !p.geschlecht)}</select><div class="hint">→ Briefanrede „${esc(briefanrede(p))}"</div></div></div>
        <div class="grid2"><div class="field"><label>✉ Primär-E-Mail</label><input id="s_mail" value="${esc(pe ? pe.email : '')}" /></div>
        <div class="field"><label>📞 Primär-Telefon</label><input id="s_tel" value="${esc(pt ? pt.nummerAnzeige : '')}" /></div></div>
        <div class="grid2"><div class="field"><label>Geburtsdatum</label><input type="date" id="s_gd" value="${esc(p.geburtsdatum || '')}" /></div>
        <div class="field"><label>Korrespondenzsprache</label><select id="s_spr">${spracheOptions(p.korrespondenzsprache)}</select></div></div>
        <div class="grid2"><div class="field"><label>Staatsangehörigkeit(en)</label><input id="s_staat" value="${esc((p.staatsangehoerigkeit || []).join(', '))}" /></div>
        <div class="field"><label>Lead-Quelle</label><select id="s_lead">${['', ...LOOKUPS.leadQuelle].map(q => `<option ${q === (p.leadQuelle || '') ? 'selected' : ''}>${q}</option>`).join('')}</select></div></div>
        <div style="display:flex;gap:8px;margin-top:6px"><button class="btn primary" id="s_save">Speichern</button><button class="btn" id="s_cancel">Abbrechen</button></div></div>`;
      $('#s_save').onclick = () => {
        if (!$('#s_g').value) { toastInfo('Bitte Geschlecht wählen (Pflichtfeld).'); return; }
        p.vorname = $('#s_vn').value.trim() || p.vorname; p.nachname = $('#s_nn').value.trim() || p.nachname;
        p.titel = $('#s_titel').value.trim(); p.geschlecht = $('#s_g').value;
        p.geburtsdatum = $('#s_gd').value || null; p.korrespondenzsprache = $('#s_spr').value.trim();
        p.staatsangehoerigkeit = $('#s_staat').value.split(',').map(x => x.trim()).filter(Boolean); p.leadQuelle = $('#s_lead').value;
        setPrimChannel(p, 'EMAIL', $('#s_mail').value.trim(), 'privat'); setPrimChannel(p, 'TELEFON', $('#s_tel').value.trim(), 'privat');
        p.unvollstaendig = false; S.editStamm = false; renderList(); rerender(); toastInfo('Gespeichert.');
      };
      $('#s_cancel').onclick = () => { S.editStamm = false; rerender(); };
    } else {
      body.innerHTML = `<div class="card"><h3>Stammdaten <button class="btn sm primary" id="s_edit" style="float:right">✎ Bearbeiten</button></h3><dl class="kv">
        <dt>Vorname</dt><dd>${esc(p.vorname)}</dd>
        <dt>Nachname</dt><dd>${esc(p.nachname)}</dd>
        <dt>Titel</dt><dd>${esc(p.titel || '–')}</dd>
        <dt>Geschlecht</dt><dd>${esc((LOOKUPS.geschlecht.find(g => g.code === p.geschlecht) || {}).bezeichnung || '–')} <span class="muted">→ Briefanrede „${esc(briefanrede(p))}"</span></dd>
        <dt>Geburtsdatum</dt><dd>${esc(p.geburtsdatum || '–')} ${vj === null ? '<span class="muted">(Volljährigkeit unbekannt → Default volljährig)</span>' : vj ? '' : '<span class="badge b-warn">minderjährig</span>'}</dd>
        <dt>Staatsangehörigkeit</dt><dd>${esc((p.staatsangehoerigkeit || []).join(', ') || '–')}</dd>
        <dt>Korrespondenzsprache</dt><dd>${esc(p.korrespondenzsprache || '–')}</dd>
        <dt>Lead-Quelle</dt><dd>${esc(p.leadQuelle || '–')}</dd>
        <dt>Login-Identität</dt><dd>${p.login.length ? p.login.map(l => `${esc(l.loginEmail)} ${l.verifiziert ? '<span class="badge b-ok">verifiziert</span>' : '<span class="badge b-grey">unverifiziert</span>'}`).join('<br>') : '<span class="muted">kein Login (PROVISORISCH erfassbar)</span>'}</dd>
      </dl></div>`;
      $('#s_edit').onclick = () => { S.editStamm = true; rerender(); };
    }
  } else if (S.tab === 'zugeh') {
    const aktiv = mitglFuerPerson(p.id, false), ehemalig = mitglFuerPerson(p.id, true).filter(m => m.gueltigBis);
    const rerender = () => renderPerson(p, d);
    const row = (m, old) => {
      const o = orgById(m.orgId);
      if (!old && S.mgEdit === m.id) return mgEditFormRow(m, 6);
      return `<tr class="${old ? 'row-old' : ''}">
      <td><a data-org="${o.id}" class="lnk">${esc(o.name)}</a>${m.hauptzugehoerigkeit ? ' <span class="badge b-info">Hauptzugehörigkeit</span>' : ''}${m.hauptansprechpartner ? ' <span class="badge b-ok">Hauptansprechpartner</span>' : ''}</td>
      <td>${m.rollen.map(r => `<span class="chip role">${esc(r)}</span>`).join('')}</td>
      <td>${esc(m.position || '–')}</td>
      <td>${m.buchungsberechtigt ? '✔ buchungsberechtigt' : '–'}${m.rechnungsempfaenger ? '<br>✔ Rechnungsempfänger' : ''}</td>
      <td>${esc(m.gueltigVon || '')}${m.gueltigBis ? ' – ' + esc(m.gueltigBis) : ' – heute'}</td>
      <td class="row-actions">${old ? '' : `<button class="btn sm" data-mg-edit="${m.id}">✎</button><button class="btn sm" data-mg-leave="${m.id}" title="Ausscheiden (historisieren)">⏏</button>`}</td></tr>`;
    };
    body.innerHTML = `<div class="card"><h3>Firmenzugehörigkeiten (N:M) <span class="muted" style="font-weight:400">· inline pflegbar</span></h3>
      <table class="grid"><thead><tr><th>Firma</th><th>Rollen</th><th>Position</th><th>Vollmacht</th><th>Gültig</th><th></th></tr></thead>
      <tbody>${aktiv.map(m => row(m, false)).join('') || '<tr><td colspan="6" class="muted">Privatperson — keine Firmenzugehörigkeit</td></tr>'}</tbody></table>
      ${S.mgAdd ? mgAddCard() : '<button class="btn sm" id="addMg" style="margin-top:10px">+ Firma verknüpfen</button>'}</div>
      ${ehemalig.length ? `<div class="card"><h3>Ehemalige Zugehörigkeiten</h3><table class="grid"><tbody>${ehemalig.map(m => row(m, true)).join('')}</tbody></table></div>` : ''}`;
    $('#addMg') && ($('#addMg').onclick = () => { S.mgAdd = true; rerender(); });
    if (S.mgAdd) wireMgAdd(p, rerender);
    $$('#detail .lnk[data-org]').forEach(e => e.onclick = () => select('org', e.dataset.org));
    $$('#detail [data-mg-edit]').forEach(b => b.onclick = () => { S.mgEdit = b.dataset.mgEdit; rerender(); });
    $$('#detail [data-mg-leave]').forEach(b => b.onclick = () => { const m = MITGLIEDSCHAFTEN.find(x => x.id === b.dataset.mgLeave); m.gueltigBis = new Date().toISOString().slice(0, 10); rerender(); renderList(); toastInfo('Zugehörigkeit beendet (historisiert).'); });
    if (S.mgEdit) { const m = MITGLIEDSCHAFTEN.find(x => x.id === S.mgEdit); if (m) wireMgEdit(m, rerender); }
  } else if (S.tab === 'komm') {
    const rerender = () => renderPerson(p, d);
    body.innerHTML = `<div class="card"><h3>Kontaktkanäle (privat) <span class="muted" style="font-weight:400">· inline pflegbar</span></h3>${kanalListeEdit(p, true)}</div>
      <div class="card"><h3>Kontakthistorie / Aktivitäten</h3>${aktListe(AKTIVITAETEN.filter(a => a.personId === p.id))}
      <button class="btn sm" id="addNote" style="margin-top:8px">+ Notiz / Kommunikation</button></div>`;
    wireKanalListe(p, true, rerender);
    $('#addNote').onclick = () => openNote(p);
  } else if (S.tab === 'dsgvo') {
    const hs = hubspotStatus(p);
    body.innerHTML = `<div class="card"><h3>↔ HubSpot-Bridge <span class="badge ${hs.badge}" style="float:right">${esc(hs.text)}</span></h3>
      <div class="muted" style="margin:-4px 0 8px">Letzter Sync: vor 1 Std · HubSpot-Kontakt hs-${esc(p.id)}</div>
      <div class="inline-info">Consent &amp; Marketing-Stammdaten werden hier gepflegt und automatisch übertragen. <b>Kampagnen &amp; Marketing-Automation laufen in HubSpot</b> — nicht in diesem Cockpit.</div></div>
      <div class="card"><h3>Marketing-Einwilligungen (Opt-In)</h3>
      <table class="grid"><thead><tr><th>Kanal</th><th>Zweck</th><th>Kontext</th><th>Status</th><th>Rechtsgrundlage</th><th>Quelle/Datum</th></tr></thead>
      <tbody>${p.einwilligungen.map(e => `<tr><td>${esc(e.kanal)}</td><td>${esc(e.zweck)}</td><td>${esc(e.kontext)}</td>
        <td><span class="badge ${e.status === 'ERTEILT' ? 'b-ok' : e.status === 'WIDERRUFEN' ? 'b-err' : 'b-warn'}">${esc(e.status)}</span></td>
        <td>${esc(e.rechtsgrundlage)}</td><td>${esc(e.quelle)} · ${esc(e.datum)}</td></tr>`).join('')}</tbody></table>
      ${p.werbesperre || p.auskunftssperre ? `<div class="inline-warn">⚠ ${p.werbesperre ? 'Werbesperre' : ''}${p.werbesperre && p.auskunftssperre ? ' & ' : ''}${p.auskunftssperre ? 'Auskunftssperre' : ''} aktiv — überstimmt jedes Opt-In.</div>` : ''}
      ${volljaehrig(p) === false ? '<div class="inline-warn">⚠ Minderjährig: Marketing/Verträge erfordern Einwilligung der Erziehungsberechtigten.</div>' : ''}
      <button class="btn sm" id="doi">✉ Double-Opt-In anstoßen</button></div>
      <div class="card"><h3>Sperren &amp; Betroffenenrechte (Datenschutz)</h3>
        <label style="font-size:13px;display:block;margin-bottom:4px"><input type="checkbox" id="d_werbe" ${p.werbesperre ? 'checked' : ''} /> Werbesperre — überstimmt jedes Opt-In</label>
        <label style="font-size:13px;display:block"><input type="checkbox" id="d_ausk" ${p.auskunftssperre ? 'checked' : ''} /> Auskunftssperre</label>
        <div class="hint" style="margin-bottom:8px">Werden in der Pflege gesetzt (bei der Erfassung nicht).</div>
        <button class="btn sm danger" id="d_forget">🗑 Recht auf Vergessen</button></div>
      <div class="card"><h3>Beziehungen</h3>${p.beziehungen.length ? p.beziehungen.map(b => { const rp = personById(b.personId); return `<div>${esc(b.typ)}: <a class="lnk" data-p="${b.personId}">${esc(rp ? personName(rp) : '?')}</a> <span class="muted">${esc(b.hinweis || '')}</span></div>`; }).join('') : '<span class="muted">keine</span>'}</div>`;
    $('#doi') && ($('#doi').onclick = () => toastInfo('Double-Opt-In-Mail (gemockt) versendet — Nachweis (Token/IP/Zeit) wird beim Klick gespeichert.'));
    $('#d_werbe').onclick = () => { p.werbesperre = $('#d_werbe').checked; renderList(); renderPerson(p, d); toastInfo(p.werbesperre ? 'Werbesperre gesetzt.' : 'Werbesperre aufgehoben.'); };
    $('#d_ausk').onclick = () => { p.auskunftssperre = $('#d_ausk').checked; renderList(); renderPerson(p, d); };
    $('#d_forget').onclick = () => openForget(p);
    $$('#detail .lnk[data-p]').forEach(e => e.onclick = () => select('person', e.dataset.p));
  } else if (S.tab === 'wb') {
    const wb = WEITERBILDUNG[p.id];
    if (!wb) { body.innerHTML = '<div class="card muted">Keine Weiterbildungspflicht erfasst.</div>'; }
    else {
      const pct = Math.min(100, Math.round(wb.istStunden / wb.sollStunden * 100));
      const farbe = pct >= 100 ? 'var(--ok)' : pct >= 60 ? 'var(--warn)' : 'var(--err2)';
      body.innerHTML = `<div class="card"><h3>Weiterbildungspflicht §34c GewO / §15b MaBV — Zeitraum ${esc(wb.zeitraum)}</h3>
        <div>${wb.istStunden} / ${wb.sollStunden} Std. <span class="badge ${pct >= 100 ? 'b-ok' : pct >= 60 ? 'b-warn' : 'b-err'}">${pct >= 100 ? 'erfüllt' : pct >= 60 ? 'im Plan' : 'kritisch'}</span></div>
        <div class="ampel"><span style="width:${pct}%;background:${farbe}"></span></div>
        <table class="grid"><thead><tr><th>Maßnahme</th><th>Std.</th><th>Jahr</th><th>Quelle</th></tr></thead>
        <tbody>${wb.nachweise.map(n => `<tr><td>${esc(n.titel)}</td><td>${n.stunden}</td><td>${n.jahr}</td><td>${esc(n.quelle)}</td></tr>`).join('')}</tbody></table>
        <button class="btn sm" style="margin-top:8px" onclick="toastInfo('Passendes EBZ-Seminar vorschlagen (gemockt)')">🎯 Passendes Seminar vorschlagen</button></div>`;
    }
  } else if (S.tab === 'v360') {
    body.innerHTML = `<div class="inline-info">360°-Sicht (intern, read-only) — Buchungen/Rechnungen/Anmeldungen, mit Verlinkung in die Bestandsmasken. Aus Firmenkontext würden nur firmenbezogene Daten erscheinen.</div>
      <div class="card"><h3>Buchungen</h3>${(BUCHUNGEN[p.id] || []).map(b => `<div>• ${esc(b.titel)} — ${esc(b.datum)} — ${esc(b.betrag)} <span class="badge ${b.status === 'bezahlt' ? 'b-ok' : 'b-warn'}">${esc(b.status)}</span></div>`).join('') || '<span class="muted">keine</span>'}</div>
      <div class="card"><h3>Verknüpfte Masken</h3><div><a href="#" onclick="toastInfo('→ Dubletten-Review (Bestandsmaske)');return false">Dubletten-Review öffnen</a></div><div><a href="#" onclick="toastInfo('→ Anmeldungen (Bestandsmaske)');return false">Anmeldungen öffnen</a></div></div>`;
  } else if (S.tab === 'login') {
    body.innerHTML = `<div class="inline-info">Daten kommen später aus dem Identity-System — hier gemockt; Zähler aktualisiert sich „live" (simulierter WebSocket).</div>
      <div class="card"><table class="grid"><thead><tr><th>Zeit</th><th>Ergebnis</th><th>IP</th></tr></thead><tbody>${(LOGINVERSUCHE[p.id] || []).map(l => `<tr><td>${esc(l.zeit)}</td><td>${l.ergebnis === 'erfolgreich' ? '<span class="badge b-ok">erfolgreich</span>' : '<span class="badge b-err">fehlgeschlagen</span>'}</td><td>${esc(l.ip)}</td></tr>`).join('') || '<tr><td colspan=3 class="muted">keine</td></tr>'}</tbody></table></div>`;
  } else if (S.tab === 'buchung') {
    body.innerHTML = `<div class="inline-info">Daten kommen später aus dem Shop/LMS — hier gemockt.</div>
      <div class="card"><table class="grid"><thead><tr><th>Produkt</th><th>Datum</th><th>Betrag</th><th>Status</th></tr></thead><tbody>${(BUCHUNGEN[p.id] || []).map(b => `<tr><td>${esc(b.titel)}</td><td>${esc(b.datum)}</td><td>${esc(b.betrag)}</td><td>${esc(b.status)}</td></tr>`).join('') || '<tr><td colspan=4 class="muted">keine</td></tr>'}</tbody></table></div>`;
  }
}

function kanalListe(kps) {
  if (!kps || !kps.length) return '<span class="muted">keine</span>';
  return `<table class="grid"><tbody>${kps.map(k => `<tr class="${k.status === 'EHEMALIG' ? 'row-old' : ''}"><td>${kpIcon(k.typ)} ${esc(kpLabel(k))}</td><td>${esc(kanalText(k))}</td><td>${k.primaer ? '<span class="badge b-info">primär</span>' : ''} ${k.status === 'EHEMALIG' ? '<span class="badge b-grey">ehemalig</span>' : ''} ${k.kontext ? `<span class="chip">${esc(k.kontext)}</span>` : ''}</td></tr>`).join('')}</tbody></table>`;
}
function aktListe(akts, withPerson) {
  if (!akts.length) return '<span class="muted">keine Einträge</span>';
  return akts.map(a => {
    let kontakt = '';
    if (withPerson) {
      const rp = a.personId ? personById(a.personId) : null;
      kontakt = rp ? ` <a class="lnk chip role" data-p="${rp.id}">👤 ${esc(personName(rp))}</a>` : ' <span class="chip">🏢 Firma direkt</span>';
    }
    return `<div style="border-bottom:1px solid var(--grey-100);padding:8px 0">
    <div><b>${esc(a.typ)}</b> ${a.richtung ? `<span class="chip">${a.richtung === 'ein' ? 'eingehend' : 'ausgehend'}</span>` : ''}${kontakt} <span class="muted">${esc(a.zeitpunkt)} · ${esc(a.bearbeiter)}${a.dauer ? ' · ' + esc(a.dauer) : ''}</span></div>
    <div style="font-weight:600">${esc(a.betreff)}</div>
    <div>${a.inhaltHtml}</div>
    ${a.anhaenge && a.anhaenge.length ? `<div class="muted">📎 ${a.anhaenge.map(esc).join(', ')}</div>` : ''}</div>`;
  }).join('');
}

// ---------- Inline-Pflege: Helfer (Kontaktkanäle + Mitgliedschaften) ----------
const KP_TYPEN = ['EMAIL', 'TELEFON', 'ADRESSE'];
function kpIcon(t) { return t === 'EMAIL' ? '✉' : t === 'TELEFON' ? '📞' : '🏠'; }
function laenderOptions(sel) { sel = sel || 'DE'; return LOOKUPS.laender.map(l => `<option value="${l.code}" ${l.code === sel ? 'selected' : ''}>${esc(l.name)}</option>`).join(''); }
function spracheOptions(sel) { sel = sel || 'DE'; return LOOKUPS.sprachen.map(s => `<option value="${s.code}" ${s.code === sel ? 'selected' : ''}>${esc(s.name)}</option>`).join(''); }
function geschlechtOptions(sel, withEmpty) { return (withEmpty ? `<option value="" ${!sel ? 'selected' : ''}>— bitte wählen —</option>` : '') + LOOKUPS.geschlecht.map(g => `<option value="${g.code}" ${g.code === sel ? 'selected' : ''}>${esc(g.bezeichnung)}</option>`).join(''); }
function laenderName(code) { const l = LOOKUPS.laender.find(x => x.code === code); return l ? l.name : (code || ''); }
// Filter für lange Auswahllisten (Element-basiert, ohne IDs)
function wireFilterEl(inp, listEl) { if (!inp || !listEl) return; inp.addEventListener('input', () => { const q = inp.value.toLowerCase(); $$('label', listEl).forEach(l => { l.style.display = l.textContent.toLowerCase().includes(q) ? '' : 'none'; }); }); }
// Primär-Kanal anlegen/ändern (genutzt von Stammdaten-Inline-Edit)
function setPrimChannel(owner, typ, val, kontext) {
  owner.kontaktpunkte = owner.kontaktpunkte || [];
  if (!val) return;
  const k = primKanal(owner.kontaktpunkte, typ);
  if (k) { if (typ === 'EMAIL') k.email = val; else { k.nummerAnzeige = val; k.nummerE164 = val.replace(/[^\d+]/g, ''); } }
  else owner.kontaktpunkte.push(typ === 'EMAIL'
    ? { typ: 'EMAIL', email: val, primaer: true, status: 'AKTIV', kontext }
    : { typ: 'TELEFON', nummerAnzeige: val, nummerE164: val.replace(/[^\d+]/g, ''), primaer: true, status: 'AKTIV', kontext });
}

function kpValueInputs(k) {
  if (k.typ === 'ADRESSE') return `<input data-f="strasse" placeholder="Straße" value="${esc(k.strasse || '')}" style="width:150px" /><input data-f="hausnummer" placeholder="Nr." value="${esc(k.hausnummer || '')}" style="width:55px" /><input data-f="plz" placeholder="PLZ" value="${esc(k.plz || '')}" style="width:65px" /><input data-f="ort" placeholder="Ort" value="${esc(k.ort || '')}" style="width:130px" /><select data-f="land" style="width:170px">${laenderOptions(k.land)}</select>`;
  if (k.typ === 'TELEFON') return `<input data-f="nummerAnzeige" placeholder="+49 …" value="${esc(k.nummerAnzeige || '')}" />`;
  return `<input data-f="email" placeholder="name@firma.de" value="${esc(k.email || '')}" />`;
}
function kpEditRow(k, ref, mitKontext) {
  const typ = k.typ || 'EMAIL';
  return `<tr class="inl-edit"><td colspan="4"><div class="inl-form">
    <select data-kp-typ>${KP_TYPEN.map(t => `<option ${t === typ ? 'selected' : ''}>${t}</option>`).join('')}</select>
    <span data-kp-fields>${kpValueInputs(k)}</span>
    ${mitKontext ? `<select data-kp-kontext><option ${k.kontext === 'privat' ? 'selected' : ''}>privat</option><option ${k.kontext === 'dienstlich' ? 'selected' : ''}>dienstlich</option></select>` : ''}
    <label class="chip"><input type="checkbox" data-kp-prim ${k.primaer ? 'checked' : ''} /> primär</label>
    <button class="btn sm primary" data-kp-save="${ref}">Speichern</button>
    <button class="btn sm" data-kp-cancel>Abbrechen</button>
  </div></td></tr>`;
}
function kanalListeEdit(owner, mitKontext) {
  const kps = owner.kontaktpunkte = owner.kontaktpunkte || [];
  const rows = kps.map((k, i) => {
    if (S.kpEdit === i) return kpEditRow(k, i, mitKontext);
    return `<tr class="${k.status === 'EHEMALIG' ? 'row-old' : ''}">
      <td>${kpIcon(k.typ)} ${esc(kpLabel(k))}</td><td>${esc(kanalText(k))}</td>
      <td>${k.primaer ? '<span class="badge b-info">primär</span>' : ''} ${k.kontext ? `<span class="chip">${esc(k.kontext)}</span>` : ''}</td>
      <td class="row-actions">${!k.primaer ? `<button class="btn sm" data-kp-prim2="${i}" title="als primär setzen">☆</button>` : ''}<button class="btn sm" data-kp-edit="${i}">✎</button><button class="btn sm danger" data-kp-del="${i}" title="entfernen">🗑</button></td></tr>`;
  }).join('');
  const newRow = S.kpEdit === 'new' ? kpEditRow({ typ: 'EMAIL', primaer: false, kontext: mitKontext ? 'privat' : undefined }, 'new', mitKontext) : '';
  return `<table class="grid"><tbody>${rows}${newRow}</tbody></table>
    ${S.kpEdit === null ? '<button class="btn sm" data-kp-add style="margin-top:8px">+ Kanal</button>' : ''}`;
}
function wireKanalListe(owner, mitKontext, rerender) {
  const add = $('#detail [data-kp-add]'); if (add) add.onclick = () => { S.kpEdit = 'new'; rerender(); };
  $$('#detail [data-kp-edit]').forEach(b => b.onclick = () => { S.kpEdit = +b.dataset.kpEdit; rerender(); });
  $$('#detail [data-kp-del]').forEach(b => b.onclick = () => { owner.kontaktpunkte.splice(+b.dataset.kpDel, 1); rerender(); renderList(); toastInfo('Kanal entfernt.'); });
  $$('#detail [data-kp-prim2]').forEach(b => b.onclick = () => { const i = +b.dataset.kpPrim2, t = owner.kontaktpunkte[i].typ; owner.kontaktpunkte.forEach(x => { if (x.typ === t) x.primaer = false; }); owner.kontaktpunkte[i].primaer = true; rerender(); });
  const tsel = $('#detail [data-kp-typ]'); if (tsel) tsel.onchange = () => { $('#detail [data-kp-fields]').innerHTML = kpValueInputs({ typ: tsel.value }); };
  const cancel = $('#detail [data-kp-cancel]'); if (cancel) cancel.onclick = () => { S.kpEdit = null; rerender(); };
  const save = $('#detail [data-kp-save]'); if (save) save.onclick = () => {
    const ref = save.dataset.kpSave, typ = $('#detail [data-kp-typ]').value;
    const obj = { typ, status: 'AKTIV' };
    $$('#detail [data-kp-fields] [data-f]').forEach(inp => obj[inp.dataset.f] = inp.value.trim());
    if (typ === 'TELEFON') obj.nummerE164 = (obj.nummerAnzeige || '').replace(/[^\d+]/g, '');
    const ks = $('#detail [data-kp-kontext]'); if (ks) obj.kontext = ks.value;
    obj.primaer = $('#detail [data-kp-prim]').checked;
    const hasVal = typ === 'ADRESSE' ? (obj.strasse || obj.ort) : typ === 'TELEFON' ? obj.nummerAnzeige : obj.email;
    if (!hasVal) { toastInfo('Bitte einen Wert eingeben.'); return; }
    if (obj.primaer) owner.kontaktpunkte.forEach(x => { if (x.typ === typ) x.primaer = false; });
    if (ref === 'new') owner.kontaktpunkte.push(obj);
    else owner.kontaktpunkte[+ref] = { ...owner.kontaktpunkte[+ref], ...obj };
    S.kpEdit = null; rerender(); renderList(); toastInfo('Kanal gespeichert.');
  };
}

function mgEditFormRow(m, colspan) {
  return `<tr class="inl-edit"><td colspan="${colspan}"><div class="inl-form-block">
    <div class="field"><label>Rollen</label><input data-mg-rsearch placeholder="Rollen filtern …" />
      <div data-mg-rlist style="max-height:110px;overflow:auto;margin-top:4px">${LOOKUPS.rollen.map(r => `<label class="chip"><input type="checkbox" name="mgr" value="${esc(r)}" ${m.rollen.includes(r) ? 'checked' : ''} /> ${esc(r)}</label>`).join('')}</div></div>
    <div class="grid2"><div class="field"><label>Position</label><input data-mg-pos value="${esc(m.position || '')}" /></div>
      <div class="field"><label>&nbsp;</label>
        <label style="font-size:13px;display:block"><input type="checkbox" data-mg-haupt ${m.hauptzugehoerigkeit ? 'checked' : ''} /> Hauptzugehörigkeit (Person)</label>
        <label style="font-size:13px;display:block"><input type="checkbox" data-mg-ansp ${m.hauptansprechpartner ? 'checked' : ''} /> Hauptansprechpartner (Firma)</label>
        <label style="font-size:13px;display:block"><input type="checkbox" data-mg-buch ${m.buchungsberechtigt ? 'checked' : ''} /> buchungsberechtigt</label>
        <label style="font-size:13px;display:block"><input type="checkbox" data-mg-rech ${m.rechnungsempfaenger ? 'checked' : ''} /> Rechnungsempfänger</label></div></div>
    <div class="grid2"><div class="field"><label>Gültig von</label><input type="date" data-mg-von value="${esc(m.gueltigVon || '')}" /></div>
      <div class="field"><label>Gültig bis (leer = aktiv)</label><input type="date" data-mg-bis value="${esc(m.gueltigBis || '')}" /></div></div>
    <div style="display:flex;gap:8px"><button class="btn sm primary" data-mg-save>Speichern</button><button class="btn sm" data-mg-cancel>Abbrechen</button></div>
  </div></td></tr>`;
}
function wireMgEdit(m, rerender) {
  wireFilterEl($('#detail [data-mg-rsearch]'), $('#detail [data-mg-rlist]'));
  $('#detail [data-mg-cancel]').onclick = () => { S.mgEdit = null; rerender(); };
  $('#detail [data-mg-save]').onclick = () => {
    m.rollen = $$('#detail [name=mgr]:checked').map(c => c.value);
    m.position = $('#detail [data-mg-pos]').value.trim();
    const haupt = $('#detail [data-mg-haupt]').checked, ansp = $('#detail [data-mg-ansp]').checked;
    if (haupt) MITGLIEDSCHAFTEN.filter(x => x.personId === m.personId && x !== m).forEach(x => x.hauptzugehoerigkeit = false);
    if (ansp) MITGLIEDSCHAFTEN.filter(x => x.orgId === m.orgId && x !== m).forEach(x => x.hauptansprechpartner = false);
    m.hauptzugehoerigkeit = haupt; m.hauptansprechpartner = ansp;
    m.buchungsberechtigt = $('#detail [data-mg-buch]').checked; m.rechnungsempfaenger = $('#detail [data-mg-rech]').checked;
    m.gueltigVon = $('#detail [data-mg-von]').value || m.gueltigVon; m.gueltigBis = $('#detail [data-mg-bis]').value || null;
    S.mgEdit = null; rerender(); renderList(); toastInfo('Zugehörigkeit gespeichert.');
  };
}

// Inline „Firma verknüpfen" (Bestand zuerst suchen) — Person-Seite
function mgAddCard() {
  return `<div class="card" style="border-color:var(--blue)"><h3>Firma verknüpfen <span class="muted" style="font-weight:400">· Bestand zuerst suchen</span></h3>
    <div class="field"><label>Firma suchen</label><input id="mgAddSearch" placeholder="Name eingeben …" autocomplete="off" /><div id="mgAddRes" class="ck-search-res"></div></div>
    <div class="field"><label>Rollen</label><input id="mgAddRsearch" placeholder="Rollen filtern …" /><div id="mgAddRlist" style="max-height:100px;overflow:auto;margin-top:4px">${LOOKUPS.rollen.map(r => `<label class="chip"><input type="checkbox" name="mgar" value="${esc(r)}" /> ${esc(r)}</label>`).join('')}</div></div>
    <div class="grid2"><div class="field"><label>Position</label><input id="mgAddPos" /></div>
      <div class="field"><label>&nbsp;</label>
        <label style="font-size:13px;display:block"><input type="checkbox" id="mgAddHaupt" /> Hauptzugehörigkeit</label>
        <label style="font-size:13px;display:block"><input type="checkbox" id="mgAddAnsp" /> Hauptansprechpartner</label>
        <label style="font-size:13px;display:block"><input type="checkbox" id="mgAddBuch" /> buchungsberechtigt</label>
        <label style="font-size:13px;display:block"><input type="checkbox" id="mgAddRech" /> Rechnungsempfänger</label></div></div>
    <div style="display:flex;gap:8px"><button class="btn primary" id="mgAddSave" disabled>Verknüpfen</button><button class="btn" id="mgAddCancel">Abbrechen</button></div></div>`;
}
function wireMgAdd(p, rerender) {
  let addOrgId = null;
  wireFilterEl($('#mgAddRsearch'), $('#mgAddRlist'));
  const renderRes = (q) => {
    const taken = mitglFuerPerson(p.id, false).map(m => m.orgId);
    const list = ORGANISATIONEN.filter(o => o.name.toLowerCase().includes(q.toLowerCase()) && !taken.includes(o.id)).slice(0, 6);
    $('#mgAddRes').innerHTML = list.map(o => `<div class="quicklink" data-id="${o.id}">${esc(o.name)} <span class="muted" style="font-size:11px">${esc(o.unternehmenstyp || '')}${ortOf(o) ? ' · ' + esc(ortOf(o)) : ''}</span></div>`).join('') || '<div class="muted" style="padding:4px">kein Treffer — über „+ Firma anlegen" neu erfassen</div>';
    $$('#mgAddRes .quicklink').forEach(e => e.onclick = () => { addOrgId = e.dataset.id; $$('#mgAddRes .quicklink').forEach(x => x.style.background = ''); e.style.background = 'var(--blue-light)'; $('#mgAddSave').disabled = false; });
  };
  renderRes('');
  $('#mgAddSearch').addEventListener('input', e => renderRes(e.target.value));
  $('#mgAddCancel').onclick = () => { S.mgAdd = false; rerender(); };
  $('#mgAddSave').onclick = () => {
    if (!addOrgId) return;
    const rollen = $$('#detail [name=mgar]:checked').map(c => c.value);
    if ($('#mgAddHaupt').checked) MITGLIEDSCHAFTEN.filter(m => m.personId === p.id).forEach(m => m.hauptzugehoerigkeit = false);
    if ($('#mgAddAnsp').checked) MITGLIEDSCHAFTEN.filter(m => m.orgId === addOrgId).forEach(m => m.hauptansprechpartner = false);
    MITGLIEDSCHAFTEN.push({ id: 'mg' + Date.now(), personId: p.id, orgId: addOrgId, rollen, hauptzugehoerigkeit: $('#mgAddHaupt').checked, hauptansprechpartner: $('#mgAddAnsp').checked, buchungsberechtigt: $('#mgAddBuch').checked, rechnungsempfaenger: $('#mgAddRech').checked, position: $('#mgAddPos').value.trim(), abteilung: '', gueltigVon: new Date().toISOString().slice(0, 10), gueltigBis: null, dienstKontaktpunkte: [] });
    S.mgAdd = false; rerender(); renderList(); toastInfo('Verknüpfung angelegt.');
  };
}

function renderOrg(o, d) {
  const tabs = [
    { key: 'ostamm', label: 'Stammdaten' },
    { key: 'opers', label: 'Personen' },
    { key: 'okomm', label: 'Kommunikation' },
    { key: 'ohier', label: 'Hierarchie' },
    { key: 'o360', label: '360°' },
  ];
  d.innerHTML = `
    <div class="dt-head">
      <button class="btn sm back-btn" id="backBtn">‹ Liste</button>
      <span class="avatar org">${esc(initials(o.name))}</span>
      <div><h1 class="dt-title">${esc(o.name)}</h1>
        <div class="dt-meta"><span class="badge b-ok">${esc(o.status)}</span><span>${esc(o.rechtsform)}</span><span>${esc(o.unternehmenstyp)}</span>${o.ausbildungsbetrieb ? '<span class="badge b-info">Ausbildungsbetrieb</span>' : ''}</div></div>
      <div class="dt-actions">
        <button class="btn" id="oNote">📝 Notiz</button>
        <button class="btn" id="oLink">🔗 Person verknüpfen</button>
        <button class="btn" id="oEnrich">🌐 Daten online ziehen</button>
      </div>
    </div>
    ${tabBar(tabs)}`;
  $('#backBtn').onclick = () => document.body.classList.remove('show-detail');
  $('#oNote').onclick = () => openNote(null, o);
  $('#oLink').onclick = () => openVerknuepfen('org', o.id);
  $('#oEnrich').onclick = () => openEnrich(o);
  wireTabs(() => renderOrg(o, d));
  const body = $('#tabBody');

  if (S.tab === 'ostamm') {
    const oe = primKanal(o.kontaktpunkte, 'EMAIL'), ot = primKanal(o.kontaktpunkte, 'TELEFON');
    const rerender = () => renderOrg(o, d);
    if (S.editStamm) {
      body.innerHTML = `<div class="card"><h3>Firmen-Stammdaten bearbeiten</h3>
        <div class="field"><label>Name <span class="req">*</span></label><input id="os_name" value="${esc(o.name)}" /></div>
        <div class="grid2"><div class="field"><label>Rechtsform</label><input id="os_rf" value="${esc(o.rechtsform || '')}" /></div>
        <div class="field"><label>USt-IdNr.</label><input id="os_ust" value="${esc(o.ustId || '')}" /></div></div>
        <div class="grid2"><div class="field"><label>✉ Primär-E-Mail</label><input id="os_mail" value="${esc(oe ? oe.email : '')}" /></div>
        <div class="field"><label>📞 Primär-Telefon</label><input id="os_tel" value="${esc(ot ? ot.nummerAnzeige : '')}" /></div></div>
        <div class="grid2"><div class="field"><label>Website</label><input id="os_web" value="${esc(o.website || '')}" /></div>
        <div class="field"><label>Bestandsgröße (Einheiten)</label><input type="number" id="os_best" value="${o.bestandsgroesse || ''}" /></div></div>
        <div class="grid2"><div class="field"><label>Unternehmenstyp</label><input id="os_typ" list="utDL" value="${esc(o.unternehmenstyp || '')}" /><datalist id="utDL">${LOOKUPS.unternehmenstyp.map(u => `<option value="${esc(u)}"></option>`).join('')}</datalist></div>
        <div class="field"><label>Branche</label><input id="os_branche" list="brDL" value="${esc(o.branche || '')}" /><datalist id="brDL">${LOOKUPS.branche.map(b => `<option value="${esc(b)}"></option>`).join('')}</datalist></div></div>
        <div class="field"><label>Verbände</label><input id="os_vsearch" placeholder="filtern …" /><div id="os_vlist" style="max-height:100px;overflow:auto;margin-top:4px">${LOOKUPS.verbaende.map(v => `<label class="chip"><input type="checkbox" name="ov" value="${esc(v)}" ${(o.verbaende || []).includes(v) ? 'checked' : ''} /> ${esc(v)}</label>`).join('')}</div></div>
        <div class="field"><label>Tätigkeitsschwerpunkte</label><input id="os_ssearch" placeholder="filtern …" /><div id="os_slist" style="max-height:100px;overflow:auto;margin-top:4px">${LOOKUPS.schwerpunkte.map(s => `<label class="chip"><input type="checkbox" name="osp" value="${esc(s)}" ${(o.schwerpunkte || []).includes(s) ? 'checked' : ''} /> ${esc(s)}</label>`).join('')}</div></div>
        <div class="field"><label><input type="checkbox" id="os_azb" ${o.ausbildungsbetrieb ? 'checked' : ''} /> Ausbildungsbetrieb</label></div>
        <div style="display:flex;gap:8px;margin-top:6px"><button class="btn primary" id="os_save">Speichern</button><button class="btn" id="os_cancel">Abbrechen</button></div></div>`;
      wireFilterEl($('#os_vsearch'), $('#os_vlist')); wireFilterEl($('#os_ssearch'), $('#os_slist'));
      $('#os_save').onclick = () => {
        o.name = $('#os_name').value.trim() || o.name; o.rechtsform = $('#os_rf').value.trim(); o.ustId = $('#os_ust').value.trim();
        o.website = $('#os_web').value.trim(); o.bestandsgroesse = $('#os_best').value ? +$('#os_best').value : null;
        o.unternehmenstyp = $('#os_typ').value.trim(); o.branche = $('#os_branche').value.trim();
        o.verbaende = $$('#os_vlist input:checked').map(c => c.value); o.schwerpunkte = $$('#os_slist input:checked').map(c => c.value);
        o.ausbildungsbetrieb = $('#os_azb').checked;
        setPrimChannel(o, 'EMAIL', $('#os_mail').value.trim()); setPrimChannel(o, 'TELEFON', $('#os_tel').value.trim());
        S.editStamm = false; renderList(); rerender(); toastInfo('Gespeichert.');
      };
      $('#os_cancel').onclick = () => { S.editStamm = false; rerender(); };
    } else {
      body.innerHTML = `<div class="card"><h3>Firmen-Stammdaten <button class="btn sm primary" id="os_edit" style="float:right">✎ Bearbeiten</button></h3><dl class="kv">
        <dt>Name</dt><dd>${esc(o.name)}</dd>
        <dt>Rechtsform</dt><dd>${esc(o.rechtsform || '–')}</dd>
        <dt>USt-IdNr.</dt><dd>${esc(o.ustId || '–')}</dd>
        <dt>Website</dt><dd>${o.website ? `<a href="${esc(o.website)}" target="_blank">${esc(o.website)}</a>` : '–'}</dd>
        <dt>Branche</dt><dd>${esc(o.branche || '–')}</dd>
        <dt>Unternehmenstyp</dt><dd>${esc(o.unternehmenstyp || '–')}</dd>
        <dt>Tätigkeitsschwerpunkte</dt><dd>${(o.schwerpunkte || []).map(s => `<span class="chip">${esc(s)}</span>`).join('') || '–'}</dd>
        <dt>Verbände</dt><dd>${(o.verbaende || []).map(v => `<span class="chip role">${esc(v)}</span>`).join('') || '–'}</dd>
        <dt>Bestandsgröße</dt><dd>${o.bestandsgroesse ? esc(o.bestandsgroesse) + ' Einheiten' : '–'}</dd>
        <dt>§34c/§34i-Erlaubnis</dt><dd>${o.erlaubnis34c && o.erlaubnis34c.vorhanden ? `✔ vorhanden (${esc(o.erlaubnis34c.behoerde)}, ${esc(o.erlaubnis34c.datum)})` : '–'}</dd>
        <dt>IHK/Kammer</dt><dd>${esc(o.ihk || '–')}</dd>
      </dl></div>`;
      $('#os_edit').onclick = () => { S.editStamm = true; rerender(); };
    }
  } else if (S.tab === 'opers') {
    const aktiv = mitglFuerOrg(o.id, false), ehemalig = mitglFuerOrg(o.id, true).filter(m => m.gueltigBis);
    const rerender = () => renderOrg(o, d);
    const row = (m, old) => {
      const p = personById(m.personId);
      if (!old && S.mgEdit === m.id) return mgEditFormRow(m, 6);
      return `<tr class="${old ? 'row-old' : ''}"><td><a class="lnk" data-p="${p.id}">${esc(personName(p))}</a>${m.hauptansprechpartner ? ' <span class="badge b-ok">Hauptansprechpartner</span>' : ''}</td>
      <td>${m.rollen.map(r => `<span class="chip role">${esc(r)}</span>`).join('')}</td><td>${esc(m.position || '–')}</td>
      <td>${m.buchungsberechtigt ? '✔' : '–'}</td><td>${m.dienstKontaktpunkte && m.dienstKontaktpunkte.length ? m.dienstKontaktpunkte.map(kanalText).map(esc).join('<br>') : '<span class="muted">–</span>'}</td>
      <td class="row-actions">${old ? '' : `<button class="btn sm" data-mg-edit="${m.id}">✎</button><button class="btn sm" data-mg-leave="${m.id}" title="Ausscheiden">⏏</button>`}</td></tr>`;
    };
    body.innerHTML = `<div class="card"><h3>Zugeordnete Personen <span class="muted" style="font-weight:400">· inline pflegbar</span></h3>
      <table class="grid"><thead><tr><th>Person</th><th>Rollen</th><th>Position</th><th>Bucht</th><th>Dienstl. Kontakt</th><th></th></tr></thead>
      <tbody>${aktiv.map(m => row(m, false)).join('') || '<tr><td colspan=6 class="muted">keine</td></tr>'}</tbody></table>
      <button class="btn sm" id="addPers" style="margin-top:10px">+ Person verknüpfen / anlegen</button></div>
      ${ehemalig.length ? `<div class="card"><h3>Ehemalige</h3><table class="grid"><tbody>${ehemalig.map(m => row(m, true)).join('')}</tbody></table></div>` : ''}`;
    $('#addPers').onclick = () => openVerknuepfen('org', o.id);
    $$('#detail .lnk[data-p]').forEach(e => e.onclick = () => select('person', e.dataset.p));
    $$('#detail [data-mg-edit]').forEach(b => b.onclick = () => { S.mgEdit = b.dataset.mgEdit; rerender(); });
    $$('#detail [data-mg-leave]').forEach(b => b.onclick = () => { const m = MITGLIEDSCHAFTEN.find(x => x.id === b.dataset.mgLeave); m.gueltigBis = new Date().toISOString().slice(0, 10); rerender(); renderList(); toastInfo('Zugehörigkeit beendet (historisiert).'); });
    if (S.mgEdit) { const m = MITGLIEDSCHAFTEN.find(x => x.id === S.mgEdit); if (m) wireMgEdit(m, rerender); }
  } else if (S.tab === 'okomm') {
    const rerender = () => renderOrg(o, d);
    const memberIds = mitglFuerOrg(o.id, true).map(m => m.personId);
    const akts = AKTIVITAETEN
      .filter(a => a.orgId === o.id || (a.personId && memberIds.includes(a.personId)))
      .sort((a, b) => (b.zeitpunkt || '').localeCompare(a.zeitpunkt || ''));
    body.innerHTML = `<div class="card"><h3>Kontaktkanäle <span class="muted" style="font-weight:400">· inline pflegbar</span></h3>${kanalListeEdit(o, false)}</div>
      <div class="card"><h3>Kontakthistorie <span class="muted" style="font-weight:400">· Firma + verknüpfte Personen</span></h3>${aktListe(akts, true)}
      <button class="btn sm" id="oAddNote" style="margin-top:8px">+ Notiz / Kommunikation</button></div>`;
    wireKanalListe(o, false, rerender);
    $('#oAddNote').onclick = () => openNote(null, o);
    $$('#detail .lnk[data-p]').forEach(e => e.onclick = () => select('person', e.dataset.p));
  } else if (S.tab === 'ohier') {
    const rerender = () => renderOrg(o, d);
    const mutter = o.uebergeordneteOrgId ? orgById(o.uebergeordneteOrgId) : null;
    const toechter = ORGANISATIONEN.filter(x => x.uebergeordneteOrgId === o.id);
    const andere = ORGANISATIONEN.filter(x => x.id !== o.id && x.uebergeordneteOrgId !== o.id);
    body.innerHTML = `<div class="card"><h3>Firmenhierarchie (Mutter/Tochter)</h3>
      <div class="field" style="max-width:380px"><label>Übergeordnete Firma</label>
        <select id="oh_parent"><option value="">— eigenständig —</option>${andere.map(x => `<option value="${x.id}" ${x.id === o.uebergeordneteOrgId ? 'selected' : ''}>${esc(x.name)}</option>`).join('')}</select></div>
      ${mutter ? `<div style="margin-top:4px">Aktuell übergeordnet: <a class="lnk" data-org="${mutter.id}">${esc(mutter.name)}</a></div>` : ''}
      <div style="margin-top:10px">Tochtergesellschaften: ${toechter.length ? toechter.map(t => `<a class="lnk" data-org="${t.id}">${esc(t.name)}</a>`).join(', ') : '<span class="muted">keine</span>'}</div></div>`;
    $('#oh_parent').onchange = (e) => { o.uebergeordneteOrgId = e.target.value || null; rerender(); toastInfo('Hierarchie aktualisiert.'); };
    $$('#detail .lnk[data-org]').forEach(e => e.onclick = () => select('org', e.dataset.org));
  } else if (S.tab === 'o360') {
    body.innerHTML = `<div class="inline-info">360°: aggregierte Buchungen/Rechnungen aller zugeordneten Personen im Firmenkontext (read-only).</div><div class="card muted">Gemockt — Daten kommen später.</div>`;
  }
}

// ===================================================================
// COCKPIT — automatisierte Prozesse überblicken, eingreifen (HITL),
// Sonderfälle manuell abarbeiten; Einstieg in den m:n-Kundenstamm.
// ===================================================================
const PR_KEY = { 'pr-untis': 'webuntis', 'pr-anm': 'anmeldung', 'pr-doi': 'opt-in', 'pr-rech': 'rechnung', 'pr-lms': 'lms' };
function egForProzess(pr) { const k = PR_KEY[pr.id]; return EINGRIFFE.filter(e => e.prozess.toLowerCase().includes(k)); }
function healthLabel(h) { return h === 'ok' ? 'läuft' : h === 'warn' ? 'beobachten' : 'gestört'; }
function alleSonderfaelle() {
  const wv = WIEDERVORLAGEN.filter(w => !w.erledigt).map(w => ({
    id: 'wv:' + w.id, icon: '⏰', titel: w.betreff, faellig: w.faelligAm, prioritaet: w.prioritaet || 'mittel',
    detail: 'Wiedervorlage' + (w.zugewiesenAn ? ' · ' + w.zugewiesenAn : ''), personId: w.personId,
  }));
  return [...SONDERFAELLE, ...wv].sort((a, b) => (a.faellig || '').localeCompare(b.faellig || ''));
}

// Nav (linke Schiene) — aktiver Eintrag + Zähler
function renderCkNav() {
  $$('.ck-nav').forEach(b => b.classList.toggle('active', b.dataset.nav === S.ckNav));
  document.body.classList.toggle('on-kunden', S.ckNav === 'kunden');
  const ce = $('#cntEingriffe'); if (ce) { ce.textContent = EINGRIFFE.length || ''; ce.style.display = EINGRIFFE.length ? '' : 'none'; }
  const cs = $('#cntSonder'); if (cs) { const n = alleSonderfaelle().length; cs.textContent = n || ''; cs.style.display = n ? '' : 'none'; }
}
function goCkNav(nav) { S.ckNav = nav; resetEdit(); renderCkNav(); renderCkMain(); }
function renderCkMain() {
  const main = $('#ckMain');
  main.classList.toggle('flush', S.ckNav === 'kunden'); // Master-Detail randlos
  if (S.ckNav === 'eingriffe') return renderEingriffePage();
  if (S.ckNav === 'sonder') return renderSonderPage();
  if (S.ckNav === 'kunden') return renderKundenPage();
  renderDashboard();
}
function renderKundenPage() {
  $('#ckMain').innerHTML = `<div class="kunden-page">
    <section class="masterlist">
      <div class="ml-head">
        <button class="seg ${S.seg === 'alle' ? 'active' : ''}" data-seg="alle">Alle</button>
        <button class="seg ${S.seg === 'person' ? 'active' : ''}" data-seg="person">Personen</button>
        <button class="seg ${S.seg === 'org' ? 'active' : ''}" data-seg="org">Firmen</button>
      </div>
      <div id="list"></div>
    </section>
    <main class="detail" id="detail"></main>
  </div>`;
  $$('#ckMain .seg').forEach(s => s.onclick = () => { S.seg = s.dataset.seg; $$('#ckMain .seg').forEach(x => x.classList.toggle('active', x === s)); renderList(); });
  renderList(); renderDetail();
}

// Karten / Zeilen
function processRow(pr) {
  const eg = egForProzess(pr).length;
  const act = (pr.health === 'err' || eg)
    ? `<button class="btn sm danger" data-prgo="${pr.id}">→ Eingriff${eg ? ' (' + eg + ')' : ''}</button>`
    : `<button class="btn sm" data-prdet="${pr.id}">Details ›</button>`;
  return `<tr>
    <td class="proc-name"><span class="ck-ico">${pr.icon}</span> <b>${esc(pr.name)}</b><div class="muted proc-desc">${esc(pr.beschreibung)}</div></td>
    <td><span class="hdot h-${pr.health}"></span> ${healthLabel(pr.health)} <div class="muted" style="font-size:11px">${esc(pr.last)}</div></td>
    <td><b>${esc(pr.kennzahl)}</b><div class="muted">${esc(pr.detail)}</div></td>
    <td class="proc-act">${act}</td></tr>`;
}
function eingriffCard(e) {
  const sev = e.schwere === 'err' ? 'b-err' : 'b-warn';
  return `<div class="ck-item sev-${e.schwere}">
    <div class="ck-item-h"><span class="badge ${sev}">${e.schwere === 'err' ? 'Fehler' : 'Achtung'}</span> <b>${esc(e.titel)}</b> <span class="muted ck-age">${esc(e.alter)}</span></div>
    <div class="ck-item-sub muted">${esc(e.prozess)} — ${esc(e.detail)}</div>
    <div class="ck-item-act">${e.aktionen.map(a => `<button class="btn sm" data-eg="${e.id}" data-act="${a.key}">${esc(a.label)}</button>`).join('')}</div>
  </div>`;
}
function sonderCard(s) {
  const prio = s.prioritaet === 'hoch' ? 'b-err' : s.prioritaet === 'niedrig' ? 'b-grey' : 'b-warn';
  return `<div class="ck-item">
    <div class="ck-item-h"><span class="ck-ico">${s.icon || '✋'}</span> <b>${esc(s.titel)}</b> <span class="badge ${prio} ck-age">fällig ${esc((s.faellig || '').slice(5))}</span></div>
    <div class="ck-item-sub muted">${esc(s.detail || '')}</div>
    <div class="ck-item-act"><button class="btn sm primary" data-sf="${s.id}">Abarbeiten ›</button>${s.personId ? `<button class="btn sm" data-sf-k="${s.personId}">↗ Kontakt</button>` : ''}</div>
  </div>`;
}
function wireEingriffe() { $$('#ckMain [data-eg]').forEach(b => b.onclick = () => resolveEingriff(b.dataset.eg, b.dataset.act)); }
function wireSonder() {
  $$('#ckMain [data-sf]').forEach(b => b.onclick = () => resolveSonder(b.dataset.sf));
  $$('#ckMain [data-sf-k]').forEach(b => b.onclick = () => select('person', b.dataset.sfK));
}
function resolveEingriff(id, act) {
  const e = EINGRIFFE.find(x => x.id === id); if (!e) return;
  if (act === 'kontakt') { if (e.personId) select('person', e.personId); return; }
  const msg = { retry: 'Erneut angestoßen — Vorgang läuft wieder.', uebernehmen: 'Manuell übernommen, Vorgang geschlossen.', merge: 'Datensätze zusammengeführt.', ignorieren: 'Als „kein Treffer" markiert.', erneut: 'Double-Opt-In erneut versendet.', verwerfen: 'Vorgang verworfen.' }[act] || 'Erledigt.';
  EINGRIFFE.splice(EINGRIFFE.indexOf(e), 1);
  renderCkNav(); if (S.view === 'cockpit') renderCkMain();
  toastInfo(msg);
}
function resolveSonder(id) {
  if (id.startsWith('wv:')) { const w = WIEDERVORLAGEN.find(x => x.id === id.slice(3)); if (w) w.erledigt = true; renderSideWv(); }
  else { const i = SONDERFAELLE.findIndex(x => x.id === id); if (i >= 0) SONDERFAELLE.splice(i, 1); }
  renderCkNav(); if (S.view === 'cockpit') renderCkMain();
  toastInfo('Sonderfall abgearbeitet.');
}
function ckSearch(q) {
  const box = $('#ckSearchRes'); if (!box) return;
  const ql = q.trim().toLowerCase();
  if (!ql) { box.innerHTML = ''; return; }
  const res = [];
  PERSONEN.forEach(p => { if ((personName(p) + ' ' + p.kontaktpunkte.map(kanalText).join(' ')).toLowerCase().includes(ql)) res.push({ t: 'person', o: p }); });
  ORGANISATIONEN.forEach(o => { if ((o.name + ' ' + (o.ustId || '')).toLowerCase().includes(ql)) res.push({ t: 'org', o }); });
  box.innerHTML = res.slice(0, 6).map(r => `<div class="quicklink" data-t="${r.t}" data-id="${r.o.id}"><span class="avatar ${r.t === 'org' ? 'org' : ''}" style="width:24px;height:24px;font-size:10px">${esc(initials(r.t === 'person' ? r.o.vorname + ' ' + r.o.nachname : r.o.name))}</span>${esc(r.t === 'person' ? personName(r.o) : r.o.name)}</div>`).join('') || '<div class="muted" style="padding:4px">kein Treffer</div>';
  $$('#ckSearchRes .quicklink').forEach(el => el.onclick = () => select(el.dataset.t, el.dataset.id));
}

function renderDashboard() {
  const eg = EINGRIFFE, sf = alleSonderfaelle();
  $('#ckMain').innerHTML = `<div class="ck-page">
    <div class="ck-head"><h1>Guten Tag, ${esc(MITARBEITER.name.split(' ')[0])}</h1>
      <div class="muted">Betriebs-Cockpit · ${esc(heute())} · ${eg.length} Eingriff(e), ${sf.length} Sonderfall/-fälle offen</div></div>
    <section class="card">
      <h3>👥 Kundenstamm (Person ↔ Firma, m:n)</h3>
      <div class="ck-kunden-row"><input id="ckSearch" placeholder="Person, Firma, USt-IdNr. suchen …" /><button class="btn primary" id="ckOpen">Kundenstamm öffnen ›</button></div>
      <div id="ckSearchRes" class="ck-search-res"></div>
      <div class="inline-info" style="margin-top:10px">Marketing-relevante Daten der Festkunden bleiben hier <b>pflegbar</b> und werden über die <b>HubSpot-Bridge</b> synchronisiert — Kampagnen &amp; Marketing-Automation laufen in HubSpot, nicht in diesem Cockpit.</div>
    </section>
    <div class="ck-cols">
      <section class="card"><h3>⚠ Eingriff nötig (HITL)</h3>${eg.length ? eg.map(eingriffCard).join('') : '<div class="muted">keine offenen Eingriffe ✓</div>'}</section>
      <section class="card"><h3>✋ Meine Sonderfälle</h3>${sf.length ? sf.map(sonderCard).join('') : '<div class="muted">keine offenen</div>'}</section>
    </div>
    <section class="card">
      <h3>Automatisierte Prozesse <span class="muted" style="font-weight:400">· live (gemockt)</span></h3>
      <table class="grid proc"><thead><tr><th>Prozess</th><th>Status</th><th>Kennzahl</th><th></th></tr></thead>
      <tbody>${PROZESSE.map(processRow).join('')}</tbody></table>
    </section></div>`;
  wireEingriffe(); wireSonder();
  $$('#ckMain [data-prgo]').forEach(b => b.onclick = () => goCkNav('eingriffe'));
  $$('#ckMain [data-prdet]').forEach(b => b.onclick = () => { const pr = PROZESSE.find(x => x.id === b.dataset.prdet); toastInfo(pr.name + ': ' + pr.beschreibung); });
  $('#ckOpen').onclick = () => goCkNav('kunden');
  $('#ckSearch').addEventListener('input', ev => ckSearch(ev.target.value));
}
function renderEingriffePage() {
  $('#ckMain').innerHTML = `<div class="ck-page">
    <div class="ck-head"><h1>⚠ Eingriffe (Human-in-the-loop)</h1>
      <div class="muted">Vorgänge, bei denen die Automatik anhält und eine Entscheidung braucht.</div></div>
    ${EINGRIFFE.length ? EINGRIFFE.map(eingriffCard).join('') : '<div class="card muted">Keine offenen Eingriffe — alles läuft. ✓</div>'}</div>`;
  wireEingriffe();
}
function renderSonderPage() {
  const list = alleSonderfaelle();
  $('#ckMain').innerHTML = `<div class="ck-page">
    <div class="ck-head"><h1>✋ Sonderfälle</h1>
      <div class="muted">Nicht automatisierte Vorgänge zur manuellen Abarbeitung (inkl. Wiedervorlagen).</div></div>
    ${list.length ? list.map(sonderCard).join('') : '<div class="card muted">Keine offenen Sonderfälle.</div>'}</div>`;
  wireSonder();
}

// ---------- Modals ----------
function openModal(html, wide) { $('#modal').className = 'modal' + (wide ? ' wide' : ''); $('#modal').innerHTML = html; $('#modalBg').classList.add('open'); }
function closeModal() { $('#modalBg').classList.remove('open'); }
$('#modalBg').onclick = (e) => { if (e.target.id === 'modalBg') closeModal(); };

// Gestufte Erfassung — Person
function openNewPerson() {
  openModal(`
    <div class="modal-h"><h2>Person anlegen</h2><button class="x" onclick="closeModal()">×</button></div>
    <div class="modal-b">
      <div class="inline-info">Schnellerfassung: nur Pflichtfelder nötig. Speichern auch <b>unvollständig</b> möglich (z. B. im Telefonat) — landet als Nachpflege-To-do.</div>
      <details class="stufe focus" open><summary><span class="tag b-err">1</span> Pflichtfelder</summary><div class="stufe-b">
        <div class="grid2">
          <div class="field"><label>Vorname <span class="req">*</span></label><input id="f_vorname" /><div class="err-msg" id="e_vorname" style="display:none">Pflichtfeld</div></div>
          <div class="field"><label>Nachname <span class="req">*</span></label><input id="f_nachname" /><div class="err-msg" id="e_nachname" style="display:none">Pflichtfeld</div></div>
        </div>
        <div class="grid2">
          <div class="field"><label>Titel</label><input id="f_titel" placeholder="z. B. Dr. (DE vorn) / MBA (int. hinten)" /></div>
          <div class="field"><label>Geschlecht <span class="req">*</span></label><select id="f_g">${geschlechtOptions('', true)}</select><div class="err-msg" id="e_g" style="display:none">Pflichtfeld</div><div class="hint">Anrede wird daraus abgeleitet (Fallback „Hallo …").</div></div>
        </div>
        <div class="field"><label>Kontaktkanal <span class="req">*</span> (E-Mail oder Telefon)</label><input id="f_kanal" placeholder="z. B. name@firma.de oder +49 …" /><div class="err-msg" id="e_kanal" style="display:none">Mindestens ein Kontaktkanal</div></div>
        <div id="dubHint"></div>
      </div></details>
      <details class="stufe"><summary><span class="tag b-info">2</span> Marketingrelevant</summary><div class="stufe-b">
        <div class="grid2">
          <div class="field"><label>Lead-Quelle</label><select id="f_lead">${LOOKUPS.leadQuelle.map(q => `<option>${q}</option>`).join('')}</select></div>
          <div class="field"><label>Marketing-Opt-In (Kanal)</label><select id="f_optin"><option value="">— kein —</option><option>E-Mail</option><option>Telefon</option><option>Post</option></select></div>
        </div>
        <div class="field"><label>Opt-In-Kontext</label><select><option>global</option><option>Firmenkontext</option></select><div class="hint">Neuer Kontakt erzeugt automatisch Einwilligung <b>AUSSTEHEND</b> → Double-Opt-In wird angestoßen.</div></div>
      </div></details>
      <details class="stufe"><summary><span class="tag b-grey">3</span> Weitere Kontaktdaten</summary><div class="stufe-b">
        <div class="field"><label>Adresse</label><input placeholder="Straße, Nr." /><div class="grid2"><input placeholder="PLZ" /><input placeholder="Ort" /></div></div>
        <div class="field"><label>Firmenzugehörigkeit</label><select><option value="">— privat —</option>${ORGANISATIONEN.map(o => `<option>${esc(o.name)}</option>`).join('')}</select></div>
      </div></details>
      <details class="stufe"><summary><span class="tag b-grey">4</span> Alles andere</summary><div class="stufe-b">
        <div class="grid2"><div class="field"><label>Geburtsdatum</label><input type="date" /></div><div class="field"><label>Korrespondenzsprache</label><select id="f_spr">${spracheOptions('DE')}</select></div></div>
        <div class="field"><label>Staatsangehörigkeit(en)</label><input placeholder="DE, …" /></div>
      </div></details>
    </div>
    <div class="modal-f">
      <button class="btn" onclick="closeModal()">Abbrechen</button>
      <button class="btn" id="saveIncomplete">Unvollständig speichern</button>
      <button class="btn primary" id="savePerson">Speichern</button>
    </div>`);
  // Live-Dublettenhinweis (on-demand: bei Namens-Eingabe, debounced)
  let t; $('#f_nachname').addEventListener('input', () => { clearTimeout(t); t = setTimeout(checkDub, 300); });
  function checkDub() {
    const nn = $('#f_nachname').value.trim().toLowerCase();
    const hit = nn && PERSONEN.find(p => p.nachname.toLowerCase() === nn);
    $('#dubHint').innerHTML = hit ? `<div class="inline-warn">⚠ Mögliche Dublette: <b>${esc(personName(hit))}</b>. <button class="btn sm" onclick="closeModal();select('person','${hit.id}')">Bestand öffnen</button> <button class="btn sm" onclick="toastInfo('KI-Ähnlichkeitsprüfung (on-demand) gestartet …')">KI prüfen</button></div>` : '';
  }
  const doSave = (incomplete) => {
    const vn = $('#f_vorname').value.trim(), nn = $('#f_nachname').value.trim(), k = $('#f_kanal').value.trim();
    let ok = true;
    [['vorname', vn], ['nachname', nn], ['kanal', k]].forEach(([id, val]) => {
      const bad = !val && !incomplete; $('#e_' + id).style.display = bad ? 'block' : 'none';
      $('#f_' + id).parentElement.classList.toggle('err', bad); if (bad) ok = false;
    });
    const gbad = !$('#f_g').value && !incomplete; $('#e_g').style.display = gbad ? 'block' : 'none'; if (gbad) ok = false;
    if (!incomplete && !ok) return;
    const id = 'p' + (PERSONEN.length + 1);
    const kps = []; if (k) kps.push(k.includes('@') ? { typ: 'EMAIL', email: k, primaer: true, status: 'AKTIV', kontext: 'privat' } : { typ: 'TELEFON', nummerAnzeige: k, nummerE164: k.replace(/[^\d+]/g, ''), primaer: true, status: 'AKTIV', kontext: 'privat' });
    PERSONEN.push({ id, vorname: vn || '(?)', nachname: nn || '(?)', geschlecht: $('#f_g').value, titel: $('#f_titel').value.trim(), geburtsdatum: null, staatsangehoerigkeit: [], korrespondenzsprache: $('#f_spr').value || 'DE', status: incomplete ? 'PROVISORISCH' : 'AKTIV', werbesperre: false, auskunftssperre: false, leadQuelle: $('#f_lead').value, unvollstaendig: !!incomplete, foto: null, login: [], kontaktpunkte: kps, einwilligungen: $('#f_optin').value ? [{ kanal: $('#f_optin').value, zweck: 'Marketing', status: 'AUSSTEHEND', kontext: 'global', quelle: $('#f_lead').value, datum: '2026-06-16', rechtsgrundlage: 'Art. 6.1.a' }] : [], beziehungen: [] });
    closeModal(); S.seg = 'person'; select('person', id);
    toastInfo(incomplete ? 'Unvollständig gespeichert — Nachpflege-To-do angelegt.' : 'Person gespeichert.');
  };
  $('#savePerson').onclick = () => doSave(false);
  $('#saveIncomplete').onclick = () => doSave(true);
}

// Gestufte Erfassung — Firma (mit „Daten online ziehen")
function openNewOrg() {
  openModal(`
    <div class="modal-h"><h2>Firma anlegen</h2><button class="x" onclick="closeModal()">×</button></div>
    <div class="modal-b">
      <div class="inline-info">Tipp: Website/USt-IdNr. eingeben und <b>„Daten online ziehen"</b> — Vorschlag aus VIES/Impressum/KI (hier gemockt) zum Übernehmen.</div>
      <div class="field"><label>Website oder USt-IdNr. (für Anreicherung)</label>
        <div style="display:flex;gap:6px"><input id="o_url" placeholder="https://… oder DE…" style="flex:1" /><button class="btn" id="o_pull">🌐 Daten ziehen</button></div></div>
      <div id="enrichResult"></div>
      <details class="stufe focus" open><summary><span class="tag b-err">1</span> Pflichtfelder</summary><div class="stufe-b">
        <div class="field"><label>Firmenname <span class="req">*</span></label><input id="o_name" /><div class="err-msg" id="e_oname" style="display:none">Pflichtfeld</div></div>
        <div class="field"><label>Kontaktkanal/Adresse <span class="req">*</span> <span class="muted">(oder Person unten)</span></label><input id="o_kanal" placeholder="Telefon, E-Mail oder Adresse" /><div class="err-msg" id="e_okanal" style="display:none">Mindestens ein Kanal/Adresse <b>oder</b> eine Person</div></div>
      </div></details>
      <details class="stufe" open><summary><span class="tag b-info">★</span> Ansprechpartner (Person)</summary><div class="stufe-b">
        <div class="inline-info">Eine Firma braucht mind. einen Kanal/Adresse <b>oder</b> eine Person. Bei bestehender Person bitte <b>verwenden</b> statt neu anlegen (Dublettenschutz).</div>
        <div class="grid2"><div class="field"><label>Vorname</label><input id="op_vn" /></div><div class="field"><label>Nachname</label><input id="op_nn" /></div></div>
        <div id="opDub"></div>
        <div class="grid2"><div class="field"><label>Geschlecht</label><select id="op_g">${geschlechtOptions('', true)}</select></div>
        <div class="field"><label>E-Mail / Telefon</label><input id="op_kanal" placeholder="optional" /></div></div>
        <div class="field"><label>Rolle</label><input id="op_rolle" list="opRolleDL" placeholder="z. B. Geschäftsführung" /><datalist id="opRolleDL">${LOOKUPS.rollen.map(r => `<option value="${esc(r)}"></option>`).join('')}</datalist></div>
      </div></details>
      <details class="stufe"><summary><span class="tag b-info">2</span> Marketingrelevant (B2B)</summary><div class="stufe-b">
        <div class="grid2"><div class="field"><label>Unternehmenstyp</label><select id="o_typ">${LOOKUPS.unternehmenstyp.map(u => `<option>${u}</option>`).join('')}</select></div>
        <div class="field"><label>Lead-Quelle</label><select>${LOOKUPS.leadQuelle.map(q => `<option>${q}</option>`).join('')}</select></div></div>
        <div class="field"><label>Verbände</label><div>${LOOKUPS.verbaende.map(v => `<label class="chip"><input type="checkbox" /> ${v}</label>`).join('')}</div></div>
      </div></details>
      <details class="stufe"><summary><span class="tag b-grey">3</span> Weitere Daten</summary><div class="stufe-b">
        <div class="grid2"><div class="field"><label>Rechtsform</label><input id="o_rf" /></div><div class="field"><label>USt-IdNr.</label><input id="o_ust" /></div></div>
        <div class="grid2"><div class="field"><label>Branche</label><select>${LOOKUPS.branche.map(b => `<option>${b}</option>`).join('')}</select></div><div class="field"><label>Bestandsgröße (Einheiten)</label><input type="number" /></div></div>
        <label style="font-size:13px"><input type="checkbox" /> Ausbildungsbetrieb</label>
      </div></details>
    </div>
    <div class="modal-f"><button class="btn" onclick="closeModal()">Abbrechen</button><button class="btn primary" id="saveOrg">Speichern</button></div>`);
  $('#o_pull').onclick = () => {
    $('#enrichResult').innerHTML = '<div class="inline-info">⏳ Rufe VIES + Impressum + KI-Extraktion (gemockt) …</div>';
    setTimeout(() => {
      $('#enrichResult').innerHTML = `<div class="inline-info">🌐 Vorschlag gefunden: <b>Beispiel Immobilien GmbH</b>, Musterstr. 1, 44787 Bochum · USt DE123456789 · IVD-Mitglied. <button class="btn sm" id="applyEnrich">Übernehmen</button></div>`;
      $('#applyEnrich').onclick = () => { $('#o_name').value = 'Beispiel Immobilien GmbH'; $('#o_kanal').value = 'Musterstr. 1, 44787 Bochum'; $('#o_rf').value = 'GmbH'; $('#o_ust').value = 'DE123456789'; toastInfo('Firmendaten übernommen — bitte prüfen.'); };
    }, 900);
  };
  // Dublettenabgleich: Ansprechpartner kann bereits (z. B. als Privatperson) existieren
  let opPersonId = null, opDubT;
  const setOpFieldsDisabled = (b) => { ['#op_vn', '#op_nn', '#op_g', '#op_kanal'].forEach(s => $(s).disabled = b); };
  const opDub = () => {
    if (opPersonId) return; // bereits eine bestehende Person gewählt
    const nn = $('#op_nn').value.trim().toLowerCase(), vn = $('#op_vn').value.trim().toLowerCase();
    const hits = nn ? PERSONEN.filter(p => p.nachname.toLowerCase() === nn && (!vn || p.vorname.toLowerCase().startsWith(vn))) : [];
    $('#opDub').innerHTML = hits.length
      ? `<div class="inline-warn">⚠ Möglicherweise vorhanden: ${hits.map(h => `<button class="btn sm" data-usep="${h.id}">${esc(personName(h))}${hauptFirmaFuer(h) ? ' (' + esc(hauptFirmaFuer(h).name) + ')' : ' (privat)'} verwenden</button>`).join(' ')} <span class="muted">— sonst neu anlegen</span></div>`
      : '';
    $$('#opDub [data-usep]').forEach(btn => btn.onclick = () => {
      opPersonId = btn.dataset.usep; const pp = personById(opPersonId);
      $('#op_vn').value = pp.vorname; $('#op_nn').value = pp.nachname; setOpFieldsDisabled(true);
      $('#opDub').innerHTML = `<div class="inline-info">✓ Bestehende Person <b>${esc(personName(pp))}</b> wird verknüpft (keine Dublette). <button class="btn sm" id="opUndo">ändern</button></div>`;
      $('#opUndo').onclick = () => { opPersonId = null; setOpFieldsDisabled(false); $('#op_vn').value = ''; $('#op_nn').value = ''; $('#opDub').innerHTML = ''; };
    });
  };
  $('#op_nn').addEventListener('input', () => { clearTimeout(opDubT); opDubT = setTimeout(opDub, 300); });
  $('#saveOrg').onclick = () => {
    const name = $('#o_name').value.trim(), k = $('#o_kanal').value.trim();
    const opVn = $('#op_vn').value.trim(), opNn = $('#op_nn').value.trim();
    const hasPerson = !!(opPersonId || opNn);
    let ok = true;
    if (!name) { $('#e_oname').style.display = 'block'; ok = false; }
    if (!k && !hasPerson) { $('#e_okanal').style.display = 'block'; ok = false; } // Kanal/Adresse ODER Person
    if (!opPersonId && opNn && !$('#op_g').value) { toastInfo('Geschlecht der neuen Person wählen (Pflichtfeld).'); ok = false; }
    if (!ok) return;
    const id = 'o' + (ORGANISATIONEN.length + 1);
    ORGANISATIONEN.push({ id, name, rechtsform: $('#o_rf').value, ustId: $('#o_ust').value, website: '', branche: '', unternehmenstyp: $('#o_typ').value, schwerpunkte: [], verbaende: [], bestandsgroesse: null, erlaubnis34c: { vorhanden: false }, ausbildungsbetrieb: false, ihk: '', uebergeordneteOrgId: null, status: 'AKTIV', kontaktpunkte: k ? [{ typ: 'ADRESSE', strasse: k, hausnummer: '', plz: '', ort: '', land: 'DE', primaer: true, status: 'AKTIV' }] : [] });
    const rolle = $('#op_rolle').value.trim();
    const mgBase = { id: 'mg' + Date.now(), orgId: id, rollen: rolle ? [rolle] : [], hauptansprechpartner: true, buchungsberechtigt: false, rechnungsempfaenger: false, position: '', abteilung: '', gueltigVon: new Date().toISOString().slice(0, 10), gueltigBis: null, dienstKontaktpunkte: [] };
    if (opPersonId) {
      // bestehende Person verknüpfen (keine Dublette); Hauptzugehörigkeit nur, wenn noch keine
      MITGLIEDSCHAFTEN.push({ ...mgBase, personId: opPersonId, hauptzugehoerigkeit: mitglFuerPerson(opPersonId, false).length === 0 });
    } else if (opNn) {
      const pid = 'p' + (PERSONEN.length + 1);
      const opk = $('#op_kanal').value.trim();
      const kps = opk ? [opk.includes('@') ? { typ: 'EMAIL', email: opk, primaer: true, status: 'AKTIV', kontext: 'dienstlich' } : { typ: 'TELEFON', nummerAnzeige: opk, nummerE164: opk.replace(/[^\d+]/g, ''), primaer: true, status: 'AKTIV', kontext: 'dienstlich' }] : [];
      PERSONEN.push({ id: pid, vorname: opVn || '(?)', nachname: opNn, geschlecht: $('#op_g').value, titel: '', geburtsdatum: null, staatsangehoerigkeit: [], korrespondenzsprache: 'DE', status: 'AKTIV', werbesperre: false, auskunftssperre: false, leadQuelle: '', unvollstaendig: false, foto: null, login: [], kontaktpunkte: kps, einwilligungen: [], beziehungen: [] });
      MITGLIEDSCHAFTEN.push({ ...mgBase, personId: pid, hauptzugehoerigkeit: true });
    }
    closeModal(); S.seg = 'org'; select('org', id);
    toastInfo(hasPerson ? 'Firma + Ansprechpartner verknüpft.' : 'Firma gespeichert.');
  };
}

function openEnrich(o) {
  openModal(`<div class="modal-h"><h2>Daten online ziehen — ${esc(o.name)}</h2><button class="x" onclick="closeModal()">×</button></div>
    <div class="modal-b"><div class="field"><label>Quelle</label><input value="${esc(o.website || o.ustId || '')}" /></div>
    <div class="inline-info">Gemockt: VIES-Prüfung der USt-IdNr. ✔, Impressum geparst, KI-Extraktion ergänzt Rechtsform & Register-Nr. — Felder würden zum Übernehmen vorgeschlagen.</div></div>
    <div class="modal-f"><button class="btn" onclick="closeModal()">Schließen</button><button class="btn primary" onclick="closeModal();toastInfo('Vorschlag übernommen (gemockt)')">Übernehmen</button></div>`);
}

// Notiz / Kommunikation (Rich-Text)
function openNote(p, o) {
  const ziel = p ? personName(p) : o.name;
  openModal(`<div class="modal-h"><h2>Notiz / Kommunikation — ${esc(ziel)}</h2><button class="x" onclick="closeModal()">×</button></div>
    <div class="modal-b">
      <div class="grid2"><div class="field"><label>Typ</label><select id="n_typ">${LOOKUPS.aktivitaetTyp.map(t => `<option>${t}</option>`).join('')}</select></div>
      <div class="field"><label>Betreff</label><input id="n_betreff" /></div></div>
      <div class="field"><label>Inhalt</label>
        <div class="rt-toolbar"><button onclick="document.execCommand('bold')"><b>B</b></button><button onclick="document.execCommand('italic')"><i>I</i></button><button onclick="document.execCommand('insertUnorderedList')">•</button><button onclick="(function(){var u=prompt('URL');if(u)document.execCommand('createLink',false,u)})()">🔗</button></div>
        <div class="rt-area" id="n_inhalt" contenteditable="true"></div></div>
      <div class="field"><label>📎 Anhang</label><input type="file" /></div>
    </div>
    <div class="modal-f"><button class="btn" onclick="closeModal()">Abbrechen</button><button class="btn primary" id="saveNote">Speichern</button></div>`);
  $('#saveNote').onclick = () => {
    AKTIVITAETEN.unshift({ id: 'a' + Date.now(), personId: p ? p.id : null, orgId: o ? o.id : null, typ: $('#n_typ').value, richtung: '', betreff: $('#n_betreff').value || '(ohne Betreff)', inhaltHtml: $('#n_inhalt').innerHTML, bearbeiter: MITARBEITER.kuerzel, zeitpunkt: new Date().toISOString().slice(0, 16).replace('T', ' '), dauer: '', anhaenge: [] });
    closeModal(); S.tab = p ? 'komm' : 'okomm'; renderDetail(); toastInfo('Kommunikation erfasst.');
  };
}

// Wiedervorlage
function openWv(p) {
  openModal(`<div class="modal-h"><h2>Wiedervorlage — ${esc(personName(p))}</h2><button class="x" onclick="closeModal()">×</button></div>
    <div class="modal-b">
      <div class="field"><label>Betreff</label><input id="w_betreff" /></div>
      <div class="grid2">
        <div class="field"><label>Fällig am (Schnellauswahl)</label>
          <div style="display:flex;gap:4px;margin-bottom:6px;flex-wrap:wrap"><button class="btn sm" data-d="1">morgen</button><button class="btn sm" data-d="3">+3 Tage</button><button class="btn sm" data-d="7">+1 Woche</button></div>
          <input type="date" id="w_datum" /></div>
        <div class="field"><label>Zuweisen an</label><select id="w_an"><optgroup label="Mitarbeiter"><option>${esc(MITARBEITER.name)}</option></optgroup><optgroup label="Gruppen">${GRUPPEN.map(g => `<option>${g}</option>`).join('')}</optgroup></select>
        <div class="hint">Kontaktperson oder Kontaktgruppe. Erinnerung: nur In-App.</div></div>
      </div>
    </div>
    <div class="modal-f"><button class="btn" onclick="closeModal()">Abbrechen</button><button class="btn primary" id="saveWv">Speichern</button></div>`);
  $$('#modal [data-d]').forEach(b => b.onclick = () => { const dt = new Date(); dt.setDate(dt.getDate() + (+b.dataset.d)); $('#w_datum').value = dt.toISOString().slice(0, 10); });
  $('#saveWv').onclick = () => { WIEDERVORLAGEN.unshift({ id: 'w' + Date.now(), personId: p.id, orgId: null, betreff: $('#w_betreff').value || 'Wiedervorlage', faelligAm: $('#w_datum').value || '2026-06-20', erledigt: false, zugewiesenAn: $('#w_an').value, typAn: GRUPPEN.includes($('#w_an').value) ? 'gruppe' : 'mitarbeiter', prioritaet: 'mittel' }); closeModal(); renderCkNav(); toastInfo('Wiedervorlage angelegt.'); };
}

// Verknüpfen (Bestandssuche zuerst)
function openVerknuepfen(von, id) {
  const istPerson = von === 'person';
  const kandidaten = istPerson ? ORGANISATIONEN : PERSONEN;
  openModal(`<div class="modal-h"><h2>${istPerson ? 'Firma' : 'Person'} verknüpfen</h2><button class="x" onclick="closeModal()">×</button></div>
    <div class="modal-b">
      <div class="inline-info">Beidseitig pflegbar. <b>Erst Bestand suchen</b> (Dublettenschutz), nur sonst neu anlegen.</div>
      <div class="field"><label>Bestand suchen</label><input id="v_search" placeholder="Name eingeben …" /></div>
      <div id="v_results"></div>
      <div class="card"><h3>Verknüpfungsdetails</h3>
        <div class="field"><label>Rollen (mehrere möglich)</label><div style="max-height:120px;overflow:auto">${LOOKUPS.rollen.map(r => `<label class="chip"><input type="checkbox" name="vr" value="${esc(r)}" /> ${esc(r)}</label>`).join('')}</div></div>
        <div class="grid2"><div class="field"><label>Position</label><input id="v_pos" /></div><div class="field"><label>&nbsp;</label>
          <label style="font-size:13px;display:block"><input type="checkbox" id="v_haupt" /> Hauptzugehörigkeit (Person)</label>
          <label style="font-size:13px;display:block"><input type="checkbox" id="v_ansp" /> Hauptansprechpartner (Firma)</label>
          <label style="font-size:13px;display:block"><input type="checkbox" id="v_buch" /> buchungsberechtigt</label>
          <label style="font-size:13px;display:block"><input type="checkbox" id="v_rech" /> Rechnungsempfänger</label></div></div>
      </div>
    </div>
    <div class="modal-f"><button class="btn" onclick="closeModal()">Abbrechen</button><button class="btn primary" id="saveLink" disabled>Verknüpfen</button></div>`);
  let zielId = null;
  const renderRes = (q) => {
    const list = kandidaten.filter(x => (istPerson ? x.name : personName(x)).toLowerCase().includes(q.toLowerCase())).slice(0, 6);
    $('#v_results').innerHTML = list.map(x => `<div class="quicklink" data-id="${x.id}">${istPerson ? esc(x.name) : esc(personName(x))}</div>`).join('') || '<div class="muted" style="padding:4px">kein Treffer — neu anlegen wäre der nächste Schritt</div>';
    $$('#v_results .quicklink').forEach(e => e.onclick = () => { zielId = e.dataset.id; $$('#v_results .quicklink').forEach(q => q.style.background = ''); e.style.background = 'var(--blue-light)'; $('#saveLink').disabled = false; });
  };
  renderRes('');
  $('#v_search').addEventListener('input', e => renderRes(e.target.value));
  $('#saveLink').onclick = () => {
    if (!zielId) return;
    const rollen = $$('#modal input[name=vr]:checked').map(c => c.value);
    const personId = istPerson ? id : zielId, orgId = istPerson ? zielId : id;
    if ($('#v_haupt').checked) MITGLIEDSCHAFTEN.filter(m => m.personId === personId).forEach(m => m.hauptzugehoerigkeit = false); // höchstens eine
    if ($('#v_ansp').checked) MITGLIEDSCHAFTEN.filter(m => m.orgId === orgId).forEach(m => m.hauptansprechpartner = false);
    MITGLIEDSCHAFTEN.push({ id: 'mg' + Date.now(), personId, orgId, rollen: rollen.length ? rollen : ['Ansprechpartner Studium'], hauptzugehoerigkeit: $('#v_haupt').checked, hauptansprechpartner: $('#v_ansp').checked, buchungsberechtigt: $('#v_buch').checked, rechnungsempfaenger: $('#v_rech').checked, position: $('#v_pos').value, abteilung: '', gueltigVon: '2026-06-16', gueltigBis: null, dienstKontaktpunkte: [] });
    closeModal(); renderDetail(); renderList(); toastInfo('Verknüpfung angelegt.');
  };
}

// Recht auf Vergessen
function openForget(p) {
  const hatBuchung = (BUCHUNGEN[p.id] || []).length > 0;
  openModal(`<div class="modal-h"><h2>Recht auf Vergessen — ${esc(personName(p))}</h2><button class="x" onclick="closeModal()">×</button></div>
    <div class="modal-b">
      <div class="inline-warn">Aktion nur mit Rolle <b>crm-datenschutz</b>. ${hatBuchung ? 'Es bestehen <b>Buchungen/Rechnungen</b> → gesetzliche Aufbewahrung (GoBD/§147 AO, 10 J.).' : 'Keine Buchungen gefunden.'}</div>
      <p>Vorgehen: <b>Sofort sperren (Art. 18)</b>${hatBuchung ? ', dann <b>geplante Anonymisierung</b> nach Fristablauf (inkl. Envers-Historie-Purge).' : ', anschließend Anonymisierung möglich.'}</p>
      <div class="field"><label>Begründung</label><input placeholder="z. B. Betroffenen-Antrag vom …" /></div>
    </div>
    <div class="modal-f"><button class="btn" onclick="closeModal()">Abbrechen</button><button class="btn danger" id="doForget">Sperren${hatBuchung ? ' + Anonymisierung vormerken' : ' + anonymisieren'}</button></div>`);
  $('#doForget').onclick = () => { p.status = 'GESPERRT'; p.werbesperre = true; closeModal(); renderList(); renderDetail(); toastInfo(hatBuchung ? 'Gesperrt; Anonymisierung nach Aufbewahrungsfrist vorgemerkt.' : 'Gesperrt und zur Anonymisierung markiert.'); };
}

// Gruppen/Rolle
function openGroups() {
  openModal(`<div class="modal-h"><h2>Mein Profil — Gruppen &amp; Rolle</h2><button class="x" onclick="closeModal()">×</button></div>
    <div class="modal-b"><dl class="kv"><dt>Mitarbeiter</dt><dd>${esc(MITARBEITER.name)} (${esc(MITARBEITER.kuerzel)})</dd>
    <dt>CRM-Rolle</dt><dd><span class="badge b-info">${esc(MITARBEITER.rolle)}</span> <span class="muted">(crm-lesen / crm-pflege / crm-datenschutz)</span></dd>
    <dt>Interne Gruppen</dt><dd>${MITARBEITER.gruppen.map(g => `<span class="chip role">${esc(g)}</span>`).join('')}</dd></dl>
    <div class="hint">Gruppen dienen als Team UND als Zuweisungsziel für Wiedervorlagen.</div></div>
    <div class="modal-f"><button class="btn primary" onclick="closeModal()">Schließen</button></div>`);
}

// ---------- Toast / Anruf ----------
function toastInfo(msg) {
  const t = $('#toast'); t.style.borderLeftColor = 'var(--blue)';
  t.innerHTML = `<div class="t-h">ℹ Hinweis<button class="x" onclick="$('#toast').classList.remove('open')">×</button></div><div class="t-b">${esc(msg)}</div>`;
  t.classList.add('open'); clearTimeout(t._tmr); t._tmr = setTimeout(() => t.classList.remove('open'), 4000);
}
function simulateCall() {
  // mal bekannte, mal unbekannte Nummer
  const bekannt = Math.random() > 0.4;
  const nummer = bekannt ? '+49 170 1112233' : '+49 30 9999999';
  const e164 = nummer.replace(/[^\d+]/g, '');
  ANRUFE.unshift({ id: 'c' + Date.now(), nummerE164: e164, richtung: 'ein', zeitpunkt: new Date().toISOString().slice(0, 16).replace('T', ' '), mitarbeiter: MITARBEITER.kuerzel, status: 'eingehend' });
  const treffer = [];
  PERSONEN.forEach(p => p.kontaktpunkte.forEach(k => { if (k.typ === 'TELEFON' && k.nummerE164 === e164) treffer.push({ typ: 'person', o: p }); }));
  ORGANISATIONEN.forEach(o => o.kontaktpunkte.forEach(k => { if (k.typ === 'TELEFON' && k.nummerE164 === e164) treffer.push({ typ: 'org', o }); }));
  const t = $('#toast'); t.style.borderLeftColor = 'var(--ok)';
  let body;
  if (treffer.length) {
    body = treffer.map(h => `<div class="hit" data-t="${h.typ}" data-id="${h.o.id}"><span class="avatar ${h.typ === 'org' ? 'org' : ''}" style="width:26px;height:26px;font-size:10px">${esc(initials(h.typ === 'person' ? h.o.vorname + ' ' + h.o.nachname : h.o.name))}</span><div><b>${esc(h.typ === 'person' ? personName(h.o) : h.o.name)}</b><div class="muted" style="font-size:11px">${esc(nummer)}</div></div></div>`).join('');
  } else {
    body = `<div class="muted" style="margin-bottom:6px">Unbekannte Nummer ${esc(nummer)}</div>
      <button class="btn sm" id="webSearch">🌐 Web-Suche (Schnellauswahl)</button>
      <button class="btn sm" id="newFromCall">+ Neu anlegen</button><div id="webRes"></div>`;
  }
  t.innerHTML = `<div class="t-h"><span class="ringing"></span> Eingehender Anruf<button class="x" onclick="$('#toast').classList.remove('open')">×</button></div>
    <div class="t-b"><div style="font-size:16px;font-weight:700;margin-bottom:6px">${esc(nummer)}</div>${body}</div>`;
  t.classList.add('open'); clearTimeout(t._tmr);
  $$('#toast .hit').forEach(e => e.onclick = () => { select(e.dataset.t, e.dataset.id); t.classList.remove('open'); });
  if (!treffer.length) {
    $('#webSearch').onclick = () => { $('#webRes').innerHTML = '<div class="muted" style="margin-top:6px">⏳ Web-Suche (gemockt) …</div>'; setTimeout(() => { $('#webRes').innerHTML = `<div class="hit" id="wr1"><div><b>Hausverwaltung Berlin GmbH</b><div class="muted" style="font-size:11px">aus Web-Suche · übernehmen</div></div></div>`; $('#wr1').onclick = () => { t.classList.remove('open'); openNewOrg(); }; }, 800); };
    $('#newFromCall').onclick = () => { t.classList.remove('open'); openNewPerson(); };
  }
}

// ---------- Globale Sofortsuche ----------
function doSearch(q) {
  const box = $('#searchResults');
  if (!q.trim()) { box.classList.remove('open'); return; }
  const ql = q.toLowerCase();
  const res = [];
  PERSONEN.forEach(p => { if ((personName(p) + ' ' + p.kontaktpunkte.map(kanalText).join(' ')).toLowerCase().includes(ql)) res.push({ t: 'person', o: p }); });
  ORGANISATIONEN.forEach(o => { if ((o.name + ' ' + (o.ustId || '') + ' ' + o.kontaktpunkte.map(kanalText).join(' ')).toLowerCase().includes(ql)) res.push({ t: 'org', o }); });
  box.innerHTML = res.slice(0, 8).map(r => {
    let info;
    if (r.t === 'person') { const hf = hauptFirmaFuer(r.o); const ort = ortOf(r.o); info = [hf ? hf.name : 'Privat', r.o.geburtsdatum ? '*' + r.o.geburtsdatum : '', ort].filter(Boolean).join(' · '); }
    else { const bk = buchungsKontaktFuer(r.o); const ort = ortOf(r.o); info = [ort, bk ? 'Buchung: ' + personName(bk) : ''].filter(Boolean).join(' · '); }
    return `<div class="sr" data-t="${r.t}" data-id="${r.o.id}"><span class="avatar ${r.t === 'org' ? 'org' : ''}" style="width:24px;height:24px;font-size:10px">${esc(initials(r.t === 'person' ? r.o.vorname + ' ' + r.o.nachname : r.o.name))}</span><div style="flex:1;min-width:0"><div>${esc(r.t === 'person' ? personName(r.o) : r.o.name)}</div><div class="muted" style="font-size:11px">${esc(info) || '—'}</div></div><span class="muted" style="font-size:11px">${r.t}</span></div>`;
  }).join('') || '<div class="sr muted">kein Treffer</div>';
  box.classList.add('open');
  $$('#searchResults .sr[data-id]').forEach(e => e.onclick = () => { select(e.dataset.t, e.dataset.id); box.classList.remove('open'); $('#search').value = ''; });
}

// ---------- WebSocket-Simulation (Tab-Bubbles) ----------
function startWsSim() {
  setInterval(() => {
    const p = PERSONEN[Math.floor(Math.random() * PERSONEN.length)];
    const key = Math.random() > 0.5 ? 'login' : 'buchung';
    wsNew[p.id] = wsNew[p.id] || { login: 0, buchung: 0 };
    wsNew[p.id][key]++;
    if (S.selType === 'person' && S.selId === p.id) renderDetail(); // Bubble live
  }, 6000);
}

// ---------- Init & Events ----------
function init() {
  renderQuicklinks();
  $('#newPerson').onclick = openNewPerson;
  $('#newOrg').onclick = openNewOrg;
  $('#simCall').onclick = simulateCall;
  $('#showGroupsCk').onclick = openGroups;
  $$('.ck-nav').forEach(b => b.onclick = () => goCkNav(b.dataset.nav));
  S.ckNav = 'cockpit'; renderCkNav(); renderCkMain(); // Startseite = Cockpit
  $('#search').addEventListener('input', e => doSearch(e.target.value));
  document.addEventListener('click', e => { if (!e.target.closest('.search')) $('#searchResults').classList.remove('open'); });
  // Shortcuts
  document.addEventListener('keydown', e => {
    if (e.target.matches('input,textarea,select,[contenteditable]')) { if (e.key === 'Escape') e.target.blur(); return; }
    if (e.key === '/') { e.preventDefault(); $('#search').focus(); }
    else if (e.key === 'n' && S.selType === 'person') openNote(personById(S.selId));
    else if (e.key === 'w' && S.selType === 'person') openWv(personById(S.selId));
    else if (e.key === 'p') openNewPerson();
    else if (e.key === 'f') openNewOrg();
    else if (e.key === 'Escape') closeModal();
  });
  startWsSim();
}
window.addEventListener('DOMContentLoaded', init);
