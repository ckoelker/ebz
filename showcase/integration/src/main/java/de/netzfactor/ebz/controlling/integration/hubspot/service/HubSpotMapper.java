package de.netzfactor.ebz.controlling.integration.hubspot.service;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke.CompanyDto;
import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke.ContactDto;
import de.netzfactor.ebz.controlling.integration.party.model.Lookups;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * Bildet Party-Entities auf die HubSpot-DTOs ab — reine Projektion, keine Seiteneffekte. Der stabile
 * Upsert-Schlüssel ist die MDM-PK ({@code externeId}), nicht die E-Mail.
 * <p>
 * <b>Datenminimierung (Art. 5/9):</b> bewusst werden <i>nur</i> Marketing-relevante Merkmale übertragen.
 * Sensible Person-Felder (Geschlecht, Geburtsdatum, Staatsangehörigkeit) bleiben im MDM-Kern.
 * Lookup-Werte gehen über ihre {@code bezeichnung}; N:M-Mengen als alphabetisch sortierte Semikolon-Liste
 * (HubSpot-Multi-Select).
 */
@ApplicationScoped
public class HubSpotMapper {

    @Inject
    HubSpotConsentGate consentGate;

    /** Stabile externe ID einer Person für den HubSpot-Upsert ({@code ebz_party_id}-Wert). */
    public static String externeIdPerson(Person p) {
        return String.valueOf(p.id);
    }

    /** Stabile externe ID einer Organisation für den HubSpot-Upsert ({@code ebz_org_id}-Wert). */
    public static String externeIdOrganisation(Organisation o) {
        return String.valueOf(o.id);
    }

    public ContactDto zuContact(Person p) {
        return new ContactDto(
                externeIdPerson(p),
                PartyHoheitService.primaerEmail(p.id),
                p.vorname,
                p.nachname,
                p.briefanrede(),
                consentGate.istMarketingErlaubt(p),
                bez(p.leadQuelle),
                Map.of());
    }

    public CompanyDto zuCompany(Organisation o) {
        return new CompanyDto(
                externeIdOrganisation(o),
                o.name,
                domain(o.website),
                o.ustId,
                bez(o.branche),
                liste(o.verbandszugehoerigkeiten),
                liste(o.taetigkeitsschwerpunkte),
                liste(o.unternehmenstypen),
                o.bestandsgroesse,
                o.gewerbeerlaubnis == null ? null : o.gewerbeerlaubnis.name(),
                o.ausbildungsbetrieb,
                bez(o.ihkKammer),
                bez(o.leadQuelle),
                Map.of());
    }

    /** {@code https://www.example.com/x} → {@code example.com} (HubSpot-Company-Schlüssel {@code domain}). */
    static String domain(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        String d = website.trim().toLowerCase()
                .replaceFirst("^https?://", "")
                .replaceFirst("^www\\.", "");
        int slash = d.indexOf('/');
        return slash > 0 ? d.substring(0, slash) : d;
    }

    private static String bez(Lookups.LookupBase l) {
        return l == null ? null : l.bezeichnung;
    }

    private static String liste(Set<? extends Lookups.LookupBase> werte) {
        if (werte == null || werte.isEmpty()) {
            return null;
        }
        return werte.stream()
                .map(w -> w.bezeichnung)
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .collect(Collectors.joining(";"));
    }
}
