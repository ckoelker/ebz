package de.netzfactor.ebz.controlling.integration.hubspot.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.arc.lookup.LookupIfProperty;

import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke;

/**
 * <b>Realer HubSpot-Adapter</b> (Camel-HTTP gegen die CRM-API) — aktiv nur bei {@code hubspot.sync.mode=real};
 * sonst greift die {@code HubSpotMockSenke} ({@code @DefaultBean}). Wiederverwendet das bewährte Muster aus
 * {@code source.HubSpotClient} (ProducerTemplate, Auth-Header, Status selbst auswerten, Backoff bei 429/5xx).
 * <p>
 * <b>Idempotenter Upsert</b> über die stabile externe ID ({@code ebz_party_id}/{@code ebz_org_id}) statt
 * E-Mail; nur MDM-eigene Felder werden gesetzt. <b>Consent property-basiert</b> ({@code ebz_marketing_consent})
 * — tier-unabhängig und immer maßgeblich. Ist der Scope {@code communication_preferences} vorhanden, wird die
 * Einwilligung <b>zusätzlich</b> in die HubSpot-Subscription gespiegelt ({@code hubspot.sync.subscriptions.enabled},
 * 403-/fehler-sicher gekapselt). Custom-Properties werden bei Bedarf einmalig angelegt ({@link #ensureProperties()}).
 */
@ApplicationScoped
@LookupIfProperty(name = "hubspot.sync.mode", stringValue = "real")
public class HubSpotApiSenke implements HubSpotSenke {

    private static final Logger LOG = Logger.getLogger(HubSpotApiSenke.class);

    /** Stabile externe Schlüssel-Properties (in HubSpot als <b>unique</b> Custom-Properties angelegt).
     *  Eigene „uid"-Namen, da HubSpot ein einmal non-unique angelegtes Property nicht nachträglich unique
     *  machen lässt (auch nach Löschung bleibt der Name archiviert/blockiert). */
    static final String PROP_PARTY_ID = "ebz_party_uid";
    static final String PROP_ORG_ID = "ebz_org_uid";
    /** Marketing-Einwilligung als schreibbares Custom-Property — {@code hs_email_optout} ist read-only
     *  (nur über die Subscriptions-API setzbar). Trägt die MDM-Entscheidung tier-unabhängig und ist immer
     *  maßgeblich; HubSpot-Automationen können darauf segmentieren/suppressen. Die echte Subscription wird
     *  bei aktivem Scope zusätzlich gespiegelt ({@link #spiegeleSubscription}). */
    static final String PROP_MARKETING = "ebz_marketing_consent";

    /** Subscribe/Unsubscribe-Vertrag (live verifiziert): GDPR-Portale verlangen BEIDES, legalBasis UND
     *  legalBasisExplanation — auch beim Unsubscribe (sonst HTTP 400). */
    private static final String LEGAL_BASIS = "CONSENT_WITH_NOTICE";

