package de.netzfactor.ebz.controlling.integration.party.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

/**
 * Lookup-Stammdaten des CRM-Kerns: pflegbare Klassifikationslisten (Rollen, Verbände, Branchen …).
 * <p>
 * <b>Bauform laut Plan (A-Leitprinzipien):</b> <i>eigene Tabelle je Kategorie</i> — damit jede
 * Klassifikation ein echtes Fremdschlüssel-Ziel ist (FK-sicher) — über einer gemeinsamen
 * {@link LookupBase}-{@code @MappedSuperclass} ({@code id}/{@code code}/{@code bezeichnung}/{@code aktiv}/
 * {@code sortierung}). <b>Kein</b> zentraler Discriminator-Topf (OTLT-Anti-Pattern, bräche das FK-Prinzip).
 * <p>
 * Die Tabellen werden alle in <i>einer</i> Datei als verschachtelte {@code @Entity}-Klassen gebündelt
 * (schlanke Datei-Struktur), behalten aber jede ihre eigene Tabelle im Schema {@code mdm}. Reine
 * Bezeichnungslisten tragen nur die Basisfelder; Kategorien mit Zusatzattributen (Verband: Kürzel/Website,
 * Branche: WZ/NACE-Code, Land: ISO/PLZ-Regel, Sprache: ISO-639) erweitern die Basis um eigene Spalten.
 * <p>
 * Befüllung idempotent beim Start über {@code LookupSeeder}; die generische Lookup-<i>Pflege</i>
 * (CRUD-UI) folgt laut Plan in einer späteren Phase.
 */
public final class Lookups {

    private Lookups() {
    }

    /** Gemeinsame Basis aller Lookups: stabiler {@code code} (FK-/Codegen-stabil) + Anzeige + Aktiv-Flag + Sortierung.
     *  {@code @Audited}, damit Relationen auditierter Kern-Entities auf auditierte Lookup-Ziele zeigen (Envers, A12). */
    @Audited
    @MappedSuperclass
    public abstract static class LookupBase extends PanacheEntity {

        /** Fachlich stabiler Schlüssel (z. B. {@code GDW}, {@code WEG_VERWALTER}); je Tabelle eindeutig. */
        @Column(name = "code", nullable = false, unique = true, length = 64)
        public String code;

        @Column(name = "bezeichnung", nullable = false, length = 200)
        public String bezeichnung;

        /** Deaktivierte Werte bleiben referenzierbar (Historie), sind aber nicht mehr neu wählbar. */
        @Column(name = "aktiv", nullable = false)
        public boolean aktiv = true;

        @Column(name = "sortierung", nullable = false)
        public int sortierung;
    }

    /** Rolle einer Person in einer Organisation (A4) — ersetzt den früheren {@code Mitgliedschaft.Rolle}-Enum. */
    @Audited
    @Entity(name = "LookupRolle")
    @Table(name = "lookup_rolle", schema = "mdm")
    public static class Rolle extends LookupBase {
        /** Vorbelegung „buchungsberechtigt" bei Neuanlage einer Mitgliedschaft mit dieser Rolle. */
        @Column(name = "buchung_default", nullable = false)
        public boolean buchungDefault = false;
    }

    /** Verbandszugehörigkeit (A2, mehrfach via Join) — mit Kürzel/Website. */
    @Audited
    @Entity(name = "LookupVerband")
    @Table(name = "lookup_verband", schema = "mdm")
    public static class Verband extends LookupBase {
        @Column(name = "kuerzel", length = 32)
        public String kuerzel;

        @Column(name = "website", length = 200)
        public String website;
    }

    /** Unternehmenstyp der Immobilienwirtschaft (A2, mehrfach via Join). */
    @Audited
    @Entity(name = "LookupUnternehmenstyp")
    @Table(name = "lookup_unternehmenstyp", schema = "mdm")
    public static class Unternehmenstyp extends LookupBase {
    }

    /** Tätigkeitsschwerpunkt (A2, mehrfach via Join). */
    @Audited
    @Entity(name = "LookupTaetigkeitsschwerpunkt")
    @Table(name = "lookup_taetigkeitsschwerpunkt", schema = "mdm")
    public static class Taetigkeitsschwerpunkt extends LookupBase {
    }

    /** Person↔Person-Beziehungstyp (A5: Erziehungsberechtigt/Notfallkontakt/…). */
    @Audited
    @Entity(name = "LookupBeziehungstyp")
    @Table(name = "lookup_beziehungstyp", schema = "mdm")
    public static class Beziehungstyp extends LookupBase {
        /** Trägt eine Eltern-/Sorgerechts-Einwilligung bei Minderjährigen (A5). */
        @Column(name = "sorgerecht", nullable = false)
        public boolean sorgerecht = false;
    }

    /** Branche nach WZ-2008/NACE (A2) — mit amtlichem Code. */
    @Audited
    @Entity(name = "LookupBranche")
    @Table(name = "lookup_branche", schema = "mdm")
    public static class Branche extends LookupBase {
        /** WZ-2008-/NACE-Schlüssel (z. B. {@code 68.32} Hausverwaltung). */
        @Column(name = "wz_code", length = 16)
        public String wzCode;
    }

    /** Land nach ISO-3166 (A3) — mit ISO-Codes + länderabhängiger PLZ-Regel. */
    @Audited
    @Entity(name = "LookupLand")
    @Table(name = "lookup_land", schema = "mdm")
    public static class Land extends LookupBase {
        /** ISO-3166 alpha-3 (der {@code code} der Basis trägt alpha-2). */
        @Column(name = "iso3", length = 3)
        public String iso3;

        /** Regex zur länderabhängigen PLZ-Validierung; {@code null} = keine strenge Prüfung. */
        @Column(name = "plz_regex", length = 120)
        public String plzRegex;
    }

    /** Korrespondenzsprache nach ISO-639 (A1) — der {@code code} trägt ISO-639-1 (z. B. {@code de}). */
    @Audited
    @Entity(name = "LookupSprache")
    @Table(name = "lookup_sprache", schema = "mdm")
    public static class Sprache extends LookupBase {
    }

    /** Lead-Quelle (A8) für Marketing-Attribution + ausstehende Einwilligung. */
    @Audited
    @Entity(name = "LookupLeadQuelle")
    @Table(name = "lookup_lead_quelle", schema = "mdm")
    public static class LeadQuelle extends LookupBase {
    }

    /** Aktivitäts-/Kontakthistorien-Typ (A9: Telefonat/E-Mail/Termin/Notiz/…). */
    @Audited
    @Entity(name = "LookupAktivitaetstyp")
    @Table(name = "lookup_aktivitaetstyp", schema = "mdm")
    public static class Aktivitaetstyp extends LookupBase {
    }

    /** Zuständige IHK/Kammer (A2). */
    @Audited
    @Entity(name = "LookupIhkKammer")
    @Table(name = "lookup_ihk_kammer", schema = "mdm",
            uniqueConstraints = @UniqueConstraint(name = "uk_lookup_ihk_kammer_code", columnNames = "code"))
    public static class IhkKammer extends LookupBase {
    }
}
