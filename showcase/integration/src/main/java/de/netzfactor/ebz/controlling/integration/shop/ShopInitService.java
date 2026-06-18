package de.netzfactor.ebz.controlling.integration.shop;

import static io.smallrye.graphql.client.core.Argument.arg;
import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.InputObject.inputObject;
import static io.smallrye.graphql.client.core.InputObjectField.prop;
import static io.smallrye.graphql.client.core.Operation.operation;
import static io.smallrye.graphql.client.core.Variable.var;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.bildung.vendure.VendureException;
import de.netzfactor.ebz.controlling.integration.shop.KatalogBeispiele.Durchfuehrung;
import de.netzfactor.ebz.controlling.integration.shop.KatalogBeispiele.Person;
import de.netzfactor.ebz.controlling.integration.shop.KatalogBeispiele.Produkt;
import de.netzfactor.ebz.controlling.integration.shop.KatalogBeispiele.Referent;
import de.netzfactor.ebz.controlling.integration.shop.KatalogBeispiele.Steuer;
import io.smallrye.graphql.client.core.Field;
import io.smallrye.graphql.client.core.OperationType;
import io.smallrye.graphql.client.core.Variable;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

/**
 * Reproduzierbarer, idempotenter Shop-Aufbau (Produktkatalog P1, §B). Treibt Vendure über die
 * Admin-API ({@link VendureAdmin}, SmallRye Dynamic Client): Grundkonfig, Steuern, Facetten,
 * Collections, Versand/Zahlung/Rolle, Platzhalter-Assets, CRM-Personen, die voll ausgestatteten
 * Beispielprodukte ({@link KatalogBeispiele}) sowie die F1–F6-Demoprodukte (Ablösung von
 * {@code seed.mjs}, identische Slugs/SKUs → Smokes bleiben grün). Mehrfacher Aufruf ist sicher.
 */
@ApplicationScoped
public class ShopInitService {

    private static final Logger LOG = Logger.getLogger(ShopInitService.class);

    @Inject
    VendureAdmin vendure;

    @Inject
    VendureAssetUploader uploader;

    /** Zusammenfassung eines Init-Laufs. */
    public record Ergebnis(int angelegt, int aktualisiert, int uebersprungen, List<String> log) {
    }

