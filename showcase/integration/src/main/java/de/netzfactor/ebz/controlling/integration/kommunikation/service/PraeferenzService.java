package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Praeferenz;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;

/**
 * Verwaltet die Benachrichtigungs-Präferenzen ({@link Praeferenz}, K1 „basic": Kanal an/aus, Opt-out).
 * {@code PORTAL} ist immer erlaubt (Inbox/Transparenz). Konsultiert von der {@code KommunikationApi} beim
 * Fan-out; gepflegt über die Portal-Resource.
 */
@ApplicationScoped
public class PraeferenzService {

    /** Ist der Kanal für die Person erlaubt? PORTAL immer; sonst aktiv, sofern keine abschaltende Zeile. */
    public boolean erlaubt(Long personId, Kanal kanal) {
        if (kanal == Kanal.PORTAL) {
            return true;
        }
        Praeferenz p = Praeferenz.find("personId = ?1 and kanal = ?2", personId, kanal).firstResult();
        return p == null || p.aktiv;
    }

    /** Alle gesetzten Präferenzen einer Person (nicht gesetzte Kanäle gelten als aktiv). */
    public List<Praeferenz> fuer(Long personId) {
        return Praeferenz.list("personId", personId);
    }

    /** Setzt (idempotent, upsert) die Präferenz für einen Kanal. */
    @Transactional
    public Praeferenz setze(Long personId, Kanal kanal, boolean aktiv) {
        if (kanal == Kanal.PORTAL) {
            throw new IllegalArgumentException("PORTAL ist nicht abschaltbar (Inbox/Transparenz).");
        }
        Praeferenz p = Praeferenz.find("personId = ?1 and kanal = ?2", personId, kanal).firstResult();
        if (p == null) {
            p = new Praeferenz();
            p.personId = personId;
            p.kanal = kanal;
            p.persist();
        }
        p.aktiv = aktiv;
        return p;
    }
}
