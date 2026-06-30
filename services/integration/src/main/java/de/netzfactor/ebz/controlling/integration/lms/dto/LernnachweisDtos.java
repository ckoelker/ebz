package de.netzfactor.ebz.controlling.integration.lms.dto;

import java.math.BigDecimal;
import java.time.Instant;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import de.netzfactor.ebz.controlling.integration.lms.model.LernleistungsFakt;

/**
 * DTOs des Nachweis-Seams (M6). Gebündelt ([[lean-file-structure]]); stabile Codegen-Namen via {@code @Schema}.
 */
public final class LernnachweisDtos {

    private LernnachweisDtos() {
    }

    /** Referenz des trackbaren Nachweis-Kurses (Rückgabe der Bereitstellung). */
    @Schema(name = "NachweisKursRef")
    public record NachweisKursRefDto(long courseId, String nodeId) {
    }

    /** Kanonischer Weiterbildungsnachweis (Soll-Stunden) — der K6-Report-Datensatz. */
    @Schema(name = "LernleistungsFakt")
    public record LernleistungsFaktDto(Long id, Long einschreibungId, Long mandantId, String mandantSchluessel,
            Long wbtKursId, String wbtCode, String wbtTitel, String keycloakSub, String lernenderName,
            boolean bestanden, Instant abgeschlossenAm, BigDecimal sollStunden, Instant erfasstAm) {

        public static LernleistungsFaktDto von(LernleistungsFakt f) {
            return new LernleistungsFaktDto(f.id, f.einschreibung != null ? f.einschreibung.id : null,
                    f.mandant != null ? f.mandant.id : null, f.mandant != null ? f.mandant.schluessel : null,
                    f.wbtKurs != null ? f.wbtKurs.id : null, f.wbtKurs != null ? f.wbtKurs.code : null,
                    f.wbtKurs != null ? f.wbtKurs.titel : null, f.keycloakSub, f.lernenderName,
                    f.bestanden, f.abgeschlossenAm, f.sollStunden, f.erfasstAm);
        }
    }
}
