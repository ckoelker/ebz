package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp.Kategorie;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Praeferenz;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;

/**
 * Verwaltet die Benachrichtigungs-Präferenzen ({@link Praeferenz}): K1 „basic" (Kanal an/aus) + K1b
 * Verfeinerung Kanal × {@link Kategorie}. Auflösung: spezifische Kategorie → global → Default „aktiv".
 * {@code PORTAL} ist immer erlaubt (Inbox/Transparenz). Konsultiert von der {@code KommunikationApi} beim
 * Fan-out; gepflegt über die Portal-Resource.
 */
@ApplicationScoped
public class PraeferenzService {

    /**
     * Ist der Kanal für die Person in dieser Kategorie erlaubt? PORTAL immer; sonst zuerst die
     * kategoriespezifische Zeile, dann die globale ({@code kategorie is null}), sonst Default {@code true}.
     */
    public boolean erlaubt(Long personId, Kanal kanal, Kategorie kategorie) {
        if (kanal == Kanal.PORTAL) {
            return true;
        }
        if (kategorie != null) {
            Praeferenz spez = Praeferenz.find("personId = ?1 and kanal = ?2 and kategorie = ?3",
                    personId, kanal, kategorie).firstResult();
            if (spez != null) {
                return spez.aktiv;
            }
        }
        Praeferenz global = Praeferenz.find("personId = ?1 and kanal = ?2 and kategorie is null",
                personId, kanal).firstResult();
        return global == null || global.aktiv;
    }

    /** Alle gesetzten Präferenzen einer Person (nicht gesetzte Kanäle/Kategorien gelten als aktiv). */
    public List<Praeferenz> fuer(Long personId) {
        return Praeferenz.list("personId", personId);
    }

    /** Setzt (idempotent, upsert) die Präferenz für einen Kanal — {@code kategorie = null} = global. */
    @Transactional
    public Praeferenz setze(Long personId, Kanal kanal, Kategorie kategorie, boolean aktiv) {
        if (kanal == Kanal.PORTAL) {
            throw new IllegalArgumentException("PORTAL ist nicht abschaltbar (Inbox/Transparenz).");
        }
        Praeferenz p = (kategorie == null
                ? Praeferenz.find("personId = ?1 and kanal = ?2 and kategorie is null", personId, kanal)
                : Praeferenz.find("personId = ?1 and kanal = ?2 and kategorie = ?3", personId, kanal, kategorie))
                .firstResult();
        if (p == null) {
            p = new Praeferenz();
            p.personId = personId;
            p.kanal = kanal;
            p.kategorie = kategorie;
            p.persist();
        }
        p.aktiv = aktiv;
        return p;
    }
}
