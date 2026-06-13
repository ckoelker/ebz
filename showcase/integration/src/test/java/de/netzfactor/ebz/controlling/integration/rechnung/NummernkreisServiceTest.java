package de.netzfactor.ebz.controlling.integration.rechnung;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.service.NummernkreisService;

/**
 * Beweist die Kernzusage des Nummernkreises (GoBD §8.1): Nummern werden lückenlos und – auch unter
 * Nebenläufigkeit – ohne Dublette vergeben. Die Test-DB trägt Vorbestand, daher wird nicht auf den
 * Startwert geprüft, sondern auf <b>Eindeutigkeit</b> und einen <b>lückenlosen, zusammenhängenden
 * Bereich</b> (max − min == n − 1) der frisch gezogenen Nummern.
 */
@QuarkusTest
class NummernkreisServiceTest {

    @Inject
    NummernkreisService nummernkreis;

    private long ziehe(Bereich bereich, Belegart belegart) {
        // vergib(...) ist @Transactional(MANDATORY) → in eine eigene Transaktion einbetten
        String formatiert = QuarkusTransaction.requiringNew().call(() -> nummernkreis.vergib(bereich, belegart));
        return Long.parseLong(formatiert.substring(formatiert.lastIndexOf('-') + 1));
    }

    @Test
    void vergibtLueckenlosNacheinander() {
        long a = ziehe(Bereich.AKADEMIE, Belegart.RECHNUNG);
        long b = ziehe(Bereich.AKADEMIE, Belegart.RECHNUNG);
        long c = ziehe(Bereich.AKADEMIE, Belegart.RECHNUNG);
        Assertions.assertEquals(a + 1, b, "fortlaufend ohne Lücke");
        Assertions.assertEquals(b + 1, c, "fortlaufend ohne Lücke");
    }

    @Test
    void vergibtNebenlaeufigEindeutigUndLueckenlos() throws InterruptedException {
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Long> gezogen = new ConcurrentLinkedQueue<>();
        try {
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        gezogen.add(ziehe(Bereich.SHOP, Belegart.RECHNUNG));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown(); // alle gleichzeitig loslassen → Lock-Wettlauf
            pool.shutdown();
            Assertions.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "alle Vergaben beendet");
        } finally {
            pool.shutdownNow();
        }

        List<Long> nummern = gezogen.stream().sorted().toList();
        Assertions.assertEquals(n, nummern.size(), "alle Threads haben gezogen");
        Assertions.assertEquals(n, nummern.stream().distinct().count(), "keine Dublette");
        long spanne = nummern.get(nummern.size() - 1) - nummern.get(0);
        Assertions.assertEquals(n - 1, spanne, "lückenloser, zusammenhängender Bereich");
    }
}
