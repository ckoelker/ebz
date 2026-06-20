import { test, expect, Page } from '@playwright/test';
import { keycloakLogin, STAFF, URLS } from './helpers/sso';

/**
 * mdm-Rechnungs-Cockpit (Staff, Realm ebz-staff). Verifiziert den fertigen Beleg-Lebenszyklus durch
 * die Oberfläche: Liste + Filter, Detailansicht eines festgeschriebenen Belegs (Positionen/ZUGFeRD),
 * und den vollen Anlege-Flow einer freien Sonderrechnung (anlegen → Position → ausstellen → Nummer).
 * Läuft gegen den laufenden compose-Stack (mdm :5174 + integration :8090 + keycloak).
 */

/** Öffnet das Cockpit und meldet den Sachbearbeiter per Keycloak-SSO an (Liste lädt anonym, „Anmelden" triggert Login). */
async function anmelden(page: Page) {
  await page.goto(`${URLS.mdm}/rechnungen`);
  await expect(page.getByRole('heading', { name: 'Rechnungen' })).toBeVisible();
  await page.getByRole('button', { name: 'Anmelden' }).click();
  await page.waitForURL(/keycloak/, { timeout: 30_000 }); // Redirect abwarten (Klick kehrt sofort zurück)
  await keycloakLogin(page, STAFF);
  // zurück im Cockpit, jetzt angemeldet (Abmelden-Button sichtbar)
  await expect(page.getByRole('button', { name: 'Abmelden' })).toBeVisible({ timeout: 30_000 });
}

test('Liste: Login + Belege + Status-Badges sichtbar', async ({ page }) => {
  await anmelden(page);
  await page.goto(`${URLS.mdm}/rechnungen`);

  // Es gibt Belege (Demo-/Bestandsdaten).
  await expect(page.locator('tbody tr').first()).toBeVisible();
  const gesamt = await page.locator('tbody tr').count();
  expect(gesamt).toBeGreaterThan(0);

  // Der Beleg-Zähler stimmt mit den Zeilen überein und die Demo-Daten enthalten bezahlte Belege.
  await expect(page.getByText(`${gesamt} Belege`)).toBeVisible();
  await expect(page.getByText('BEZAHLT', { exact: true }).first()).toBeVisible();
});

test('Detail: festgeschriebener Beleg zeigt Positionen + Aktionen', async ({ page }) => {
  await anmelden(page);
  await page.goto(`${URLS.mdm}/rechnungen`);

  // Ersten Beleg mit einer Nummer (festgeschrieben) öffnen.
  const zeileMitNummer = page.locator('tbody tr').filter({ hasText: /RE-|GU-|ST-/ }).first();
  await expect(zeileMitNummer).toBeVisible();
  await zeileMitNummer.click();

  await expect(page).toHaveURL(/\/rechnungen\/\d+$/);
  // Positionen-Tabelle (Summe-Fuß) + Status-Badge + ZUGFeRD-Aktion vorhanden.
  await expect(page.getByText('Summe')).toBeVisible();
  await expect(page.getByRole('button', { name: 'ZUGFeRD' })).toBeVisible();
});

test('Sonderrechnung: anlegen → Position → ausstellen → Nummer', async ({ page }) => {
  await anmelden(page);
  await page.goto(`${URLS.mdm}/rechnungen`);
  const marke = `E2E Sonder ${Date.now()}`;

  // Modal öffnen.
  await page.getByRole('button', { name: 'Sonderrechnung' }).click();
  const modal = page.getByRole('dialog');
  await expect(modal.getByText('Sonderrechnung anlegen')).toBeVisible();

  // Debitor wählen (erste Option) + Zeitraum eindeutig setzen.
  await modal.getByRole('combobox').first().click();
  await page.getByRole('option').first().click();
  await modal.getByPlaceholder(/Einmalleistung/).fill(marke);
  await modal.getByRole('button', { name: 'Anlegen' }).click();

  // Detail des frischen Entwurfs.
  await expect(page).toHaveURL(/\/rechnungen\/\d+$/);
  await expect(page.getByText('— Entwurf —')).toBeVisible();

  // Position ergänzen.
  await page.getByRole('button', { name: 'Position', exact: true }).click();
  const posModal = page.getByRole('dialog');
  await posModal.getByRole('textbox').first().fill('E2E Beratungsleistung');
  // Einzelbetrag (2. Zahlenfeld: Menge | Einzelbetrag | USt) auf 500 setzen.
  await posModal.getByRole('spinbutton').nth(1).fill('500');
  await posModal.getByRole('button', { name: 'Hinzufügen' }).click();
  await expect(page.getByText('E2E Beratungsleistung')).toBeVisible();

  // Ausstellen (Aktions-Button → Bestätigungs-Dialog).
  await page.getByRole('button', { name: 'Ausstellen', exact: true }).click();
  const confirm = page.getByRole('dialog');
  await expect(confirm.getByText('Beleg ausstellen?')).toBeVisible();
  await confirm.getByRole('button', { name: 'Ausstellen' }).click();

  // Festgeschrieben: Status-Badge AUSGESTELLT + eine Belegnummer im Titel.
  await expect(page.getByText('AUSGESTELLT', { exact: true })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('heading', { level: 2 })).toContainText(/[A-Z]{2}-[A-Z]{2}-\d+/);
});
