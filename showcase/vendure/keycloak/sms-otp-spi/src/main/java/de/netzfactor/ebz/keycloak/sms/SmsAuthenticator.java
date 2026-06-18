package de.netzfactor.ebz.keycloak.sms;

import java.security.SecureRandom;

import jakarta.ws.rs.core.MultivaluedMap;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

/**
 * Zwei-Phasen-SMS-OTP-Authenticator (für die Registrierungs-Verifizierung):
 * <ol>
 *   <li>Mobilnummer abfragen → Code erzeugen + per {@link SmsSender Twilio} senden.</li>
 *   <li>Code abfragen → gegen die in der Auth-Session gemerkte (TTL-begrenzte) Vorgabe prüfen.</li>
 * </ol>
 * Selbst-enthalten (kein eigener Registrierungs-Formular-Eingriff). Code-Länge/TTL sind im
 * Flow-Execution-Config einstellbar ({@link SmsAuthenticatorFactory}).
 */
public class SmsAuthenticator implements Authenticator {

    static final String NOTE_CODE = "sms_otp_code";
    static final String NOTE_TS = "sms_otp_ts";
    static final String NOTE_PHONE = "sms_otp_phone";
    static final String CFG_LENGTH = "length";
    static final String CFG_TTL = "ttl";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Phase 1: Mobilnummer-Formular anzeigen.
        context.challenge(context.form().createForm("sms-phone.ftl"));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        AuthenticationSessionModel auth = context.getAuthenticationSession();

        String code = form.getFirst("code");
        if (code != null) {
            pruefeCode(context, auth, code.trim());
            return;
        }
        sendeCode(context, auth, form.getFirst("phoneNumber"));
    }

    private void sendeCode(AuthenticationFlowContext context, AuthenticationSessionModel auth, String phone) {
        if (phone == null || phone.isBlank()) {
            context.challenge(context.form().setError("Bitte geben Sie eine Mobilnummer an.").createForm("sms-phone.ftl"));
            return;
        }
        String nummer = phone.trim();
        String generated = randomDigits(length(context));
        auth.setAuthNote(NOTE_CODE, generated);
        auth.setAuthNote(NOTE_TS, String.valueOf(System.currentTimeMillis()));
        auth.setAuthNote(NOTE_PHONE, nummer);
        UserModel user = context.getUser();
        if (user != null) {
            user.setSingleAttribute("phoneNumber", nummer);
        }
        try {
            new SmsSender().send(nummer, "Ihr EBZ-Bestätigungscode lautet: " + generated);
        } catch (SmsSender.SmsException e) {
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("SMS konnte nicht gesendet werden. Bitte erneut versuchen.")
                            .createForm("sms-phone.ftl"));
            return;
        }
        context.challenge(context.form().setAttribute("phone", nummer).createForm("sms-code.ftl"));
    }

    private void pruefeCode(AuthenticationFlowContext context, AuthenticationSessionModel auth, String code) {
        String expected = auth.getAuthNote(NOTE_CODE);
        String ts = auth.getAuthNote(NOTE_TS);
        String phone = auth.getAuthNote(NOTE_PHONE);
        long ttlMs = ttl(context) * 1000L;
        if (expected == null || ts == null || System.currentTimeMillis() - Long.parseLong(ts) > ttlMs) {
            context.failureChallenge(AuthenticationFlowError.EXPIRED_CODE,
                    context.form().setError("Der Code ist abgelaufen. Bitte fordern Sie einen neuen an.")
                            .createForm("sms-phone.ftl"));
            return;
        }
        if (!expected.equals(code)) {
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    context.form().setAttribute("phone", phone).setError("Der Code ist falsch.")
                            .createForm("sms-code.ftl"));
            return;
        }
        auth.removeAuthNote(NOTE_CODE);
        auth.removeAuthNote(NOTE_TS);
        context.success();
    }

    private int length(AuthenticationFlowContext context) {
        return configInt(context, CFG_LENGTH, 6);
    }

    private int ttl(AuthenticationFlowContext context) {
        return configInt(context, CFG_TTL, 300);
    }

    private int configInt(AuthenticationFlowContext context, String key, int fallback) {
        AuthenticatorConfigModel cfg = context.getAuthenticatorConfig();
        if (cfg == null || cfg.getConfig() == null) {
            return fallback;
        }
        String v = cfg.getConfig().get(key);
        try {
            return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String randomDigits(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // keine
    }

    @Override
    public void close() {
        // nichts
    }
}
