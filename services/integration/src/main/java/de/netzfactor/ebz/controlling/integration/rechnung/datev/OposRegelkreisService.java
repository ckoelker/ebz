package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RechnungService;

/**
 * D3 — OPOS-Regelkreis (DATEV → Kern): liest die ausgeglichenen offenen Posten aus der {@link OposQuelle}
 * und setzt den zugehörigen Beleg (Match über die Belegnummer) von {@code AUSGESTELLT} auf {@code BEZAHLT}
 * (über den bestehenden {@link RechnungService#bezahlen}). Idempotent: bereits bezahlte/nicht offene Belege
 * werden übersprungen. Damit löst der Zahlungsstatus aus DATEV den manuellen Zahlungseingang ab; das
 * BEZAHLT-Setzen speist zugleich das Controlling.
 */
@ApplicationScoped
public class OposRegelkreisService {

    private static final Logger LOG = Logger.getLogger(OposRegelkreisService.class);

    @Inject
    OposQuelle quelle;

    @Inject
    RechnungService rechnungService;

    @Inject
    Prozessspur prozess;

    @Transactional
    public int verarbeiteFaellige(int max) {
        int verbucht = 0;
        for (OposQuelle.Posten p : quelle.ausgeglichenePosten(max)) {
            Rechnung r = Rechnung.find("nummer", p.belegnummer()).firstResult();
            if (r == null || r.status != RechnungStatus.AUSGESTELLT) {
                continue; // idempotent: nur offene Forderungen
            }
            Long betrag = p.betragCent() > 0 ? Long.valueOf(p.betragCent()) : null;
            rechnungService.bezahlen(r.id, p.bezahltAm(), betrag, p.referenz());
            prozess.schritt("DATEV-OPOS: Zahlungseingang verbucht (BEZAHLT)", Prozess.Akteur.EBZ,
                    Prozess.System.DATEV, Prozess.Typ.SERVICE_TASK, Prozess.Phase.ZAHLUNGSEINGANG);
            verbucht++;
        }
        if (verbucht > 0) {
            LOG.infof("OPOS-Regelkreis: %d Beleg(e) auf BEZAHLT gesetzt.", verbucht);
        }
        return verbucht;
    }
}