    @Inject
    ProducerTemplate producer;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "hubspot.base-url", defaultValue = "https://api.hubapi.com")
    String baseUrl;

    @ConfigProperty(name = "hubspot.token")
    Optional<String> token;

    /** Zusätzlich zur Property auch die HubSpot-Subscription (Communication Preferences) spiegeln.
     *  Braucht den Scope {@code communication_preferences.read/write}; fehler-/403-sicher gekapselt. */
    @ConfigProperty(name = "hubspot.sync.subscriptions.enabled", defaultValue = "false")
    boolean subscriptionsAktiv;

    /** ID des Marketing-Subscription-Typs (portal-spezifisch — via GET /communication-preferences/v3/
     *  definitions ermitteln). Ohne Wert wird die Subscription-Spiegelung übersprungen. */
    @ConfigProperty(name = "hubspot.sync.subscription-id")
    Optional<String> subscriptionId;

    private volatile boolean propertiesGeprueft = false;

    @Override
    public String upsertContact(ContactDto c) {
        ensureProperties();
        ObjectNode props = mapper.createObjectNode();
        setze(props, "email", c.email());
        setze(props, "firstname", c.vorname());
        setze(props, "lastname", c.nachname());
        setze(props, PROP_MARKETING, c.marketingErlaubt() ? "true" : "false");
        setze(props, PROP_PARTY_ID, c.externeId());
        if (c.leadQuelle() != null) {
            setze(props, "hs_lead_status", c.leadQuelle());
        }
        c.weitere().forEach((k, v) -> setze(props, k, v));
        return batchUpsert("contacts", PROP_PARTY_ID, c.externeId(), props);
    }

    @Override
    public String upsertCompany(CompanyDto c) {
        ensureProperties();
        ObjectNode props = mapper.createObjectNode();
        setze(props, "name", c.name());
        setze(props, "domain", c.domain());
        setze(props, PROP_ORG_ID, c.externeId());
        setze(props, "ebz_branche", c.branche());
        setze(props, "ebz_verband", c.verbaende());
        setze(props, "ebz_schwerpunkt", c.schwerpunkte());
        setze(props, "ebz_unternehmenstyp", c.unternehmenstyp());
        setze(props, "ebz_gewerbeerlaubnis", c.gewerbeerlaubnis());
        setze(props, "ebz_ausbildungsbetrieb", String.valueOf(c.ausbildungsbetrieb()));
        if (c.bestandsgroesse() != null) {
            setze(props, "ebz_bestandsgroesse", String.valueOf(c.bestandsgroesse()));
        }
        setze(props, "ebz_ihk_kammer", c.ihkKammer());
        c.weitere().forEach((k, v) -> setze(props, k, v));
        return batchUpsert("companies", PROP_ORG_ID, c.externeId(), props);
    }

    @Override
    public void verknuepfe(String contactId, String companyId) {
        // v4 Default-Association Contact→Company.
        ruf("PUT", "/crm/v4/objects/contacts/" + contactId + "/associations/default/companies/" + companyId, null);
    }

    @Override
    public void setzeMarketingStatus(String contactId, boolean erlaubt, ConsentNachweis nachweis) {
        // 1) Property ist immer maßgeblich (tier-unabhängig, kein Scope nötig).
        ObjectNode body = mapper.createObjectNode();
        ObjectNode props = body.putObject("properties");
        props.put(PROP_MARKETING, erlaubt ? "true" : "false");
        ruf("PATCH", "/crm/v3/objects/contacts/" + contactId, body.toString());
        // 2) Subscription zusätzlich spiegeln, wenn Scope/Config vorhanden — schlägt das fehl, bleibt
        //    der property-basierte Status maßgeblich (kein Sync-Abbruch).
        if (subscriptionsAktiv) {
            spiegeleSubscription(contactId, erlaubt, nachweis);
        }
    }

    @Override
    public void gdprLoesche(ObjektTyp typ, String hubspotId) {
        if (typ != ObjektTyp.CONTACT) {
            // GDPR-Delete ist contact-spezifisch; Companies werden archiviert.
            archiviere(typ, hubspotId);
            return;
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("objectId", hubspotId);
        ruf("POST", "/crm/v3/objects/contacts/gdpr-delete", body.toString());
    }

    @Override
    public void archiviere(ObjektTyp typ, String hubspotId) {
        String objekt = typ == ObjektTyp.COMPANY ? "companies" : "contacts";
        ruf("DELETE", "/crm/v3/objects/" + objekt + "/" + hubspotId, null);
    }

    // ───────────────────────── intern ─────────────────────────

    /**
     * Spiegelt die Marketing-Einwilligung in die HubSpot-Subscription (Communication Preferences).
     * Die API arbeitet per E-Mail (nicht contactId) → E-Mail wird nachgeladen. <b>403-/fehler-sicher:</b>
     * fehlender Scope, fehlende E-Mail oder GDPR-Validierungsfehler werden nur geloggt — der zuvor gesetzte
     * property-basierte Status bleibt maßgeblich, der Sync läuft weiter.
     */
    private void spiegeleSubscription(String contactId, boolean erlaubt, ConsentNachweis nachweis) {
        if (subscriptionId.isEmpty()) {
            LOG.warn("hubspot.sync.subscriptions.enabled=true, aber hubspot.sync.subscription-id fehlt → Subscription übersprungen.");
            return;
        }
        try {
            String email = ladeEmail(contactId);
            if (email == null || email.isBlank()) {
                LOG.debugf("Kontakt %s ohne E-Mail → keine Subscription-Spiegelung.", contactId);
                return;
            }
            ObjectNode body = mapper.createObjectNode();
            body.put("emailAddress", email);
            body.put("subscriptionId", subscriptionId.get());
            // GDPR-Portal: legalBasis UND legalBasisExplanation sind auch beim Unsubscribe Pflicht.
            body.put("legalBasis", LEGAL_BASIS);
            body.put("legalBasisExplanation", erklaerung(nachweis, erlaubt));
            ruf("POST", "/communication-preferences/v3/" + (erlaubt ? "subscribe" : "unsubscribe"), body.toString());
        } catch (RuntimeException e) {
            LOG.warnf("Subscription-Spiegelung für Kontakt %s fehlgeschlagen (property-Status bleibt maßgeblich): %s",
                    contactId, e.getMessage());
        }
    }

    /** Lädt die primäre E-Mail eines Kontakts (für die E-Mail-basierte Subscription-API). */
    private String ladeEmail(String contactId) {
        String antwort = ruf("GET", "/crm/v3/objects/contacts/" + contactId + "?properties=email", null);
        try {
            return mapper.readTree(antwort).path("properties").path("email").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Revisions-Hinweis für HubSpot (legalBasisExplanation) aus dem MDM-Einwilligungsnachweis. */
    private static String erklaerung(ConsentNachweis n, boolean erlaubt) {
        StringBuilder sb = new StringBuilder(erlaubt ? "MDM-Opt-in" : "MDM-Widerruf");
        if (n != null) {
            if (n.rechtsgrundlage() != null && !n.rechtsgrundlage().isBlank()) sb.append("; Rechtsgrundlage ").append(n.rechtsgrundlage());
            if (n.stand() != null && !n.stand().isBlank()) sb.append("; Stand ").append(n.stand());
            if (n.quelle() != null && !n.quelle().isBlank()) sb.append("; Quelle ").append(n.quelle());
        }
        return sb.toString();
    }

    private String batchUpsert(String objekt, String idProperty, String idWert, ObjectNode props) {
        ObjectNode input = mapper.createObjectNode();
        input.put("idProperty", idProperty);
        input.put("id", idWert);
        input.set("properties", props);
        ObjectNode body = mapper.createObjectNode();
        ArrayNode inputs = body.putArray("inputs");
        inputs.add(input);
        String antwort = ruf("POST", "/crm/v3/objects/" + objekt + "/batch/upsert", body.toString());
        try {
            JsonNode results = mapper.readTree(antwort).path("results");
            if (results.isArray() && results.size() > 0) {
                return results.get(0).path("id").asText(null);
            }
        } catch (Exception e) {
            throw new IllegalStateException("HubSpot-Upsert-Antwort nicht parsebar: " + e.getMessage(), e);
        }
        throw new IllegalStateException("HubSpot-Upsert ohne Ergebnis für " + objekt + " " + idWert);
    }

    /** Legt die EBZ-Custom-Properties einmalig an (idempotent: 409 = existiert bereits → ok). */
    void ensureProperties() {
        if (propertiesGeprueft) {
            return;
        }
        // Die externen ID-Properties MÜSSEN unique sein — Batch-Upsert per idProperty verlangt das.
        ensureProperty("contacts", PROP_PARTY_ID, "EBZ Party-ID", "string", "text", true);
        ensureProperty("companies", PROP_ORG_ID, "EBZ Organisations-ID", "string", "text", true);
        // Marketing-Einwilligung als schreibbares Feld (hs_email_optout ist read-only).
        ensureProperty("contacts", PROP_MARKETING, "EBZ Marketing-Einwilligung", "string", "text", false);
        for (String p : new String[]{"ebz_branche", "ebz_verband", "ebz_schwerpunkt", "ebz_unternehmenstyp",
                "ebz_gewerbeerlaubnis", "ebz_ausbildungsbetrieb", "ebz_bestandsgroesse", "ebz_ihk_kammer"}) {
            ensureProperty("companies", p, "EBZ " + p, "string", "text", false);
        }
        propertiesGeprueft = true;
    }

    private void ensureProperty(String objekt, String name, String label, String typ, String feldTyp, boolean unique) {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", name);
        body.put("label", label);
        body.put("type", typ);
        body.put("fieldType", feldTyp);
        body.put("groupName", objekt.equals("contacts") ? "contactinformation" : "companyinformation");
        if (unique) {
            body.put("hasUniqueValue", true);
        }
        try {
            ruf("POST", "/crm/v3/properties/" + objekt, body.toString());
        } catch (RuntimeException e) {
            // 409 (existiert bereits) ist erwartet/ok; andere Fehler nur loggen, nicht den Sync blockieren.
            LOG.debugf("ensureProperty %s.%s: %s", objekt, name, e.getMessage());
        }
    }

    /** Ein HTTP-Aufruf mit Auth + Backoff bei 429/5xx; liefert den Body bei 2xx, wirft sonst. */
    private String ruf(String methode, String pfad, String body) {
        String url = baseUrl + pfad + (pfad.contains("?") ? "&" : "?") + "throwExceptionOnFailure=false";
        long waitMs = 1000;
        for (int versuch = 1; versuch <= 5; versuch++) {
            Map<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token.orElse(""));
            headers.put("Accept", "application/json");
            headers.put("Content-Type", "application/json");
            headers.put("CamelHttpMethod", methode);
            Exchange ex = producer.request(url, e -> {
                e.getIn().setHeaders(headers);
                e.getIn().setBody(body);
            });
            Integer status = ex.getMessage().getHeader("CamelHttpResponseCode", Integer.class);
            String antwort = ex.getMessage().getBody(String.class);
            if (status != null && status >= 200 && status < 300) {
                return antwort;
            }
            if (status != null && (status == 429 || status >= 500)) {
                LOG.warnf("HubSpot %s %s → HTTP %d (Versuch %d/5), Backoff %dms", methode, pfad, status, versuch, waitMs);
                schlafe(waitMs);
                waitMs *= 2;
                continue;
            }
            throw new IllegalStateException("HubSpot " + methode + " " + pfad + " → HTTP " + status + ": " + antwort);
        }
        throw new IllegalStateException("HubSpot " + methode + " " + pfad + " nach 5 Versuchen nicht erreichbar");
    }

    private static void setze(ObjectNode props, String key, String wert) {
        if (wert != null && !wert.isBlank()) {
            props.put(key, wert);
        }
    }

    private static void schlafe(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
