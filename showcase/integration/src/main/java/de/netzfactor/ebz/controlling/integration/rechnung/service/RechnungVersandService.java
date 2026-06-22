package de.netzfactor.ebz.controlling.integration.rechnung.service;

import java.time.Instant;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungVersandStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdDaten;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.ZugferdService;

/**
 * Rechnungsversand: stellt einem <b>festgeschriebenen</b> Beleg seine ZUGFeRD-E-Rechnung (PDF/A-3 +
 * EN-16931-XML) per E-Mail an den Debitor zu und hält den {@link RechnungVersandStatus} nach. Der
 * Mustang-Validator bleibt das harte Tor — ein nicht valides Dokument wird nicht versendet (Status
 * {@code FEHLGESCHLAGEN}, 409). Genutzt wird derselbe Quarkus-{@link Mailer} wie bei der Anmeldung
 * (Dev → Mailpit; Test → MockMailbox). Erneuter Aufruf ist erlaubt (Re-Send, {@code versendetAm} wird
 * aktualisiert) — Zahlungseingang/Mahnwesen bleiben bei DATEV.
 */
@ApplicationScoped
public class RechnungVersandService {

    private static final Logger LOG = Logger.getLogger(RechnungVersandService.class);

    @Inject
    ZugferdService zugferd;

    @Inject
    de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdMapper mapper;

    /**
     * Event-Spine: der Versand fährt über ein {@link KommunikationsEreignis} (Direkt-Empfänger Debitor-
     * Postfach, kein Person-Bezug) → Template + Zustell-Outbox (Retry/Dead-Letter); das ZUGFeRD-PDF hängt
     * der {@code RechnungAnhangAdapter} (AnhangPort) beim Versand an. Das Validierungs-Tor bleibt hier synchron.
     */
    @Inject
    Event<KommunikationsEreignis> benachrichtigung;

    @Inject
    Prozessspur prozess;

    /**
     * Versendet die E-Rechnung des Belegs an die Debitor-Adresse und markiert ihn als versendet.
     *
     * @throws RegelVerletzung 404 (Beleg fehlt), 409 (nicht festgeschrieben, kein Debitor-Postfach oder
     *                         ZUGFeRD nicht valide)
     */
    @Transactional
    public Rechnung versende(Long id) {
        Rechnung r = Rechnung.findById(id);
        if (r == null) {
            throw RegelVerletzung.nichtGefunden("Beleg nicht gefunden: " + id);
        }
        if (r.nummer == null || r.status == RechnungStatus.ENTWURF) {
            throw new RegelVerletzung("Versand nur für festgeschriebene Belege (Status: " + r.status + ").");
        }
        String email = r.debitor == null ? null : r.debitor.email;
        if (email == null || email.isBlank()) {
            throw new RegelVerletzung("Debitor " + (r.debitor == null ? "?" : r.debitor.debitorNr)
                    + " hat keine E-Mail-Adresse für den Rechnungsversand.");
        }

        // Validiertes ZUGFeRD erzeugen — Validator ist Pflicht-Tor (kein Versand eines invaliden Belegs).
        ZugferdService.Ergebnis erg;
        try {
            RechnungZugferdDaten daten = mapper.baue(r);
            erg = zugferd.erzeugeUndValidiere(daten);
        } catch (Exception e) {
            r.versandStatus = RechnungVersandStatus.FEHLGESCHLAGEN;
            throw new RegelVerletzung("ZUGFeRD-Erzeugung für den Versand fehlgeschlagen: " + e.getMessage());
        }
        if (!erg.valide()) {
            r.versandStatus = RechnungVersandStatus.FEHLGESCHLAGEN;
            throw new RegelVerletzung("Beleg " + r.nummer
                    + " nicht versendet — ZUGFeRD-Validierung fehlgeschlagen. Report: " + erg.report());
        }

        // Versand über die Spine: Direkt-Empfänger Debitor-Postfach (kein Person-Bezug) → Template +
        // Zustell-Outbox; das validierte ZUGFeRD-PDF hängt der RechnungAnhangAdapter (AnhangPort) beim
        // Versand an (Kontext RECHNUNG/Beleg-ID). Re-Send erlaubt → kein Dedupe-Schlüssel.
        benachrichtigung.fire(KommunikationsEreignis.anEmpfaenger(
                EreignisTyp.RECHNUNG_VERSANDT, email, "Ihre Rechnung " + r.nummer + " vom EBZ",
                KontextTyp.RECHNUNG, r.id, null,
                Map.of("nummer", r.nummer, "debitorNr", r.debitor.debitorNr,
                        "zahlungszielTage", r.zahlungszielTage)));

        r.versandStatus = RechnungVersandStatus.VERSENDET;
        r.versendetAm = Instant.now();
        r.versendetAn = email;
        LOG.infof("Beleg %s als E-Rechnung an %s versendet.", r.nummer, email);

        prozess.schritt("Rechnung versenden", Akteur.SYSTEM, Prozess.System.MAIL, Typ.MESSAGE,
                Phase.RECHNUNG_VERSAND);
        return r;
    }
}
