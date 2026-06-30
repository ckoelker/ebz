package de.netzfactor.ebz.controlling.integration.lms.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;

/**
 * Kanonischer <b>Weiterbildungsnachweis</b> (M6/K6): das MDM-Faktum, dass ein Lernender einen {@link WbtKurs}
 * abgeschlossen hat. Entsteht im Nachweis-Seam, indem die in OpenOLAT (System-of-Record) per REST gelesene
 * Completion des trackbaren Nachweis-Kurses auf einen kanonischen Fakt projiziert wird.
 * <p>
 * Die anrechenbare Zeit ist bewusst {@link #sollStunden} — die <b>rechtlich maßgebliche</b> Soll-Stunden-Zahl
 * des Kurses (F1/F2), als Snapshot zum Abschlusszeitpunkt eingefroren — NICHT die unzuverlässige SCORM
 * {@code session_time} (höchstens informativ in {@link #sessionTimeSekunden}).
 * <p>
 * Flach im Schema {@code mdm}, echte FKs ([[prefer-manytoone-real-fks]]): {@link #einschreibung} ist die Quelle
 * und der Idempotenz-Schlüssel (ein Fakt je Einschreibung), {@link #mandant}/{@link #wbtKurs} sind direkte
 * Dimensions-FKs für das Reporting. Der Lernende wird — wie in {@link Kurseinschreibung} — über
 * {@link #keycloakSub} (externe IdP-Identität, keine lokale Person-Tabelle) geführt.
 */
@Entity
@Table(name = "lernleistungs_fakt", schema = "mdm",
        uniqueConstraints = @UniqueConstraint(name = "uq_lernfakt_einschreibung",
                columnNames = {"einschreibung_id"}))
public class LernleistungsFakt extends PanacheEntity {

    /** Quelle + Idempotenz-Schlüssel: genau ein Fakt je Einschreibung. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "einschreibung_id", nullable = false)
    public Kurseinschreibung einschreibung;

    /** Dimensions-FK fürs Reporting (kann für Bestands-Einschreibungen ohne Mandant null sein). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mandant_id")
    public Mandant mandant;

    /** Dimensions-FK fürs Reporting. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wbt_kurs_id", nullable = false)
    public WbtKurs wbtKurs;

    /** Lernenden-Identität (Keycloak-Subject, Realm ebz-customers) — wie in {@link Kurseinschreibung}. */
    @Column(name = "keycloak_sub", nullable = false, length = 64)
    public String keycloakSub;

    /** Denormalisierter Anzeigename des Lernenden (Reporting ohne Identity-Lookup). */
    @Column(name = "lernender_name", length = 240)
    public String lernenderName;

    /** Bestanden-Flag aus dem OpenOLAT-Assessment ({@code passed}). */
    @Column(name = "bestanden", nullable = false)
    public boolean bestanden;

    /** Abschlusszeitpunkt aus OpenOLAT ({@code assessmentDone}). */
    @Column(name = "abgeschlossen_am", nullable = false)
    public Instant abgeschlossenAm;

    /** Anrechenbare Soll-Stunden (Snapshot aus {@link WbtKurs#sollStundenAnrechenbar} beim Abschluss). */
    @Column(name = "soll_stunden", precision = 5, scale = 2)
    public BigDecimal sollStunden;

    /** Informativ: SCORM-{@code session_time} in Sekunden (kein Rechtsnachweis), falls vorhanden. */
    @Column(name = "session_time_sekunden")
    public Long sessionTimeSekunden;

    /** Erfassungszeitpunkt des Fakts (Projektion). */
    @Column(name = "erfasst_am", nullable = false)
    public Instant erfasstAm;
}
