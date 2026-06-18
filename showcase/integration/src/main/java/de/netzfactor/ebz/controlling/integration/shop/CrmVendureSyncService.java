package de.netzfactor.ebz.controlling.integration.shop;

import static io.smallrye.graphql.client.core.Argument.arg;
import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.Operation.operation;
import static io.smallrye.graphql.client.core.Variable.var;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.smallrye.graphql.client.core.OperationType;
import io.smallrye.graphql.client.core.Variable;

import de.netzfactor.ebz.controlling.integration.party.model.Mitarbeiter;
import de.netzfactor.ebz.controlling.integration.shop.VendureAdmin.Verbindung;

/**
 * CRM→Vendure-Personen-Sync (P7b). CRM (Party-Kern) bleibt Personen-SoR; Vendure hält nur eine
 * projizierte Kopie als Custom-Entity {@code Ansprechpartner} (Schlüssel {@code crmPersonId}).
 * So wirkt eine Namens-/E-Mail-Änderung an EINER Stelle (CRM) über die Produkt-Relationen auf alle
 * verknüpften Produkte. Quelle = aktive EBZ-{@link Mitarbeiter} (Nutzer-Entscheidung: Mitarbeiter =
 * Ansprechpartner). Dozenten bleiben vorerst Seed-gepflegt.
 */
@ApplicationScoped
public class CrmVendureSyncService {

    @Inject
    VendureAdmin vendure;

    /** Ergebnis des Sync-Laufs. */
    public record Ergebnis(int gesendet, List<String> log) {
    }

    /** Mitarbeiter-Projektion (entkoppelt von der JPA-Session). */
    private record MA(String crmPersonId, String name, String email) {
    }

    public Ergebnis syncAnsprechpartner() {
        List<MA> mitarbeiter = ladeAktiveMitarbeiter();
        Verbindung v = vendure.anmelden();
        List<String> log = new ArrayList<>();
        for (MA m : mitarbeiter) {
            upsertAnsprechpartner(v, m);
            log.add("→ Ansprechpartner " + m.name() + " (" + m.crmPersonId() + ")");
        }
        return new Ergebnis(mitarbeiter.size(), log);
    }

    /** DB-Lesezugriff in eigener Transaktion; sofort in Records entkoppeln (keine externen Calls in der TX). */
    @Transactional
    List<MA> ladeAktiveMitarbeiter() {
        List<MA> out = new ArrayList<>();
        for (Mitarbeiter m : Mitarbeiter.<Mitarbeiter>list("aktiv", true)) {
            out.add(new MA("mitarbeiter-" + m.id, m.anzeigeName, m.email));
        }
        return out;
    }

    private void upsertAnsprechpartner(Verbindung v, MA m) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("crmPersonId", m.crmPersonId());
        input.put("name", m.name());
        if (m.email() != null && !m.email().isBlank()) {
            input.put("email", m.email());
        }
        Variable var = var("input", VendureAdmin.vt("UpsertAnsprechpartnerInput!"));
        vendure.fuehreAus(v.client(),
                document(operation(OperationType.MUTATION, List.of(var),
                        field("upsertAnsprechpartner", List.of(arg("input", var)), field("id")))),
                Map.of("input", input));
    }
}
