package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.time.LocalDate;
import java.util.List;

/**
 * D3-Port: Quelle der <b>ausgeglichenen offenen Posten</b> (OPOS) aus DATEV. Im Gegensatz zu D1/D2 gibt
 * es für den Zahlungsstatus <i>keine</i> Cloud-Self-Service-API — OPOS liest man bei DATEV über
 * <b>DATEVconnect (on-prem Desktop-API)</b>, das die Rechnungswesen-Installation der Kanzlei voraussetzt
 * (siehe {@code DATEV-Sandbox-Onboarding.md}). Darum hängt der reale Adapter an der Kanzlei; der Showcase
 * nutzt {@link OposQuelleMock}. Der {@code OposRegelkreisService} setzt jeden gemeldeten Posten auf
 * {@code BEZAHLT} (Verknüpfung über die Belegnummer).
 */
public interface OposQuelle {

    /** Liefert (höchstens {@code max}) seit dem letzten Lauf ausgeglichene Posten. */
    List<Posten> ausgeglichenePosten(int max);

    /** Ein ausgeglichener Posten: Belegnummer (= Belegfeld 1), Ausgleichsdatum, Betrag, DATEV-Referenz. */
    record Posten(String belegnummer, LocalDate bezahltAm, long betragCent, String referenz) {
    }
}
