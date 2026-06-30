package de.netzfactor.ebz.controlling.integration.rechnung.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.AnhangPort;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdDaten;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdMapper;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.ZugferdService;

/**
 * {@link AnhangPort}-Implementierung des Rechnungsmoduls (erlaubte Richtung {@code rechnung→kommunikation}):
 * liefert dem {@code EmailVersand}-Adapter beim async Rechnungsversand das <b>validierte ZUGFeRD-PDF</b>
 * des Belegs als E-Mail-Anhang. So bleibt der Anhang aus dem serialisierbaren Event heraus — die Spine
 * trägt nur den {@code RECHNUNG}-Kontext (Beleg-ID), die Bytes holt der Versand hier zum Sendezeitpunkt.
 * Das Validierungs-Tor selbst bleibt synchron im {@link RechnungVersandService} (kein Versand invalider Belege).
 */
@ApplicationScoped
public class RechnungAnhangAdapter implements AnhangPort {

    private static final Logger LOG = Logger.getLogger(RechnungAnhangAdapter.class);

    @Inject
    ZugferdService zugferd;

    @Inject
    RechnungZugferdMapper mapper;

    @Override
    public List<Anhang> anhaenge(KontextTyp kontextTyp, Long kontextId) {
        if (kontextTyp != KontextTyp.RECHNUNG || kontextId == null) {
            return List.of();
        }
        Rechnung r = Rechnung.findById(kontextId);
        if (r == null || r.nummer == null) {
            return List.of();
        }
        try {
            RechnungZugferdDaten daten = mapper.baue(r);
            ZugferdService.Ergebnis erg = zugferd.erzeugeUndValidiere(daten);
            if (!erg.valide()) {
                // Sollte nicht passieren — RechnungVersandService validiert vor dem Spine-Event (hartes Tor).
                LOG.errorf("ZUGFeRD für Beleg %s beim Anhang nicht valide — Mail ohne Anhang.", r.nummer);
                return List.of();
            }
            return List.of(new Anhang("beleg-" + r.nummer + ".pdf", erg.pdf(), "application/pdf"));
        } catch (Exception e) {
            // Erzeugung fehlgeschlagen → Mail-Versand wirft (Backoff/Retry/Dead-Letter im Dispatcher).
            throw new IllegalStateException("ZUGFeRD-Anhang für Beleg " + r.nummer + " fehlgeschlagen", e);
        }
    }
}
