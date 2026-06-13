package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Art des Belegs. Nach Festschreibung wird eine Rechnung nicht geändert, sondern nur durch einen
 * Korrekturbeleg ({@code GUTSCHRIFT}/{@code STORNO}/{@code NACHBERECHNUNG}) mit Bezug auf das Original
 * korrigiert. Jede Belegart hat je Bereich einen eigenen lückenlosen Nummernkreis.
 */
public enum Belegart {
    RECHNUNG, GUTSCHRIFT, STORNO, NACHBERECHNUNG
}
