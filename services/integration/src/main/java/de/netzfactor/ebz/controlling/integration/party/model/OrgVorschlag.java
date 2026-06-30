package de.netzfactor.ebz.controlling.integration.party.model;

/**
 * Vorschlag für Organisations-Stammdaten aus der Firmen-Online-Anreicherung (Plan A15): das
 * strukturierte Ergebnis der LLM-Extraktion aus VIES- + Impressum-Daten. Reiner <b>Vorschlag</b> —
 * der/die Mitarbeiter:in übernimmt ihn bewusst in die Anlage-Maske (kein automatisches Schreiben).
 *
 * @param name           offizieller Firmenname
 * @param rechtsform     z. B. GmbH, AG, eG
 * @param ustId          USt-IdNr. (falls im Impressum/VIES gefunden)
 * @param website        Website-URL
 * @param strasse        Straße + Hausnummer
 * @param plz            Postleitzahl
 * @param ort            Ort
 * @param brancheHinweis frei formulierter Branchen-/Tätigkeitshinweis (kein Lookup-Code)
 */
public record OrgVorschlag(String name, String rechtsform, String ustId, String website,
        String strasse, String plz, String ort, String brancheHinweis) {
}
