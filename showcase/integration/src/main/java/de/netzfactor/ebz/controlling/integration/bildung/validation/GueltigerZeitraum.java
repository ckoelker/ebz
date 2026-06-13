package de.netzfactor.ebz.controlling.integration.bildung.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Klassen-Constraint (§11.9-D, F14): {@code gueltigBis} muss am oder nach {@code gueltigAb} liegen.
 * <p>
 * Bewusst eine Cross-Field-Regel: sie steht NICHT im JSON-Schema/OpenAPI (smallrye spiegelt nur
 * Feld-Constraints) → bleibt server-seitig und erscheint im Cockpit aus der 400-Violation-Antwort.
 * Genau die Naht, die generierte zod (Feld-Ebene) NICHT abdeckt — der Beleg für „Cross-Field bleibt
 * Server-Wahrheit".
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GueltigerZeitraumValidator.class)
@Documented
public @interface GueltigerZeitraum {
    String message() default "gueltigBis muss am oder nach gueltigAb liegen";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
