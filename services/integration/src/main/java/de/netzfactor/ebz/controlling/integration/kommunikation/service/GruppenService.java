package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe.Mitglied;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Personengruppe.Quelle;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.EmpfaengerAufloesung;

/**
 * Verteiler-Pflege und <b>Gruppen-Broadcast</b> (K3, Person→Gruppe): manuelle Gruppen werden gepflegt,
 * abgeleitete (ORGANISATION) lösen ihre Mitglieder zum Sendezeitpunkt auf ({@link EmpfaengerAufloesung}).
 * Der Broadcast fächert <i>pro Mitglied</i> ein {@link EreignisTyp#GRUPPEN_INFO}-Ereignis über die
 * {@link KommunikationApi} auf — damit greifen Aktivitätslog, Kanal-Präferenzen und vor allem die
 * <b>Consent-/Werbesperre-Durchsetzung</b> (im {@code ErreichbarkeitPort}: nicht-transaktional → bei
 * Werbe-/Auskunftssperre nur PORTAL, kein E-Mail) automatisch wie bei System→Person.
 */
@ApplicationScoped
public class GruppenService {

    @Inject
    EmpfaengerAufloesung aufloesung;

    @Inject
    KommunikationApi kommunikation;

    // ───────────────────────── Pflege ─────────────────────────

    public List<Personengruppe> gruppen() {
        return Personengruppe.listAll();
    }

    @Transactional
    public Personengruppe anlegenManuell(String name, String beschreibung) {
        return anlegen(name, beschreibung, Quelle.MANUELL, null);
    }

    @Transactional
    public Personengruppe anlegenOrganisation(String name, String beschreibung, Long organisationId) {
        return anlegen(name, beschreibung, Quelle.ORGANISATION, organisationId);
    }

    /** Abgeleitete Kohorte (K3b): alle Teilnehmenden eines Bildungsangebots (Seminar/Berufsschulklasse). */
    @Transactional
    public Personengruppe anlegenKohorte(String name, String beschreibung, Long bildungsangebotId) {
        return anlegen(name, beschreibung, Quelle.BILDUNGSANGEBOT, bildungsangebotId);
    }

    private Personengruppe anlegen(String name, String beschreibung, Quelle quelle, Long refId) {
        Personengruppe g = new Personengruppe();
        g.name = name;
        g.beschreibung = beschreibung;
        g.quelle = quelle;
        g.quelleRefId = refId;
        g.persist();
        return g;
    }

    /** Manuelles Mitglied hinzufügen (idempotent); nur bei {@link Quelle#MANUELL} sinnvoll. */
    @Transactional
    public void mitgliedHinzu(Long gruppeId, Long personId) {
        Personengruppe g = mussGruppe(gruppeId);
        if (g.quelle != Quelle.MANUELL) {
            throw new jakarta.ws.rs.BadRequestException("Mitglieder lassen sich nur bei manuellen Gruppen pflegen.");
        }
        if (Mitglied.count("gruppe.id = ?1 and personId = ?2", gruppeId, personId) == 0) {
            Mitglied m = new Mitglied();
            m.gruppe = g;
            m.personId = personId;
            m.persist();
        }
    }

    @Transactional
    public void mitgliedEntfernen(Long gruppeId, Long personId) {
        Mitglied.delete("gruppe.id = ?1 and personId = ?2", gruppeId, personId);
    }

    /** Aufgelöste Empfänger (manuell gepflegt bzw. dynamisch) — für Vorschau/Zähler. */
    public List<Long> mitglieder(Long gruppeId) {
        return aufloesung.mitglieder(gruppeId);
    }

    @Transactional
    public void loeschen(Long gruppeId) {
        Mitglied.delete("gruppe.id", gruppeId);
        Personengruppe.deleteById(gruppeId);
    }

    // ───────────────────────── Broadcast ─────────────────────────

    /**
     * Sendet {@code nachricht} an alle (zum Sendezeitpunkt aufgelösten) Mitglieder der Gruppe und liefert
     * die Anzahl erreichter Empfänger. Pro Mitglied ein {@code GRUPPEN_INFO}-Ereignis (PORTAL immer,
     * E-Mail nur ohne Werbe-/Auskunftssperre); Idempotenz pro Lauf über einen eindeutigen Broadcast-Schlüssel.
     */
    @Transactional
    public int broadcast(Long gruppeId, String nachricht) {
        mussGruppe(gruppeId);
        long lauf = System.nanoTime();
        int erreicht = 0;
        for (Long personId : aufloesung.mitglieder(gruppeId)) {
            String key = "broadcast:" + gruppeId + ":" + lauf + ":" + personId;
            if (kommunikation.protokolliere(KommunikationsEreignis.ohneKontext(
                    EreignisTyp.GRUPPEN_INFO, personId, nachricht, key)) != null) {
                erreicht++;
            }
        }
        return erreicht;
    }

    private static Personengruppe mussGruppe(Long gruppeId) {
        Personengruppe g = Personengruppe.findById(gruppeId);
        if (g == null) {
            throw new jakarta.ws.rs.NotFoundException("Personengruppe nicht gefunden: " + gruppeId);
        }
        return g;
    }
}
