package de.netzfactor.ebz.controlling.integration.rechnung.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.KohortenAuskunft;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;

/**
 * Implementierung der Inbound-SPI {@link KohortenAuskunft} (K3b) im {@code rechnung}-Modul: löst die
 * Teilnehmenden eines Bildungsangebots aus {@code rechnung.Anmeldung} auf (die Person↔Angebot-Verknüpfung
 * lebt hier). Bewusst hier statt in {@code kommunikation} — die erlaubte Abhängigkeitsrichtung ist
 * {@code rechnung→kommunikation} (umgekehrt verbietet die ArchUnit-Regel die Kopplung an volatile Module).
 * Stornierte ({@code ABGEBROCHEN}) Anmeldungen zählen nicht zur Kohorte.
 */
@ApplicationScoped
public class KohortenAuskunftAdapter implements KohortenAuskunft {

    @Override
    @Transactional
    public List<Long> teilnehmerPersonIds(Long bildungsangebotId) {
        if (bildungsangebotId == null) {
            return List.of();
        }
        return Anmeldung.<Anmeldung>list(
                        "bildungsangebot.id = ?1 and teilnehmerPerson is not null and status <> ?2",
                        bildungsangebotId, AnmeldungStatus.ABGEBROCHEN).stream()
                .map(Anmeldung::teilnehmerPersonId)
                .distinct()
                .toList();
    }
}
