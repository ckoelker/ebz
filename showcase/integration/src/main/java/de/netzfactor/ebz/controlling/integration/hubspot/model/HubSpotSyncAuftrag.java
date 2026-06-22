package de.netzfactor.ebz.controlling.integration.hubspot.model;

import java.time.Instant;

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

import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke.ObjektTyp;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * <b>Transactional-Outbox-Auftrag des HubSpot-Sync</b> — entkoppelt den unzuverlässigen externen Push vom
 * auslösenden Geschäftsvorgang (Muster wie {@code kommunikation.ZustellAuftrag}, aber eigene Tabelle mit
 * echten FKs auf {@link #person}/{@link #organisation} statt Domänen-Kopplung). In <b>derselben Tx</b> wie
 * die Geschäftsänderung geschrieben; der {@code HubSpotSyncDispatcher} zieht fällige, offene Aufträge,
 * ruft die {@code HubSpotSenke} und führt Erfolg/Backoff/Dead-Letter nach.
 * <p>
 * Tabelle im {@code mdm}-Schema (keine neuen Schemas; FK-Prinzip). Für {@link Operation#ERASE} gilt
 * <b>Vorrang</b>: eine vorgemerkte Löschung wird nie durch ein konkurrierendes UPSERT überholt (kein
 * „Wiederauferstehen") — durchgesetzt im Dispatcher/Enqueue.
 */
@Entity
@Table(name = "hubspot_sync_auftrag", schema = "mdm")
public class HubSpotSyncAuftrag extends PanacheEntity {

    /** Maximale Versuche, bevor der Auftrag als Dead-Letter (TOT) zur manuellen Klärung eskaliert. */
    public static final int MAX_VERSUCHE = 5;

    /** Fachliche Operation — bestimmt, welche {@code HubSpotSenke}-Methode der Dispatcher ruft. */
    public enum Operation {
        /** Stammdaten anlegen/aktualisieren (Contact/Company/Association). */
        UPSERT,
        /** Marketing-Entscheidung spiegeln (Opt-in erteilt → marketable, Widerruf → nicht marketable). */
        CONSENT_UPDATE,
        /** Marketing hart abschalten (Werbesperre) — Kontakt bleibt, aber non-marketing. */
        SUPPRESS,
        /** Recht auf Vergessen (Art. 17): GDPR-Delete bzw. Archiv-Fallback + Mapping entfernen. */
        ERASE
    }

    public enum Status {
        NEU, IN_ARBEIT, ERLEDIGT,
        /** Nur bei abgeschaltetem GDPR-Delete: archiviert + auf manuelle Löschung im Cockpit gestellt. */
        MANUELL,
        FEHLER,
        /** Dead-Letter nach {@link #MAX_VERSUCHE}. */
        TOT
    }

    @Version
    public long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "objekt_typ", nullable = false, length = 16)
    public ObjektTyp objektTyp;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 16)
    public Operation operation;

    /** Betroffene Person (Contact / Association); {@code null} bei reinen Company-Aufträgen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    public Person person;

    /** Betroffene Organisation (Company / Association); {@code null} bei reinen Contact-Aufträgen. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    public Organisation organisation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.NEU;

    @Column(name = "versuche", nullable = false)
    public int versuche;

    /** Frühester Zeitpunkt des nächsten Versuchs (Exponential Backoff). */
    @Column(name = "naechster_versuch_am", nullable = false)
    public Instant naechsterVersuchAm = Instant.now();

    @Column(name = "letzter_fehler", length = 1000)
    public String letzterFehler;

    /** Idempotenz-/Dedupe-Schlüssel ({@code operation:objektTyp:partyRef}) — kein Doppelversand. */
    @Column(name = "idempotenz_schluessel", nullable = false, unique = true, length = 200)
    public String idempotenzSchluessel;

    /** Prozessdoku-Korrelation ({@code prozess.fall}); der async Dispatcher hat keinen HTTP-Kontext mehr. */
    @Column(name = "prozess_fall", length = 120)
    public String prozessFall;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm = Instant.now();

    @Column(name = "erledigt_am")
    public Instant erledigtAm;
}
