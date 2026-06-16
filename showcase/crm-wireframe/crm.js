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
  if (k.typ === 'ADRESSE') return `${k.strasse} ${k.hausnummer || ''}, ${k.plz} ${k.ort}` + (k.land && k.land !== 'DE' ? ` (${k.land})` : '');
  return '';
}

// ---------- State ----------
const S = { seg: 'alle', selType: null, selId: null, tab: null };
// simulierte WebSocket-Ereignisse (neue Items je Person → Tab-Bubbles)
const wsNew = {}; // pid -> { login: n, buchung: n }
function bubble(pid, key) { return (wsNew[pid] && wsNew[pid][key]) || 0; }

// ---------- Quicklinks & Sidebar ----------
function renderQuicklinks() {
  $('#quicklinks').innerHTML = QUICKLINKS.map(q => {
    if (q.typ === 'person') { const p = personById(q.id); return `<div class="quicklink" data-t="person" data-id="${p.id}"><span class="avatar" style="width:24px;height:24px;font-size:10px">${initials(p.vorname + ' ' + p.nachname)}</span>${esc(personName(p))}</div>`; }
    const o = orgById(q.id); return `<div class="quicklink" data-t="org" data-id="${o.id}"><span class="avatar org" style="width:24px;height:24px;font-size:10px">${esc(initials(o.name))}</span>${esc(o.name)}</div>`;
  }).join('');
  $$('#quicklinks .quicklink').forEach(e => e.onclick = () => select(e.dataset.t, e.dataset.id));
}
function renderSideWv() {
  const mine = WIEDERVORLAGEN.filter(w => !w.erledigt);
  $('#sideWv').innerHTML = mine.map(w => `<div class="quicklink" data-id="${w.id}"><span class="badge ${w.prioritaet === 'hoch' ? 'b-err' : 'b-warn'}">${esc(w.faelligAm.slice(5))}</span> ${esc(w.betreff)}</div>`).join('') || '<div class="muted" style="padding:4px 10px">keine offenen</div>';
  $$('#sideWv .quicklink').forEach(e => e.onclick = () => { const w = WIEDERVORLAGEN.find(x => x.id === e.dataset.id); if (w && w.personId) select('person', w.personId); });
}

// ---------- Master list ----------
function pushQuicklink(typ, id) {
  const i = QUICKLINKS.findIndex(q => q.typ === typ && q.id === id);
  if (i >= 0) QUICKLINKS.splice(i, 1);
  QUICKLINKS.unshift({ typ, id });
  QUICKLINKS.splice(6);
  renderQuicklinks();
}
function renderList() {
  const items = [];
  if (S.seg === 'alle' || S.seg === 'person') PERSONEN.forEach(p => items.push({ typ: 'person', obj: p }));
  if (S.seg === 'alle' || S.seg === 'org') ORGANISATIONEN.forEach(o => items.push({ typ: 'org', obj: o }));
  $('#list').innerHTML = items.map(it => {
    if (it.typ === 'person') {
      const p = it.obj; const mg = mitglFuerPerson(p.id, false).find(m => m.hauptzugehoerigkeit);
      const firma = mg ? orgById(mg.orgId).name : (mitglFuerPerson(p.id, false)[0] ? orgById(mitglFuerPerson(p.id, false)[0].orgId).name : 'Privat');
      return `<div class="ml-item ${S.selType === 'person' && S.selId === p.id ? 'active' : ''}" data-t="person" data-id="${p.id}">
        <span class="avatar">${esc(initials(p.vorname + ' ' + p.nachname))}</span>
        <div><div class="name">${esc(personName(p))} ${p.unvollstaendig ? '<span class="badge b-warn">unvollständig</span>' : ''} ${p.werbesperre ? '<span class="badge b-err">Werbesperre</span>' : ''}</div>
        <div class="sub">${esc(firma)}</div></div></div>`;
    }
    const o = it.obj;
    return `<div class="ml-item ${S.selType === 'org' && S.selId === o.id ? 'active' : ''}" data-t="org" data-id="${o.id}">
      <span class="avatar org">${esc(initials(o.name))}</span>
      <div><div class="name">${esc(o.name)}</div><div class="sub">${esc(o.unternehmenstyp)} · ${mitglFuerOrg(o.id, false).length} Personen</div></div></div>`;
  }).join('');
  $$('#list .ml-item').forEach(e => e.onclick = () => select(e.dataset.t, e.dataset.id));
}

