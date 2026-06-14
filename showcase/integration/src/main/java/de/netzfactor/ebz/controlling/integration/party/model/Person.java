package de.netzfactor.ebz.controlling.integration.party.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Natürliche Person als <b>Identitätsanker</b> des Party-Kerns: ein Mensch = ein Datensatz = ein
 * Login. Der Login-Anker ist die Keycloak-{@code sub} (nicht die E-Mail!). E-Mails hängen als
 * {@link PersonEmail} darunter, weil ein Mensch privat und dienstlich verschiedene Adressen führt.
 * <p>
 * Bewusst <i>ohne</i> Firmenbezug: die N:M-Verknüpfung zu {@link Organisation} liegt in
 * {@link Mitgliedschaft}, die Abrechnungs-Verantwortung im {@code rechnung.debitor} (Projektion, nicht
 * Identität). Damit ist „selbe E-Mail privat <i>und</i> als Firmen-Azubi" kein Konflikt, sondern
 * dieselbe Person in zwei Kontexten.
 * <p>
 * Match/Merge folgt demselben Golden-Record-Muster wie der Debitor: {@link #status} +
 * {@link #goldenPersonId}. Schema {@code party} explizit am {@code @Table}.
 */
@Entity
@Table(name = "person", schema = "party")
public class Person extends PanacheEntity {

    /** AKTIV = Golden-Record mit Login; PROVISORISCH = von einer Firma vor-angelegt, noch nicht
     *  selbst eingeloggt; ZUSAMMENGEFUEHRT = in {@link #goldenPersonId} gemergte Dublette. */
    public enum Status {
        AKTIV, PROVISORISCH, ZUSAMMENGEFUEHRT
    }

    @Version
    public long version;

    /** Login-Anker (Keycloak {@code sub}); erst beim ersten Selbst-Login gesetzt → bis dahin PROVISORISCH. */
    @Column(name = "keycloak_sub", unique = true, length = 64)
    public String keycloakSub;

    @Column(name = "anzeige_name", nullable = false, length = 200)
    public String anzeigeName;

    @Column(name = "plz", length = 10)
    public String plz;

    @Column(name = "ort", length = 120)
    public String ort;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public Status status = Status.PROVISORISCH;

    /** Bei ZUSAMMENGEFUEHRT die überlebende Person; sonst {@code null}. */
    @Column(name = "golden_person_id")
    public Long goldenPersonId;

    /** Schwacher Dublettenschlüssel (normalisierter Name) für Merge-Kandidaten; E-Mail ist der starke. */
    @Column(name = "match_schluessel", length = 200)
    public String matchSchluessel;
}
