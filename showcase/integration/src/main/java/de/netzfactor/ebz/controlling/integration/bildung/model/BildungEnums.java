package de.netzfactor.ebz.controlling.integration.bildung.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Alle Bildungsangebot-Enums gebündelt (schlank: eine Datei statt sieben). Jedes Enum trägt
 * {@code @Schema(name=…)}, damit der OpenAPI-/zod-Schemaname trotz Verschachtelung stabil bleibt
 * (z. B. {@code Bereich}, nicht {@code BildungEnums$Bereich}) — die generierte Schicht ändert sich
 * durch das Zusammenlegen also nicht.
 */
public final class BildungEnums {

    private BildungEnums() {
    }

    /** Diskriminator der Familie + lesbares Typ-Feld (§11.9-D) + Frontend-Union-Diskriminator. */
    @Schema(name = "BildungsangebotTyp")
    public enum BildungsangebotTyp {
        SEMINAR, TAGUNG, BERUFSSCHULJAHR, STUDIENGANG
    }

    @Schema(name = "Bereich")
    public enum Bereich {
        BERUFSSCHULE, HOCHSCHULE, AKADEMIE
    }

    /** Lebenszyklus (§11.9-F): Soft-Delete = ARCHIVIERT statt Hard-Delete. */
    @Schema(name = "AngebotStatus")
    public enum AngebotStatus {
        ENTWURF, AKTIV, ARCHIVIERT
    }

    @Schema(name = "PreisModell")
    public enum PreisModell {
        EINMALIG, ABO, RATEN
    }

    @Schema(name = "SeminarKategorie")
    public enum SeminarKategorie {
        FUEHRUNG, FACHKOMPETENZ, COMPLIANCE, IT_DIGITAL, SPRACHEN, SONSTIGE
    }

    @Schema(name = "Studienform")
    public enum Studienform {
        VOLLZEIT, DUAL, BERUFSBEGLEITEND
    }

    @Schema(name = "Studienabschluss")
    public enum Studienabschluss {
        BACHELOR, MASTER
    }
}
