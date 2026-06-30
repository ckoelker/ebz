package de.netzfactor.ebz.controlling.integration.bildung.dto;

import java.time.LocalDate;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.bildung.model.PreisModell;

/**
 * Gemeinsame Supertyp-Felder, die jedes per-Typ-DTO flach trägt (§11.3). Record-Accessoren erfüllen
 * dieses Interface automatisch → der {@code BildungResource}-Mapper übernimmt die gemeinsamen Felder
 * typ-übergreifend (DTO→Entity), und das Cockpit kann EINE gemeinsame Stammdaten-Komponente über
 * allen vier Typ-Formularen wiederverwenden (gleiche Feldnamen, hier garantiert).
 */
public interface GemeinsamesAngebot {
    String code();

    String titel();

    Bereich bereich();

    String kurzbeschreibung();

    AngebotStatus status();

    LocalDate gueltigAb();

    LocalDate gueltigBis();

    String verantwortlich();

    PreisModell preisModell();

    Integer preisCent();

    Integer abrechnungIntervallMonate();

    Integer ratenGesamt();

    boolean shopVerkauf();

    String vendureProductId();

    String zielgruppe();
}
