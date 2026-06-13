package de.netzfactor.ebz.controlling.integration.rechnung.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.rechnung.dto.DebitorAnlageDto;
import de.netzfactor.ebz.controlling.integration.rechnung.dto.ExterneBestellung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Debitor;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Leistungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungPosition;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;

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
        String schluessel = b.quelle() + "|" + b.externeId();
        Rechnung vorhanden = Rechnung.find("laufSchluessel", schluessel).firstResult();
        if (vorhanden != null) {
            return vorhanden; // idempotent: pro externer Bestellung genau ein Beleg
        }

        DebitorAnlageDto d = b.debitor();
        Debitor debitor = debitorHoheit.findeOderLege(new DebitorHoheitService.Stammdaten(
                d.bereich(), d.rolle(), d.name(), d.strasse(), d.plz(), d.ort(), d.land(),
                d.ustId(), d.iban(), d.email()));

        Rechnung r = new Rechnung();
        r.belegart = Belegart.RECHNUNG;
        r.bereich = d.bereich();
        r.debitorId = debitor.id;
        r.zeitraumBezeichnung = "%s-Bestellung %s (%s)".formatted(b.quelle(), b.externeId(), b.zahlungsart());
        r.laufSchluessel = schluessel;
        r.status = RechnungStatus.ENTWURF;
        r.persist();

        for (ExterneBestellung.Position pos : b.positionen()) {
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
