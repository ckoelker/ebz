import { test, expect } from '@playwright/test';
import { URLS, keycloakLogin } from './helpers/sso';

const OL = URLS.openolat;
const ADMIN = 'Basic ' + Buffer.from('administrator:openolat').toString('base64');

/**
 * M0 (CI/Branding) — gelöst über den OFFIZIELLEN OpenOLAT-Erweiterungsweg (kein Core-Fork):
 * die Extension `ebz-mandant-branding.jar` registriert via `_spring`-Context einen AfterLogin-
 * Interceptor, der die Organisation-cssClass `mandant-<schlüssel>` (vom Integration-Backend gesetzt)
 * über die öffentliche API `ChiefController.addBodyCssClass(...)` an den `<body>` hängt. Das
 * extern geladene EBZ-Theme targetet dann `body.mandant-demo-ag .o_navbar` → sichtbare per-Mandant-CI.
 *
 * Dieser Test beweist die Kette end-to-end: Mitglied der DEMO_AG-Org → `<body class="… mandant-demo-ag">`
 * → Navbar trägt die DEMO-AG-Marke (Orange #ff6600 statt EBZ-Regenbogen).
 */
// Nach dem Test customer wieder aus der DEMO_AG-Org entfernen → Baseline (EBZ-Regenbogen) bleibt für
// die übrigen Tests erhalten (customer ist ein B2C-EBZ-Kunde; die Mitgliedschaft ist reines Test-Setup).
test.afterEach(async ({ request }) => {
  try {
    const orgs = await (
      await request.get(`${OL}/restapi/organisations`, {
        headers: { Authorization: ADMIN, Accept: 'application/json' },
      })
    ).json();
    const demo = orgs.find((o: { externalId?: string }) => o.externalId === 'DEMO_AG');
    const users = await (
      await request.get(`${OL}/restapi/users?login=customer`, {
        headers: { Authorization: ADMIN, Accept: 'application/json' },
      })
    ).json();
    const cust = (Array.isArray(users) ? users : [users])[0];
    if (demo && cust) {
      await request.delete(`${OL}/restapi/organisations/${demo.key}/users/${cust.key}`, {
        headers: { Authorization: ADMIN },
      });
    }
  } catch {
    /* Best-effort-Cleanup */
  }
});

test('M0: DEMO_AG-Org-Mitglied sieht per-Mandant-Branding (body-cssClass + Navbar-Marke)', async ({
  page,
}) => {
  // 1) Setup (idempotent): customer (Carla Kundin) als Mitglied der DEMO_AG-Org.
  const orgs = await (
    await page.request.get(`${OL}/restapi/organisations`, {
      headers: { Authorization: ADMIN, Accept: 'application/json' },
    })
  ).json();
  const demo = orgs.find((o: { externalId?: string }) => o.externalId === 'DEMO_AG');
  expect(demo, 'DEMO_AG-Org existiert (mandanten-seed + projizieren)').toBeTruthy();
  expect(demo.cssClass).toBe('mandant-demo-ag');
  const users = await (
    await page.request.get(`${OL}/restapi/users?login=customer`, {
      headers: { Authorization: ADMIN, Accept: 'application/json' },
    })
  ).json();
  const cust = (Array.isArray(users) ? users : [users])[0];
  await page.request.put(`${OL}/restapi/organisations/${demo.key}/users/${cust.key}`, {
    headers: { Authorization: ADMIN },
  });

  // 2) Login als Mitglied → OpenOLAT-Oberfläche.
  await page.goto(`${OL}/`, { waitUntil: 'domcontentloaded' });
  await keycloakLogin(page);
  const accept = page.locator(
    'a:has-text("Akzeptieren"), button:has-text("Akzeptieren"), a:has-text("Accept")',
  );
  if (await accept.count()) {
    await accept.first().click();
    await page.waitForLoadState('networkidle').catch(() => {});
  }
  const resume = page.getByRole('button', { name: /^(No|Nein)$/ });
  if (await resume.count()) {
    await resume.first().click();
    await page.waitForLoadState('networkidle').catch(() => {});
  }
  await page.waitForTimeout(1000);

  // 3) Der AfterLogin-Interceptor hat die Org-cssClass an den <body> gehängt.
  const bodyClass = await page.evaluate(() => document.body.className);
  expect(bodyClass, 'body trägt die per-Org cssClass (Extension via addBodyCssClass)').toContain(
    'mandant-demo-ag',
  );

  // 4) Das Theme rendert daraufhin die DEMO-AG-Marke auf der Navbar (Orange #ff6600, kein Regenbogen).
  const navbar = page.locator('.o_navbar:visible').first();
  await expect(navbar).toBeVisible();
  const bg = await navbar.evaluate((el) => {
    const s = getComputedStyle(el);
    return { color: s.backgroundColor, image: s.backgroundImage };
  });
  expect(bg.color, 'Navbar trägt die DEMO-AG-Primärfarbe').toBe('rgb(255, 102, 0)');
  expect(bg.image, 'kein EBZ-Regenbogen-Verlauf für den Mandanten').toBe('none');

  await page.screenshot({ path: 'screenshots/mandant-branding-demo-ag.png' });
});
