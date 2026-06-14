package de.netzfactor.ebz.controlling.integration.rechnung.service;

import java.time.LocalDate;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.rechnung.dto.ManuellePositionDto;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Belegart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Leistungsart;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungPosition;
import de.netzfactor.ebz.controlling.integration.rechnung.model.RechnungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;

/**
 * Lebenszyklus eines Belegs: Ausstellen (Festschreibung + lückenlose Nummer), Storno/Gutschrift/
 * Nachberechnung (Korrekturbelege mit Bezug aufs Original) und das Ergänzen manueller Positionen
 * (nur im ENTWURF). Verstöße gegen die Festschreibung werfen {@link RegelVerletzung} (→ 409).
 */
@ApplicationScoped
public class RechnungService {

    @Inject
    NummernkreisService nummernkreis;

    /** Schreibt eine Position aus dem manuellen DTO; nutzt Defaults für menge/steuersatz analog Lauf. */
    @Transactional
    public Rechnung addManuellePosition(Long rechnungId, ManuellePositionDto dto) {
        Rechnung r = mussExistieren(rechnungId);
        if (r.status != RechnungStatus.ENTWURF) {
            throw new RegelVerletzung("Positionen können nur im Status ENTWURF ergänzt werden (Festschreibung ab Ausstellung).");
        }
        r.positionen.add(ausDto(r, dto, "MANUELL"));
        return r;
    }

    /**
     * Stellt den Entwurf aus: vergibt die lückenlose Nummer (atomar), setzt {@code ausstellungsdatum}
     * und Status {@code AUSGESTELLT}. Danach unveränderbar. Idempotenz-Schutz: nur ein ENTWURF mit
     * mindestens einer Position lässt sich ausstellen.
     */
    @Transactional
    public Rechnung ausstellen(Long rechnungId) {
        Rechnung r = mussExistieren(rechnungId);
        if (r.status != RechnungStatus.ENTWURF) {
            throw new RegelVerletzung("Nur ein ENTWURF kann ausgestellt werden (aktuell: " + r.status + ").");
        }
        if (r.positionen.isEmpty()) {
            throw new RegelVerletzung("Eine Rechnung ohne Positionen kann nicht ausgestellt werden.");
        }
        r.nummer = nummernkreis.vergib(r.bereich, r.belegart);
        r.ausstellungsdatum = LocalDate.now();
        r.status = RechnungStatus.AUSGESTELLT;
        return r;
    }

    /** Vollständige Umkehr einer ausgestellten Rechnung als eigener STORNO-Beleg (gespiegelte Beträge). */
    @Transactional
    public Rechnung storno(Long rechnungId) {
        Rechnung orig = mussAusgestellt(rechnungId);
        Rechnung s = korrekturkopf(orig, Belegart.STORNO);
        for (RechnungPosition p : orig.positionen) {
            RechnungPosition k = new RechnungPosition();
            k.rechnung = s;
            k.teilnehmerName = p.teilnehmerName;
            k.beschreibung = "Storno: " + p.beschreibung;
            k.menge = p.menge;
            k.einzelbetragCent = -p.einzelbetragCent; // Umkehr
            k.steuerfall = p.steuerfall;
            k.steuersatz = p.steuersatz;
            k.befreiungsgrund = p.befreiungsgrund;
            k.leistungsart = Leistungsart.KORREKTUR;
            k.herkunft = "AUTO";
            s.positionen.add(k);
        }
        s.nummer = nummernkreis.vergib(s.bereich, s.belegart);
        s.ausstellungsdatum = LocalDate.now();
        s.status = RechnungStatus.AUSGESTELLT;
        orig.status = RechnungStatus.STORNIERT;
        return s;
    }

    /** Gutschrift (negative Beträge) zu einer Rechnung; Positionen manuell erfasst (variabel). */
    @Transactional
    public Rechnung gutschrift(Long rechnungId, String grund, List<ManuellePositionDto> positionen) {
        return korrekturmitPositionen(rechnungId, Belegart.GUTSCHRIFT, grund, positionen, true);
    }

    /** Nachberechnung (positive Beträge) zu einer Rechnung; Positionen manuell erfasst (variabel). */
    @Transactional
    public Rechnung nachberechnung(Long rechnungId, String grund, List<ManuellePositionDto> positionen) {
        return korrekturmitPositionen(rechnungId, Belegart.NACHBERECHNUNG, grund, positionen, false);
    }

    // ───────────────────────── intern ─────────────────────────

    private Rechnung korrekturmitPositionen(Long rechnungId, Belegart art, String grund,
            List<ManuellePositionDto> positionen, boolean negieren) {
        Rechnung orig = mussAusgestellt(rechnungId);
        Rechnung k = korrekturkopf(orig, art);
        if (grund != null && !grund.isBlank()) {
            k.zeitraumBezeichnung = orig.zeitraumBezeichnung + " – " + grund;
        }
        for (ManuellePositionDto dto : positionen) {
            RechnungPosition p = ausDto(k, dto, "MANUELL");
            if (negieren) {
                p.einzelbetragCent = -Math.abs(p.einzelbetragCent);
            }
            k.positionen.add(p);
        }
        k.nummer = nummernkreis.vergib(k.bereich, k.belegart);
        k.ausstellungsdatum = LocalDate.now();
        k.status = RechnungStatus.AUSGESTELLT;
        return k;
    }

    private static Rechnung korrekturkopf(Rechnung orig, Belegart art) {
        Rechnung k = new Rechnung();
        k.belegart = art;
        k.bereich = orig.bereich;
        k.debitor = orig.debitor;
        k.zeitraumBezeichnung = orig.zeitraumBezeichnung;
        k.originalRechnung = orig;
        k.status = RechnungStatus.ENTWURF;
        k.persist();
        return k;
    }

    private static RechnungPosition ausDto(Rechnung r, ManuellePositionDto dto, String herkunft) {
        RechnungPosition p = new RechnungPosition();
        p.rechnung = r;
        p.teilnehmerName = dto.teilnehmerName();
        p.beschreibung = dto.beschreibung();
        p.menge = dto.menge() <= 0 ? 1 : dto.menge();
        p.einzelbetragCent = dto.einzelbetragCent();
        p.steuerfall = dto.steuerfall() == null ? Steuerfall.BEFREIT : dto.steuerfall();
        p.steuersatz = dto.steuersatz();
        p.befreiungsgrund = dto.befreiungsgrund();
        p.leistungsart = dto.leistungsart() == null ? Leistungsart.SONSTIGE : dto.leistungsart();
        p.herkunft = herkunft;
        return p;
    }

    private static Rechnung mussExistieren(Long id) {
        Rechnung r = Rechnung.findById(id);
        if (r == null) {
            throw RegelVerletzung.nichtGefunden("Rechnung nicht gefunden: " + id);
        }
        return r;
    }

    private static Rechnung mussAusgestellt(Long id) {
        Rechnung r = mussExistieren(id);
        if (r.status != RechnungStatus.AUSGESTELLT && r.status != RechnungStatus.BEZAHLT) {
            throw new RegelVerletzung("Korrektur nur zu einer ausgestellten Rechnung möglich (aktuell: " + r.status + ").");
        }
        return r;
    }
}
