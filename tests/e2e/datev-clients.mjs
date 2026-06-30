// DATEV-Sandbox-Smoke: refresh_token → access_token → GET accounting:clients (listet Mandanten/clientIds).
// Belegt Refresh-Grant, Pflicht-Header X-DATEV-Client-Id und den korrekten datev:-Scope. Liest .env.
import fs from 'node:fs';
const P = 'c:/dev/workspacesIJ/ebz/.env';
const env = Object.fromEntries(fs.readFileSync(P, 'utf8').split(/\r?\n/).filter(l => /^DATEV_/.test(l)).map(l => { const i = l.indexOf('='); return [l.slice(0, i), l.slice(i + 1)]; }));
const auth = 'Basic ' + Buffer.from(`${env.DATEV_CLIENT_ID}:${env.DATEV_CLIENT_SECRET}`).toString('base64');
const r = await fetch('https://sandbox-api.datev.de/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', Authorization: auth }, body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: env.DATEV_REFRESH_TOKEN }) });
const t = await r.json();
console.log('refresh HTTP', r.status, '| expires_in', t.expires_in);
if (!t.access_token) { console.log('FEHLER', JSON.stringify(t).slice(0, 300)); process.exit(1); }
if (t.refresh_token) { let c = fs.readFileSync(P, 'utf8').replace(/\r?\n*DATEV_REFRESH_TOKEN=.*$/m, '').replace(/\s*$/, ''); fs.writeFileSync(P, c + `\nDATEV_REFRESH_TOKEN=${t.refresh_token}\n`); }
// JWT-Payload des Access-Tokens decodieren (Scopes/aud sehen)
try {
  const seg = t.access_token.split('.');
  if (seg.length === 3) {
    const payload = JSON.parse(Buffer.from(seg[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'));
    console.log('JWT-Claims:', JSON.stringify({ scope: payload.scope, scp: payload.scp, aud: payload.aud, client_id: payload.client_id, iss: payload.iss }, null, 0));
  } else { console.log('Access-Token ist KEIN JWT (opaque), Segmente:', seg.length); }
} catch (e) { console.log('JWT-decode-Fehler:', e.message); }
const H = { Authorization: 'Bearer ' + t.access_token, Accept: 'application/json', 'X-DATEV-Client-Id': env.DATEV_CLIENT_ID };
for (const url of [
  'https://accounting-clients.api.datev.de/platform-sandbox/v2/clients',
]) {
  try { const a = await fetch(url, { headers: H }); const b = await a.text(); console.log('\nGET', url, '→', a.status); console.log(b.slice(0, 600)); } catch (e) { console.log('ERR', url, e.message); }
}
