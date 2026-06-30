package de.netzfactor.ebz.keycloak.sms;

import java.util.List;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Factory/Registrierung des {@link SmsAuthenticator} (Provider-ID {@code sms-otp-authenticator}).
 * In einen Authentication-Flow (z. B. Registrierung) als REQUIRED-Execution einhängbar; Code-Länge
 * und Gültigkeit (TTL) sind je Execution konfigurierbar.
 */
public class SmsAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "sms-otp-authenticator";
    private static final SmsAuthenticator INSTANCE = new SmsAuthenticator();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "SMS-OTP (Twilio)";
    }

    @Override
    public String getReferenceCategory() {
        return "sms-otp";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[] { Requirement.REQUIRED, Requirement.DISABLED };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Verifiziert eine Mobilnummer per SMS-Einmalcode (Versand über Twilio).";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty length = new ProviderConfigProperty(
                SmsAuthenticator.CFG_LENGTH, "Code-Länge", "Anzahl der Ziffern des Einmalcodes.",
                ProviderConfigProperty.STRING_TYPE, "6");
        ProviderConfigProperty ttl = new ProviderConfigProperty(
                SmsAuthenticator.CFG_TTL, "Gültigkeit (Sekunden)", "Wie lange der Code gültig ist.",
                ProviderConfigProperty.STRING_TYPE, "300");
        return List.of(length, ttl);
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return INSTANCE;
    }

    @Override
    public void init(Config.Scope config) {
        // nichts
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nichts
    }

    @Override
    public void close() {
        // nichts
    }
}
