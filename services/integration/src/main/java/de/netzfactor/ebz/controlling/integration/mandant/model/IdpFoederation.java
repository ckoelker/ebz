package de.netzfactor.ebz.controlling.integration.mandant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Identitäts-Föderation eines B2B-{@link Mandant}en: das MDM ist Source-of-Truth fürs Domain→Mandant-Mapping
 * (A3) und projiziert daraus eine <b>Keycloak-Organization</b> (v26 GA, M3) mit gebrokertem Kunden-IdP. Der
 * Login {@code @kunde.de} landet über die hinterlegten {@link #emailDomains} im richtigen IdP-Redirect und
 * trägt den {@code mandant}-Claim → Org X (K4). Föderierte Logins ohne erwarteten Claim werden
 * <b>fail-closed</b> abgewiesen (A4).
 * <p>
 * EBZ-Kontexte ({@code EBZ_CUSTOMER}/{@code EBZ_STAFF}) brauchen keine Föderation (Direkt-Login im
 * jeweiligen Realm) → diese Entity gilt nur für {@code ENTERPRISE_FLAT}. Flach im Schema {@code mdm}, echte
 * FK auf {@link Mandant}.
 */
@Entity
@Table(name = "idp_foederation", schema = "mdm")
public class IdpFoederation extends PanacheEntity {

    /** Brokering-Protokoll des Kunden-IdP. */
    public enum Protokoll {
        OIDC, SAML
    }

    /** Provisionierungs-Zustand der Keycloak-Organization/IdP. */
    public enum Status {
        ENTWURF, AKTIV, INAKTIV
    }

    @Version
    public long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mandant_id", nullable = false)
    public Mandant mandant;

    /** Keycloak-IdP-Alias der gebrokerten Verbindung (eindeutig je Realm). */
    @Column(name = "idp_alias", nullable = false, length = 60)
    public String idpAlias;

    /** E-Mail-Domains des Kunden, die auf diesen IdP routen — Semikolon-separiert (z. B. {@code kunde.de;kunde.com}). */
    @Column(name = "email_domains", nullable = false, length = 500)
    public String emailDomains;

    @Enumerated(EnumType.STRING)
    @Column(name = "protokoll", nullable = false, length = 8)
    public Protokoll protokoll = Protokoll.OIDC;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.ENTWURF;
}
