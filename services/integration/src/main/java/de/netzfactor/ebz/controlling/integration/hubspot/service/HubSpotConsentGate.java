package de.netzfactor.ebz.controlling.integration.hubspot.service;

import jakarta.enterprise.context.ApplicationScoped;

import de.netzfactor.ebz.controlling.integration.party.model.Einwilligung;
import de.netzfactor.ebz.controlling.integration.party.model.Person;

/**
 * <b>Einzige Quelle der DSGVO-/Marketing-Regel</b> für den HubSpot-Sync. Bildet den maßgeblichen Zustand
 * im MDM-Kern (Einwilligung/Werbesperre/Auskunftssperre/Lösch-Lebenszyklus) auf ein eindeutiges Urteil ab;
 * Mapper und Dispatcher entscheiden ausschließlich anhand dieses Urteils — keine verstreute Consent-Logik.
 * <p>
 * Reihenfolge der Tore (das erste greifende gewinnt):
 * <ol>
 *   <li>{@code loeschStatus != AKTIV} → {@link Urteil#ERASE} (Art. 17 — Vorrang vor allem).</li>
 *   <li>{@code auskunftssperre} → {@link Urteil#NIE} (niemals an ein Marketing-System).</li>
 *   <li>{@code werbesperre} <i>oder</i> kein gültiges Opt-in → {@link Urteil#SYNC_NICHT_MARKETABLE}
 *       (Kontakt darf bestehen, aber Marketing aus).</li>
 *   <li>gültiges EMAIL/NEWSLETTER-Opt-in {@code ERTEILT} → {@link Urteil#SYNC_MARKETABLE}.</li>
 * </ol>
 */
@ApplicationScoped
public class HubSpotConsentGate {

    public enum Urteil {
        /** Kontakt synchronisieren und marketingfähig setzen (gültiges Opt-in). */
        SYNC_MARKETABLE,
        /** Kontakt synchronisieren, aber Marketing abgeschaltet (Widerruf/Werbesperre). */
        SYNC_NICHT_MARKETABLE,
        /** Niemals an HubSpot (Auskunftssperre/Person unbekannt). */
        NIE,
        /** Recht auf Vergessen — in HubSpot löschen + Mapping entfernen. */
        ERASE
    }

    /** Urteil für eine Person (siehe Tor-Reihenfolge in der Klassen-Doku). */
    public Urteil beurteile(Person p) {
        if (p == null) {
            return Urteil.NIE;
        }
        if (p.loeschStatus != Person.LoeschStatus.AKTIV) {
            return Urteil.ERASE;
        }
        if (p.auskunftssperre) {
            return Urteil.NIE;
        }
        if (p.werbesperre) {
            return Urteil.SYNC_NICHT_MARKETABLE;
        }
        return hatNewsletterOptIn(p.id) ? Urteil.SYNC_MARKETABLE : Urteil.SYNC_NICHT_MARKETABLE;
    }

    /** Marketingfähig (= aktives Opt-in, kein hartes Tor)? Bequemer Shortcut für den Mapper. */
    public boolean istMarketingErlaubt(Person p) {
        return beurteile(p) == Urteil.SYNC_MARKETABLE;
    }

    private boolean hatNewsletterOptIn(Long personId) {
        return Einwilligung.count(
                "person.id = ?1 and kanal = ?2 and zweck = ?3 and status = ?4",
                personId, Einwilligung.Kanal.EMAIL, Einwilligung.Zweck.NEWSLETTER,
                Einwilligung.Status.ERTEILT) > 0;
    }
}
