package de.netzfactor.ebz.controlling.integration.outbox.webuntis;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Zielsystem;
import de.netzfactor.ebz.controlling.integration.outbox.service.Zielsystemexport;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;

/**
 * {@link Zielsystemexport}-Adapter für WebUntis: überträgt den Azubi der Anmeldung als Schüler ins
 * (gemockte) WebUntis. Idempotent (WebUntis legt per E-Mail keinen Dublett an), wirft bei Downtime
 * {@link WebUntisMock.WebUntisNichtVerfuegbar} weiter — der Dispatcher übernimmt Retry/Dead-Letter.
 */
@ApplicationScoped
public class WebUntisExport implements Zielsystemexport {

    @Inject
    WebUntisMock webuntis;

    @Override
    public Zielsystem ziel() {
        return Zielsystem.WEBUNTIS;
    }

    @Override
    public void exportiere(OutboxAuftrag auftrag) {
        Anmeldung a = auftrag.anmeldung;
        webuntis.importiereSchueler(a.teilnehmerName, a.teilnehmerEmail, klasse(a));
    }

    /** Klassen-Bezeichnung aus Schuljahr/Halbjahr (Showcase-Heuristik). */
    private static String klasse(Anmeldung a) {
        if (a.schuljahr == null) {
            return "Berufsschule";
        }
        return "BS " + a.schuljahr + (a.halbjahr == null ? "" : " (HJ " + a.halbjahr + ")");
    }
}
