package de.netzfactor.ebz.controlling.integration.kommunikation.event;

import java.util.Set;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;

/**
 * <b>Single Source of Truth</b> des Event-Katalogs: jede Ereignisart deklariert <i>einmal</i> ihre
 * Eigenschaften — {@link #kategorie}, ob personenseitig {@link #sichtbar} (Allowlist, Default-Disziplin),
 * die Default-{@link #pushKanaele Push-Kanäle}, die DSGVO-{@link #rechtsgrundlage}, den
 * {@link #templateName Template-Namen} und ob eine Pflicht-{@link #bestaetigungErforderlich Bestätigung}
 * nötig ist. Aus <i>derselben</i> Liste speisen sich beide Projektionen (Trace/Observability und
 * Benachrichtigung) — keine divergierenden Kataloge.
 * <p>
 * {@link #transaktional()} (Rechtsgrundlage Vertrag/Art. 6 (1) b) umgeht das Marketing-Opt-In; alles
 * andere prüft im Versand Einwilligung + {@code Person.werbesperre} ({@code auskunftssperre} gewinnt immer).
 */
public enum EreignisTyp {

    /** Berufsschul-Anmeldung des Azubis bestätigt (transaktional). Vorerst PORTAL-only: die E-Mail
     *  versendet noch der Bestands-Flow inline; die EMAIL-Migration in die Spine folgt schrittweise (K1b). */
    ANMELDUNG_BESTAETIGT(Kategorie.ANMELDUNG, true, Set.of(Kanal.PORTAL),
            Rechtsgrundlage.VERTRAG_6_1_B, "anmeldung-bestaetigt", false, 0),

    /** Ausbildungsvertrag final bestätigt → Anmeldung abrechenbar (transaktional, Kenntnisnahme erbeten).
     *  Vorerst PORTAL-only (s. {@link #ANMELDUNG_BESTAETIGT}). Pflicht-Kenntnisnahme binnen 14 Tagen (K5). */
    AZUBI_VERTRAG_BESTAETIGT(Kategorie.ANMELDUNG, true, Set.of(Kanal.PORTAL),
            Rechtsgrundlage.VERTRAG_6_1_B, "vertrag-bestaetigt", true, 14),

    /** Rechnung erstellt/versandt (transaktional). */
    RECHNUNG_VERSANDT(Kategorie.RECHNUNG, true, Set.of(Kanal.PORTAL, Kanal.EMAIL),
            Rechtsgrundlage.VERTRAG_6_1_B, "rechnung-versandt", false, 0),

    /** Kurs-/WBT-Einschreibung aktiv (transaktional). */
    EINSCHREIBUNG_AKTIV(Kategorie.EINSCHREIBUNG, true, Set.of(Kanal.PORTAL, Kanal.EMAIL),
            Rechtsgrundlage.VERTRAG_6_1_B, "einschreibung-aktiv", false, 0),

    /** Allgemeiner System-Hinweis an die Person (berechtigtes Interesse), nur Portal-Inbox. */
    SYSTEM_HINWEIS(Kategorie.SYSTEM, true, Set.of(Kanal.PORTAL),
            Rechtsgrundlage.BERECHTIGTES_INTERESSE_6_1_F, "system-hinweis", false, 0),

    /** Gruppen-Broadcast an einen Verteiler (K3, berechtigtes Interesse). PORTAL immer; EMAIL nur ohne
     *  Werbe-/Auskunftssperre (nicht transaktional → Consent/werbesperre greift im ErreichbarkeitPort). */
    GRUPPEN_INFO(Kategorie.SYSTEM, true, Set.of(Kanal.PORTAL, Kanal.EMAIL),
            Rechtsgrundlage.BERECHTIGTES_INTERESSE_6_1_F, "gruppen-info", false, 0),

    /** Erinnerung an eine ausstehende Pflicht-Kenntnisnahme (K5, vom {@code BestaetigungService} erzeugt).
     *  Transaktional (es geht um eine Vertrags-Obliegenheit) → umgeht das Marketing-Opt-In; selbst nicht
     *  bestätigungspflichtig (sonst Endlos-Kette). PORTAL immer, EMAIL als Nachfass-Kanal. */
    BESTAETIGUNG_ERINNERUNG(Kategorie.SYSTEM, true, Set.of(Kanal.PORTAL, Kanal.EMAIL),
            Rechtsgrundlage.VERTRAG_6_1_B, "bestaetigung-erinnerung", false, 0),

    /** Interner Vermerk — bewusst NICHT personenseitig sichtbar (Allowlist-Disziplin), keine Zustellung. */
    INTERNER_VERMERK(Kategorie.INTERN, false, Set.of(),
            Rechtsgrundlage.BERECHTIGTES_INTERESSE_6_1_F, null, false, 0);

    /** Grobklassifikation für Preference-Center (Kanal×Kategorie) und Filterung. */
    public enum Kategorie {
        ANMELDUNG, RECHNUNG, EINSCHREIBUNG, PRUEFUNG, SYSTEM, INTERN
    }

    public final Kategorie kategorie;
    /** Allowlist: nur {@code true} erscheint im personenseitigen Aktivitätslog. */
    public final boolean sichtbar;
    /** Default-Push-Kanäle (vor Anwendung der Personen-Präferenz/Consent). */
    public final Set<Kanal> pushKanaele;
    public final Rechtsgrundlage rechtsgrundlage;
    public final String templateName;
    public final boolean bestaetigungErforderlich;
    /** Frist in Tagen für die Pflicht-Kenntnisnahme (K5); {@code 0} = ohne Frist (kein Eskalations-Lauf). */
    public final int bestaetigungFristTage;

    EreignisTyp(Kategorie kategorie, boolean sichtbar, Set<Kanal> pushKanaele,
            Rechtsgrundlage rechtsgrundlage, String templateName, boolean bestaetigungErforderlich,
            int bestaetigungFristTage) {
        this.kategorie = kategorie;
        this.sichtbar = sichtbar;
        this.pushKanaele = pushKanaele;
        this.rechtsgrundlage = rechtsgrundlage;
        this.templateName = templateName;
        this.bestaetigungErforderlich = bestaetigungErforderlich;
        this.bestaetigungFristTage = bestaetigungFristTage;
    }

    /** Transaktional (Vertrag, Art. 6 (1) b) → umgeht das Marketing-Opt-In; sonst Einwilligung nötig. */
    public boolean transaktional() {
        return rechtsgrundlage == Rechtsgrundlage.VERTRAG_6_1_B;
    }
}
