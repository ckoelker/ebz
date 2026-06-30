package de.netzfactor.ebz.controlling.integration.shop;

import static io.smallrye.graphql.client.core.Argument.arg;
import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.Operation.operation;
import static io.smallrye.graphql.client.core.Variable.var;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.transaction.Transactional;

import io.smallrye.graphql.client.core.OperationType;
import io.smallrye.graphql.client.core.Variable;

import de.netzfactor.ebz.controlling.integration.party.model.Mitarbeiter;
import de.netzfactor.ebz.controlling.integration.shop.VendureAdmin.Verbindung;

/**
 * CRM→Vendure-Personen-Sync (P7b + Foto). CRM (Party-Kern) bleibt Personen-SoR; Vendure hält nur eine
 * projizierte Kopie als Custom-Entity {@code Ansprechpartner} (Schlüssel {@code crmPersonId}).
 * Das Profilfoto ({@link Mitarbeiter#foto}) wird als Vendure-Asset hochgeladen und am Ansprechpartner
 * verlinkt — <b>idempotent</b> über einen Foto-Hash: nur geänderte Fotos werden neu hochgeladen.
 * Wird periodisch über {@link CrmVendureSyncScheduler} ausgeführt.
 */
@ApplicationScoped
public class CrmVendureSyncService {

    @Inject
    VendureAdmin vendure;

    @Inject
    VendureAssetUploader uploader;

    /** Ergebnis des Sync-Laufs. */
    public record Ergebnis(int gesendet, int fotosHochgeladen, List<String> log) {
    }

    /** Mitarbeiter-Projektion (entkoppelt von der JPA-Session). */
    private record MA(String crmPersonId, String name, String email, byte[] foto) {
    }

    public Ergebnis syncAnsprechpartner() {
        List<MA> mitarbeiter = ladeAktiveMitarbeiter();
        Verbindung v = vendure.anmelden();
        Map<String, String> bestehendeHashes = ladeFotoHashes(v);
        List<String> log = new ArrayList<>();
        int fotos = 0;
        for (MA m : mitarbeiter) {
            String neuerHash = m.foto() != null && m.foto().length > 0 ? sha256(m.foto()) : null;
            boolean fotoNeu = neuerHash != null && !neuerHash.equals(bestehendeHashes.get(m.crmPersonId()));
            String fotoAssetId = null;
            if (fotoNeu) {
                fotoAssetId = uploader.uploadBild(v.token(), m.foto());
                fotos++;
            }
            upsertAnsprechpartner(v, m, fotoNeu, fotoAssetId, neuerHash);
            log.add("→ Ansprechpartner " + m.name() + (fotoNeu ? " (Foto aktualisiert)" : ""));
        }
        return new Ergebnis(mitarbeiter.size(), fotos, log);
    }

    /** DB-Lesezugriff in eigener Transaktion; sofort in Records entkoppeln (keine externen Calls in der TX). */
    @Transactional
    List<MA> ladeAktiveMitarbeiter() {
        List<MA> out = new ArrayList<>();
        for (Mitarbeiter m : Mitarbeiter.<Mitarbeiter>list("aktiv", true)) {
            // Stabiler Schlüssel = Keycloak-sub (Identitäts-Anker des Mitarbeiters); Fallback auf die DB-Id.
            String key = m.keycloakSub != null && !m.keycloakSub.isBlank() ? m.keycloakSub : "mitarbeiter-" + m.id;
            out.add(new MA(key, m.anzeigeName, m.email, m.foto));
        }
        return out;
    }

    /** Aktuelle Ansprechpartner inkl. fotoHash → Map crmPersonId→fotoHash (für Idempotenz). */
    private Map<String, String> ladeFotoHashes(Verbindung v) {
        Map<String, String> map = new LinkedHashMap<>();
        JsonObject d = vendure.fuehreAus(v.client(),
                document(operation(OperationType.QUERY,
                        field("ansprechpartner", field("crmPersonId"), field("fotoHash")))),
                Map.of());
        for (JsonValue jv : d.getJsonArray("ansprechpartner")) {
            JsonObject ap = jv.asJsonObject();
            if (!ap.isNull("fotoHash")) {
                map.put(ap.getString("crmPersonId"), ap.getString("fotoHash"));
            }
        }
        return map;
    }

    private void upsertAnsprechpartner(Verbindung v, MA m, boolean fotoSetzen, String fotoAssetId, String fotoHash) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("crmPersonId", m.crmPersonId());
        input.put("name", m.name());
        if (m.email() != null && !m.email().isBlank()) {
            input.put("email", m.email());
        }
        // Foto-Felder nur senden, wenn das Foto neu/aktualisiert ist (sonst bewahrt der Service das Bild).
        if (fotoSetzen) {
            input.put("fotoAssetId", fotoAssetId);
            input.put("fotoHash", fotoHash);
        }
        Variable var = var("input", VendureAdmin.vt("UpsertAnsprechpartnerInput!"));
        vendure.fuehreAus(v.client(),
                document(operation(OperationType.MUTATION, List.of(var),
                        field("upsertAnsprechpartner", List.of(arg("input", var)), field("id")))),
                Map.of("input", input));
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
