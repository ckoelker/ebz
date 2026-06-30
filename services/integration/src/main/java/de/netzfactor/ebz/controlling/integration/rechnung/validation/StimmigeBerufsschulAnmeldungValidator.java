package de.netzfactor.ebz.controlling.integration.rechnung.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import de.netzfactor.ebz.controlling.integration.rechnung.dto.AnmeldungDto;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungTyp;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zimmerart;

/**
 * Validiert {@link StimmigeBerufsschulAnmeldung}. Hängt jede Verletzung an das fachlich passende Feld,
 * damit das Cockpit sie an der richtigen Eingabe anzeigt. Greift nur für {@code typ=BERUFSSCHULE};
 * Hochschule-Regeln folgen in R6.
 */
public class StimmigeBerufsschulAnmeldungValidator
        implements ConstraintValidator<StimmigeBerufsschulAnmeldung, AnmeldungDto> {

    @Override
    public boolean isValid(AnmeldungDto a, ConstraintValidatorContext ctx) {
        if (a == null || a.typ() != AnmeldungTyp.BERUFSSCHULE) {
            return true; // Pflicht-Null deckt @NotNull ab; Nicht-BS hier nicht prüfen
        }
        boolean ok = true;
        ctx.disableDefaultConstraintViolation();

        if (a.schuljahr() == null || a.schuljahr().isBlank()) {
            ok = verletze(ctx, "schuljahr", "schuljahr ist für BERUFSSCHULE Pflicht");
        }
        if (a.halbjahr() == null) {
            ok = verletze(ctx, "halbjahr", "halbjahr ist für BERUFSSCHULE Pflicht");
        }
        if (a.unterrichtBetragCent() == null) {
            ok = verletze(ctx, "unterrichtBetragCent", "unterrichtBetragCent ist für BERUFSSCHULE Pflicht");
        }
        Zimmerart z = a.zimmerart();
        if (z == null) {
            return verletze(ctx, "zimmerart", "zimmerart ist für BERUFSSCHULE Pflicht") && ok;
        }
        if (z == Zimmerart.KEINE && a.uebernachtungBetragCent() != null) {
            ok = verletze(ctx, "uebernachtungBetragCent", "ohne Übernachtung (KEINE) darf kein uebernachtungBetragCent gesetzt sein");
        }
        if (z != Zimmerart.KEINE && a.uebernachtungBetragCent() == null) {
            ok = verletze(ctx, "uebernachtungBetragCent", "bei Doppel-/Einzelzimmer ist uebernachtungBetragCent Pflicht");
        }
        return ok;
    }

    private static boolean verletze(ConstraintValidatorContext ctx, String feld, String msg) {
        ctx.buildConstraintViolationWithTemplate(msg).addPropertyNode(feld).addConstraintViolation();
        return false;
    }
}
