// DATEV-Sandbox OAuth-Bootstrap (einmalig / bei abgelaufenem Refresh-Token).
// Headless Authorization-Code-Flow + PKCE über den Sandbox-Test-User (DATEV-Benutzerkonto),
// schreibt den (rotierenden) refresh_token nach .env. Liest DATEV_* aus .env (keine Secrets im Code).
// Aufruf aus tests/e2e:  DATEV_SCOPE='openid datev:accounting:extf-files-import datev:accounting:documents datev:accounting:clients' node datev-token-bootstrap.mjs
import { chromium } from 'playwright';
import fs from 'node:fs';
import { randomUUID, randomBytes, createHash } from 'node:crypto';

const ENVPATH = 'c:/dev/workspacesIJ/ebz/.env';
const raw = fs.readFileSync(ENVPATH, 'utf8');
const env = Object.fromEntries(
  raw.split(/\r?\n/).filter(l => /^DATEV_/.test(l)).map(l => { const i = l.indexOf('='); return [l.slice(0, i), l.slice(i + 1)]; })
);
const CID = env.DATEV_CLIENT_ID, CSEC = env.DATEV_CLIENT_SECRET, USER = env.DATEV_SANDBOX_USER, PASS = env.DATEV_SANDBOX_PASSWORD;
const SCOPE = process.env.DATEV_SCOPE || 'openid';
const b64url = b => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const STATE = (randomUUID() + randomUUID()).replace(/-/g, '');
const VERIFIER = b64url(randomBytes(48));
const CHALLENGE = b64url(createHash('sha256').update(VERIFIER).digest());
const REDIRECT = 'http://localhost';
const url = `https://login.datev.de/openidsandbox/authorize?response_type=code&client_id=${CID}`
  + `&redirect_uri=${encodeURIComponent(REDIRECT)}&scope=${encodeURIComponent(SCOPE)}&state=${STATE}`
  + `&code_challenge=${CHALLENGE}&code_challenge_method=S256`;

let page;
const log = (...a) => console.log('[step]', ...a);
const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0 Safari/537.36', viewport: { width: 1280, height: 900 } });
let captured = null;
await ctx.route(/^http:\/\/localhost/, route => { captured = route.request().url(); route.abort().catch(() => {}); });
ctx.on('request', r => { if (r.url().startsWith('http://localhost')) captured = captured || r.url(); });
page = await ctx.newPage();

try {
  await page.goto(url, { waitUntil: 'networkidle', timeout: 45000 });
  await page.check('#DATEV_secure8');
  await page.click('#btn_MethodNext');
  await page.waitForSelector('#username', { timeout: 30000 });
  await page.click('#username'); await page.type('#username', USER, { delay: 25 });
  await page.click('#password'); await page.type('#password', PASS, { delay: 25 });
  await page.keyboard.press('Tab');
  await page.waitForSelector('#formButton:not([disabled])', { timeout: 8000 }).catch(() => {});
  await page.click('#formButton', { force: true });
  await page.waitForTimeout(4500);
  const dump = async () => JSON.stringify(await page.evaluate(() => [...document.querySelectorAll('input,button,a[role=button]')].filter(e => e.offsetParent !== null).map(e => ({ tag: e.tagName, type: e.type, id: e.id, txt: (e.innerText || '').trim().slice(0, 40) }))));
  log('nach Login:', page.url());
  // Consent-Seite: "Ich stimme zu"
  if (!captured && await page.locator('#btnAccept').count()) {
    await page.check('#persistConsent').catch(() => {});
    log('consent: klicke #btnAccept ("Ich stimme zu")');
    await page.click('#btnAccept', { force: true });
    await page.waitForTimeout(4500);
    log(' -> url:', page.url());
  }
} catch (e) { log('ERROR:', e.message); }
await browser.close();

if (!captured) { log('Kein Redirect gefangen'); process.exit(1); }
const u = new URL(captured);
const code = u.searchParams.get('code');
if (!code) { log('Redirect-Fehler:', u.searchParams.get('error'), u.searchParams.get('error_description')); process.exit(1); }
log('AUTH-CODE erhalten (Länge ' + code.length + ') — tausche gegen Tokens…');

const body = new URLSearchParams({ grant_type: 'authorization_code', code, redirect_uri: REDIRECT, code_verifier: VERIFIER });
const resp = await fetch('https://sandbox-api.datev.de/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded', Authorization: 'Basic ' + Buffer.from(`${CID}:${CSEC}`).toString('base64') },
  body,
});
const tok = await resp.json();
log('Token-HTTP', resp.status, '| scope=', tok.scope, '| expires_in=', tok.expires_in, '| token_type=', tok.token_type);
if (!tok.refresh_token) { log('FEHLER: kein refresh_token:', JSON.stringify(tok).slice(0, 300)); process.exit(1); }

// refresh_token (+ id-info) in .env schreiben/aktualisieren — Wert NICHT loggen
let content = fs.readFileSync(ENVPATH, 'utf8').replace(/\r?\n*DATEV_REFRESH_TOKEN=.*$/m, '');
content = content.replace(/\s*$/, '') + `\nDATEV_REFRESH_TOKEN=${tok.refresh_token}\n`;
fs.writeFileSync(ENVPATH, content);
log('OK ✓ DATEV_REFRESH_TOKEN in .env geschrieben (Länge ' + tok.refresh_token.length + '). access_token-Länge ' + (tok.access_token || '').length);
