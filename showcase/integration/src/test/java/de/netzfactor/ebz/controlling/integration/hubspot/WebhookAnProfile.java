package de.netzfactor.ebz.controlling.integration.hubspot;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Gemeinsames Test-Profil „Webhook aktiv" für die HubSpot-Webhook-Tests (HubSpotWebhookTest +
 * HubSpotSyncE2ETest). EIN gemeinsames Profil statt zwei identischer Inner-Profile: Quarkus gruppiert
 * Testklassen nach Profil-KLASSE (nicht nach Config-Gleichheit) → beide Klassen teilen sich denselben
 * Quarkus-Boot, statt je einen Neustart auszulösen (spart einen Boot in der Suite).
 */
public class WebhookAnProfile implements QuarkusTestProfile {

    public static final String SECRET = "webhook-secret-123";
    public static final String BASE_URL = "https://hooks.ebz.test";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "hubspot.webhook.enabled", "true",
                "hubspot.webhook.app-secret", SECRET,
                "hubspot.webhook.public-base-url", BASE_URL);
    }
}
