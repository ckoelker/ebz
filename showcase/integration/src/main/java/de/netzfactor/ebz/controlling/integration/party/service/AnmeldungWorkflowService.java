package de.netzfactor.ebz.controlling.integration.party.service;

import java.time.Instant;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Ereignis;
import de.netzfactor.ebz.controlling.integration.outbox.model.OutboxAuftrag.Zielsystem;
import de.netzfactor.ebz.controlling.integration.outbox.service.OutboxService;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
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
    Prozessspur prozess;

    @Inject
    OutboxService outbox;

    /**
     * Event-Spine (K1): die Bestätigungs-Übergänge feuern ein {@link KommunikationsEreignis}; der
     * {@code BenachrichtigungService} (@Observes) projiziert es in den personenseitigen Aktivitätslog
     * (Portal-Zeitstrahl + Badge). Reine Richtung party→kommunikation-Vertrag (kein Modul-Interna-Zugriff).
     */
    @Inject
    Event<KommunikationsEreignis> benachrichtigung;

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

        Map<String, Object> vars = Map.of("teilnehmerName", a.teilnehmerName == null ? "" : a.teilnehmerName,
                "schuljahrHalbjahr", schuljahrHalbjahr(a));

        // Event-Spine: Azubi-Mail an die teilnehmerEmail (Direkt-Empfänger, oft keine Person) → E-Mail-only.
        String azubiMail = a.teilnehmerEmail;
        if (azubiMail != null && !azubiMail.isBlank()) {
            benachrichtigung.fire(KommunikationsEreignis.anEmpfaenger(
                    EreignisTyp.ANMELDUNG_BESTAETIGT_AZUBI, azubiMail,
                    "Deine Anmeldung zur Berufsschule ist bestätigt", KontextTyp.ANMELDUNG, a.id,
                    "anmeldung-azubi:" + a.id, vars));
            prozess.schritt("Bestätigungsmail an Azubi", Akteur.SYSTEM, Prozess.System.MAIL, Typ.MESSAGE,
                    Phase.EBZ_BESTAETIGUNG);
        }

        // Event-Spine: Besteller/Firma (Person) → Portal-Aktivitätslog + E-Mail (Bestands-Mail migriert).
        Long besteller = a.bestellerPersonId();
        if (besteller != null) {
            benachrichtigung.fire(KommunikationsEreignis.mitVariablen(
                    EreignisTyp.ANMELDUNG_BESTAETIGT, besteller,
                    "Anmeldebestätigung: " + a.teilnehmerName, KontextTyp.ANMELDUNG, a.id,
                    "anmeldung-bestaetigt:" + a.id, vars));
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
        a.vertragBestaetiger = bestaetigerPersonId == null ? null : Person.findById(bestaetigerPersonId);
        prozess.schritt("Ausbildungsvertrag bestätigen", Akteur.FIRMA, Prozess.System.PORTAL,
                Typ.USER_TASK, Phase.VERTRAG);

        // Drittsystem-Provisionierung NICHT synchron hier (würde die Bestätigung an die Drittsysteme
        // koppeln), sondern je Ziel als Outbox-Auftrag in DERSELBEN Transaktion (atomar). Der Dispatcher
        // zieht sie asynchron, idempotent und mit Retry/Dead-Letter nach. [[outbox-drittsystem-provisionierung]]
        // - WebUntis: Übernahme als Schüler (Stundenplan/Klassenbuch)
        // - Suite8:   Konto + Bezahlkarte für Kiosk & Kantine
        outbox.enqueue(a, Zielsystem.WEBUNTIS, Ereignis.AZUBI_VERTRAG_BESTAETIGT);
        outbox.enqueue(a, Zielsystem.SUITE8, Ereignis.AZUBI_VERTRAG_BESTAETIGT);

        // Event-Spine: Portal-Benachrichtigung an den Bestätiger (Kenntnisnahme-Pflicht, K5-Durchsetzung).
        if (bestaetigerPersonId != null) {
            benachrichtigung.fire(KommunikationsEreignis.mitKontext(
                    EreignisTyp.AZUBI_VERTRAG_BESTAETIGT, bestaetigerPersonId,
                    "Ausbildungsvertrag bestätigt: " + a.teilnehmerName, KontextTyp.ANMELDUNG, a.id, null,
                    "vertrag-bestaetigt:" + a.id));
        }
        return a;
    }

    private static String schuljahrHalbjahr(Anmeldung a) {
        return a.schuljahr == null ? "" : a.schuljahr + ", " + a.halbjahr + ". Halbjahr";
    }
}
