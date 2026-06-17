package de.netzfactor.ebz.controlling.integration.party.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Identitätsstiftende Login-E-Mail einer {@link Person} (Plan A1b) — bewusst <b>entkoppelt</b> von den
 * Kommunikations-E-Mails ({@link Kontaktpunkt} Typ EMAIL).
 * <p>
 * Die <b>globale Unique-Constraint auf {@link #loginEmail}</b> ist der Dreh- und Angelpunkt der
 * idempotenten Identitäts-Auflösung („eine Login-Adresse → genau eine Person"); sie löst zugleich den
 * früheren Unique-Konflikt der gemeinsamen Kontaktpunkt-Entity, weil Kommunikations-E-Mails dadurch
 * <i>nicht</i> mehr unique sein müssen. Eine Person kann mehrere Login-Adressen bündeln.
 */
@Audited
@Entity
@Table(name = "login", schema = "mdm")
public class Login extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    public Person person;

    @Column(name = "login_email", nullable = false, unique = true, length = 200)
    public String loginEmail;

    /** Keycloak {@code sub}, das diese Login-Adresse beim Selbst-Login beansprucht hat ({@code null} bis dahin). */
    @Column(name = "keycloak_sub", length = 64)
    public String keycloakSub;

    /** Verifiziert, sobald der Mensch die Adresse beim Selbst-Login bestätigt hat (Account-Claiming). */
    @Column(name = "verifiziert", nullable = false)
    public boolean verifiziert = false;

    /** Abgeleitete FK-ID (View-/Mapping-Komfort). */
    public Long personId() {
        return person == null ? null : person.id;
    }
}
