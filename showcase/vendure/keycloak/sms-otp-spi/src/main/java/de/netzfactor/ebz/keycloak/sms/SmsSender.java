package de.netzfactor.ebz.keycloak.sms;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.jboss.logging.Logger;

/**
 * Versendet SMS über die Twilio-REST-API — bewusst ohne Twilio-SDK, nur mit dem JDK-{@link HttpClient}
 * (keine Zusatz-Abhängigkeiten/Shading in Keycloak). Credentials kommen aus Umgebungsvariablen:
 * <ul>
 *   <li>{@code SMS_TWILIO_ACCOUNT_SID} — Account-SID ({@code AC…}); steht immer in der API-URL.</li>
 *   <li>Authentifizierung — <b>entweder</b> {@code SMS_TWILIO_AUTH_TOKEN} (Account-Auth-Token)
 *       <b>oder</b> ein API-Key-Paar {@code SMS_TWILIO_API_KEY_SID} ({@code SK…}) +
 *       {@code SMS_TWILIO_API_KEY_SECRET}.</li>
 *   <li>{@code SMS_TWILIO_SENDER} — Absender: Telefonnummer ({@code +49…}) ODER Messaging-Service-SID
 *       ({@code MG…}).</li>
 * </ul>
 * <b>Dev-Fallback:</b> fehlen Credentials, wird der Code nur geloggt statt versendet — so ist der
 * Flow lokal ohne Twilio-Account vorführbar.
 */
public final class SmsSender {

    private static final Logger LOG = Logger.getLogger(SmsSender.class);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String accountSid = System.getenv("SMS_TWILIO_ACCOUNT_SID");
    private final String authToken = System.getenv("SMS_TWILIO_AUTH_TOKEN");
    private final String apiKeySid = System.getenv("SMS_TWILIO_API_KEY_SID");
    private final String apiKeySecret = System.getenv("SMS_TWILIO_API_KEY_SECRET");
    private final String sender = System.getenv("SMS_TWILIO_SENDER");

    private boolean apiKeyAuth() {
        return notBlank(apiKeySid) && notBlank(apiKeySecret);
    }

    /** True, wenn Account-SID, ein Auth-Paar (Token ODER API-Key) und ein Absender gesetzt sind. */
    public boolean konfiguriert() {
        return notBlank(accountSid) && notBlank(sender) && (notBlank(authToken) || apiKeyAuth());
    }

    /** Sendet {@code text} an {@code phone}. Bei fehlenden Credentials: Dev-Log-Fallback. */
    public void send(String phone, String text) {
        if (!konfiguriert()) {
            LOG.warnf("[SMS-OTP Dev-Fallback] Kein Twilio konfiguriert — Nachricht an %s: %s", phone, text);
            return;
        }
        try {
            String body = "To=" + enc(phone)
                    + "&" + (sender.startsWith("MG") ? "MessagingServiceSid=" : "From=") + enc(sender)
                    + "&Body=" + enc(text);
            // Basic-Auth: API-Key (SK…:Secret) bevorzugt, sonst Account-SID:Auth-Token.
            String user = apiKeyAuth() ? apiKeySid : accountSid;
            String pass = apiKeyAuth() ? apiKeySecret : authToken;
            String auth = Base64.getEncoder()
                    .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new SmsException("Twilio-Antwort " + resp.statusCode() + ": " + resp.body());
            }
            LOG.infof("[SMS-OTP] Code an %s gesendet (Twilio).", phone);
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            throw new SmsException("SMS-Versand fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Fehler beim SMS-Versand. */
    public static class SmsException extends RuntimeException {
        public SmsException(String m) {
            super(m);
        }

        public SmsException(String m, Throwable c) {
            super(m, c);
        }
    }
}
