package de.netzfactor.ebz.controlling.integration.party.service;

/**
 * Naht zur Identitäts-Provider-Hoheit (Keycloak): legt für eine geprüfte Identität einen Customer-Login
 * an. Bewusst hinter einem Interface, damit der HITL-/Mail-Workflow ohne laufendes Keycloak testbar
 * bleibt (Mock) und der Anbieter austauschbar ist.
 */
public interface LoginProvisionierung {

    /**
     * @param keycloakUserId Id des (vorhandenen oder neu angelegten) Keycloak-Users; {@code null}, wenn
     *                       die Provisionierung übersprungen wurde ({@code provisioniert=false})
     * @param neuAngelegt    {@code true}, wenn der User neu erzeugt wurde (sonst bereits vorhanden)
     * @param provisioniert  {@code true}, wenn tatsächlich gegen Keycloak provisioniert wurde
     */
    record Ergebnis(String keycloakUserId, boolean neuAngelegt, boolean provisioniert) {
    }

    /** Legt idempotent einen Login für die E-Mail an (oder findet den bestehenden). */
    Ergebnis anlegen(String email, String anzeigeName);
}
