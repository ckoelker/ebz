package de.netzfactor.ebz.controlling.integration.lms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.EinschreibungStatus;
import de.netzfactor.ebz.controlling.integration.lms.model.Kurseinschreibung;
import de.netzfactor.ebz.controlling.integration.lms.model.WbtKurs;
import de.netzfactor.ebz.controlling.integration.lms.service.KurseinschreibungService;

/**
 * L2-Beweis: die {@link KurseinschreibungService}-Outbox-Naht (anfordern → Dispatcher → OpenOLAT)
 * inkl. Idempotenz, Erfolg, Backoff und Dead-Letter. OpenOLAT ist per {@link FakeOpenolatProvisioning}
 * ersetzt (kein echter Call); der Scheduler ist im Test-Profil faktisch aus → der Test treibt
 * {@code verarbeiteFaellige()} selbst. Test-DB = echte {@code controlling}-DB → eindeutige Codes/Subs.
 */
@QuarkusTest
class KurseinschreibungServiceTest {

    @Inject
    KurseinschreibungService service;

    @Inject
    FakeOpenolatProvisioning openolat;

    @BeforeEach
    void setup() {
        openolat.reset();
    }

    private Long neuerKurs() {
        return QuarkusTransaction.requiringNew().call(() -> {
            WbtKurs k = new WbtKurs();
            k.code = "WBT-T-" + (System.nanoTime() % 1_000_000_000L);
            k.titel = "Testkurs";
            k.status = AngebotStatus.AKTIV;
            k.openolatKey = 884736L;
            k.persist();
            return k.id;
        });
    }

    private EinschreibungStatus statusVon(Long id) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Kurseinschreibung e = Kurseinschreibung.findById(id);
            return e == null ? null : e.status;
        });
    }

    @Test
    void anfordernIstIdempotent() {
        Long kursId = neuerKurs();
        String sub = UUID.randomUUID().toString();

        Long id1 = service.anfordern(sub, "a@ebz.de", "Anna Azubi", kursId, "ORD-1").id;
        Long id2 = service.anfordern(sub, "a@ebz.de", "Anna Azubi", kursId, "ORD-1").id;

        assertEquals(id1, id2, "Zweite Anforderung darf keine zweite Zeile erzeugen (Unique kurs×sub)");
        long anzahl = QuarkusTransaction.requiringNew()
                .call(() -> Kurseinschreibung.count("keycloakSub", sub));
        assertEquals(1, anzahl);
        assertEquals(EinschreibungStatus.ANGEFORDERT, statusVon(id1));
    }

    @Test
    void dispatcherSchreibtEin() {
        Long kursId = neuerKurs();
        String sub = UUID.randomUUID().toString();
        openolat.nextIdentityKey = 458752L;
        Long id = service.anfordern(sub, "c@ebz.de", "Carla Kundin", kursId, "ORD-2").id;

        service.verarbeite(id);

        Kurseinschreibung e = QuarkusTransaction.requiringNew().call(() -> Kurseinschreibung.findById(id));
        assertEquals(EinschreibungStatus.EINGESCHRIEBEN, e.status);
        assertEquals(458752L, e.openolatIdentityKey);
        assertNull(e.letzterFehler);
        assertEquals(1, openolat.enrolCalls.get());
    }

    @Test
    void fehlerFuehrtZuBackoff() {
        Long kursId = neuerKurs();
        String sub = UUID.randomUUID().toString();
        openolat.fail = true;
        Long id = service.anfordern(sub, "x@ebz.de", "X Y", kursId, "ORD-3").id;

        service.verarbeite(id);

        Kurseinschreibung e = QuarkusTransaction.requiringNew().call(() -> Kurseinschreibung.findById(id));
        assertEquals(EinschreibungStatus.ANGEFORDERT, e.status, "noch nicht eskaliert → bleibt offen");
        assertEquals(1, e.versuche);
        assertNotNull(e.letzterFehler);
        assertTrue(e.naechsterVersuchAm.isAfter(Instant.now()), "Backoff: nächster Versuch in der Zukunft");
    }

    @Test
    void deadLetterNachMaxVersuchen() {
        Long kursId = neuerKurs();
        String sub = UUID.randomUUID().toString();
        openolat.fail = true;
        Long id = service.anfordern(sub, "z@ebz.de", "Z Z", kursId, "ORD-4").id;
        // Auf "kurz vor Eskalation" setzen und sofort fällig machen.
        QuarkusTransaction.requiringNew().run(() -> {
            Kurseinschreibung e = Kurseinschreibung.findById(id);
            e.versuche = Kurseinschreibung.MAX_VERSUCHE - 1;
            e.naechsterVersuchAm = Instant.now();
        });

        service.verarbeite(id);

        assertEquals(EinschreibungStatus.FEHLGESCHLAGEN, statusVon(id), "Dead-Letter nach MAX_VERSUCHE");
    }

    @Test
    void stornoSchreibtAus() {
        Long kursId = neuerKurs();
        String sub = UUID.randomUUID().toString();
        Long id = service.anfordern(sub, "s@ebz.de", "S S", kursId, "ORD-5").id;
        service.verarbeite(id); // EINGESCHRIEBEN
        assertEquals(EinschreibungStatus.EINGESCHRIEBEN, statusVon(id));

        service.ausschreiben(id);
        service.verarbeite(id);

        assertEquals(EinschreibungStatus.AUSGESCHRIEBEN, statusVon(id));
        assertEquals(1, openolat.unenrolCalls.get());
    }
}
