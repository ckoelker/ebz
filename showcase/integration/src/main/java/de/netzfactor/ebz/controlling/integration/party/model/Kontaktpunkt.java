package de.netzfactor.ebz.controlling.integration.party.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Gemeinsame Kommunikations-Entity (Plan A3): E-Mail, Telefon oder Adresse — je {@link #typ} sind
 * andere Spalten relevant. <b>Reiner Kommunikationskanal, nicht unique</b> (die identitätsstiftende
 * Login-Adresse liegt in {@link Login}).
 * <p>
 * <b>Besitzer = Person ODER Organisation ODER Mitgliedschaft</b> (dienstlich/Firmenkontext, A4). Statt
 * eines polymorphen {@code (besitzerTyp, besitzerId)}-Paares drei <i>echte</i> nullable FKs — genau einer
 * ist gesetzt (im Service geprüft) — so bleibt das FK-Prinzip gewahrt. {@link #status} + Gültigkeit
 * bilden die Historie (Umzug = alter Kanal bleibt EHEMALIG). PLZ-Validierung ist länderabhängig
 * ({@link Lookups.Land#plzRegex}).
 */
@Audited
@Entity
@Table(name = "kontaktpunkt", schema = "mdm")
public class Kontaktpunkt extends PanacheEntity {

    public enum Typ {
        EMAIL, TELEFON, ADRESSE
    }

    public enum Telefonart {
        FESTNETZ, MOBIL, FAX
    }

    public enum Status {
        AKTIV, EHEMALIG
    }

    @Version
    public long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "typ", nullable = false, length = 16)
    public Typ typ;

    // ── Besitzer: genau einer gesetzt (echte FKs statt polymorpher Spalte) ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    public Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    public Organisation organisation;

    /** Dienstlicher Kanal im Firmenkontext (A4) — hängt an der Mitgliedschaft. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mitgliedschaft_id")
    public Mitgliedschaft mitgliedschaft;

    /** Freie Bezeichnung des Kanals (z. B. „Privat", „Zentrale", „Rechnungsadresse"). */
    @Column(name = "label", length = 80)
    public String label;

    /** Vorrang-Kanal seines Typs beim jeweiligen Besitzer. */
    @Column(name = "primaer", nullable = false)
    public boolean primaer = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.AKTIV;

    @Column(name = "gueltig_von")
    public LocalDate gueltigVon;

    /** {@code null} = offen/unbefristet; gesetzt = historisiert (EHEMALIG). */
    @Column(name = "gueltig_bis")
    public LocalDate gueltigBis;

    // ── Typ EMAIL ──
    @Column(name = "email", length = 200)
    public String email;

    // ── Typ TELEFON ──
    /** Normalisierte E.164-Nummer fürs Anruf-Matching (A13). */
    @Column(name = "nummer_e164", length = 24)
    public String nummerE164;

    @Column(name = "nummer_anzeige", length = 40)
    public String nummerAnzeige;

    @Enumerated(EnumType.STRING)
    @Column(name = "telefonart", length = 16)
    public Telefonart telefonart;

    // ── Typ ADRESSE ──
    @Column(name = "strasse", length = 200)
    public String strasse;

    @Column(name = "hausnummer", length = 20)
    public String hausnummer;

    @Column(name = "adresszusatz", length = 120)
    public String adresszusatz;

    @Column(name = "plz", length = 12)
    public String plz;

    @Column(name = "ort", length = 120)
    public String ort;

    /** Bundesland/Region — außerhalb DACH relevant. */
    @Column(name = "region", length = 120)
    public String region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "land_id")
    public Lookups.Land land;

    /** Freitext-Adressblock für Länder außerhalb DACH (unstrukturiert). */
    @Column(name = "auslandsblock", length = 500)
    public String auslandsblock;
}
