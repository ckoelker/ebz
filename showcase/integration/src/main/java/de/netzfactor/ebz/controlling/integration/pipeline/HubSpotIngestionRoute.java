package de.netzfactor.ebz.controlling.integration.pipeline;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.apache.camel.builder.RouteBuilder;

/**
 * Camel-Orchestrierung des Ingestion-Laufs.
 * <ul>
 *   <li>{@code timer:hubspotPull} — zeitgesteuerter Pull (erster Lauf nach {@code delay}, dann alle
 *       {@code period} ms). Autostart über {@code ingestion.timer.enabled} abschaltbar.</li>
 *   <li>{@code direct:ingest} — die eigentliche Arbeit (auch vom REST-Trigger genutzt), delegiert an
 *       die injizierte CDI-Bean {@link IngestionService}.</li>
 * </ul>
 * Bewusst {@code .process(...)} mit der injizierten Bean statt {@code .bean(Class, "ingest")} —
 * Letzteres würde eine neue Instanz ohne CDI-Injektion erzeugen ({@code @Inject}-Felder null).
 */
@ApplicationScoped
public class HubSpotIngestionRoute extends RouteBuilder {

    @Inject
    IngestionService ingestionService;

    @Override
    public void configure() {
        from("timer:hubspotPull?period={{ingestion.timer.period}}&delay={{ingestion.timer.delay}}")
                .routeId("hubspot-timer")
                .autoStartup("{{ingestion.timer.enabled}}")
                .log("Timer-getriggerter HubSpot-Ingestion-Lauf")
                .to("direct:ingest");

        from("direct:ingest")
                .routeId("hubspot-ingest")
                .process(exchange -> exchange.getMessage().setBody(ingestionService.ingest()))
                .log("Ingestion-Ergebnis: ${body}");
    }
}
