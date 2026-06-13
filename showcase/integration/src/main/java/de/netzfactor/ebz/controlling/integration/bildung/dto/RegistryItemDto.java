package de.netzfactor.ebz.controlling.integration.bildung.dto;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungsangebotTyp;

/** Read-only-Header-Projektion für die typ-übergreifende Registry-Liste (§11.4). */
public record RegistryItemDto(
        Long id,
        BildungsangebotTyp typ,
        String code,
        String titel,
        Bereich bereich,
        AngebotStatus status,
        boolean shopVerkauf) {
}
