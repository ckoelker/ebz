package de.netzfactor.ebz.controlling.integration.bildung.vendure;

import static io.smallrye.graphql.client.core.Argument.arg;
import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.Operation.operation;
import static io.smallrye.graphql.client.core.Variable.var;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;
import de.netzfactor.ebz.controlling.integration.shop.VendureAdmin;
import io.smallrye.graphql.client.core.OperationType;
import io.smallrye.graphql.client.core.Variable;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;

/**
 * Verknüpft ein Bildungsangebot über die <b>Nummer</b> mit dem Shop (Produktkatalog P1, §C —
 * SoR-Umkehr): Vendure ist alleinige Quelle des Katalog-/Detailinhalts, der MDM-Kern hält nur die
 * Nummer. Diese Klasse <i>schreibt kein Produkt/keine Variante mehr</i> (kein Content-/Preis-Push),
 * sondern sucht das passende Produkt per Custom-Field {@code angebotsnummer == code} bzw. die Variante
 * per {@code sku == code} und liefert die Vendure-IDs zurück. Pflege der Inhalte erfolgt im
 * Java-Initializer ({@code ShopInitService}) / in Vendure.
 * <p>
 * Transport: {@link VendureAdmin} (SmallRye Dynamic Client, Superadmin-Login).
 */
@ApplicationScoped
public class VendureProjektion {

    /** Rückgabe der Verknüpfung: die gefundenen Vendure-IDs ({@code null} = kein Treffer). */
    public record Ergebnis(String productId, String variantId) {
    }

    @Inject
    VendureAdmin vendure;

    /** Sucht Produkt/Variante per Nummer und liefert die Vendure-IDs (kein Treffer → {@code null}/{@code null}). */
    public Ergebnis projiziere(Bildungsangebot e) {
        VendureAdmin.Verbindung v = vendure.anmelden();
        try (DynamicGraphQLClient client = v.client()) {
            // 1) Produkt per Custom-Field angebotsnummer == code.
            Variable opt = var("options", VendureAdmin.vt("ProductListOptions"));
            JsonObject d = vendure.fuehreAus(client,
                    document(operation(OperationType.QUERY, List.of(opt),
                            field("products", List.of(arg("options", opt)),
                                    field("items", field("id"), field("variants", field("id"), field("sku")))))),
                    Map.of("options", Map.of("filter", Map.of("angebotsnummer", Map.of("eq", e.code)), "take", 1)));
            JsonArray items = d.getJsonObject("products").getJsonArray("items");
            if (!items.isEmpty()) {
                JsonObject p = items.getJsonObject(0);
                String productId = p.getString("id");
                String variantId = variantPassend(p.getJsonArray("variants"), e.code);
                return new Ergebnis(productId, variantId);
            }

            // 2) Sonst Variante per sku == code (Produkt-ID daraus).
            Variable vopt = var("options", VendureAdmin.vt("ProductVariantListOptions"));
            JsonObject dv = vendure.fuehreAus(client,
                    document(operation(OperationType.QUERY, List.of(vopt),
                            field("productVariants", List.of(arg("options", vopt)),
                                    field("items", field("id"), field("productId"))))),
                    Map.of("options", Map.of("filter", Map.of("sku", Map.of("eq", e.code)), "take", 1)));
            JsonArray vitems = dv.getJsonObject("productVariants").getJsonArray("items");
            if (!vitems.isEmpty()) {
                JsonObject variant = vitems.getJsonObject(0);
                return new Ergebnis(variant.getString("productId"), variant.getString("id"));
            }

            return new Ergebnis(null, null);
        } catch (VendureException ve) {
            throw ve;
        } catch (Exception ex) {
            throw new VendureException("Vendure-Verknüpfung fehlgeschlagen: " + ex.getMessage(), ex);
        }
    }

    /** Variante, deren SKU der Nummer entspricht; sonst die erste; sonst {@code null}. */
    private static String variantPassend(JsonArray variants, String code) {
        String erste = null;
        for (int i = 0; i < variants.size(); i++) {
            JsonObject vr = variants.getJsonObject(i);
            if (erste == null) {
                erste = vr.getString("id");
            }
            if (code.equals(vr.getString("sku"))) {
                return vr.getString("id");
            }
        }
        return erste;
    }
}