// ---------- Selection ----------
function select(typ, id) {
  S.selType = typ; S.selId = id; S.tab = null;
  if (typ === 'person') { wsNew[id] = { login: 0, buchung: 0 }; } // "gelesen"
  pushQuicklink(typ, id);
  document.body.classList.add('show-detail');
  renderList(); renderDetail();
}

// ---------- Detail ----------
function renderDetail() {
  const d = $('#detail');
  if (!S.selId) { d.innerHTML = '<p class="muted">Wähle links einen Kontakt oder lege einen neuen an.</p>'; return; }
  if (S.selType === 'person') renderPerson(personById(S.selId), d);
  else renderOrg(orgById(S.selId), d);
}

function tabBar(tabs) {
  const active = S.tab || tabs[0].key;
  S.tab = active;
  return `<div class="tabs">${tabs.map(t => `<button class="tab ${t.key === active ? 'active' : ''}" data-tab="${t.key}">${esc(t.label)}${t.bubble ? `<span class="bubble">${t.bubble}</span>` : ''}</button>`).join('')}</div><div id="tabBody"></div>`;
}
function wireTabs(render) {
  $$('#detail .tab').forEach(e => e.onclick = () => { S.tab = e.dataset.tab; render(); });
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
        <button class="btn danger" id="aForget">🗑 Recht auf Vergessen</button>
      </div>
    </div>
    ${tabBar(tabs)}`;
  $('#backBtn').onclick = () => document.body.classList.remove('show-detail');
  $('#aNote').onclick = () => openNote(p);
  $('#aWv').onclick = () => openWv(p);
  $('#aLink').onclick = () => openVerknuepfen('person', p.id);
  $('#aForget').onclick = () => openForget(p);
  wireTabs(() => renderPerson(p, d));
  const body = $('#tabBody');

  if (S.tab === 'stamm') {
    body.innerHTML = `<div class="card"><h3>Stammdaten</h3><dl class="kv">
      <dt>Vorname</dt><dd>${esc(p.vorname)}</dd>
      <dt>Nachname</dt><dd>${esc(p.nachname)}</dd>
      <dt>Geschlecht</dt><dd>${esc((LOOKUPS.geschlecht.find(g => g.code === p.geschlecht) || {}).bezeichnung || '–')} <span class="muted">→ Briefanrede „${esc(briefanrede(p))}"</span></dd>
      <dt>Titel</dt><dd>${esc(p.titel || '–')}</dd>
      <dt>Geburtsdatum</dt><dd>${esc(p.geburtsdatum || '–')} ${vj === null ? '<span class="muted">(Volljährigkeit unbekannt → Default volljährig)</span>' : vj ? '' : '<span class="badge b-warn">minderjährig</span>'}</dd>
      <dt>Staatsangehörigkeit</dt><dd>${esc((p.staatsangehoerigkeit || []).join(', ') || '–')}</dd>
      <dt>Korrespondenzsprache</dt><dd>${esc(p.korrespondenzsprache || '–')}</dd>
      <dt>Lead-Quelle</dt><dd>${esc(p.leadQuelle || '–')}</dd>
      <dt>Login-Identität</dt><dd>${p.login.length ? p.login.map(l => `${esc(l.loginEmail)} ${l.verifiziert ? '<span class="badge b-ok">verifiziert</span>' : '<span class="badge b-grey">unverifiziert</span>'}`).join('<br>') : '<span class="muted">kein Login (PROVISORISCH erfassbar)</span>'}</dd>
    </dl></div>`;
  } else if (S.tab === 'zugeh') {
    const aktiv = mitglFuerPerson(p.id, false), ehemalig = mitglFuerPerson(p.id, true).filter(m => m.gueltigBis);
    const row = (m, old) => { const o = orgById(m.orgId); return `<tr class="${old ? 'row-old' : ''}">
      <td><a data-org="${o.id}" class="lnk">${esc(o.name)}</a>${m.hauptzugehoerigkeit ? ' <span class="badge b-info">Hauptzugehörigkeit</span>' : ''}${m.hauptansprechpartner ? ' <span class="badge b-ok">Hauptansprechpartner</span>' : ''}</td>
      <td>${m.rollen.map(r => `<span class="chip role">${esc(r)}</span>`).join('')}</td>
      <td>${esc(m.position || '–')}</td>
      <td>${m.buchungsberechtigt ? '✔ buchungsberechtigt' : '–'}${m.rechnungsempfaenger ? '<br>✔ Rechnungsempfänger' : ''}</td>
      <td>${esc(m.gueltigVon || '')}${m.gueltigBis ? ' – ' + esc(m.gueltigBis) : ' – heute'}</td></tr>`; };
    body.innerHTML = `<div class="card"><h3>Firmenzugehörigkeiten (N:M)</h3>
      <table class="grid"><thead><tr><th>Firma</th><th>Rollen</th><th>Position</th><th>Vollmacht</th><th>Gültig</th></tr></thead>
      <tbody>${aktiv.map(m => row(m, false)).join('') || '<tr><td colspan="5" class="muted">Privatperson — keine Firmenzugehörigkeit</td></tr>'}</tbody></table>
      <button class="btn sm" id="addMg" style="margin-top:10px">+ Firma verknüpfen</button></div>
      ${ehemalig.length ? `<div class="card"><h3>Ehemalige Zugehörigkeiten</h3><table class="grid"><tbody>${ehemalig.map(m => row(m, true)).join('')}</tbody></table></div>` : ''}`;
    $('#addMg') && ($('#addMg').onclick = () => openVerknuepfen('person', p.id));
    $$('#detail .lnk[data-org]').forEach(e => e.onclick = () => select('org', e.dataset.org));
  } else if (S.tab === 'komm') {
    body.innerHTML = `<div class="card"><h3>Kontaktkanäle (privat)</h3>${kanalListe(p.kontaktpunkte)}</div>
      <div class="card"><h3>Kontakthistorie / Aktivitäten</h3>${aktListe(AKTIVITAETEN.filter(a => a.personId === p.id))}
      <button class="btn sm" id="addNote" style="margin-top:8px">+ Notiz / Kommunikation</button></div>`;
    $('#addNote').onclick = () => openNote(p);
  } else if (S.tab === 'dsgvo') {
    body.innerHTML = `<div class="card"><h3>Marketing-Einwilligungen (Opt-In)</h3>
      <table class="grid"><thead><tr><th>Kanal</th><th>Zweck</th><th>Kontext</th><th>Status</th><th>Rechtsgrundlage</th><th>Quelle/Datum</th></tr></thead>
      <tbody>${p.einwilligungen.map(e => `<tr><td>${esc(e.kanal)}</td><td>${esc(e.zweck)}</td><td>${esc(e.kontext)}</td>
        <td><span class="badge ${e.status === 'ERTEILT' ? 'b-ok' : e.status === 'WIDERRUFEN' ? 'b-err' : 'b-warn'}">${esc(e.status)}</span></td>
        <td>${esc(e.rechtsgrundlage)}</td><td>${esc(e.quelle)} · ${esc(e.datum)}</td></tr>`).join('')}</tbody></table>
      ${p.werbesperre || p.auskunftssperre ? `<div class="inline-warn">⚠ ${p.werbesperre ? 'Werbesperre' : ''}${p.werbesperre && p.auskunftssperre ? ' & ' : ''}${p.auskunftssperre ? 'Auskunftssperre' : ''} aktiv — überstimmt jedes Opt-In.</div>` : ''}
      ${volljaehrig(p) === false ? '<div class="inline-warn">⚠ Minderjährig: Marketing/Verträge erfordern Einwilligung der Erziehungsberechtigten.</div>' : ''}
      <button class="btn sm" id="doi">✉ Double-Opt-In anstoßen</button></div>
      <div class="card"><h3>Beziehungen</h3>${p.beziehungen.length ? p.beziehungen.map(b => { const rp = personById(b.personId); return `<div>${esc(b.typ)}: <a class="lnk" data-p="${b.personId}">${esc(rp ? personName(rp) : '?')}</a> <span class="muted">${esc(b.hinweis || '')}</span></div>`; }).join('') : '<span class="muted">keine</span>'}</div>`;
    $('#doi') && ($('#doi').onclick = () => toastInfo('Double-Opt-In-Mail (gemockt) versendet — Nachweis (Token/IP/Zeit) wird beim Klick gespeichert.'));
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
  return `<table class="grid"><tbody>${kps.map(k => `<tr class="${k.status === 'EHEMALIG' ? 'row-old' : ''}"><td>${k.typ === 'EMAIL' ? '✉' : k.typ === 'TELEFON' ? '📞' : '🏠'} ${esc(k.typ)}</td><td>${esc(kanalText(k))}</td><td>${k.primaer ? '<span class="badge b-info">primär</span>' : ''} ${k.status === 'EHEMALIG' ? '<span class="badge b-grey">ehemalig</span>' : ''} ${k.kontext ? `<span class="chip">${esc(k.kontext)}</span>` : ''}</td></tr>`).join('')}</tbody></table>`;
}
function aktListe(akts) {
  if (!akts.length) return '<span class="muted">keine Einträge</span>';
  return akts.map(a => `<div style="border-bottom:1px solid var(--grey-100);padding:8px 0">
    <div><b>${esc(a.typ)}</b> ${a.richtung ? `<span class="chip">${a.richtung === 'ein' ? 'eingehend' : 'ausgehend'}</span>` : ''} <span class="muted">${esc(a.zeitpunkt)} · ${esc(a.bearbeiter)}${a.dauer ? ' · ' + esc(a.dauer) : ''}</span></div>
    <div style="font-weight:600">${esc(a.betreff)}</div>
    <div>${a.inhaltHtml}</div>
    ${a.anhaenge && a.anhaenge.length ? `<div class="muted">📎 ${a.anhaenge.map(esc).join(', ')}</div>` : ''}</div>`).join('');
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
    body.innerHTML = `<div class="card"><h3>Firmen-Stammdaten</h3><dl class="kv">
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
  } else if (S.tab === 'opers') {
    const aktiv = mitglFuerOrg(o.id, false), ehemalig = mitglFuerOrg(o.id, true).filter(m => m.gueltigBis);
    const row = (m, old) => { const p = personById(m.personId); return `<tr class="${old ? 'row-old' : ''}"><td><a class="lnk" data-p="${p.id}">${esc(personName(p))}</a>${m.hauptansprechpartner ? ' <span class="badge b-ok">Hauptansprechpartner</span>' : ''}</td>
      <td>${m.rollen.map(r => `<span class="chip role">${esc(r)}</span>`).join('')}</td><td>${esc(m.position || '–')}</td>
      <td>${m.buchungsberechtigt ? '✔' : '–'}</td><td>${m.dienstKontaktpunkte && m.dienstKontaktpunkte.length ? m.dienstKontaktpunkte.map(kanalText).map(esc).join('<br>') : '<span class="muted">–</span>'}</td></tr>`; };
    body.innerHTML = `<div class="card"><h3>Zugeordnete Personen</h3>
      <table class="grid"><thead><tr><th>Person</th><th>Rollen</th><th>Position</th><th>Bucht</th><th>Dienstl. Kontakt</th></tr></thead>
      <tbody>${aktiv.map(m => row(m, false)).join('') || '<tr><td colspan=5 class="muted">keine</td></tr>'}</tbody></table>
      <button class="btn sm" id="addPers" style="margin-top:10px">+ Person verknüpfen (Bestand suchen)</button></div>
      ${ehemalig.length ? `<div class="card"><h3>Ehemalige</h3><table class="grid"><tbody>${ehemalig.map(m => row(m, true)).join('')}</tbody></table></div>` : ''}`;
    $('#addPers').onclick = () => openVerknuepfen('org', o.id);
    $$('#detail .lnk[data-p]').forEach(e => e.onclick = () => select('person', e.dataset.p));
  } else if (S.tab === 'okomm') {
    body.innerHTML = `<div class="card"><h3>Kontaktkanäle</h3>${kanalListe(o.kontaktpunkte)}</div>`;
  } else if (S.tab === 'ohier') {
    const mutter = o.uebergeordneteOrgId ? orgById(o.uebergeordneteOrgId) : null;
    const toechter = ORGANISATIONEN.filter(x => x.uebergeordneteOrgId === o.id);
    body.innerHTML = `<div class="card"><h3>Firmenhierarchie (Mutter/Tochter)</h3>
      <div>Übergeordnet: ${mutter ? `<a class="lnk" data-org="${mutter.id}">${esc(mutter.name)}</a>` : '<span class="muted">– (eigenständig)</span>'}</div>
      <div style="margin-top:8px">Tochtergesellschaften: ${toechter.length ? toechter.map(t => `<a class="lnk" data-org="${t.id}">${esc(t.name)}</a>`).join(', ') : '<span class="muted">keine</span>'}</div></div>`;
    $$('#detail .lnk[data-org]').forEach(e => e.onclick = () => select('org', e.dataset.org));
  } else if (S.tab === 'o360') {
    body.innerHTML = `<div class="inline-info">360°: aggregierte Buchungen/Rechnungen aller zugeordneten Personen im Firmenkontext (read-only).</div><div class="card muted">Gemockt — Daten kommen später.</div>`;
  }
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
        <div class="field"><label>Kontaktkanal <span class="req">*</span> (E-Mail oder Telefon)</label><input id="f_kanal" placeholder="z. B. name@firma.de oder +49 …" /><div class="err-msg" id="e_kanal" style="display:none">Mindestens ein Kontaktkanal</div></div>
        <div id="dubHint"></div>
      </div></details>
      <details class="stufe"><summary><span class="tag b-info">2</span> Marketingrelevant</summary><div class="stufe-b">
        <div class="grid2">
          <div class="field"><label>Lead-Quelle</label><select id="f_lead">${LOOKUPS.leadQuelle.map(q => `<option>${q}</option>`).join('')}</select></div>
          <div class="field"><label>Marketing-Opt-In (Kanal)</label><select id="f_optin"><option value="">— kein —</option><option>E-Mail</option><option>Telefon</option><option>Post</option></select></div>
        </div>
        <div class="field"><label>Opt-In-Kontext</label><select><option>global</option><option>Firmenkontext</option></select><div class="hint">Neuer Kontakt erzeugt automatisch Einwilligung <b>AUSSTEHEND</b> → Double-Opt-In wird angestoßen.</div></div>
        <label style="font-size:13px"><input type="checkbox" id="f_werbe" /> Werbesperre</label>
        <label style="font-size:13px;margin-left:14px"><input type="checkbox" id="f_auskunft" /> Auskunftssperre</label>
      </div></details>
      <details class="stufe"><summary><span class="tag b-grey">3</span> Weitere Kontaktdaten</summary><div class="stufe-b">
        <div class="field"><label>Weitere E-Mail</label><input placeholder="optional" /></div>
        <div class="field"><label>Adresse</label><input placeholder="Straße, Nr." /><div class="grid2"><input placeholder="PLZ" /><input placeholder="Ort" /></div></div>
        <div class="field"><label>Firmenzugehörigkeit</label><select><option value="">— privat —</option>${ORGANISATIONEN.map(o => `<option>${esc(o.name)}</option>`).join('')}</select></div>
      </div></details>
      <details class="stufe"><summary><span class="tag b-grey">4</span> Alles andere</summary><div class="stufe-b">
        <div class="grid2">
          <div class="field"><label>Geschlecht</label><select id="f_g">${LOOKUPS.geschlecht.map(g => `<option value="${g.code}">${g.bezeichnung}</option>`).join('')}</select><div class="hint">Anrede wird daraus abgeleitet (Fallback „Hallo …").</div></div>
          <div class="field"><label>Titel</label><input placeholder="z. B. Dr. (DE vorn) / MBA (int. hinten)" /></div>
        </div>
        <div class="grid2"><div class="field"><label>Geburtsdatum</label><input type="date" /></div><div class="field"><label>Korrespondenzsprache</label><input value="DE" /></div></div>
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
    if (!incomplete && !ok) return;
    const id = 'p' + (PERSONEN.length + 1);
    const kps = []; if (k) kps.push(k.includes('@') ? { typ: 'EMAIL', email: k, primaer: true, status: 'AKTIV', kontext: 'privat' } : { typ: 'TELEFON', nummerAnzeige: k, nummerE164: k.replace(/[^\d+]/g, ''), primaer: true, status: 'AKTIV', kontext: 'privat' });
    PERSONEN.push({ id, vorname: vn || '(?)', nachname: nn || '(?)', geschlecht: $('#f_g').value, titel: '', geburtsdatum: null, staatsangehoerigkeit: [], korrespondenzsprache: 'DE', status: incomplete ? 'PROVISORISCH' : 'AKTIV', werbesperre: $('#f_werbe').checked, auskunftssperre: $('#f_auskunft').checked, leadQuelle: $('#f_lead').value, unvollstaendig: !!incomplete, foto: null, login: [], kontaktpunkte: kps, einwilligungen: $('#f_optin').value ? [{ kanal: $('#f_optin').value, zweck: 'Marketing', status: 'AUSSTEHEND', kontext: 'global', quelle: $('#f_lead').value, datum: '2026-06-16', rechtsgrundlage: 'Art. 6.1.a' }] : [], beziehungen: [] });
    closeModal(); S.seg = 'person'; $$('.seg').forEach(s => s.classList.toggle('active', s.dataset.seg === 'person')); renderList(); select('person', id);
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
        <div class="field"><label>Kontaktkanal/Adresse <span class="req">*</span></label><input id="o_kanal" placeholder="Telefon, E-Mail oder Adresse" /><div class="err-msg" id="e_okanal" style="display:none">Mindestens ein Kanal/Adresse/Person</div></div>
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
  $('#saveOrg').onclick = () => {
    const name = $('#o_name').value.trim(), k = $('#o_kanal').value.trim();
    let ok = true; if (!name) { $('#e_oname').style.display = 'block'; ok = false; } if (!k) { $('#e_okanal').style.display = 'block'; ok = false; } if (!ok) return;
    const id = 'o' + (ORGANISATIONEN.length + 1);
    ORGANISATIONEN.push({ id, name, rechtsform: $('#o_rf').value, ustId: $('#o_ust').value, website: '', branche: '', unternehmenstyp: $('#o_typ').value, schwerpunkte: [], verbaende: [], bestandsgroesse: null, erlaubnis34c: { vorhanden: false }, ausbildungsbetrieb: false, ihk: '', uebergeordneteOrgId: null, status: 'AKTIV', kontaktpunkte: [{ typ: 'ADRESSE', strasse: k, hausnummer: '', plz: '', ort: '', land: 'DE', primaer: true, status: 'AKTIV' }] });
    closeModal(); S.seg = 'org'; $$('.seg').forEach(s => s.classList.toggle('active', s.dataset.seg === 'org')); renderList(); select('org', id); toastInfo('Firma gespeichert.');
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
    if (p) AKTIVITAETEN.unshift({ id: 'a' + Date.now(), personId: p.id, orgId: null, typ: $('#n_typ').value, richtung: '', betreff: $('#n_betreff').value || '(ohne Betreff)', inhaltHtml: $('#n_inhalt').innerHTML, bearbeiter: MITARBEITER.kuerzel, zeitpunkt: new Date().toISOString().slice(0, 16).replace('T', ' '), dauer: '', anhaenge: [] });
    closeModal(); if (p) { S.tab = 'komm'; renderDetail(); } toastInfo('Kommunikation erfasst.');
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
  $('#saveWv').onclick = () => { WIEDERVORLAGEN.unshift({ id: 'w' + Date.now(), personId: p.id, orgId: null, betreff: $('#w_betreff').value || 'Wiedervorlage', faelligAm: $('#w_datum').value || '2026-06-20', erledigt: false, zugewiesenAn: $('#w_an').value, typAn: GRUPPEN.includes($('#w_an').value) ? 'gruppe' : 'mitarbeiter', prioritaet: 'mittel' }); closeModal(); renderSideWv(); toastInfo('Wiedervorlage angelegt.'); };
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
  box.innerHTML = res.slice(0, 8).map(r => `<div class="sr" data-t="${r.t}" data-id="${r.o.id}"><span class="avatar ${r.t === 'org' ? 'org' : ''}" style="width:24px;height:24px;font-size:10px">${esc(initials(r.t === 'person' ? r.o.vorname + ' ' + r.o.nachname : r.o.name))}</span>${esc(r.t === 'person' ? personName(r.o) : r.o.name)} <span class="muted" style="margin-left:auto;font-size:11px">${r.t}</span></div>`).join('') || '<div class="sr muted">kein Treffer</div>';
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
  renderQuicklinks(); renderSideWv(); renderList();
  $('#newPerson').onclick = openNewPerson;
  $('#newOrg').onclick = openNewOrg;
  $('#simCall').onclick = simulateCall;
  $('#showGroups').onclick = openGroups;
  $$('.seg').forEach(s => s.onclick = () => { S.seg = s.dataset.seg; $$('.seg').forEach(x => x.classList.toggle('active', x === s)); renderList(); });
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
