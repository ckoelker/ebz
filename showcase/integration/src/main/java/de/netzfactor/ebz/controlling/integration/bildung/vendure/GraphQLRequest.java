package de.netzfactor.ebz.controlling.integration.bildung.vendure;

import java.util.Map;

/** Minimaler GraphQL-Request-Envelope (query + variables) für die Vendure-Admin-API. */
public record GraphQLRequest(String query, Map<String, Object> variables) {
}
