# Keycloak-Provider (SPI-JARs)

Wird beim Container-Start nach `/opt/keycloak/providers` gemountet. Hier liegt das
**SMS-OTP-SPI-JAR** (`keycloak-sms-otp-*.jar`) für die Registrierungs-Verifizierung per Twilio.

Gebaut aus `showcase/vendure/keycloak/sms-otp-spi/` (Maven). Build:

```
cd showcase/vendure/keycloak/sms-otp-spi && mvn -q -DskipTests package
cp target/keycloak-sms-otp-*.jar ../providers/
```

Credentials kommen aus der `.env` (am `keycloak`-Service gesetzt):
`SMS_TWILIO_ACCOUNT_SID`, `SMS_TWILIO_AUTH_TOKEN`, `SMS_TWILIO_SENDER`.
Ohne Credentials protokolliert der Authenticator den Code (Dev-Fallback) statt zu senden.
