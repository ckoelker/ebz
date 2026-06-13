package de.netzfactor.ebz.controlling.integration.rechnung.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungTyp;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Bereich;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungPosition;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Leistungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Zimmerart;

/**
 * Erzeugt aus den aktiven Berufsschul-Anmeldungen eines Zeitraums Rechnungs-Entwürfe (R1).
 * <p>
 * Ablauf: alle {@code AKTIV}en BERUFSSCHULE-Anmeldungen mit {@code schuljahr/halbjahr} →
 * <b>gruppiert je zahlungspflichtigem Debitor</b> (= Sammelrechnung) → je Gruppe eine
 * {@code Rechnung(ENTWURF)} ohne Nummer; je Anmeldung 1–2 Positionen (Unterricht immer, Übernachtung
 * nur bei {@code zimmerart ≠ KEINE}), alle {@code BEFREIT}. Die Positionsbeträge sind aus den
 * Anmeldung-Feldern vorbefüllt (Entscheidung a) und im Entwurf editierbar.
 * <p>
 * <b>Idempotent:</b> über {@code laufSchluessel} (Bereich+Zeitraum+Debitor) wird je Debitor und
 * Zeitraum kein zweiter Entwurf erzeugt; ein erneuter Lauf liefert die bestehenden Entwürfe zurück.
 */
@ApplicationScoped
public class RechnungslaufService {

    private static final String BEFREIUNGSGRUND = "Umsatzsteuerbefreit nach § 4 UStG (Bildungsleistung)";

    @Transactional
    public List<Rechnung> erzeugeBerufsschulEntwuerfe(String schuljahr, int halbjahr) {
        List<Anmeldung> anmeldungen = Anmeldung.list(
                "typ = ?1 and status = ?2 and schuljahr = ?3 and halbjahr = ?4",
                AnmeldungTyp.BERUFSSCHULE, AnmeldungStatus.AKTIV, schuljahr, halbjahr);

        // Gruppierung je zahlungspflichtigem Debitor → eine Sammelrechnung
        Map<Long, List<Anmeldung>> jeDebitor = new LinkedHashMap<>();
        for (Anmeldung a : anmeldungen) {
            jeDebitor.computeIfAbsent(a.zahlungspflichtigerDebitorId, k -> new ArrayList<>()).add(a);
        }

        String zeitraum = "Schuljahr %s, %d. Halbjahr".formatted(schuljahr, halbjahr);
        List<Rechnung> ergebnis = new ArrayList<>();

        for (Map.Entry<Long, List<Anmeldung>> e : jeDebitor.entrySet()) {
            Long debitorId = e.getKey();
            String schluessel = "BERUFSSCHULE|%s|%d|%d".formatted(schuljahr, halbjahr, debitorId);

            Rechnung r = Rechnung.find("laufSchluessel", schluessel).firstResult();
            if (r != null) {
                ergebnis.add(r); // idempotent: bestehenden Entwurf wiederverwenden
                continue;
            }

            r = new Rechnung();
            r.belegart = Belegart.RECHNUNG;
            r.bereich = Bereich.BERUFSSCHULE;
            r.debitorId = debitorId;
            r.zeitraumBezeichnung = zeitraum;
            r.laufSchluessel = schluessel;
            r.status = RechnungStatus.ENTWURF;
            r.persist();

            for (Anmeldung a : e.getValue()) {
                r.positionen.add(unterrichtsposition(r, a, zeitraum));
                if (a.zimmerart != null && a.zimmerart != Zimmerart.KEINE && a.uebernachtungBetragCent != null) {
                    r.positionen.add(uebernachtungsposition(r, a, zeitraum));
                }
            }
            ergebnis.add(r);
        }
        return ergebnis;
    }

    private static RechnungPosition unterrichtsposition(Rechnung r, Anmeldung a, String zeitraum) {
        RechnungPosition p = basis(r, a);
        p.beschreibung = "Unterricht (%s) – %s".formatted(zeitraum, a.teilnehmerName);
        p.einzelbetragCent = a.unterrichtBetragCent == null ? 0 : a.unterrichtBetragCent;
        p.leistungsart = Leistungsart.UNTERRICHT;
        return p;
    }

    private static RechnungPosition uebernachtungsposition(Rechnung r, Anmeldung a, String zeitraum) {
        RechnungPosition p = basis(r, a);
        String zimmer = a.zimmerart == Zimmerart.EINZEL ? "Einzelzimmer" : "Doppelzimmer";
        p.beschreibung = "Übernachtung %s (%s) – %s".formatted(zimmer, zeitraum, a.teilnehmerName);
        p.einzelbetragCent = a.uebernachtungBetragCent;
        p.leistungsart = Leistungsart.UEBERNACHTUNG;
        return p;
    }

    private static RechnungPosition basis(Rechnung r, Anmeldung a) {
        RechnungPosition p = new RechnungPosition();
        p.rechnung = r;
        p.teilnehmerName = a.teilnehmerName;
        p.menge = 1;
        p.steuerfall = Steuerfall.BEFREIT;
        p.steuersatz = 0;
        p.befreiungsgrund = BEFREIUNGSGRUND;
        p.herkunft = "AUTO";
        return p;
    }
}
