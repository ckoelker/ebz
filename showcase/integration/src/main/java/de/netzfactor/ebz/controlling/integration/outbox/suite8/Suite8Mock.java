package de.netzfactor.ebz.controlling.integration.outbox.suite8;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * <b>Showcase-Mock des Drittsystems Suite8</b> (Kassen-/Bewirtungssystem für Kiosk &amp; Kantine). Steht
 * stellvertretend für das echte Suite8, das einem übernommenen Berufsschul-Teilnehmer ein Konto anlegt
 * und eine <b>Bezahlkarte</b> ausgibt. Wie {@code WebUntisMock} in-process mit zwei Showcase-Hebeln:
 * <ul>
 *   <li><b>Verfügbarkeit</b> umschaltbar ({@link #setVerfuegbar}) → simuliert eine Suite8-Downtime;
 *       ein Import wirft dann {@link Suite8NichtVerfuegbar} → Retry/Backoff/Dead-Letter werden sichtbar.</li>
 *   <li><b>Idempotenz</b>: ein bereits angelegtes Konto (per E-Mail) liefert dieselbe Bezahlkarte zurück
 *       (At-least-once-Zustellung legt keinen Dublett / keine zweite Karte an).</li>
 * </ul>
 */
@ApplicationScoped
public class Suite8Mock {

    /** Konten je E-Mail (Idempotenz-Schlüssel des Drittsystems). */
    private final Map<String, Konto> konten = new ConcurrentHashMap<>();
    private final AtomicLong kartenZaehler = new AtomicLong(1000);
    private volatile boolean verfuegbar = true;

    /** Suite8-Konto mit ausgegebener Bezahlkarte (Kiosk/Kantine). */
    public record Konto(String name, String email, String bezahlkartenNr, Instant angelegtAm) {
    }

    /** Signalisiert eine Suite8-Downtime; der Outbox-Dispatcher wertet das als fehlgeschlagenen Versuch. */
    public static class Suite8NichtVerfuegbar extends RuntimeException {
        public Suite8NichtVerfuegbar() {
            super("Suite8 ist derzeit nicht erreichbar (HTTP 503).");
        }
    }

    /** Legt das Konto an und gibt eine Bezahlkarte aus (idempotent). Wirft bei simulierter Downtime. */
    public Konto legeKontoAn(String name, String email) {
        if (!verfuegbar) {
            throw new Suite8NichtVerfuegbar();
        }
        return konten.computeIfAbsent(email,
                e -> new Konto(name, e, "BK-" + kartenZaehler.incrementAndGet(), Instant.now()));
    }

    public List<Konto> alle() {
        return List.copyOf(konten.values());
    }

    public boolean kennt(String email) {
        return konten.containsKey(email);
    }

    public boolean istVerfuegbar() {
        return verfuegbar;
    }

    public void setVerfuegbar(boolean verfuegbar) {
        this.verfuegbar = verfuegbar;
    }

    /** Showcase-/Test-Reset. */
    public void reset() {
        konten.clear();
        verfuegbar = true;
    }
}
