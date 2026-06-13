package de.netzfactor.ebz.controlling.integration.bildung.dto;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.bildung.model.Bildungsangebot;
import de.netzfactor.ebz.controlling.integration.bildung.model.BildungsangebotTyp;

/**
 * Read-only-Projektion der gemeinsamen Header-Felder über die STI-Registry (§11.2/§11.4).
 * Liefert typ-übergreifend die Listen-/Filterspalten, ohne die subtyp-spezifischen Felder; die
 * TS-Union ({@code Seminar | Tagung | …}) baut das Frontend selbst anhand von {@link #typ}.
 */
public record RegistryItemDto(
        Long id,
        BildungsangebotTyp typ,
        String code,
        String titel,
        Bereich bereich,
        AngebotStatus status,
        boolean shopVerkauf) {

    public static RegistryItemDto from(Bildungsangebot b) {
        return new RegistryItemDto(b.id, b.typ(), b.code, b.titel, b.bereich, b.status, b.shopVerkauf);
    }
}
