package de.netzfactor.ebz.controlling.integration.mandant.service;

import java.util.Collection;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;

/**
 * Landing-Regel A4 (M3c): bildet einen authentifizierten Login auf <b>genau einen</b> {@link Mandant} ab —
 * realm-/claim-basiert, <b>fail-closed</b> ({@code mandanten-vermarktung-planung/PoC-…} §1, A4).
 * <ul>
 *   <li>Gebrokerter B2B-Login: der Kunden-IdP stempelt pro Kunde einen einfachen {@value #CLAIM_MANDANT}-
 *       Claim (= Mandant-Schlüssel; Teil der kundenindividuellen, geseedeten IdP-Config). → der genannte
 *       Mandant, sofern {@link Mandant.Status#AKTIV}; sonst <b>abgewiesen</b>.</li>
 *   <li>Gebrokerter Login <i>ohne</i> {@value #CLAIM_MANDANT}-Claim, aber mit Org-Mitgliedschaft
 *       ({@value #CLAIM_ORGANIZATION}-Claim) → <b>abgewiesen</b> (kein Cross-Tenant-Leak ins EBZ-Branding).</li>
 *   <li>Direkter Login ohne beide Claims → der EBZ-<b>Kernmandant</b> nach Realm: {@code ebz-staff} →
 *       {@link Mandant.Vertragstyp#EBZ_STAFF}, sonst {@link Mandant.Vertragstyp#EBZ_CUSTOMER} (B2C, legitim).</li>
 * </ul>
 * Reiner Service (Claims als {@code Map}) → ohne echtes OIDC unit-testbar; die HTTP-Naht reicht die
 * Token-Claims herein.
 */
@ApplicationScoped
public class MandantLandingResolver {

    private static final Logger LOG = Logger.getLogger(MandantLandingResolver.class);

    /** Vom Kunden-IdP gestempelter Claim mit dem Mandant-Schlüssel (Landing-Linchpin, A4). */
    public static final String CLAIM_MANDANT = "mandant";

    /** Org-Membership-Claim (scope-gated) — Präsenz = B2B/gebrokerte Mitgliedschaft. */
    public static final String CLAIM_ORGANIZATION = "organization";

    /** Fail-closed-Signal der Landing-Regel → die HTTP-Naht übersetzt es in {@code 403}. */
    public static class MandantLandingException extends RuntimeException {
        public MandantLandingException(String message) {
            super(message);
        }
    }

    /**
     * Löst den Login auf einen Mandanten auf (A4). Wirft {@link MandantLandingException} (fail-closed), wenn
     * ein gebrokerter Login keinen passenden aktiven Mandanten ergibt.
     */
    @Transactional
    public Mandant aufloesen(String realm, Map<String, Object> claims) {
        String schluessel = claimAlsString(claims, CLAIM_MANDANT);
        if (schluessel != null && !schluessel.isBlank()) {
            Mandant m = Mandant.find("schluessel", schluessel.trim()).firstResult();
            if (m == null || m.status != Mandant.Status.AKTIV) {
                throw new MandantLandingException("Gebrokerter Login mit mandant-Claim '" + schluessel
                        + "' ohne aktiven Mandanten → abgewiesen (fail-closed, A4).");
            }
            LOG.debugf("Landing: mandant-Claim '%s' → Mandant %d", schluessel, m.id);
            return m;
        }
        // Kein mandant-Claim: ein gebrokerter (Org-Mitglied) Login ohne Claim ist fail-closed.
        if (hatOrgMitgliedschaft(claims)) {
            throw new MandantLandingException(
                    "Gebrokerter Login (Org-Mitglied) ohne mandant-Claim → abgewiesen (fail-closed, A4).");
        }
        // Direkter Login → EBZ-Kernmandant nach Realm.
        Mandant.Vertragstyp typ = istStaffRealm(realm)
                ? Mandant.Vertragstyp.EBZ_STAFF
                : Mandant.Vertragstyp.EBZ_CUSTOMER;
        Mandant kern = Mandant.find("vertragstyp = ?1 and status = ?2", typ, Mandant.Status.AKTIV).firstResult();
        if (kern == null) {
            throw new MandantLandingException("Kein aktiver EBZ-Kernmandant (" + typ + ") konfiguriert.");
        }
        LOG.debugf("Landing: direkter Login (Realm %s) → Kernmandant %s", realm, typ);
        return kern;
    }

    /** {@code true}, wenn der {@value #CLAIM_ORGANIZATION}-Claim mit nicht-leerem Inhalt vorliegt. */
    private static boolean hatOrgMitgliedschaft(Map<String, Object> claims) {
        Object org = claims == null ? null : claims.get(CLAIM_ORGANIZATION);
        if (org == null) {
            return false;
        }
        if (org instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (org instanceof Map<?, ?> mp) {
            return !mp.isEmpty();
        }
        return !org.toString().isBlank();
    }

    /** Liest einen String-Claim; akzeptiert auch eine einelementige Collection (KC-Multivalue). */
    private static String claimAlsString(Map<String, Object> claims, String name) {
        Object v = claims == null ? null : claims.get(name);
        if (v == null) {
            return null;
        }
        if (v instanceof Collection<?> c) {
            return c.isEmpty() ? null : String.valueOf(c.iterator().next());
        }
        return String.valueOf(v);
    }

    private static boolean istStaffRealm(String realm) {
        return realm != null && realm.toLowerCase().contains("staff");
    }
}
