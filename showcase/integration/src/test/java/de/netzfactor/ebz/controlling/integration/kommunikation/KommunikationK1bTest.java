package de.netzfactor.ebz.controlling.integration.kommunikation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp.Kategorie;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.DigestScheduler;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.EinstellungService;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.PraeferenzService;
import de.netzfactor.ebz.controlling.integration.kommunikation.service.ZustellService;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.party.service.PartyHoheitService;

/**
 * K1b — Komfort: Kanal×Kategorie-Präferenz (Override vor global), Digest-Bündelung (eine Sammel-Mail statt
 * Einzelmails) und Quiet-Hours (Deferred-Send: kein Sofortversand im Ruhefenster). Getrieben über die
 * Event-Spine; externer Versand wird deterministisch manuell ausgelöst (Dispatcher/Digest im Test aus).
 */
@QuarkusTest
class KommunikationK1bTest {

    @Inject
    Event<KommunikationsEreignis> spine;

    @Inject
    PraeferenzService praeferenzen;

    @Inject
    EinstellungService einstellungen;

    @Inject
    DigestScheduler digest;

    @Inject
    ZustellService zustellService;

    @Inject
    PartyHoheitService party;

    @Inject
    MockMailbox mailbox;

    @Test
    void kategorieOverride_blocktNurDieKategorie() {
        Person p = party.selbstRegistrieren("k1b-kat-sub", "k1b-kat@ebz.de", "Kai Kategorie");
        // E-Mail global an (Default), aber für RECHNUNG abgeschaltet
        praeferenzen.setze(p.id, Kanal.EMAIL, Kategorie.RECHNUNG, false);
        mailbox.clear();

        spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.RECHNUNG_VERSANDT, p.id,
                "Rechnung " + System.nanoTime(), KontextTyp.RECHNUNG, null, null, "k1b:kat-re:" + System.nanoTime()));
        spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.EINSCHREIBUNG_AKTIV, p.id,
                "Einschreibung " + System.nanoTime(), KontextTyp.BILDUNGSANGEBOT, null, null, "k1b:kat-es:" + System.nanoTime()));
        zustellService.verarbeiteFaellige(100);

        // RECHNUNG-E-Mail unterdrückt (Kategorie aus), EINSCHREIBUNG-E-Mail zugestellt (global an)
        assertEquals(1, mailbox.getMailMessagesSentTo("k1b-kat@ebz.de").size(),
                "nur die EINSCHREIBUNG-E-Mail; RECHNUNG per Kategorie-Override geblockt");
    }

    @Test
    void digest_buendeltZuEinerMail() {
        Person p = party.selbstRegistrieren("k1b-digest-sub", "k1b-digest@ebz.de", "Dora Digest");
        einstellungen.setze(p.id, true, null, null, 0); // Digest an
        mailbox.clear();

        spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.RECHNUNG_VERSANDT, p.id,
                "Posten A " + System.nanoTime(), KontextTyp.RECHNUNG, null, null, "k1b:dig-a:" + System.nanoTime()));
        spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.EINSCHREIBUNG_AKTIV, p.id,
                "Posten B " + System.nanoTime(), KontextTyp.BILDUNGSANGEBOT, null, null, "k1b:dig-b:" + System.nanoTime()));

        // Kein Sofortversand über die Outbox (digest-ausstehend, kein Auftrag)
        zustellService.verarbeiteFaellige(100);
        assertTrue(mailbox.getMailMessagesSentTo("k1b-digest@ebz.de").isEmpty(), "kein Einzelversand bei Digest");

        // Sammel-Lauf → genau eine gebündelte Mail mit beiden Posten
        digest.versendeFaellige();
        var mails = mailbox.getMailMessagesSentTo("k1b-digest@ebz.de");
        assertEquals(1, mails.size(), "genau eine Sammel-Mail");
        assertTrue(mails.get(0).getText().contains("Posten A") && mails.get(0).getText().contains("Posten B"),
                "Sammel-Mail listet beide Benachrichtigungen");
    }

    @Test
    void quietHours_verschiebtDenVersand() {
        Person p = party.selbstRegistrieren("k1b-quiet-sub", "k1b-quiet@ebz.de", "Quirin Quiet");
        // Ruhefenster, das die aktuelle Uhrzeit sicher einschließt (jetzt-1h bis jetzt+1h)
        LocalTime jetzt = LocalTime.now();
        einstellungen.setze(p.id, false, jetzt.minusHours(1), jetzt.plusHours(1), 0);
        mailbox.clear();

        spine.fire(KommunikationsEreignis.mitKontext(EreignisTyp.RECHNUNG_VERSANDT, p.id,
                "Ruhe-Rechnung " + System.nanoTime(), KontextTyp.RECHNUNG, null, null, "k1b:quiet:" + System.nanoTime()));

        // Auftrag ist eingereiht, aber wegen Quiet-Hours noch nicht fällig → kein Versand jetzt
        zustellService.verarbeiteFaellige(100);
        assertTrue(mailbox.getMailMessagesSentTo("k1b-quiet@ebz.de").isEmpty(), "Versand im Ruhefenster verschoben");
    }
}
