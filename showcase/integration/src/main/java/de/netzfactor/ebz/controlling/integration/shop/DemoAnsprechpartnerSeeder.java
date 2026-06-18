package de.netzfactor.ebz.controlling.integration.shop;

import java.io.InputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

import de.netzfactor.ebz.controlling.integration.party.model.Mitarbeiter;

/**
 * Seedet beim Start idempotent je {@link KatalogBeispiele#ANSPRECHPARTNER} einen aktiven
 * EBZ-{@link Mitarbeiter} <b>mit Profilfoto</b>. So existiert eine reale CRM-Quelle für den
 * {@link CrmVendureSyncService CRM→Vendure-Personen-Sync}: dessen stabiler Schlüssel ist der
 * {@code keycloakSub}, den wir hier auf die {@code crmPersonId} der Beispiel-Person setzen —
 * dadurch projiziert der Sync das Foto auf genau den Ansprechpartner, den die Produkte bereits
 * referenzieren. Foto = Porträt-Platzhalter aus {@code shop-assets/} (Binärdaten in der DB).
 * <p>
 * Idempotent über {@code keycloakSub}: bereits vorhandene Mitarbeiter werden nicht angetastet.
 */
@ApplicationScoped
public class DemoAnsprechpartnerSeeder {

    private static final Logger LOG = Logger.getLogger(DemoAnsprechpartnerSeeder.class);

    @Transactional
    void seed(@Observes StartupEvent ev) {
        int neu = 0;
        for (KatalogBeispiele.Person p : KatalogBeispiele.ANSPRECHPARTNER) {
            if (Mitarbeiter.find("keycloakSub", p.crmPersonId()).firstResult() != null) {
                continue;
            }
            byte[] foto = ladeFoto(p.fotoDatei());
            Mitarbeiter m = new Mitarbeiter();
            m.keycloakSub = p.crmPersonId();
            m.anzeigeName = p.name();
            m.email = p.email();
            m.aktiv = true;
            m.foto = foto;
            m.fotoMime = "image/png";
            m.persist();
            neu++;
        }
        if (neu > 0) {
            LOG.infof("Demo-Ansprechpartner geseedet: %d aktive Mitarbeiter mit Foto", neu);
        }
    }

    private byte[] ladeFoto(String datei) {
        String resource = "shop-assets/" + datei;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                LOG.warnf("Porträt-Ressource fehlt: %s — Mitarbeiter wird ohne Foto angelegt", resource);
                return null;
            }
            return in.readAllBytes();
        } catch (java.io.IOException e) {
            LOG.warnf("Porträt %s konnte nicht gelesen werden: %s", resource, e.getMessage());
            return null;
        }
    }
}
