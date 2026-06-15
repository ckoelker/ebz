package de.netzfactor.ebz.controlling.integration.outbox.webuntis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * <b>Showcase-Mock des Drittsystems WebUntis</b> — steht stellvertretend für die externe Stundenplan-/
 * Klassenbuch-Software, in die ein neuer Azubi als Schüler übernommen wird. In-Memory-„Schülerstamm"
 * mit zwei Showcase-Hebeln:
 * <ul>
 *   <li><b>Verfügbarkeit</b> umschaltbar ({@link #setVerfuegbar}) → simuliert eine Downtime; ein Import
 *       wirft dann {@link WebUntisNichtVerfuegbar}, womit Retry/Backoff/Dead-Letter sichtbar werden.</li>
 *   <li><b>Idempotenz</b>: ein bereits importierter Schüler (per E-Mail) wird nicht doppelt angelegt —
 *       so wie ein echtes Zielsystem mit At-least-once-Zustellung umgehen muss.</li>
 * </ul>
 * Bewusst in-process (kein eigener Container): der Showcase zeigt das <i>Outbox-/Resilienz-Verhalten</i>,
 * nicht die WebUntis-API. Über {@code WebUntisMockResource} ist der Mock per REST steuer-/einsehbar.
 */
@ApplicationScoped
public class WebUntisMock {

    /** importierte Schüler je E-Mail (Idempotenz-Schlüssel des Drittsystems). */
    private final Map<String, Schueler> schueler = new ConcurrentHashMap<>();
    private volatile boolean verfuegbar = true;

    public record Schueler(String name, String email, String klasse, Instant importiertAm) {
    }

    /** Signalisiert eine WebUntis-Downtime; der Outbox-Dispatcher wertet das als fehlgeschlagenen Versuch. */
    public static class WebUntisNichtVerfuegbar extends RuntimeException {
        public WebUntisNichtVerfuegbar() {
            super("WebUntis ist derzeit nicht erreichbar (HTTP 503).");
        }
    }

    /** Übernimmt den Azubi als Schüler (idempotent). Wirft bei simulierter Downtime. */
    public Schueler importiereSchueler(String name, String email, String klasse) {
        if (!verfuegbar) {
            throw new WebUntisNichtVerfuegbar();
        }
        return schueler.computeIfAbsent(email, e -> new Schueler(name, e, klasse, Instant.now()));
    }

    public List<Schueler> alle() {
        return List.copyOf(schueler.values());
    }

    public boolean kennt(String email) {
        return schueler.containsKey(email);
    }

    public boolean istVerfuegbar() {
        return verfuegbar;
    }

    public void setVerfuegbar(boolean verfuegbar) {
        this.verfuegbar = verfuegbar;
    }

    /** Showcase-/Test-Reset. */
    public void reset() {
        schueler.clear();
        verfuegbar = true;
    }
}
