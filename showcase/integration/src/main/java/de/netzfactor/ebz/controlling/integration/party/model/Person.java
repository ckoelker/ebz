package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.LocalDate;
import java.time.Period;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Natürliche Person als <b>Identitätsanker</b> des CRM-/Party-Kerns: ein Mensch = ein Datensatz. Der
 * Login-Anker ist die Keycloak-{@code sub}; identitätsstiftende Login-E-Mails liegen entkoppelt in
 * {@link Login} (global unique), die Kommunikationskanäle (E-Mail/Telefon/Adresse) als {@link Kontaktpunkt}
 * (nicht unique). Damit ist „selbe E-Mail privat <i>und</i> dienstlich" kein Konflikt.
 * <p>
 * Pflicht minimal (Plan A1): {@link #vorname} + {@link #nachname} (+ mind. 1 Kontaktpunkt, fachlich im
 * Service geprüft). {@link #geschlecht} ist ein echter Enum (Code verzweigt → {@link #briefanrede()});
 * alle übrigen Klassifikationen (Sprache, Land, Lead-Quelle) sind FK-Lookups. Match/Merge folgt dem
 * Golden-Record-Muster ({@link #status} + {@link #goldenPersonId}); DSGVO-Lebenszyklus über
 * {@link #loeschStatus} (A7). Schema {@code mdm} am {@code @Table}.
 */
@Audited
@Entity
@Table(name = "person", schema = "mdm")
public class Person extends PanacheEntity {

    /** AKTIV = Golden-Record mit Login; PROVISORISCH = vor-angelegt, noch nicht selbst eingeloggt;
     *  ZUSAMMENGEFUEHRT = in {@link #goldenPersonId} gemergte Dublette. */
    public enum Status {
        AKTIV, PROVISORISCH, ZUSAMMENGEFUEHRT
    }

    /** Biologisches/rechtliches Geschlecht → steuert die abgeleitete Anrede ({@link #briefanrede()}). */
    public enum Geschlecht {
        MAENNLICH, WEIBLICH, DIVERS, KEINE_ANGABE
    }

    /** DSGVO-Lebenszyklus (A7): produktiv → gesperrt (Art. 18) → nach Aufbewahrungsfrist anonymisiert. */
    public enum LoeschStatus {
        AKTIV, GESPERRT, ANONYMISIERT
    }

    @Version
    public long version;

    /** Login-Anker (Keycloak {@code sub}); erst beim ersten Selbst-Login gesetzt → bis dahin PROVISORISCH. */
    @Column(name = "keycloak_sub", unique = true, length = 64)
    public String keycloakSub;

    @Column(name = "vorname", nullable = false, length = 120)
    public String vorname;

    @Column(name = "nachname", nullable = false, length = 120)
    public String nachname;

    @Enumerated(EnumType.STRING)
    @Column(name = "geschlecht", nullable = false, length = 16)
    public Geschlecht geschlecht = Geschlecht.KEINE_ANGABE;

    /** Freitext mit Positionslogik (DE-Grade vorangestellt, internationale nachgestellt) — keine starre Liste. */
    @Column(name = "titel", length = 80)
    public String titel;

    @Column(name = "geburtsdatum")
    public LocalDate geburtsdatum;

    @Column(name = "geburtsort", length = 120)
    public String geburtsort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "geburtsland_id")
    public Lookups.Land geburtsland;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staatsangehoerigkeit1_id")
    public Lookups.Land staatsangehoerigkeit1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staatsangehoerigkeit2_id")
    public Lookups.Land staatsangehoerigkeit2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "korrespondenzsprache_id")
    public Lookups.Sprache korrespondenzsprache;

    /** Optionales Profilbild (Referenz/Objektschlüssel); sonst Initialen-Fallback in der UI. */
    @Column(name = "foto_url", length = 500)
    public String fotoUrl;

    /** Werbesperre — überstimmt jedes Marketing-Opt-In (A1/A6). */
    @Column(name = "werbesperre", nullable = false)
    public boolean werbesperre = false;

    /** Auskunftssperre (Adressbuch/Behörde) — überstimmt jede Marketing-Verarbeitung. */
    @Column(name = "auskunftssperre", nullable = false)
    public boolean auskunftssperre = false;

    /** Lead-Quelle (A8) für Marketing-Attribution; bei Neuanlage pflichtnah. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_quelle_id")
    public Lookups.LeadQuelle leadQuelle;

    @Column(name = "lead_datum")
    public LocalDate leadDatum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public Status status = Status.PROVISORISCH;

    @Enumerated(EnumType.STRING)
    @Column(name = "loesch_status", nullable = false, length = 20)
    public LoeschStatus loeschStatus = LoeschStatus.AKTIV;

    /** Geplante Anonymisierung ab diesem Datum (A7: nach Aufbewahrungsfrist GoBD/§147 AO). */
    @Column(name = "anonymisieren_ab")
    public LocalDate anonymisierenAb;

    /** Bei ZUSAMMENGEFUEHRT die überlebende Person; sonst {@code null}. */
    @Column(name = "golden_person_id")
    public Long goldenPersonId;

    /** Schwacher Dublettenschlüssel (normalisierter Name); E-Mail/Login ist der starke. */
    @Column(name = "match_schluessel", length = 200)
    public String matchSchluessel;

    /** Anzeigename (Titel + Vor- + Nachname) — Kompatibilitäts-Helfer für Listen/Logs/Mails. */
    @Transient
    public String anzeigeName() {
        String t = (titel == null || titel.isBlank()) ? "" : titel.trim() + " ";
        return (t + vorname + " " + nachname).trim();
    }

    /** Abgeleitete Briefanrede (Plan A1): geschlechtsspezifisch, mit neutralem Fallback „Hallo …". */
    @Transient
    public String briefanrede() {
        String t = (titel == null || titel.isBlank()) ? "" : titel.trim() + " ";
        return switch (geschlecht) {
            case MAENNLICH -> "Sehr geehrter Herr " + t + nachname;
            case WEIBLICH -> "Sehr geehrte Frau " + t + nachname;
            default -> "Hallo " + vorname + " " + nachname;
        };
    }

    /** Volljährig (Plan A1/A9-Gating); ohne Geburtsdatum als volljährig angenommen (Default + UI-Hinweis). */
    @Transient
    public boolean volljaehrig() {
        return geburtsdatum == null || Period.between(geburtsdatum, LocalDate.now()).getYears() >= 18;
    }
}