    public Ergebnis initialisiere() {
        VendureAdmin.Verbindung v = vendure.anmelden();
        try (DynamicGraphQLClient client = v.client()) {
            Lauf lauf = new Lauf(client, v.token());
            return lauf.run();
        } catch (VendureException e) {
            throw e;
        } catch (Exception e) {
            throw new VendureException("Shop-Init fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    // ───────────────────────── ein Init-Lauf ─────────────────────────

    /** Bündelt Client, Token, Zähler und alle ensure-Schritte eines Laufs (nicht thread-safe; Aufruf selten). */
    private final class Lauf {
        private final DynamicGraphQLClient client;
        private final String token;
        private int angelegt;
        private int aktualisiert;
        private int uebersprungen;
        private final List<String> log = new ArrayList<>();

        Lauf(DynamicGraphQLClient client, String token) {
            this.client = client;
            this.token = token;
        }

        Ergebnis run() {
            ensureGlobalSettings();
            String countryId = ensureCountry();
            String zoneId = ensureZone(countryId);
            ensureChannel(zoneId);
            Map<Steuer, String> tax = ensureTax(zoneId);
            Map<String, String> facetValues = ensureFacets();
            ensureCollections(facetValues);
            ensureRole();
            ensureShipping();
            ensurePayment();
            Assets assets = ensureAssets();
            Map<String, String> personen = ensurePersonen(assets.imageId());
            ensureProdukte(tax, facetValues, personen, assets);
            ensureDemoF1F6(tax);
            ensureContentPages();
            ensureFruehbucherPromotion();
            reindex();
            return new Ergebnis(angelegt, aktualisiert, uebersprungen, log);
        }

        // ── Grundkonfiguration ──

        // Globale Grundeinstellungen: (1) Inventory-Tracking aus (keine Kapazitätslogik im Showcase,
        // sonst „insufficient stock"); (2) Deutsch als Default-/Verfügbar-Sprache des Backends.
        private void ensureGlobalSettings() {
            JsonObject gs = q(field("globalSettings", field("trackInventory"), field("availableLanguages")))
                    .getJsonObject("globalSettings");
            boolean trackAus = !gs.getBoolean("trackInventory", false);
            boolean deDa = false;
            for (JsonValue l : gs.getJsonArray("availableLanguages")) {
                if ("de".equals(((jakarta.json.JsonString) l).getString())) {
                    deDa = true;
                }
            }
            if (trackAus && deDa) {
                skip("GlobalSettings (trackInventory=false, Sprache de)");
                return;
            }
            Map<String, Object> input = obj("trackInventory", false);
            input.put("availableLanguages", List.of("de", "en"));
            m("updateGlobalSettings", "UpdateGlobalSettingsInput!", input, field("__typename"));
            add("GlobalSettings (trackInventory=false, Sprache de)");
        }

        private String ensureCountry() {
            JsonObject d = q(field("countries", List.of(arg("options", inputObject(prop("take", 300)))),
                    field("items", field("id"), field("code"))));
            for (JsonValue jv : d.getJsonObject("countries").getJsonArray("items")) {
                JsonObject c = jv.asJsonObject();
                if ("DE".equals(c.getString("code"))) {
                    skip("Land DE");
                    return c.getString("id");
                }
            }
            Map<String, Object> input = obj("code", "DE", "enabled", true);
            input.put("translations", List.of(
                    obj("languageCode", "en", "name", "Germany"),
                    obj("languageCode", "de", "name", "Deutschland")));
            String id = m("createCountry", "CreateCountryInput!", input, field("id"))
                    .getJsonObject("createCountry").getString("id");
            add("Land DE");
            return id;
        }

        private String ensureZone(String countryId) {
            JsonObject d = q(field("zones", List.of(arg("options", inputObject(prop("take", 200)))),
                    field("items", field("id"), field("name"))));
            for (JsonValue jv : d.getJsonObject("zones").getJsonArray("items")) {
                JsonObject z = jv.asJsonObject();
                if ("Deutschland".equals(z.getString("name"))) {
                    skip("Zone Deutschland");
                    return z.getString("id");
                }
            }
            Map<String, Object> input = obj("name", "Deutschland");
            input.put("memberIds", List.of(countryId));
            String id = m("createZone", "CreateZoneInput!", input, field("id"))
                    .getJsonObject("createZone").getString("id");
            add("Zone Deutschland");
            return id;
        }

        private void ensureChannel(String zoneId) {
            JsonObject d = q(field("activeChannel", field("id"), field("defaultCurrencyCode"),
                    field("defaultLanguageCode"), field("availableLanguageCodes"),
                    field("defaultTaxZone", field("id")), field("defaultShippingZone", field("id"))));
            JsonObject ch = d.getJsonObject("activeChannel");
            boolean taxOk = !ch.isNull("defaultTaxZone");
            boolean shipOk = !ch.isNull("defaultShippingZone");
            boolean eur = "EUR".equals(str(ch, "defaultCurrencyCode"));
            boolean de = "de".equals(str(ch, "defaultLanguageCode"));
            boolean deVerfuegbar = false;
            for (JsonValue l : ch.getJsonArray("availableLanguageCodes")) {
                if ("de".equals(((jakarta.json.JsonString) l).getString())) {
                    deVerfuegbar = true;
                }
            }
            if (taxOk && shipOk && eur && de && deVerfuegbar) {
                skip("Channel (Zonen + EUR + de)");
                return;
            }
            Map<String, Object> input = obj("id", ch.getString("id"),
                    "defaultTaxZoneId", zoneId, "defaultShippingZoneId", zoneId,
                    "defaultCurrencyCode", "EUR", "defaultLanguageCode", "de");
            input.put("availableCurrencyCodes", List.of("EUR"));
            // de muss in den Channel-Sprachen sein, sonst indiziert der Such-Index nur en → Shop(de) leer.
            input.put("availableLanguageCodes", List.of("de", "en"));
            m("updateChannel", "UpdateChannelInput!", input, field("__typename"));
            add("Channel: Default-Zonen + EUR + Sprache de");
        }

        // ── Steuern (Bildung steuerfrei / Standard 19 % / Ermäßigt 7 %) ──

        private Map<Steuer, String> ensureTax(String zoneId) {
            record TaxDef(Steuer key, String catName, String rateName, int value, boolean isDefault) {
            }
            List<TaxDef> defs = List.of(
                    new TaxDef(Steuer.BEFREIT, "Bildung steuerfrei (§4 Nr. 21 UStG)", "Bildung steuerfrei", 0, false),
                    new TaxDef(Steuer.STANDARD, "Standard", "Standard DE 19%", 19, true),
                    new TaxDef(Steuer.ERMAESSIGT, "Ermäßigt", "Ermäßigt 7%", 7, false));

            Map<String, String> cats = new LinkedHashMap<>();
            for (JsonValue jv : q(field("taxCategories", List.of(arg("options", inputObject(prop("take", 50)))),
                    field("items", field("id"), field("name")))).getJsonObject("taxCategories").getJsonArray("items")) {
                JsonObject c = jv.asJsonObject();
                cats.put(c.getString("name"), c.getString("id"));
            }
            List<String> rates = new ArrayList<>();
            for (JsonValue jv : q(field("taxRates", List.of(arg("options", inputObject(prop("take", 50)))),
                    field("items", field("name")))).getJsonObject("taxRates").getJsonArray("items")) {
                rates.add(jv.asJsonObject().getString("name"));
            }

            Map<Steuer, String> result = new LinkedHashMap<>();
            for (TaxDef def : defs) {
                String catId = cats.get(def.catName());
                if (catId == null) {
                    catId = m("createTaxCategory", "CreateTaxCategoryInput!",
                            obj("name", def.catName(), "isDefault", def.isDefault()), field("id"))
                            .getJsonObject("createTaxCategory").getString("id");
                    add("TaxCategory " + def.catName());
                } else {
                    skip("TaxCategory " + def.catName());
                }
                result.put(def.key(), catId);
                if (!rates.contains(def.rateName())) {
                    m("createTaxRate", "CreateTaxRateInput!",
                            obj("name", def.rateName(), "enabled", true, "value", def.value(),
                                    "categoryId", catId, "zoneId", zoneId), field("id"));
                    add("Steuersatz " + def.rateName() + " (" + def.value() + "%)");
                } else {
                    skip("Steuersatz " + def.rateName());
                }
            }
            return result;
        }

        // ── Facetten + Werte (aus den Beispielprodukten abgeleitet) ──

        private Map<String, String> ensureFacets() {
            // gewünschte Facetten → {Wert-Code → Anzeigename}
            Map<String, Map<String, String>> wanted = new LinkedHashMap<>();
            wanted.put("veranstaltungsart", new LinkedHashMap<>());
            wanted.put("thema", new LinkedHashMap<>());
            wanted.put("branche", new LinkedHashMap<>());
            wanted.put("region", new LinkedHashMap<>());
            wanted.put("format", new LinkedHashMap<>());
            for (Produkt p : KatalogBeispiele.PRODUKTE) {
                addVal(wanted.get("veranstaltungsart"), p.veranstaltungsart());
                addVal(wanted.get("thema"), p.thema());
                addVal(wanted.get("branche"), p.branche());
                addVal(wanted.get("region"), p.region());
                for (Durchfuehrung df : p.durchfuehrungen()) {
                    addVal(wanted.get("format"), df.format().name());
                }
            }

            // Bestand laden
            Map<String, JsonObject> existing = new LinkedHashMap<>();
            for (JsonValue jv : q(field("facets", List.of(arg("options", inputObject(prop("take", 100)))),
                    field("items", field("id"), field("code"),
                            field("values", field("id"), field("code")))))
                    .getJsonObject("facets").getJsonArray("items")) {
                JsonObject f = jv.asJsonObject();
                existing.put(f.getString("code"), f);
            }

            Map<String, String> facetValueIds = new LinkedHashMap<>(); // "facetCode:valueCode" → id
            for (var e : wanted.entrySet()) {
                String facetCode = e.getKey();
                Map<String, String> werte = e.getValue();
                JsonObject f = existing.get(facetCode);
                if (f == null) {
                    // Facet inkl. aller Werte neu anlegen
                    List<Object> values = new ArrayList<>();
                    for (var w : werte.entrySet()) {
                        values.add(obj("code", w.getKey(),
                                "translations", List.of(obj("languageCode", "de", "name", w.getValue()))));
                    }
                    Map<String, Object> input = obj("code", facetCode, "isPrivate", false);
                    input.put("translations", List.of(obj("languageCode", "de", "name", facetName(facetCode))));
                    input.put("values", values);
                    JsonObject created = m("createFacet", "CreateFacetInput!", input,
                            field("id"), field("values", field("id"), field("code")))
                            .getJsonObject("createFacet");
                    for (JsonValue vv : created.getJsonArray("values")) {
                        JsonObject val = vv.asJsonObject();
                        facetValueIds.put(facetCode + ":" + val.getString("code"), val.getString("id"));
                    }
                    add("Facet " + facetCode + " (" + werte.size() + " Werte)");
                } else {
                    // vorhandene Werte übernehmen, fehlende ergänzen
                    Map<String, String> have = new LinkedHashMap<>();
                    for (JsonValue vv : f.getJsonArray("values")) {
                        JsonObject val = vv.asJsonObject();
                        have.put(val.getString("code"), val.getString("id"));
                    }
                    for (var w : werte.entrySet()) {
                        String id = have.get(w.getKey());
                        if (id == null) {
                            JsonObject created = m("createFacetValues", "[CreateFacetValueInput!]!",
                                    List.of(obj("facetId", f.getString("id"), "code", w.getKey(),
                                            "translations", List.of(obj("languageCode", "de", "name", w.getValue())))),
                                    field("id"), field("code"));
                            id = created.getJsonArray("createFacetValues").getJsonObject(0).getString("id");
                            add("FacetValue " + facetCode + ":" + w.getKey());
                        }
                        facetValueIds.put(facetCode + ":" + w.getKey(), id);
                    }
                    skip("Facet " + facetCode + " (Bestand)");
                }
            }
            return facetValueIds;
        }

        // ── Collections (Kategorie-Browsing) je thema + branche ──

        private void ensureCollections(Map<String, String> facetValueIds) {
            List<String> existing = new ArrayList<>();
            for (JsonValue jv : q(field("collections", List.of(arg("options", inputObject(prop("take", 200)))),
                    field("items", field("slug")))).getJsonObject("collections").getJsonArray("items")) {
                existing.add(jv.asJsonObject().getString("slug"));
            }
            for (var e : facetValueIds.entrySet()) {
                String key = e.getKey();
                if (!(key.startsWith("thema:") || key.startsWith("branche:"))) {
                    continue;
                }
                String valueCode = key.substring(key.indexOf(':') + 1);
                String slug = "kat-" + valueCode;
                if (existing.contains(slug)) {
                    skip("Collection " + slug);
                    continue;
                }
                String name = titel(valueCode);
                Map<String, Object> filter = obj("code", "facet-value-filter");
                filter.put("arguments", List.of(
                        obj("name", "facetValueIds", "value", "[\"" + e.getValue() + "\"]"),
                        obj("name", "containsAny", "value", "false")));
                Map<String, Object> input = new LinkedHashMap<>();
                input.put("translations", List.of(obj("languageCode", "de", "name", name, "slug", slug, "description", "")));
                input.put("filters", List.of(filter));
                m("createCollection", "CreateCollectionInput!", input, field("id"));
                add("Collection " + slug);
            }
        }

        // ── Versand / Zahlung / Rolle (Ablösung seed.mjs) ──

        private void ensureRole() {
            // Globale Vendure-Rollen (P5/RBAC), kein Channel-Scoping. Der Admin-SSO-Strategy mappt
            // die Keycloak-Realm-Rolle `katalog-pflege` → 'katalog-pflege' (CRUD), sonst 'katalog-lesen'.
            record R(String code, String desc, List<String> perms) {
            }
            List<R> rollen = List.of(
                    new R("katalog-lesen", "Katalog-Lesen (SSO, nur Read*)",
                            List.of("ReadCatalog", "ReadOrder", "ReadCustomer", "ReadSettings")),
                    new R("katalog-pflege", "Katalog-Pflege (SSO, Create/Update/Delete)",
                            List.of("ReadCatalog", "CreateCatalog", "UpdateCatalog", "DeleteCatalog",
                                    "ReadOrder", "ReadCustomer", "ReadSettings")));
            JsonObject d = q(field("roles", List.of(arg("options", inputObject(prop("take", 100)))),
                    field("items", field("code"))), field("activeChannel", field("id")));
            List<String> have = new ArrayList<>();
            for (JsonValue jv : d.getJsonObject("roles").getJsonArray("items")) {
                have.add(jv.asJsonObject().getString("code"));
            }
            String channelId = d.getJsonObject("activeChannel").getString("id");
            for (R r : rollen) {
                if (have.contains(r.code())) {
                    skip("Rolle " + r.code());
                    continue;
                }
                Map<String, Object> input = obj("code", r.code(), "description", r.desc());
                input.put("permissions", r.perms());
                input.put("channelIds", List.of(channelId));
                m("createRole", "CreateRoleInput!", input, field("id"));
                add("Rolle " + r.code());
            }
        }

        private void ensureShipping() {
            record M(String code, int rate, String de, String desc) {
            }
            List<M> methods = List.of(
                    new M("standard", 490, "Standardversand", "Versand physischer Ware per Post"),
                    new M("digital", 0, "Digitale Bereitstellung", "Kein Versand — Download/Seminar (0 €)"));
            List<String> have = new ArrayList<>();
            for (JsonValue jv : q(field("shippingMethods", List.of(arg("options", inputObject(prop("take", 100)))),
                    field("items", field("code")))).getJsonObject("shippingMethods").getJsonArray("items")) {
                have.add(jv.asJsonObject().getString("code"));
            }
            for (M mth : methods) {
                if (have.contains(mth.code())) {
                    skip("Versandart " + mth.code());
                    continue;
                }
                Map<String, Object> checker = obj("code", "default-shipping-eligibility-checker");
                checker.put("arguments", List.of(obj("name", "orderMinimum", "value", "0")));
                Map<String, Object> calc = obj("code", "default-shipping-calculator");
                calc.put("arguments", List.of(
                        obj("name", "rate", "value", String.valueOf(mth.rate())),
                        obj("name", "includesTax", "value", "auto"),
                        obj("name", "taxRate", "value", "19")));
                Map<String, Object> input = obj("code", mth.code(), "fulfillmentHandler", "manual-fulfillment");
                input.put("checker", checker);
                input.put("calculator", calc);
                input.put("translations", List.of(obj("languageCode", "de", "name", mth.de(), "description", mth.desc())));
                m("createShippingMethod", "CreateShippingMethodInput!", input, field("id"));
                add("Versandart " + mth.code());
            }
        }

        private void ensurePayment() {
            // Zwei Checkout-Wege (P4): Kauf auf Rechnung (B2B) + Kreditkarte/SEPA (Stripe).
            // Beide laufen im Showcase über den dummyPaymentHandler (automaticSettle) — der
            // echte Stripe-PSP-Anschluss bleibt ein späterer Schritt.
            record PM(String code, String name, String desc) {
            }
            List<PM> methods = List.of(
                    new PM("rechnung", "Rechnung", "Kauf auf Rechnung (B2B, Showcase)"),
                    new PM("stripe-sepa", "Kreditkarte / SEPA-Lastschrift", "Zahlung per Karte/SEPA über Stripe (Showcase-Stand-in)"));
            List<String> have = new ArrayList<>();
            for (JsonValue jv : q(field("paymentMethods", List.of(arg("options", inputObject(prop("take", 100)))),
                    field("items", field("code")))).getJsonObject("paymentMethods").getJsonArray("items")) {
                have.add(jv.asJsonObject().getString("code"));
            }
            for (PM pm : methods) {
                if (have.contains(pm.code())) {
                    skip("Zahlart " + pm.code());
                    continue;
                }
                Map<String, Object> handler = obj("code", "dummy-payment-handler");
                handler.put("arguments", List.of(obj("name", "automaticSettle", "value", "true")));
                Map<String, Object> input = obj("code", pm.code(), "enabled", true);
                input.put("handler", handler);
                input.put("translations", List.of(obj("languageCode", "de", "name", pm.name(), "description", pm.desc())));
                m("createPaymentMethod", "CreatePaymentMethodInput!", input, field("id"));
                add("Zahlart " + pm.code());
            }
        }

        // ── Platzhalter-Assets (best-effort) ──

        private Assets ensureAssets() {
            try {
                String imageId = ensureAsset(VendureAssetApi.PLATZHALTER_BILD, "shop-assets/platzhalter.png", true);
                String pdfId = ensureAsset(VendureAssetApi.PLATZHALTER_PDF, "shop-assets/platzhalter.pdf", false);
                return new Assets(imageId, pdfId);
            } catch (RuntimeException e) {
                LOG.warn("Asset-Upload übersprungen (Platzhalter nicht verfügbar): " + e.getMessage());
                log.add("⚠ Assets übersprungen: " + e.getMessage());
                return new Assets(null, null);
            }
        }

        private String ensureAsset(String dateiname, String resource, boolean bild) {
            // Bestehendes Asset gleichen Namens wiederverwenden (Idempotenz).
            JsonObject d = q(field("assets", List.of(arg("options", inputObject(prop("take", 200)))),
                    field("items", field("id"), field("name"))));
            for (JsonValue jv : d.getJsonObject("assets").getJsonArray("items")) {
                JsonObject a = jv.asJsonObject();
                if (dateiname.equals(a.getString("name"))) {
                    skip("Asset " + dateiname);
                    return a.getString("id");
                }
            }
            byte[] bytes = ladeRessource(resource);
            String id = bild ? uploader.uploadBild(token, bytes) : uploader.uploadPdf(token, bytes);
            add("Asset " + dateiname);
            return id;
        }

        private byte[] ladeRessource(String resource) {
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new VendureException("Platzhalter-Ressource fehlt: " + resource);
                }
                return in.readAllBytes();
            } catch (java.io.IOException e) {
                throw new VendureException("Platzhalter-Datei konnte nicht gelesen werden: " + e.getMessage(), e);
            }
        }

        // ── CRM-Personen (Upsert; Schlüssel crmPersonId) → crmPersonId → Vendure-ID ──

        private Map<String, String> ensurePersonen(String imageId) {
            Map<String, String> ids = new LinkedHashMap<>();
            List<String> apVorhanden = crmIds("ansprechpartner");
            for (Person p : KatalogBeispiele.ANSPRECHPARTNER) {
                Map<String, Object> input = obj("crmPersonId", p.crmPersonId(), "name", p.name(),
                        "email", p.email(), "telefon", p.telefon());
                if (imageId != null) {
                    input.put("fotoAssetId", imageId);
                }
                String id = m("upsertAnsprechpartner", "UpsertAnsprechpartnerInput!", input, field("id"))
                        .getJsonObject("upsertAnsprechpartner").getString("id");
                ids.put(p.crmPersonId(), id);
                count("Ansprechpartner " + p.name(), apVorhanden.contains(p.crmPersonId()));
            }
            List<String> dozVorhanden = crmIds("dozenten");
            for (Referent r : KatalogBeispiele.DOZENTEN) {
                Map<String, Object> input = obj("crmPersonId", r.crmPersonId(), "name", r.name(), "vita", r.vita());
                if (imageId != null) {
                    input.put("fotoAssetId", imageId);
                }
                String id = m("upsertDozent", "UpsertDozentInput!", input, field("id"))
                        .getJsonObject("upsertDozent").getString("id");
                ids.put(r.crmPersonId(), id);
                count("Dozent " + r.name(), dozVorhanden.contains(r.crmPersonId()));
            }
            return ids;
        }

        private List<String> crmIds(String query) {
            List<String> ids = new ArrayList<>();
            for (JsonValue jv : q(field(query, field("crmPersonId"))).getJsonArray(query)) {
                ids.add(jv.asJsonObject().getString("crmPersonId"));
            }
            return ids;
        }

        // ── Beispielprodukte (zwei Pässe wegen Querverweisen) ──

        private void ensureProdukte(Map<Steuer, String> tax, Map<String, String> facetValues,
                Map<String, String> personen, Assets assets) {
            // Bestand: slug → {id, hatVarianten}
            Map<String, JsonObject> existing = new LinkedHashMap<>();
            for (JsonValue jv : q(field("products", List.of(arg("options", inputObject(prop("take", 500)))),
                    field("items", field("id"), field("slug"), field("variants", field("id")))))
                    .getJsonObject("products").getJsonArray("items")) {
                JsonObject p = jv.asJsonObject();
                existing.put(p.getString("slug"), p);
            }

            Map<String, String> slugToId = new LinkedHashMap<>();
            for (Produkt p : KatalogBeispiele.PRODUKTE) {
                JsonObject vorhanden = existing.get(p.slug());
                Map<String, Object> cf = produktCustomFields(p, personen);
                List<String> fvIds = produktFacetValueIds(p, facetValues);

                Map<String, Object> input = new LinkedHashMap<>();
                input.put("enabled", true);
                input.put("translations", List.of(obj("languageCode", "de", "name", p.name(),
                        "slug", p.slug(), "description", nz(p.kurzbeschreibung()))));
                input.put("facetValueIds", fvIds);
                if (p.bildDatei() != null && assets.imageId() != null) {
                    input.put("featuredAssetId", assets.imageId());
                }
                input.put("customFields", cf);

                String productId;
                if (vorhanden == null) {
                    productId = m("createProduct", "CreateProductInput!", input, field("id"))
                            .getJsonObject("createProduct").getString("id");
                    add("Produkt " + p.angebotsnummer());
                } else {
                    productId = vorhanden.getString("id");
                    input.put("id", productId);
                    m("updateProduct", "UpdateProductInput!", input, field("id"));
                    aktualisiert++;
                    log.add("~ Produkt " + p.angebotsnummer());
                }
                slugToId.put(p.slug(), productId);

                boolean hatVarianten = vorhanden != null && !vorhanden.getJsonArray("variants").isEmpty();
                if (!hatVarianten) {
                    ensureVarianten(productId, p, tax);
                } else {
                    skip("Varianten " + p.angebotsnummer());
                }
                ensureBewertungen(productId, p);
            }

            // Pass 2: Querverweise (verwandteProdukte) jetzt, da alle Slugs bekannt sind.
            for (Produkt p : KatalogBeispiele.PRODUKTE) {
                if (p.verwandteSlugs() == null || p.verwandteSlugs().isEmpty()) {
                    continue;
                }
                List<String> ids = new ArrayList<>();
                for (String slug : p.verwandteSlugs()) {
                    String id = slugToId.get(slug);
                    if (id != null) {
                        ids.add(id);
                    }
                }
                if (ids.isEmpty()) {
                    continue;
                }
                Map<String, Object> cf = new LinkedHashMap<>();
                cf.put("verwandteProdukteIds", ids);
                Map<String, Object> input = obj("id", slugToId.get(p.slug()));
                input.put("customFields", cf);
                m("updateProduct", "UpdateProductInput!", input, field("id"));
            }
        }

        private void ensureVarianten(String productId, Produkt p, Map<Steuer, String> tax) {
            String taxId = tax.get(p.steuer());
            List<Durchfuehrung> dfs = p.durchfuehrungen();

            if (dfs.isEmpty()) {
                // Vertragsangebot: eine 0-€-„Info"-Variante, damit es im Katalog/Suche sichtbar ist
                // (Storefront blendet den Warenkorb über customFields.bestellbar=false aus).
                Map<String, Object> variant = obj("productId", productId, "sku", p.angebotsnummer(),
                        "price", 0L, "taxCategoryId", taxId);
                variant.put("translations", List.of(obj("languageCode", "de", "name", p.name())));
                variant.put("optionIds", List.of());
                variant.put("customFields", obj("fulfillmentType", "seminar"));
                m("createProductVariants", "[CreateProductVariantInput!]!", List.of(variant), field("id"));
                add("Variante (Vertrag) " + p.angebotsnummer());
                return;
            }

            Map<String, String> optionIdBySku = new LinkedHashMap<>();
            if (dfs.size() > 1) {
                // OptionGroup „Termin" mit je einer Option pro Durchführung (Pflicht bei >1 Variante).
                List<Object> options = new ArrayList<>();
                for (Durchfuehrung df : dfs) {
                    options.add(obj("code", df.sku().toLowerCase(Locale.ROOT),
                            "translations", List.of(obj("languageCode", "de", "name", terminLabel(df)))));
                }
                Map<String, Object> groupInput = obj("code", "termin-" + p.slug());
                groupInput.put("translations", List.of(obj("languageCode", "de", "name", "Termin")));
                groupInput.put("options", options);
                JsonObject group = m("createProductOptionGroup", "CreateProductOptionGroupInput!", groupInput,
                        field("id"), field("options", field("id"), field("code"))).getJsonObject("createProductOptionGroup");
                String groupId = group.getString("id");
                for (JsonValue jv : group.getJsonArray("options")) {
                    JsonObject o = jv.asJsonObject();
                    optionIdBySku.put(o.getString("code"), o.getString("id"));
                }
                // Gruppe dem Produkt zuordnen (2 Argumente).
                Variable pid = var("productId", VendureAdmin.vt("ID!"));
                Variable gid = var("optionGroupId", VendureAdmin.vt("ID!"));
                vendure.fuehreAus(client, document(operation(OperationType.MUTATION, List.of(pid, gid),
                        field("addOptionGroupToProduct", List.of(arg("productId", pid), arg("optionGroupId", gid)),
                                field("id")))),
                        Map.of("productId", productId, "optionGroupId", groupId));
            }

            List<Object> variants = new ArrayList<>();
            for (Durchfuehrung df : dfs) {
                Map<String, Object> variant = obj("productId", productId, "sku", df.sku(),
                        "price", df.preisCent(), "taxCategoryId", taxId);
                variant.put("translations", List.of(obj("languageCode", "de", "name", p.name() + " — " + terminLabel(df))));
                String optId = optionIdBySku.get(df.sku().toLowerCase(Locale.ROOT));
                variant.put("optionIds", optId == null ? List.of() : List.of(optId));
                Map<String, Object> vcf = obj("fulfillmentType", "seminar",
                        "terminDatum", df.terminIso(), "ort", df.ort(), "veranstaltungsformat", df.format().name());
                variant.put("customFields", vcf);
                variants.add(variant);
            }
            m("createProductVariants", "[CreateProductVariantInput!]!", variants, field("id"));
            add("Varianten " + p.angebotsnummer() + " (" + variants.size() + ")");
        }

        private void ensureBewertungen(String productId, Produkt p) {
            for (KatalogBeispiele.Rezension r : p.rezensionen()) {
                Map<String, Object> input = obj("productId", productId, "autor", r.autor(),
                        "text", r.text(), "sterne", r.sterne(), "datum", r.datumIso() + "T00:00:00.000Z");
                m("upsertBewertung", "UpsertBewertungInput!", input, field("id"));
            }
        }

        // ── F1–F6 Demoprodukte (Ablösung seed.mjs; identische Slugs/SKUs) ──

        private void ensureDemoF1F6(Map<Steuer, String> tax) {
            String taxId = tax.get(Steuer.STANDARD);
            Map<String, JsonObject> existing = new LinkedHashMap<>();
            for (JsonValue jv : q(field("products", List.of(arg("options", inputObject(prop("take", 500)))),
                    field("items", field("id"), field("slug"), field("variants", field("id"), field("name")))))
                    .getJsonObject("products").getJsonArray("items")) {
                JsonObject p = jv.asJsonObject();
                existing.put(p.getString("slug"), p);
            }
            for (DemoProdukt d : DEMO) {
                JsonObject vorhanden = existing.get(d.slug());
                String productId;
                if (vorhanden == null) {
                    Map<String, Object> input = obj("enabled", true);
                    input.put("translations", List.of(
                            obj("languageCode", "en", "name", d.name(), "slug", d.slug(), "description", d.desc()),
                            obj("languageCode", "de", "name", d.name(), "slug", d.slug(), "description", d.desc())));
                    productId = m("createProduct", "CreateProductInput!", input, field("id"))
                            .getJsonObject("createProduct").getString("id");
                    add("Demoprodukt " + d.slug());
                } else {
                    productId = vorhanden.getString("id");
                    skip("Demoprodukt " + d.slug());
                }
                boolean hatVarianten = vorhanden != null && !vorhanden.getJsonArray("variants").isEmpty();
                if (!hatVarianten) {
                    Map<String, Object> variant = obj("productId", productId, "sku", d.sku(),
                            "price", d.price(), "taxCategoryId", taxId, "stockOnHand", d.stock(),
                            "trackInventory", d.track());
                    variant.put("translations", List.of(obj("languageCode", "en", "name", d.name())));
                    variant.put("optionIds", List.of());
                    Map<String, Object> vcf = obj("fulfillmentType", d.fulfillmentType());
                    if (d.interval() != null) {
                        vcf.put("subscriptionInterval", d.interval());
                        vcf.put("subscriptionIntervalCount", d.intervalCount());
                        vcf.put("subscriptionTotalCount", d.totalCount());
                    }
                    variant.put("customFields", vcf);
                    m("createProductVariants", "[CreateProductVariantInput!]!", List.of(variant),
                            field("id"), field("name"));
                    add("Demovariante " + d.sku());
                } else {
                    skip("Demovariante " + d.sku());
                }
            }
            ensureSeminarKosten();
        }

        private void ensureSeminarKosten() {
            // Variante des Tagesseminars (F3) finden.
            JsonObject d = q(field("products", List.of(arg("options", inputObject(prop("take", 500)))),
                    field("items", field("slug"), field("variants", field("id")))));
            String variantId = null;
            for (JsonValue jv : d.getJsonObject("products").getJsonArray("items")) {
                JsonObject p = jv.asJsonObject();
                if ("tagesseminar-mietrecht-aktuell".equals(p.getString("slug")) && !p.getJsonArray("variants").isEmpty()) {
                    variantId = p.getJsonArray("variants").getJsonObject(0).getString("id");
                }
            }
            if (variantId == null) {
                return;
            }
            Variable vid = var("vid", VendureAdmin.vt("ID!"));
            JsonObject sc = vendure.fuehreAus(client,
                    document(operation(OperationType.QUERY, List.of(vid),
                            field("seminarCosts", List.of(arg("productVariantId", vid)), field("costType")))),
                    Map.of("vid", variantId));
            List<String> have = new ArrayList<>();
            for (JsonValue jv : sc.getJsonArray("seminarCosts")) {
                have.add(jv.asJsonObject().getString("costType"));
            }
            record Kosten(String typ, String label, int amount, boolean variabel, boolean jeTN) {
            }
            List<Kosten> kosten = List.of(
                    new Kosten("dozent", "Dozentenhonorar", 120000, false, false),
                    new Kosten("raum", "Raummiete", 60000, false, false),
                    new Kosten("material", "Seminarunterlagen", 1500, true, true),
                    new Kosten("catering", "Tagungsverpflegung", 1000, true, true));
            for (Kosten k : kosten) {
                if (have.contains(k.typ())) {
                    skip("Seminarkosten " + k.typ());
                    continue;
                }
                Map<String, Object> input = obj("productVariantId", variantId, "costType", k.typ(),
                        "label", k.label(), "amount", k.amount(), "currencyCode", "EUR",
                        "isVariable", k.variabel(), "perParticipant", k.jeTN());
                m("createSeminarCost", "CreateSeminarCostInput!", input, field("id"));
                add("Seminarkosten " + k.typ());
            }
        }

        // ── Reindex ──

        private void ensureContentPages() {
            // Redaktionelle Seiten (CMS, P6) — idempotent über slug. imMenu-Seiten erscheinen im
            // Burger-/Hauptmenü der Storefront (nach menuSortierung).
            record CP(String slug, String titel, String html, boolean imMenu, int sort) {
            }
            List<CP> pages = List.of(
                    new CP("ueber-uns", "Über die EBZ Akademie",
                            "<p>Die EBZ Akademie ist der Bildungsdienstleister der Immobilienwirtschaft — "
                                    + "Seminare, Lehrgänge, Tagungen und Studiengänge aus einer Hand.</p>", true, 10),
                    new CP("kontakt", "Kontakt",
                            "<p>EBZ Akademie · Springorumallee 20 · 44795 Bochum<br>"
                                    + "Telefon: 0234 9447-0 · E-Mail: akademie@ebz-training.de</p>", true, 20),
                    new CP("agb", "Allgemeine Geschäftsbedingungen",
                            "<p>Es gelten die Allgemeinen Geschäftsbedingungen der EBZ Akademie (Showcase-Platzhalter).</p>",
                            false, 90));
            List<String> have = new ArrayList<>();
            for (JsonValue jv : q(field("contentPages", field("slug"))).getJsonArray("contentPages")) {
                have.add(jv.asJsonObject().getString("slug"));
            }
            for (CP cp : pages) {
                Map<String, Object> input = obj("slug", cp.slug(), "titel", cp.titel(), "inhaltHtml", cp.html(),
                        "published", true, "imMenu", cp.imMenu(), "menuSortierung", cp.sort());
                m("upsertContentPage", "UpsertContentPageInput!", input, field("id"));
                if (have.contains(cp.slug())) {
                    aktualisiert++;
                    log.add("• CMS-Seite " + cp.slug() + " (aktualisiert)");
                } else {
                    add("CMS-Seite " + cp.slug());
                }
            }
        }

        private void ensureFruehbucherPromotion() {
            // Frühbucher (P7): 10 % Rabatt, wenn der Termin (Varianten-Custom-Field terminDatum)
            // mind. 30 Tage in der Zukunft liegt — eigene Condition `fruehbucher` + eingebaute
            // Aktion order_percentage_discount. Idempotent über den Promotion-Namen.
            String name = "Frühbucherrabatt 10 %";
            for (JsonValue jv : q(field("promotions", List.of(arg("options", inputObject(prop("take", 100)))),
                    field("items", field("name")))).getJsonObject("promotions").getJsonArray("items")) {
                if (name.equals(jv.asJsonObject().getString("name"))) {
                    skip("Promotion Frühbucher");
                    return;
                }
            }
            Map<String, Object> bedingung = obj("code", "fruehbucher");
            bedingung.put("arguments", List.of(obj("name", "daysBefore", "value", "30")));
            Map<String, Object> aktion = obj("code", "order_percentage_discount");
            aktion.put("arguments", List.of(obj("name", "discount", "value", "10")));
            Map<String, Object> input = obj("enabled", true);
            input.put("conditions", List.of(bedingung));
            input.put("actions", List.of(aktion));
            input.put("translations", List.of(obj("languageCode", "de", "name", name,
                    "description", "10 % Rabatt bei Buchung mind. 30 Tage vor Veranstaltungsbeginn")));
            // createPromotion liefert eine Union (Promotion | MissingConditionsError) → __typename selektieren.
            JsonObject d = m("createPromotion", "CreatePromotionInput!", input, field("__typename"));
            String typ = d.getJsonObject("createPromotion").getString("__typename");
            if (!"Promotion".equals(typ)) {
                throw new VendureException("Frühbucher-Promotion abgelehnt: " + typ);
            }
            add("Promotion Frühbucher");
        }

        private void reindex() {
            // reindex liefert einen Job (asynchron) — nur die Job-ID selektieren.
            vendure.fuehreAus(client,
                    document(operation(OperationType.MUTATION, field("reindex", field("id")))), Map.of());
            log.add("• Search-Reindex angestoßen");
        }

        // ── Bausteine ──

        private Map<String, Object> produktCustomFields(Produkt p, Map<String, String> personen) {
            Map<String, Object> cf = new LinkedHashMap<>();
            put(cf, "angebotsnummer", p.angebotsnummer());
            put(cf, "inhalteHtml", leerNull(p.texte().inhalte()));
            put(cf, "lernzieleHtml", leerNull(p.texte().lernziele()));
            put(cf, "nutzenHtml", leerNull(p.texte().nutzen()));
            put(cf, "methodikHtml", leerNull(p.texte().methodik()));
            put(cf, "voraussetzungenHtml", leerNull(p.texte().voraussetzungen()));
            put(cf, "foerderhinweisHtml", leerNull(p.texte().foerderhinweis()));
            put(cf, "ablaufHtml", leerNull(p.texte().ablauf()));
            put(cf, "leistungenHtml", leerNull(p.texte().leistungen()));
            put(cf, "faqHtml", leerNull(p.texte().faq()));
            put(cf, "zielgruppe", p.zielgruppe());
            put(cf, "abschluss", p.abschluss());
            put(cf, "dauerUE", p.dauerUE());
            put(cf, "studienform", p.studienform());
            put(cf, "regelstudienzeitSemester", p.regelstudienzeitSemester());
            put(cf, "akkreditierungBis", p.akkreditierungBisIso());
            cf.put("bestellbar", p.bestellbar());
            put(cf, "anmeldungUrl", p.anmeldungUrl());
            String apId = personen.get(p.ansprechpartnerCrmId());
            put(cf, "ansprechpartnerId", apId);
            if (p.dozentCrmIds() != null && !p.dozentCrmIds().isEmpty()) {
                List<String> dozIds = new ArrayList<>();
                for (String crm : p.dozentCrmIds()) {
                    String id = personen.get(crm);
                    if (id != null) {
                        dozIds.add(id);
                    }
                }
                if (!dozIds.isEmpty()) {
                    cf.put("dozentenIds", dozIds);
                }
            }
            return cf;
        }

        private List<String> produktFacetValueIds(Produkt p, Map<String, String> facetValues) {
            List<String> ids = new ArrayList<>();
            addFv(ids, facetValues, "veranstaltungsart", p.veranstaltungsart());
            addFv(ids, facetValues, "thema", p.thema());
            addFv(ids, facetValues, "branche", p.branche());
            addFv(ids, facetValues, "region", p.region());
            for (Durchfuehrung df : p.durchfuehrungen()) {
                addFv(ids, facetValues, "format", df.format().name());
            }
            return ids;
        }

        private void addFv(List<String> ids, Map<String, String> facetValues, String facet, String wert) {
            if (wert == null) {
                return;
            }
            String id = facetValues.get(facet + ":" + slug(wert));
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }

        // ── GraphQL-Helfer ──

        /** QUERY ohne Variablen über die übergebenen Root-Felder. */
        private JsonObject q(Field... roots) {
            return vendure.fuehreAus(client, document(operation(OperationType.QUERY, roots)), Map.of());
        }

        /** MUTATION mit genau einer {@code $input}-Variable vom angegebenen GraphQL-Typ. */
        private JsonObject m(String op, String inputType, Object inputValue, Field... selection) {
            Variable v = var("input", VendureAdmin.vt(inputType));
            return vendure.fuehreAus(client,
                    document(operation(OperationType.MUTATION, List.of(v),
                            field(op, List.of(arg("input", v)), selection))),
                    Map.of("input", inputValue));
        }

        // ── Zähler/Log ──
        private void add(String was) {
            angelegt++;
            log.add("+ " + was);
        }

        private void skip(String was) {
            uebersprungen++;
            log.add("= " + was);
        }

        private void count(String was, boolean vorhanden) {
            if (vorhanden) {
                aktualisiert++;
                log.add("~ " + was);
            } else {
                add(was);
            }
        }
    }

    // ───────────────────────── statische Helfer & Daten ─────────────────────────

    private record Assets(String imageId, String pdfId) {
    }

    /** F1–F6-Demoprodukte (übernommen aus seed.mjs; identische Slugs/SKUs → Smokes bleiben grün). */
    private record DemoProdukt(String slug, String name, String desc, String sku, long price, int stock,
            String track, String fulfillmentType, String interval, Integer intervalCount, Integer totalCount) {
    }

    private static final List<DemoProdukt> DEMO = List.of(
            new DemoProdukt("fachbuch-immobilienbewertung", "Fachbuch: Immobilienbewertung kompakt",
                    "Gedrucktes Fachbuch, wird per Post versendet (Showcase F1 — physische Ware).",
                    "BOOK-IMMO-01", 4990, 100, "TRUE", "physical", null, null, null),
            new DemoProdukt("skript-maklerrecht-download", "Skript: Maklerrecht (PDF-Download)",
                    "Digitales Skript als Download, kein Versand (Showcase F2 — Digitalprodukt).",
                    "DL-MAKLER-01", 1500, 0, "FALSE", "digital", null, null, null),
            new DemoProdukt("tagesseminar-mietrecht-aktuell", "Tagesseminar: Mietrecht aktuell",
                    "Eintägiges Präsenzseminar mit begrenzter Teilnehmerzahl (Showcase F3 — Seminar; Teilnehmerdaten je Position).",
                    "SEM-MIET-2026-09", 19000, 20, "TRUE", "seminar", null, null, null),
            new DemoProdukt("abo-veranstaltungsreihe", "Abo: Veranstaltungsreihe (monatlich)",
                    "Monatliches Abo für eine wiederkehrende Veranstaltungsreihe (Showcase F4 — unbefristet).",
                    "ABO-REIHE-01", 2900, 0, "FALSE", "subscription", "month", 1, 0),
            new DemoProdukt("berufsschule-halbjahresbeitrag", "Berufsschule: Halbjahresbeitrag",
                    "Halbjährliche Schulgebühr über die Ausbildung (Showcase F5 — 6 Raten alle 6 Monate, 3 Jahre).",
                    "BS-HALBJAHR-01", 60000, 0, "FALSE", "subscription", "month", 6, 6),
            new DemoProdukt("studiengang-monatsrate", "Studiengang: Monatsrate (36 Monate)",
                    "Monatliche Studiengebühr über 3 Jahre (Showcase F6 — 36 Monatsraten, festes Ende).",
                    "STUD-MONAT-01", 30000, 0, "FALSE", "subscription", "month", 1, 36));

    /** {@code code} → URL-sicherer Wert-Code (lowercase, nur a–z0–9-). */
    private static String slug(String s) {
        String x = s.toLowerCase(Locale.GERMAN)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        x = x.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return x;
    }

    private static String titel(String slugVal) {
        String s = slugVal.replace('-', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String facetName(String code) {
        return switch (code) {
            case "veranstaltungsart" -> "Veranstaltungsart";
            case "thema" -> "Thema";
            case "branche" -> "Branche";
            case "region" -> "Region";
            case "format" -> "Format";
            default -> code;
        };
    }

    private static String terminLabel(Durchfuehrung df) {
        String datum = df.terminIso().length() >= 10 ? df.terminIso().substring(0, 10) : df.terminIso();
        return datum + " · " + df.ort();
    }

    private static void addVal(Map<String, String> werte, String anzeige) {
        if (anzeige != null && !anzeige.isBlank()) {
            werte.putIfAbsent(slug(anzeige), anzeige);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String leerNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static void put(Map<String, Object> m, String k, Object v) {
        if (v != null) {
            m.put(k, v);
        }
    }

    private static String str(JsonObject o, String k) {
        return o.containsKey(k) && !o.isNull(k) ? o.getString(k) : null;
    }

    /** Baut eine LinkedHashMap aus Schlüssel/Wert-Paaren (null-Werte werden übersprungen). */
    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                m.put((String) kv[i], kv[i + 1]);
            }
        }
        return m;
    }
}
