package de.netzfactor.ebz.controlling.integration.party.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Schlanker In-Memory-Rate-Limiter (Sliding Window) — Spam-/Bot-Schutz für den öffentlichen,
 * unauthentifizierten Lead-Endpunkt (Ausbildungsbetrieb-Anfrage). Bewusst minimal gehalten
 * (showcase): pro Schlüssel werden die Treffer-Zeitstempel im Zeitfenster gezählt. Für den
 * produktiven Mehr-Knoten-Betrieb gehörte das hinter einen geteilten Speicher (z. B. Redis).
 */
@ApplicationScoped
public class RateLimiter {

    private final Map<String, Deque<Long>> treffer = new ConcurrentHashMap<>();

    /** {@code true}, wenn der Aufruf erlaubt ist (≤ {@code max} im {@code fensterMillis}-Fenster). */
    public synchronized boolean erlaube(String schluessel, int max, long fensterMillis) {
        long jetzt = System.currentTimeMillis();
        Deque<Long> dq = treffer.computeIfAbsent(schluessel, k -> new ArrayDeque<>());
        while (!dq.isEmpty() && jetzt - dq.peekFirst() > fensterMillis) {
            dq.pollFirst();
        }
        if (dq.size() >= max) {
            return false;
        }
        dq.addLast(jetzt);
        return true;
    }
}
