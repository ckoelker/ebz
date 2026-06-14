package de.netzfactor.ebz.controlling.integration.party.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

import de.netzfactor.ebz.controlling.integration.party.model.PersonEmail;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Akteur;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Phase;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess.Typ;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;
import de.netzfactor.ebz.controlling.integration.rechnung.model.Anmeldung;
import de.netzfactor.ebz.controlling.integration.rechnung.model.AnmeldungStatus;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;

/**
 * Lebenszyklus-Übergänge einer Anmeldung (Anmeldung Berufsschule, Schritt E+). Hält die Stufen
 * {@code ANGEFRAGT → BESTAETIGT_EBZ → AKTIV} an einer Stelle zusammen, inkl. der begleitenden
 * Benachrichtigungen (Quarkus-{@link Mailer}; Dev → Mailpit, Test → MockMailbox).
 */
@ApplicationScoped
public class AnmeldungWorkflowService {

    @Inject
    Mailer mailer;

    @Inject
    Prozessspur prozess;

    /**
     * EBZ bestätigt eine angefragte Anmeldung ({@code ANGEFRAGT → BESTAETIGT_EBZ}) und benachrichtigt
     * Azubi <b>und</b> Firma (Besteller). Noch nicht abrechenbar — das wird sie erst mit der
     * Vertragsbestätigung der Firma (Schritt F → {@code AKTIV}).
     */
    @Transactional
    public Anmeldung bestaetigeDurchEbz(Long anmeldungId) {
        Anmeldung a = Anmeldung.findById(anmeldungId);
        if (a == null) {
            throw RegelVerletzung.nichtGefunden("Anmeldung nicht gefunden: " + anmeldungId);
        }
        if (a.status != AnmeldungStatus.ANGEFRAGT) {
            throw new RegelVerletzung("Nur ANGEFRAGTe Anmeldungen können bestätigt werden (aktuell: "
                    + a.status + ").");
        }
        a.status = AnmeldungStatus.BESTAETIGT_EBZ;
        prozess.schritt("Anmeldung prüfen & bestätigen", Akteur.EBZ, Prozess.System.COCKPIT,
                Typ.USER_TASK, Phase.EBZ_BESTAETIGUNG);

        String azubiMail = a.teilnehmerEmail;
        if (azubiMail != null && !azubiMail.isBlank()) {
            mailer.send(Mail.withText(azubiMail,
                    "Deine Anmeldung zur Berufsschule ist bestätigt",
                    """
                    Hallo %s,

                    deine Anmeldung zur Berufsschule (%s) wurde vom EBZ geprüft und bestätigt.
                    Den Zugang zum Portal hast du per separater Einladung erhalten.

                    Viele Grüße
                    Dein EBZ-Team
                    """.formatted(a.teilnehmerName, schuljahrHalbjahr(a))));
            prozess.schritt("Bestätigungsmail an Azubi", Akteur.SYSTEM, Prozess.System.MAIL, Typ.MESSAGE,
                    Phase.EBZ_BESTAETIGUNG);
        }

        String firmaMail = bestellerEmail(a.bestellerPersonId);
        if (firmaMail != null && !firmaMail.isBlank()) {
            mailer.send(Mail.withText(firmaMail,
                    "Anmeldebestätigung: " + a.teilnehmerName,
                    """
                    Guten Tag,

                    die Anmeldung von %s zur Berufsschule (%s) ist vom EBZ bestätigt. Bitte bestätigen
                    Sie abschließend den Ausbildungsvertrag im Portal, damit die Abrechnung erfolgen kann.

                    Viele Grüße
                    Ihr EBZ-Team
                    """.formatted(a.teilnehmerName, schuljahrHalbjahr(a))));
            prozess.schritt("Bestätigungsmail an Firma", Akteur.SYSTEM, Prozess.System.MAIL, Typ.MESSAGE,
                    Phase.EBZ_BESTAETIGUNG);
        }
        return a;
    }

    /**
     * Die Firma bestätigt abschließend den Vertrag ({@code BESTAETIGT_EBZ → AKTIV}) — ab jetzt
     * abrechenbar, der bestehende Rechnungslauf zieht sie. Mit Audit (wer/wann).
     */
    @Transactional
    public Anmeldung bestaetigeVertrag(Long anmeldungId, Long bestaetigerPersonId) {
        Anmeldung a = Anmeldung.findById(anmeldungId);
        if (a == null) {
            throw RegelVerletzung.nichtGefunden("Anmeldung nicht gefunden: " + anmeldungId);
        }
        if (a.status != AnmeldungStatus.BESTAETIGT_EBZ) {
            throw new RegelVerletzung("Nur EBZ-bestätigte Anmeldungen können vertraglich bestätigt werden "
                    + "(aktuell: " + a.status + ").");
        }
        a.status = AnmeldungStatus.AKTIV;
        a.vertragBestaetigtAm = Instant.now();
        a.vertragBestaetigtVon = bestaetigerPersonId;
        prozess.schritt("Ausbildungsvertrag bestätigen", Akteur.FIRMA, Prozess.System.PORTAL,
                Typ.USER_TASK, Phase.VERTRAG);
        return a;
    }

    private static String schuljahrHalbjahr(Anmeldung a) {
        return a.schuljahr == null ? "" : a.schuljahr + ", " + a.halbjahr + ". Halbjahr";
    }

    private static String bestellerEmail(Long bestellerPersonId) {
        if (bestellerPersonId == null) {
            return null;
        }
        PersonEmail e = PersonEmail.find("personId = ?1 and primaer = true", bestellerPersonId).firstResult();
        if (e == null) {
            e = PersonEmail.find("personId", bestellerPersonId).firstResult();
        }
        return e == null ? null : e.email;
    }
}
