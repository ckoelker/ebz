package de.netzfactor.ebz.controlling.integration.prozessdoku;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;

/**
 * Markiert fachliche Prozess-Schritte als <b>OpenTelemetry-Business-Spans</b> (Living Documentation).
 * Jeder Aufruf erzeugt einen kurzen Span mit den Prozessdoku-Attributen
 * ({@code prozess.akteur/system/typ/phase}) und der Fall-Korrelation {@code prozess.fall}.
 * <p>
 * Dual-Use: In Prod exportiert Quarkus die Spans per OTLP an Jaeger (Live-Trace-Ansicht); im E2E-Test
 * greift der {@code SpanLogExporter} sie ab → {@code spans.jsonl} → PM4py → BPMN
 * ({@code showcase/prozessdoku/}). Die {@code fall}-Id kommt aus dem W3C-Baggage (Header
 * {@code baggage: prozess.fall=…}), das über Systemgrenzen propagiert; alternativ explizit übergebbar.
 */
@ApplicationScoped
public class Prozessspur {

    public static final String ATTR_FALL = "prozess.fall";
    public static final String ATTR_AKTEUR = "prozess.akteur";
    public static final String ATTR_SYSTEM = "prozess.system";
    public static final String ATTR_TYP = "prozess.typ";
    public static final String ATTR_PHASE = "prozess.phase";

    /** Baggage-Schlüssel für die Fall-Korrelation (= BPMN-Case-Id). */
    public static final String BAGGAGE_FALL = "prozess.fall";

    @Inject
    Tracer tracer;

    /** Markiert einen Schritt; die Fall-Id wird aus dem aktuellen Baggage gezogen. */
    public void schritt(String aktivitaet, Akteur akteur, Prozess.System system, Typ typ, Phase phase) {
        schritt(aktuellerFall(), aktivitaet, akteur, system, typ, phase);
    }

    /** Markiert einen Schritt mit explizit gesetzter Fall-Id (z. B. Test-Schritte ohne HTTP-Kontext). */
    public void schritt(String fall, String aktivitaet, Akteur akteur, Prozess.System system, Typ typ,
            Phase phase) {
        Span span = tracer.spanBuilder(aktivitaet)
                .setAttribute(ATTR_FALL, fall)
                .setAttribute(ATTR_AKTEUR, akteur.label)
                .setAttribute(ATTR_SYSTEM, system.label)
                .setAttribute(ATTR_TYP, typ.name())
                .setAttribute(ATTR_PHASE, phase.name())
                .startSpan();
        span.end();
    }

    private static String aktuellerFall() {
        String fall = Baggage.current().getEntryValue(BAGGAGE_FALL);
        return (fall == null || fall.isBlank()) ? "unbekannt" : fall;
    }
}
