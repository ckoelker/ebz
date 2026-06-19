package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.BenachrichtigungsEinstellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;

/**
 * Verwaltet die Komfort-{@link BenachrichtigungsEinstellung} (K1b) und berechnet daraus den frühesten
 * erlaubten <b>externen</b> Versandzeitpunkt ({@link #faelligAb}) — Quiet-Hours (Deferred-Send bis
 * Fensterende) + Rate-Limit (max N/Stunde → zurückstellen). PORTAL bleibt davon unberührt (sofort).
 */
@ApplicationScoped
public class EinstellungService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    public BenachrichtigungsEinstellung fuer(Long personId) {
        return BenachrichtigungsEinstellung.find("personId", personId).firstResult();
    }

    /** Hat die Person den gebündelten Digest-Versand aktiviert? */
    public boolean istDigest(Long personId) {
        BenachrichtigungsEinstellung e = fuer(personId);
        return e != null && e.digest;
    }

    @Transactional
    public BenachrichtigungsEinstellung setze(Long personId, boolean digest, LocalTime quietVon,
            LocalTime quietBis, int maxProStunde) {
        BenachrichtigungsEinstellung e = fuer(personId);
        if (e == null) {
            e = new BenachrichtigungsEinstellung();
            e.personId = personId;
            e.persist();
        }
        e.digest = digest;
        e.quietVon = quietVon;
        e.quietBis = quietBis;
        e.maxProStunde = Math.max(0, maxProStunde);
        return e;
    }

    /**
     * Frühester erlaubter externer Versandzeitpunkt für die Person: erst Quiet-Hours (auf Fensterende
     * verschieben), dann Rate-Limit (auf das Freiwerden eines Slots verschieben). Ohne Einstellung = jetzt.
     */
    public Instant faelligAb(Long personId, Instant jetzt) {
        BenachrichtigungsEinstellung e = fuer(personId);
        if (e == null) {
            return jetzt;
        }
        Instant zeit = quietHoursDefer(e, jetzt);
        return rateLimitDefer(e, personId, zeit);
    }

    private static Instant quietHoursDefer(BenachrichtigungsEinstellung e, Instant jetzt) {
        if (e.quietVon == null || e.quietBis == null) {
            return jetzt;
        }
        LocalDateTime lokal = LocalDateTime.ofInstant(jetzt, ZONE);
        if (!e.inRuhezeit(lokal.toLocalTime())) {
            return jetzt;
        }
        // Auf das nächste Fensterende (quietBis) verschieben — ggf. am Folgetag (Quiet-Hours über Mitternacht).
        LocalDateTime ende = lokal.toLocalDate().atTime(e.quietBis);
        if (!ende.isAfter(lokal)) {
            ende = ende.plusDays(1);
        }
        return ende.atZone(ZONE).toInstant();
    }

    private static Instant rateLimitDefer(BenachrichtigungsEinstellung e, Long personId, Instant zeit) {
        if (e.maxProStunde <= 0) {
            return zeit;
        }
        Instant fensterStart = zeit.minusSeconds(3600);
        long imFenster = Zustellung.count(
                "personEreignis.empfaengerPersonId = ?1 and kanal in ?2 and status = ?3 and zeitpunkt >= ?4",
                personId, java.util.List.of(Kanal.EMAIL, Kanal.SMS), Zustellung.Status.ZUGESTELLT,
                LocalDateTime.ofInstant(fensterStart, ZONE));
        if (imFenster < e.maxProStunde) {
            return zeit;
        }
        // Slot frei, sobald die älteste Zustellung im Fenster aus dem Stundenfenster fällt.
        Zustellung aelteste = Zustellung.find(
                "personEreignis.empfaengerPersonId = ?1 and kanal in ?2 and status = ?3 and zeitpunkt >= ?4 order by zeitpunkt asc",
                personId, java.util.List.of(Kanal.EMAIL, Kanal.SMS), Zustellung.Status.ZUGESTELLT,
                LocalDateTime.ofInstant(fensterStart, ZONE)).firstResult();
        if (aelteste == null) {
            return zeit;
        }
        return aelteste.zeitpunkt.atZone(ZONE).toInstant().plusSeconds(3600);
    }
}
