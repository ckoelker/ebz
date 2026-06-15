package de.netzfactor.ebz.controlling.integration.prozessdoku;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import io.quarkus.arc.Unremovable;

/**
 * Greift im E2E-Testlauf die Prozessdoku-Business-Spans ab und schreibt sie als JSONL-Event-Log nach
 * {@code target/prozess-log/spans.jsonl} — die Eingabe für den BPMN-Generator
 * ({@code showcase/prozessdoku/}). Quarkus-OpenTelemetry registriert CDI-{@link SpanProcessor}-Beans
 * zusätzlich zum Exporter; im Test ist der OTLP-Export aus ({@code %test.quarkus.otel.traces.exporter=none}),
 * sodass kein Collector nötig ist und dennoch jeder Span hier landet.
 * <p>
 * Nur <b>Business-Spans</b> (Attribut {@code prozess.phase} gesetzt) werden geschrieben — technische
 * Auto-Spans (HTTP/DB) werden ignoriert. Die Datei wird beim Boot einmalig neu angelegt; alle Tests
 * eines Laufs hängen an. Test-Scope (nur unter {@code src/test}), läuft also nicht in Prod.
 */
@ApplicationScoped
@Unremovable
public class SpanLogExporter implements SpanProcessor {

    private static final Path LOG = Paths.get("target", "prozess-log", "spans.jsonl");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final AttributeKey<String> K_FALL = AttributeKey.stringKey(Prozessspur.ATTR_FALL);
    private static final AttributeKey<String> K_AKTEUR = AttributeKey.stringKey(Prozessspur.ATTR_AKTEUR);
    private static final AttributeKey<String> K_SYSTEM = AttributeKey.stringKey(Prozessspur.ATTR_SYSTEM);
    private static final AttributeKey<String> K_TYP = AttributeKey.stringKey(Prozessspur.ATTR_TYP);
    private static final AttributeKey<String> K_PHASE = AttributeKey.stringKey(Prozessspur.ATTR_PHASE);
    private static final AttributeKey<String> K_PARALLELGRUPPE =
            AttributeKey.stringKey(Prozessspur.ATTR_PARALLELGRUPPE);

    static {
        try {
            Files.createDirectories(LOG.getParent());
            Files.writeString(LOG, "", StandardCharsets.UTF_8); // Truncate beim Boot
        } catch (IOException e) {
            throw new UncheckedIOException("Prozess-Span-Log konnte nicht angelegt werden", e);
        }
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // nichts zu tun
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        String phase = span.getAttribute(K_PHASE);
        if (phase == null) {
            return; // nur Business-Spans (Prozessspur), keine technischen Auto-Spans
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fall", orElse(span.getAttribute(K_FALL), "unbekannt"));
        row.put("name", span.getName());
        row.put("startEpochNanos", span.toSpanData().getStartEpochNanos());
        row.put("akteur", span.getAttribute(K_AKTEUR));
        row.put("system", span.getAttribute(K_SYSTEM));
        row.put("typ", span.getAttribute(K_TYP));
        row.put("phase", phase);
        row.put("parallelgruppe", span.getAttribute(K_PARALLELGRUPPE)); // null = sequenziell
        try {
            String zeile = MAPPER.writeValueAsString(row) + "\n";
            synchronized (SpanLogExporter.class) {
                Files.writeString(LOG, zeile, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Prozess-Span konnte nicht geschrieben werden", e);
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return CompletableResultCode.ofSuccess();
    }

    private static String orElse(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
