package de.netzfactor.ebz.controlling.integration.party.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.party.model.DublettenUrteil;
import de.netzfactor.ebz.controlling.integration.party.model.DublettenUrteil.Einschaetzung;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * Bewertet ein Dubletten-Kandidatenpaar — primär via {@link DublettenKlassifikator} (KI), mit einem
 * <b>robusten regelbasierten Fallback</b> (analog {@code DealEnricher}): ist die KI deaktiviert
 * ({@code anmeldung.dubletten.ki.enabled=false}, z. B. im Test), nicht erreichbar oder liefert
 * Unbrauchbares, wird ein deterministisches Urteil mit {@link Einschaetzung#UNSICHER} erzeugt — der
 * Fall landet dann garantiert in der menschlichen Queue. Die KI <b>entscheidet nie</b>; sie priorisiert.
 *
 * <p>Datensparsamkeit: dem Modell werden nur <i>abstrahierte Merkmale</i> übergeben (normalisierter
 * Name, PLZ, Ort, USt-Vorhandensein bzw. Name/PLZ/Ort bei Personen) — keine Roh-PII wie E-Mail oder
 * die USt-IdNr. selbst (deren Gleichheit steckt bereits deterministisch im {@code matchSchluessel}).
 */
@ApplicationScoped
public class DublettenBerater {

    private static final Logger LOG = Logger.getLogger(DublettenBerater.class);

    @Inject
    DublettenKlassifikator klassifikator;

    @ConfigProperty(name = "anmeldung.dubletten.ki.enabled", defaultValue = "true")
    boolean kiEnabled;

    public DublettenUrteil bewerteFirma(Organisation kandidat, Organisation ziel) {
        return bewerte(merkmaleFirma(kandidat), merkmaleFirma(ziel),
                regelScore(kandidat.matchSchluessel, ziel.matchSchluessel));
    }

    public DublettenUrteil bewertePerson(Person kandidat, Person ziel) {
        return bewerte(merkmalePerson(kandidat), merkmalePerson(ziel),
                regelScore(kandidat.matchSchluessel, ziel.matchSchluessel));
    }

    private DublettenUrteil bewerte(String a, String b, double regelScore) {
        if (kiEnabled) {
            try {
                DublettenUrteil u = klassifikator.vergleiche(a, b);
                DublettenUrteil gueltig = validiere(u);
                if (gueltig != null) {
                    return gueltig;
                }
                LOG.warn("KI-Dublettenurteil ungültig → regelbasierter Fallback");
            } catch (RuntimeException ex) {
                LOG.warnf("KI-Dublettenberater nicht erreichbar (%s) → regelbasierter Fallback",
                        ex.getClass().getSimpleName());
            }
        }
        return new DublettenUrteil(regelScore, Einschaetzung.UNSICHER,
                "regelbasiert (ohne KI): Identitätsschlüssel "
                        + (regelScore >= 0.8 ? "identisch" : "abweichend"));
    }

    /** Halluzinationen abfangen: einschaetzung gesetzt, aehnlichkeit in [0,1]. */
    private static DublettenUrteil validiere(DublettenUrteil u) {
        if (u == null || u.einschaetzung() == null) {
            return null;
        }
        double a = Math.max(0.0, Math.min(1.0, u.aehnlichkeit()));
        String b = (u.begruendung() == null || u.begruendung().isBlank()) ? "(KI ohne Begründung)" : u.begruendung().trim();
        return new DublettenUrteil(a, u.einschaetzung(), b);
    }

    private static double regelScore(String a, String b) {
        return a != null && a.equals(b) ? 0.85 : 0.3;
    }

    private static String merkmaleFirma(Organisation o) {
        PartyHoheitService.Adresse a = PartyHoheitService.orgAdresse(o.id);
        return "Firma; Name=" + norm(o.name) + "; PLZ=" + nz(a.plz()) + "; Ort=" + norm(a.ort())
                + "; USt-Id vorhanden=" + (o.ustId != null && !o.ustId.isBlank() ? "ja" : "nein");
    }

    private static String merkmalePerson(Person p) {
        PartyHoheitService.Adresse a = PartyHoheitService.personAdresse(p.id);
        return "Person; Name=" + norm(p.anzeigeName()) + "; PLZ=" + nz(a.plz()) + "; Ort=" + norm(a.ort());
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
