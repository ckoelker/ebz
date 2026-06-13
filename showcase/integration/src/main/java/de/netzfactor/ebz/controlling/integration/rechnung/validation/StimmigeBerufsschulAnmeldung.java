package de.netzfactor.ebz.controlling.integration.rechnung.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Klassen-Constraint für die Berufsschul-Anmeldung (Cross-Field, server-seitig — steht bewusst NICHT
 * im JSON-Schema, vgl. {@code GueltigerZeitraum} in {@code bildung}):
 * <ul>
 *   <li>{@code typ=BERUFSSCHULE} ⇒ {@code schuljahr}, {@code halbjahr}, {@code zimmerart} und
 *       {@code unterrichtBetragCent} sind Pflicht (die Unterrichts-Position entsteht immer);</li>
 *   <li>{@code zimmerart ≠ KEINE} ⇒ {@code uebernachtungBetragCent} ist Pflicht (zweite Position);</li>
 *   <li>{@code zimmerart = KEINE} ⇒ {@code uebernachtungBetragCent} muss leer sein (keine zweite Position).</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StimmigeBerufsschulAnmeldungValidator.class)
@Documented
public @interface StimmigeBerufsschulAnmeldung {
    String message() default "Berufsschul-Anmeldung unvollständig oder Übernachtungsbetrag passt nicht zur Zimmerart";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
