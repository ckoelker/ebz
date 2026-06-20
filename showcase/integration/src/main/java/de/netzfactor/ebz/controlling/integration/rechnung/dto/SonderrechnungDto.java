package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import jakarta.validation.constraints.NotNull;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;

/**
 * Anlage einer freien <b>Sonderrechnung</b> (Ad-hoc-Beleg außerhalb der Standard-Läufe): legt einen
 * leeren {@code ENTWURF} an, der danach über {@code /positionen} bestückt und {@code /ausstellen}
 * festgeschrieben wird. {@code bereich} optional (Default = Bereich des Debitors), {@code zahlungszielTage}
 * optional (Default 14). {@code debitorId} ist Pflicht — der Beleg braucht einen Forderungs-Empfänger.
 */
public record SonderrechnungDto(@NotNull Long debitorId, Bereich bereich, String zeitraumBezeichnung,
        Integer zahlungszielTage) {
}
