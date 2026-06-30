package de.netzfactor.ebz.controlling.integration.outbox.suite8;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Zielsystem;
import de.netzfactor.ebz.controlling.integration.outbox.service.Zielsystemexport;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;

/**
 * {@link Zielsystemexport}-Adapter für Suite8: legt den Azubi der Anmeldung als Suite8-Konto an und
 * stößt damit die Bezahlkarte für Kiosk &amp; Kantine an. Idempotent (Suite8 vergibt je E-Mail genau
 * eine Karte), wirft bei Downtime {@link Suite8Mock.Suite8NichtVerfuegbar} weiter — Retry/Dead-Letter
 * übernimmt der Dispatcher.
 */
@ApplicationScoped
public class Suite8Export implements Zielsystemexport {

    @Inject
    Suite8Mock suite8;

    @Override
    public Zielsystem ziel() {
        return Zielsystem.SUITE8;
    }

    @Override
    public void exportiere(OutboxAuftrag auftrag) {
        Anmeldung a = auftrag.anmeldung;
        suite8.legeKontoAn(a.teilnehmerName, a.teilnehmerEmail);
    }
}
