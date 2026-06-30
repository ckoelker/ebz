package de.netzfactor.ebz.controlling.integration.hubspot.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HubSpot-Webhook-Signatur v3: Base64(HMAC-SHA256(appSecret, {@code method + URI + body + timestamp})).
 * Die {@code URI} ist die <b>öffentliche</b> URL, die HubSpot aufruft (hinter einem Reverse-Proxy weicht sie
 * von der lokal gesehenen ab → {@code hubspot.webhook.public-base-url}). Vergleich in konstanter Zeit gegen
 * Timing-Angriffe.
 */
public final class HubSpotSignatur {

    private HubSpotSignatur() {
    }

    /** Berechnet die erwartete v3-Signatur (Base64-HMAC-SHA256). */
    public static String berechne(String appSecret, String methode, String uri, String body, String timestamp) {
        String basis = methode + uri + (body == null ? "" : body) + timestamp;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] roh = mac.doFinal(basis.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(roh);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-Berechnung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Konstantzeit-Vergleich der erwarteten mit der mitgelieferten Signatur. */
    public static boolean gueltig(String appSecret, String methode, String uri, String body,
                                  String timestamp, String signatur) {
        if (signatur == null || appSecret == null || appSecret.isBlank()) {
            return false;
        }
        String erwartet = berechne(appSecret, methode, uri, body, timestamp);
        return MessageDigest.isEqual(
                erwartet.getBytes(StandardCharsets.UTF_8), signatur.getBytes(StandardCharsets.UTF_8));
    }
}
