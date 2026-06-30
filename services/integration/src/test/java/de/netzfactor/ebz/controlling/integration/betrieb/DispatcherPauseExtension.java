package de.netzfactor.ebz.controlling.integration.betrieb;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * Global (per JUnit-Autodetection) registrierte Extension, die <b>vor dem gesamten Testlauf einmal</b> die
 * Hintergrund-Scheduler des laufenden Integration-Containers pausiert und <b>danach einmal</b> fortsetzt
 * — über {@code POST /betrieb/scheduler/pause|resume} ({@link SchedulerSteuerResource}). So schnappt der
 * Container-Dispatcher der Test-JVM keine {@code ZustellAuftrag}-Zeilen per SKIP-LOCKED weg (ersetzt das
 * frühere {@code docker stop}; der Stack bleibt oben).
 * <p>
 * <b>Best effort:</b> läuft kein Container (z. B. CI), schlägt der Aufruf still fehl — dann gibt es ohnehin
 * keinen Race (die Test-JVM-Dispatcher sind im {@code %test}-Profil faktisch aus). Über das Wurzel-Store
 * läuft Pause genau einmal und der Resume-Hook am Ende des kompletten Laufs.
 */
public class DispatcherPauseExtension implements BeforeAllCallback {

    private static final Logger LOG = Logger.getLogger(DispatcherPauseExtension.class.getName());
    private static final String BASIS = env("BETRIEB_BASE_URL", "http://localhost:8090");
    private static final String TOKEN = env("BETRIEB_STEUER_TOKEN", "showcase-betrieb-dev");

    @Override
    public void beforeAll(ExtensionContext ctx) {
        // Im Wurzel-Store: Pause einmalig auslösen; der gespeicherte AutoCloseable resumed am Laufende
        // (JUnit schließt AutoCloseable-Store-Werte automatisch, s. junit-platform.properties).
        ctx.getRoot().getStore(Namespace.GLOBAL).getOrComputeIfAbsent(
                "betrieb-dispatcher-pause",
                k -> {
                    steuere("pause");
                    return (AutoCloseable) () -> steuere("resume");
                },
                AutoCloseable.class);
    }

    private static void steuere(String aktion) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASIS + "/betrieb/scheduler/" + aktion))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Betrieb-Token", TOKEN)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            LOG.info(() -> "Betriebs-Scheduler '" + aktion + "' → HTTP " + resp.statusCode());
        } catch (Exception e) {
            // Kein laufender Container o. Ä. → kein Race, still ignorieren.
            LOG.fine(() -> "Betriebs-Scheduler '" + aktion + "' übersprungen: " + e.getMessage());
        }
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
