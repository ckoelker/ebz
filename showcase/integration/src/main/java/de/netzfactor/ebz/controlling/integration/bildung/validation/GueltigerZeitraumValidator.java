package de.netzfactor.ebz.controlling.integration.bildung.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import de.netzfactor.ebz.controlling.integration.bildung.dto.GemeinsamesAngebot;

/**
 * Validiert {@link GueltigerZeitraum} über das gemeinsame Interface {@link GemeinsamesAngebot} —
 * deshalb einmal geschrieben, für alle vier per-Typ-DTOs gültig. Hängt die Verletzung an das Feld
 * {@code gueltigBis}, damit das Cockpit sie an der richtigen Eingabe anzeigt.
 */
public class GueltigerZeitraumValidator implements ConstraintValidator<GueltigerZeitraum, GemeinsamesAngebot> {

    @Override
    public boolean isValid(GemeinsamesAngebot a, ConstraintValidatorContext ctx) {
        if (a == null || a.gueltigAb() == null || a.gueltigBis() == null) {
            return true; // Pflicht/Null-Prüfung übernehmen @NotNull-Feld-Constraints
        }
        if (!a.gueltigBis().isBefore(a.gueltigAb())) {
            return true;
        }
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                .addPropertyNode("gueltigBis")
                .addConstraintViolation();
        return false;
    }
}
