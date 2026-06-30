import { test, expect } from '@playwright/test';
import { URLS, keycloakLogin } from './helpers/sso';

/**
 * Verifiziert das eigene EBZ-„Regenbogen"-SCSS-Theme, das AUSSERHALB der OpenOLAT-.war
 * geladen wird (kompiliert im Docker-Build, vom Entrypoint nach
 * olatdata/customizing/themes/ebz kopiert, ausgewählt via layout.theme=ebz).
 *
 * Setzt den laufenden compose-Stack voraus (openolat + keycloak). Der Login läuft über
 * Keycloak-SSO (Realm ebz-customers, customer/customer).
 */
test('OpenOLAT lädt das externe EBZ-Regenbogen-Theme (Navbar-Verlauf sichtbar)', async ({ page }) => {
  // 1) Das kompilierte Theme wird von OpenOLAT überhaupt ausgeliefert.
  const css = await page.request.get(`${URLS.openolat}/raw/_noversion_/themes/ebz/theme.css`);
  expect(css.status(), 'Theme-CSS wird von OpenOLAT serviert').toBe(200);
  expect(await css.text()).toContain('linear-gradient(90deg');

  // 2) SSO-Login -> OpenOLAT-Oberfläche.
  await page.goto(`${URLS.openolat}/`, { waitUntil: 'domcontentloaded' });
  await keycloakLogin(page);

  // Optionalen Disclaimer beim Erst-Login akzeptieren.
  const accept = page.locator(
    'a:has-text("Akzeptieren"), button:has-text("Akzeptieren"), a:has-text("Accept")',
  );
  if (await accept.count()) {
    await accept.first().click();
    await page.waitForLoadState('networkidle').catch(() => {});
  }

  // Erst-Login zeigt evtl. den Dialog „Do you want to resume your last session?" — schließen.
  const resume = page.getByRole('button', { name: /^(No|Nein)$/ });
  if (await resume.count()) {
    await resume.first().click();
    await page.waitForLoadState('networkidle').catch(() => {});
  }

  // 3) Das aktive Theme im gerenderten <head> ist 'ebz'.
  const themeLinks = await page.evaluate(() =>
    [...document.querySelectorAll('link[rel=stylesheet]')]
      .map((l) => (l as HTMLLinkElement).href)
      .filter((h) => /\/themes\//.test(h)),
  );
  expect(themeLinks.join('\n'), 'aktives Theme ist ebz').toMatch(/\/themes\/ebz\//);

  // 4) Der Regenbogen-Verlauf ist auf der (sichtbaren Haupt-)Navbar tatsächlich berechnet
  //    (nicht nur im CSS vorhanden). Die rechte Offcanvas-`.o_navbar` ist hidden — daher :visible.
  const navbar = page.locator('.o_navbar:visible').first();
  await expect(navbar).toBeVisible();
  const backgroundImage = await navbar.evaluate((el) => getComputedStyle(el).backgroundImage);
  expect(backgroundImage, 'Navbar trägt den linearen Regenbogen-Verlauf').toContain('linear-gradient');

  // 5) Screenshot als sichtbarer Beleg.
  await page.screenshot({ path: 'screenshots/openolat-ebz-theme.png' });
});
