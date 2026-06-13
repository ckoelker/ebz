package de.netzfactor.ebz.controlling.integration.rechnung.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import de.netzfactor.ebz.controlling.integration.rechnung.dto.AnmeldungDto;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungTyp;

/**
 * Validiert {@link StimmigeHochschulAnmeldung}; greift nur für {@code typ=HOCHSCHULE}. Hängt jede
 * Verletzung an das fachlich passende Feld (Cockpit-Anzeige).
 */
public class StimmigeHochschulAnmeldungValidator
        implements ConstraintValidator<StimmigeHochschulAnmeldung, AnmeldungDto> {

    @Override
    public boolean isValid(AnmeldungDto a, ConstraintValidatorContext ctx) {
        if (a == null || a.typ() != AnmeldungTyp.HOCHSCHULE) {
            return true;
        }
        boolean ok = true;
        ctx.disableDefaultConstraintViolation();

        if (a.semester() == null || a.semester().isBlank()) {
            ok = verletze(ctx, "semester", "semester ist für HOCHSCHULE Pflicht");
        }
        if (a.semesterbetragCent() == null || a.semesterbetragCent() <= 0) {
            ok = verletze(ctx, "semesterbetragCent", "semesterbetragCent ist für HOCHSCHULE Pflicht und > 0");
        }
        // Firmen-Split (dualer Studienplatz): zwei getrennte Forderungen
        if (a.firmaDebitorId() != null) {
            if (a.firmaAnteilCent() == null) {
                ok = verletze(ctx, "firmaAnteilCent", "bei Firmen-Split ist firmaAnteilCent Pflicht");
            } else if (a.semesterbetragCent() != null
                    && (a.firmaAnteilCent() <= 0 || a.firmaAnteilCent() >= a.semesterbetragCent())) {
                ok = verletze(ctx, "firmaAnteilCent",
                        "firmaAnteilCent muss zwischen 1 und semesterbetragCent-1 liegen (echter Split)");
            }
            if (a.firmaDebitorId().equals(a.zahlungspflichtigerDebitorId())) {
                ok = verletze(ctx, "firmaDebitorId", "Firma und Studierende:r müssen verschiedene Debitoren sein");
            }
        } else if (a.firmaAnteilCent() != null) {
            ok = verletze(ctx, "firmaDebitorId", "firmaAnteilCent ohne firmaDebitorId ist unzulässig");
        }
        return ok;
    }

    private static boolean verletze(ConstraintValidatorContext ctx, String feld, String msg) {
        ctx.buildConstraintViolationWithTemplate(msg).addPropertyNode(feld).addConstraintViolation();
        return false;
    }
}
