package de.netzfactor.ebz.controlling.integration.rechnung.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Klassen-Constraint für die Hochschul-Anmeldung (Cross-Field, server-seitig, nicht im JSON-Schema):
 * <ul>
 *   <li>{@code typ=HOCHSCHULE} ⇒ {@code semester} und {@code semesterbetragCent} (&gt; 0) sind Pflicht;</li>
 *   <li>Firmen-Split: {@code firmaDebitorId} ⇒ {@code firmaAnteilCent} Pflicht und strikt zwischen
 *       1 und {@code semesterbetragCent}-1 (echter Split in zwei Forderungen), und Firma ≠
 *       Studierende:r-Debitor;</li>
 *   <li>{@code firmaAnteilCent} ohne {@code firmaDebitorId} ist unzulässig.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StimmigeHochschulAnmeldungValidator.class)
@Documented
public @interface StimmigeHochschulAnmeldung {
    String message() default "Hochschul-Anmeldung unvollständig oder Firmen-Split unstimmig";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
