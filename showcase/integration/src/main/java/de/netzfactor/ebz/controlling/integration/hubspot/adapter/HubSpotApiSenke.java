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
 * E-Mail; nur MDM-eigene Felder werden gesetzt. <b>Consent property-basiert</b> ({@code hs_email_optout}) —
 * tier-unabhängig, ohne den fehlenden {@code communication_preferences}-Scope. Custom-Properties werden bei
 * Bedarf einmalig angelegt ({@link #ensureProperties()}).
 */
@ApplicationScoped
@LookupIfProperty(name = "hubspot.sync.mode", stringValue = "real")
public class HubSpotApiSenke implements HubSpotSenke {

    private static final Logger LOG = Logger.getLogger(HubSpotApiSenke.class);

    /** Stabile externe Schlüssel-Properties (in HubSpot als eindeutige Custom-Properties angelegt). */
    static final String PROP_PARTY_ID = "ebz_party_id";
    static final String PROP_ORG_ID = "ebz_org_id";

    @Inject
    ProducerTemplate producer;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "hubspot.base-url", defaultValue = "https://api.hubapi.com")
    String baseUrl;

    @ConfigProperty(name = "hubspot.token")
    Optional<String> token;

    private volatile boolean propertiesGeprueft = false;

    @Override
    public String upsertContact(ContactDto c) {
        ensureProperties();
        ObjectNode props = mapper.createObjectNode();
        setze(props, "email", c.email());
        setze(props, "firstname", c.vorname());
        setze(props, "lastname", c.nachname());
        setze(props, "hs_email_optout", c.marketingErlaubt() ? "false" : "true");
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
        ObjectNode body = mapper.createObjectNode();
        ObjectNode props = body.putObject("properties");
        props.put("hs_email_optout", erlaubt ? "false" : "true");
        ruf("PATCH", "/crm/v3/objects/contacts/" + contactId, body.toString());
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
        ensureProperty("contacts", PROP_PARTY_ID, "EBZ Party-ID", "string", "text");
        ensureProperty("companies", PROP_ORG_ID, "EBZ Organisations-ID", "string", "text");
        for (String p : new String[]{"ebz_branche", "ebz_verband", "ebz_schwerpunkt", "ebz_unternehmenstyp",
                "ebz_gewerbeerlaubnis", "ebz_ausbildungsbetrieb", "ebz_bestandsgroesse", "ebz_ihk_kammer"}) {
            ensureProperty("companies", p, "EBZ " + p, "string", "text");
        }
        propertiesGeprueft = true;
    }

    private void ensureProperty(String objekt, String name, String label, String typ, String feldTyp) {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", name);
        body.put("label", label);
        body.put("type", typ);
        body.put("fieldType", feldTyp);
        body.put("groupName", objekt.equals("contacts") ? "contactinformation" : "companyinformation");
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
