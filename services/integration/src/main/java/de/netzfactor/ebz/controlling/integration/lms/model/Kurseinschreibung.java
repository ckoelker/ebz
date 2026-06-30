package de.netzfactor.ebz.controlling.integration.lms.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import de.netzfactor.ebz.controlling.integration.mandant.model.Mandant;

/**
 * Einschreibung eines Lernenden in einen {@link WbtKurs} — <b>zugleich der Transactional-Outbox-
 * Datensatz</b> der OpenOLAT-Provisionierung. Statt des anmeldungs-gebundenen {@code OutboxAuftrag}
 * (echte FK auf {@code mdm.anmeldung}) trägt diese Entity selbst den Outbox-Zustand
 * ({@code status}/{@code versuche}/{@code naechsterVersuchAm}/{@code letzterFehler}): in <b>derselben
 * DB-Transaktion</b> wie die Anforderung geschrieben (atomar), ein Dispatcher zieht die fälligen Zeilen
 * und stellt idempotent zu (Backoff-Retry, Dead-Letter → HITL). So bleibt die FK-Disziplin gewahrt
 * ({@code wbtKurs} = echte {@code @ManyToOne}-FK) ohne den anmeldungs-fixierten Outbox um polymorphe
 * Referenzen aufzuweichen.
 * <p>
 * Identität des Lernenden = {@code keycloakSub} (Subject im Realm {@code ebz-customers}). Genau diesen
 * Sub legt der Dispatcher als OpenOLAT-Authentifizierung {@code provider=KEYCLOAK} an, damit eine
 * <b>vor</b> dem ersten OpenOLAT-Login provisionierte Einschreibung beim späteren SSO-Login mit
 * <i>derselben</i> Identität verschmilzt (kein Dublett-User). {@code keycloakSub} referenziert eine
 * externe IdP-Identität (keine lokale Tabelle) → bewusst Spalte, keine FK.
 */
@Entity
@Table(name = "kurseinschreibung", schema = "mdm",
        uniqueConstraints = @UniqueConstraint(name = "uq_einschreibung_kurs_sub",
                columnNames = {"wbt_kurs_id", "keycloak_sub"}))
public class Kurseinschreibung extends PanacheEntity {

    /** Maximale Zustellversuche, bevor die Einschreibung zur manuellen Klärung (HITL) eskaliert. */
    public static final int MAX_VERSUCHE = 5;

    @Version
    public long version;

    /** Welcher WBT (echte FK ins MDM); aus ihm liest der Dispatcher den {@code openolatKey}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wbt_kurs_id", nullable = false)
    public WbtKurs wbtKurs;

    /**
     * Mandant, unter dem diese Einschreibung läuft (echte FK). Nullable: Bestands-Einschreibungen der
     * bisherigen Ein-Mandanten-Strecke (vor M1) tragen keinen Mandanten; neue Einschreibungen werden dem
     * EBZ-Kernmandanten bzw. — bei B2B-Login — dem föderierten Mandanten zugeordnet. Trägt den Mandanten
     * in den Nachweis-Fakt (M6) und die Seat-Zählung (M5).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mandant_id")
    public Mandant mandant;

    /** Lernenden-Identität: Keycloak-Subject (Realm ebz-customers) — Match-Schlüssel zur OpenOLAT-Auth. */
    @Column(name = "keycloak_sub", nullable = false, length = 64)
    public String keycloakSub;

    /** Für die OpenOLAT-User-Anlage (falls noch nicht via SSO vorhanden). */
    @Column(name = "email", length = 200)
    public String email;

    @Column(name = "anzeige_name", length = 200)
    public String anzeigeName;

    /** Provenienz: auslösende bezahlte Vendure-Order (Audit / Storno-Bezug). */
    @Column(name = "vendure_order_id", length = 64)
    public String vendureOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    public EinschreibungStatus status;

    /** Aufgelöste OpenOLAT-Identität (vom Dispatcher gesetzt); Bezug für Enrol/Unenrol. */
    @Column(name = "openolat_identity_key")
    public Long openolatIdentityKey;

    @Column(name = "versuche", nullable = false)
    public int versuche;

    /** Frühester Zeitpunkt des nächsten Versuchs (Exponential Backoff); der Dispatcher ignoriert frühere. */
    @Column(name = "naechster_versuch_am", nullable = false)
    public Instant naechsterVersuchAm;

    @Column(name = "letzter_fehler", length = 1000)
    public String letzterFehler;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    @Column(name = "erledigt_am")
    public Instant erledigtAm;

    /** Prozessdoku-Korrelation ({@code prozess.fall}), beim Enqueue aus dem Baggage abgegriffen. */
    @Column(name = "prozess_fall", length = 120)
    public String prozessFall;

    /** Fällig für den Dispatcher? (offene Richtung + Zeit erreicht) */
    public boolean istFaellig(Instant jetzt) {
        return (status == EinschreibungStatus.ANGEFORDERT || status == EinschreibungStatus.STORNO_ANGEFORDERT)
                && !naechsterVersuchAm.isAfter(jetzt);
    }
}
