package de.netzfactor.ebz.controlling.integration.rechnung.dto;

import java.time.LocalDate;

/**
 * Verbuchung eines manuellen Zahlungseingangs ({@code AUSGESTELLT → BEZAHLT}). Alle Felder optional:
 * {@code bezahltAm} default = heute, {@code zahlbetragCent} default = Belegsumme, {@code zahlungsReferenz}
 * = Freitext (Kontoauszug-/Überweisungs-Vermerk). Offene Posten/Mahnwesen/Lastschrift bleiben bei DATEV.
 */
public record ZahlungseingangDto(LocalDate bezahltAm, Long zahlbetragCent, String zahlungsReferenz) {
}
