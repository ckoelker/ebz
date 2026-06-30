package de.netzfactor.ebz.controlling.integration.rechnung.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.rechnung.datev.PeppolVersand;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungVersandStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdMapper;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.ZugferdService;

/**
 * D5 — Versand der E-Rechnung über das Peppol-/TRAFFIQX-Netz (DATEV SmartTransfer), der zweite Weg neben
 * dem E-Mail-Versand ({@link RechnungVersandService}). Erzeugung + Validierung sind identisch (ZUGFeRD,
 * Mustang-Validator als Pflicht-Tor); nur der Transport läuft über den {@link PeppolVersand}-Port. Der
 * Empfänger wird aus der USt-IdNr. des Debitors als Peppol-Teilnehmer-ID abgeleitet (Schema 9930 = DE).
 */
@ApplicationScoped
public class RechnungPeppolVersandService {

    private static final Logger LOG = Logger.getLogger(RechnungPeppolVersandService.class);

    @Inject
    ZugferdService zugferd;

    @Inject
    RechnungZugferdMapper mapper;

    @Inject
    PeppolVersand peppol;

    @Inject
    Prozessspur prozess;

    @Transactional
    public PeppolVersand.Quittung versende(Long id) {
        Rechnung r = Rechnung.findById(id);
        if (r == null) {
            throw RegelVerletzung.nichtGefunden("Beleg nicht gefunden: " + id);
        }
        if (r.nummer == null || r.status == RechnungStatus.ENTWURF) {
            throw new RegelVerletzung("Versand nur für festgeschriebene Belege (Status: " + r.status + ").");
        }
        String ustId = r.debitor == null ? null : r.debitor.ustId;
        if (ustId == null || ustId.isBlank()) {
            throw new RegelVerletzung("Kein Peppol-Empfänger: Debitor "
                    + (r.debitor == null ? "?" : r.debitor.debitorNr) + " hat keine USt-IdNr.");
        }
        String empfaenger = "9930:" + ustId.replaceAll("\\s", ""); // Peppol-Schema 9930 = DE USt-IdNr.

        ZugferdService.Ergebnis erg;
        try {
            erg = zugferd.erzeugeUndValidiere(mapper.baue(r));
        } catch (Exception e) {
            r.versandStatus = RechnungVersandStatus.FEHLGESCHLAGEN;
            throw new RegelVerletzung("ZUGFeRD-Erzeugung für den Peppol-Versand fehlgeschlagen: " + e.getMessage());
        }
        if (!erg.valide()) {
            r.versandStatus = RechnungVersandStatus.FEHLGESCHLAGEN;
            throw new RegelVerletzung("Beleg " + r.nummer
                    + " nicht versendet — ZUGFeRD-Validierung fehlgeschlagen. Report: " + erg.report());
        }

        PeppolVersand.Quittung q = peppol.versende(erg.pdf(), empfaenger, r.nummer);
        r.versandStatus = RechnungVersandStatus.VERSENDET;
        r.versendetAm = Instant.now();
        r.versendetAn = empfaenger;
        LOG.infof("Beleg %s via Peppol/SmartTransfer an %s versendet (ID %s).",
                r.nummer, empfaenger, q.uebertragungsId());
        prozess.schritt("Rechnung via Peppol/SmartTransfer versenden", Prozess.Akteur.SYSTEM,
                Prozess.System.SMARTTRANSFER, Prozess.Typ.MESSAGE, Prozess.Phase.RECHNUNG_VERSAND);
        return q;
    }
}
