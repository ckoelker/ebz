// Showcase M4 — Security-Smoke für Lightdash (soweit ohne Browser prüfbar).
// Prüft über die Lightdash-Health-/API-Endpunkte:
//   1) Lightdash ist erreichbar.
//   2) OIDC (Keycloak) ist als SSO-Methode aktiv.
//   3) Passwort-Login ist deaktiviert (SSO-Zwang) — NACH dem L21-Bootstrap (Schritt 4 im README).
//   4) Ein geschützter API-Call ohne Session wird abgewiesen (401), nicht anonym bedient.
// Der vollständige Login-/Redirect-Flow + der ebz-customers-Negativtest laufen im Browser
// (siehe README) — der Client `lightdash` existiert nur im Realm `ebz-staff`.
//
// Aufruf:  node smoke-lightdash-security.mjs   (Stack mit Profil controlling muss laufen)

const SITE = process.env.LIGHTDASH_URL || 'http://localhost:8084';
let fails = 0;
const ok = (c, m) => { console.log(`${c ? '✓' : '✗'} ${m}`); if (!c) fails++; };

async function getJson(path) {
    const res = await fetch(SITE + path, { redirect: 'manual', headers: { accept: 'application/json' } });
    let body = null;
    try { body = await res.json(); } catch { /* nicht-JSON */ }
    return { status: res.status, body };
}

async function main() {
    console.log(`→ Lightdash-Security-Smoke gegen ${SITE}\n`);

    // 1) + 2) + 3) Health-Endpoint liefert die Auth-Konfiguration.
    const health = await getJson('/api/v1/health');
    ok(health.status === 200, `Lightdash erreichbar (/api/v1/health → ${health.status})`);
    const r = health.body?.results ?? health.body ?? {};
    const auth = r.auth ?? {};
    // Feldnamen je Lightdash-Version leicht unterschiedlich — defensiv prüfen.
    const oidcEnabled = !!(auth.oidc?.enabled ?? auth.okta?.enabled ?? auth.oidc);
    ok(oidcEnabled, 'OIDC/SSO ist aktiviert');
    const pwDisabled = (auth.pat?.enabled === false)
        || auth.disablePasswordAuthentication === true
        || auth.isPasswordDisabled === true
        || r.auth?.disablePasswordAuthentication === true;
    ok(pwDisabled, 'Passwort-Login ist deaktiviert (SSO-Zwang, L21 Schritt 4) — sonst noch im Bootstrap');

    // 4) Geschützter Endpunkt ohne Session → abgewiesen.
    const me = await getJson('/api/v1/user');
    ok(me.status === 401 || me.status === 403,
       `Geschützter API-Call ohne Session abgewiesen (/api/v1/user → ${me.status})`);

    console.log('');
    if (fails) {
        console.error(`${fails} Prüfung(en) offen/fehlgeschlagen. Hinweis: 'Passwort deaktiviert' schlägt`);
        console.error(`bis zum L21-Bootstrap (README Schritt 4) bewusst fehl. Browser-Flow separat prüfen.`);
        process.exit(1);
    }
    console.log('✓ Security-Smoke (API-Teil) bestanden. Browser-Flow gemäß README ergänzen.');
}

main().catch(e => { console.error('\n' + e.message); process.exit(1); });
