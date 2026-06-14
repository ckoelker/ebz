package de.netzfactor.ebz.controlling.integration.rechnung.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

import de.netzfactor.ebz.controlling.integration.rechnung.dto.DebitorAnlageDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.ExterneBestellung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Leistungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungPosition;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zahlungsart;

/**
 * Überführt eine externe Bestellung (R7, z. B. bezahlte Vendure-Order) <b>quellen-agnostisch</b> in
 * einen Rechnungs-Entwurf — die zweite Abrechnungsquelle neben dem Anmeldungs-Rechnungslauf. Der Kunde
 * wird über die Debitoren-Hoheit (R3) idempotent aufgelöst/angelegt; die Bestellung selbst ist über
 * {@code quelle|externeId} idempotent (kein Doppel-Beleg bei erneutem Push). Festschreibung/ZUGFeRD/
 * GoBD/DATEV laufen anschließend über denselben Beleg-Lebenszyklus wie bei der Anmeldung.
 */
@ApplicationScoped
public class BestellungBillingService {

    @Inject
    DebitorHoheitService debitorHoheit;

    @Transactional
    public Rechnung ausBestellung(ExterneBestellung b) {
        DebitorAnlageDto d = b.debitor();
        Debitor debitor = debitorHoheit.findeOderLege(new DebitorHoheitService.Stammdaten(
                d.bereich(), d.rolle(), d.name(), d.strasse(), d.plz(), d.ort(), d.land(),
                d.ustId(), d.iban(), d.email()));
        return belegAusBestellung(b.quelle(), b.externeId(), b.zahlungsart(), d.bereich(),
                debitor.id, b.positionen());
    }

    /**
     * Beleg-Erzeugung mit <b>bereits aufgelöstem</b> Debitor — die gemeinsame Naht für beide R7-Wege:
     * den quellen-agnostischen Debitor-Stammdaten-Push ({@link #ausBestellung}) und den
     * identitäts-/kontextgeführten Shop-Weg (Party-Kern). Idempotent über {@code quelle|externeId}.
     */
    @Transactional
    public Rechnung belegAusBestellung(String quelle, String externeId, Zahlungsart zahlungsart,
            Bereich bereich, Long debitorId, List<ExterneBestellung.Position> positionen) {
        String schluessel = quelle + "|" + externeId;
        Rechnung vorhanden = Rechnung.find("laufSchluessel", schluessel).firstResult();
        if (vorhanden != null) {
            return vorhanden; // idempotent: pro externer Bestellung genau ein Beleg
        }

        Rechnung r = new Rechnung();
        r.belegart = Belegart.RECHNUNG;
        r.bereich = bereich;
        r.debitorId = debitorId;
        r.zeitraumBezeichnung = "%s-Bestellung %s (%s)".formatted(quelle, externeId, zahlungsart);
        r.laufSchluessel = schluessel;
        r.status = RechnungStatus.ENTWURF;
        r.persist();

        for (ExterneBestellung.Position pos : positionen) {
            RechnungPosition p = new RechnungPosition();
            p.rechnung = r;
            p.beschreibung = pos.beschreibung();
            p.menge = 1;
            p.einzelbetragCent = (int) pos.betragCent();
            p.steuerfall = pos.steuerfall();
            p.steuersatz = pos.steuersatz() == null ? 0 : pos.steuersatz();
            p.befreiungsgrund = pos.steuerfall() == Steuerfall.BEFREIT
                    ? "Umsatzsteuerbefreit nach § 4 UStG" : null;
            p.leistungsart = Leistungsart.SONSTIGE;
            p.herkunft = "EXTERN";
            r.positionen.add(p);
        }
        return r;
    }
}
