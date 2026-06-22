package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.baggage.Baggage;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ErreichbarkeitPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.RealtimePort;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozess;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * <b>Veröffentlichte Fassade</b> des Kommunikations-Moduls ({@code KommunikationApi}) — das Einzige, was
 * andere Module/Web aufrufen; der Rest des Pakets bleibt intern (per ArchUnit durchgesetzt). Bündelt die
 * Projektion eines {@link KommunikationsEreignis} in den personenseitigen <b>Aktivitätslog</b>
 * ({@link PersonEreignis}) plus die <b>Push</b>-Teilmenge ({@link Zustellung}: PORTAL synchron,
 * EMAIL/SMS über die Outbox) sowie Lese-/Bestätigungs-Operationen.
 * <p>
 * Der Kern kennt Personen nur als {@code Long}-IDs; Erreichbarkeit/Consent löst der
 * {@link ErreichbarkeitPort} auf (Party-Bezug nur im Adapter). In K0 wird die Fassade direkt aufgerufen
 * (Seeder/Tests); ab K1 delegiert der {@code BenachrichtigungService} (@Observes) hierher.
 */
@ApplicationScoped
public class KommunikationApi {

    private static final Logger LOG = Logger.getLogger(KommunikationApi.class);

    @Inject
    ZustellService zustellService;

    @Inject
    ErreichbarkeitPort erreichbarkeit;

    @Inject
    PraeferenzService praeferenz;

    @Inject
    EinstellungService einstellung;

    @Inject
    RealtimePort realtime;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Prozessspur prozess;

    /**
     * Projiziert ein Ereignis. Zwei Pfade:
     * <ul>
     *   <li><b>Person-Empfänger</b> (Default): legt — idempotent, nur wenn {@link EreignisTyp#sichtbar} — ein
     *       {@link PersonEreignis} an und fächert die <b>erlaubten</b> Push-Kanäle auf (PORTAL synchron;
     *       EMAIL/SMS über die Outbox, Consent/Präferenz/Digest greifen).</li>
     *   <li><b>Direkt-Empfänger</b> ({@code ev.anEmail()} gesetzt, keine Person auflösbar — Bestands-Mail-
     *       Migration für Azubi-/Debitor-Adressen): rein transaktionale E-Mail über Template + Zustell-Outbox,
     *       <b>ohne</b> Portal-Log/Consent (der Eintrag dient nur als Zustell-/Audit-Anker, {@code sichtbar=false}).</li>
     * </ul>
     * Interne Vermerke (nicht sichtbar, ohne {@code anEmail}) sowie unbekannte Person ohne {@code anEmail}
     * erzeugen nichts. Liefert das Log-/Anker-Ereignis bzw. {@code null}.
     */
    @Transactional
    public PersonEreignis protokolliere(KommunikationsEreignis ev) {
        EreignisTyp typ = ev.ereignisTyp();
        boolean direktEmail = ev.anEmail() != null && !ev.anEmail().isBlank();
        if (!typ.sichtbar && !direktEmail) {
            return null; // interner Vermerk → gehört in das staff-interne CRM-Log (Aktivitaet), nicht hierher
        }
        if (ev.idempotenzSchluessel() != null) {
            PersonEreignis vorhanden = PersonEreignis.find("idempotenzSchluessel", ev.idempotenzSchluessel()).firstResult();
            if (vorhanden != null) {
                return vorhanden; // Dedupe (Retry/Re-Delivery)
            }
        }
        Set<Kanal> kanaele = ev.empfaengerPersonId() == null ? Set.of()
                : erreichbarkeit.erlaubteKanaele(ev.empfaengerPersonId(), typ);
        boolean personErreichbar = !kanaele.isEmpty();
        if (!personErreichbar && !direktEmail) {
            LOG.warnf("Kommunikations-Ereignis ohne erreichbare/auflösbare Person %s verworfen",
                    ev.empfaengerPersonId());
            return null;
        }

        PersonEreignis pe = new PersonEreignis();
        pe.empfaengerPersonId = ev.empfaengerPersonId();
        pe.anEmail = direktEmail ? ev.anEmail() : null;
        pe.variablenJson = variablenJson(ev.variablen());
        pe.ereignisTyp = typ;
        pe.betreff = ev.betreff();
        pe.kontextTyp = ev.kontextTyp() == null ? PersonEreignis.KontextTyp.KEINER : ev.kontextTyp();
        pe.kontextId = ev.kontextId();
        // Trace-/BPMN-Korrelation: explizit am Event ODER aus dem aktuellen W3C-Baggage (der @Observes-
        // Consumer läuft synchron im Auslöser-Kontext) → Support springt Benachrichtigung ↔ Trace/BPMN.
        pe.prozessFall = ev.prozessFall() != null ? ev.prozessFall() : aktuellerFall();
        // Portal-Log nur für eine bekannte Person bei sichtbarem Typ; Direkt-E-Mail bleibt unsichtbar.
        pe.sichtbar = typ.sichtbar && personErreichbar;
        if (personErreichbar) {
            pe.bestaetigungErforderlich = typ.bestaetigungErforderlich;
            if (typ.bestaetigungErforderlich && typ.bestaetigungFristTage > 0) {
                pe.bestaetigenBis = pe.zeitpunkt.plusDays(typ.bestaetigungFristTage); // K5: Frist ab Eingang
            }
        }
        pe.idempotenzSchluessel = ev.idempotenzSchluessel();
        pe.persist();
        prozess.schritt("Benachrichtigung auslösen (" + typ.name() + ")", Prozess.Akteur.SYSTEM,
                Prozess.System.PORTAL, Prozess.Typ.SERVICE_TASK, Prozess.Phase.BENACHRICHTIGUNG_AUSLOESEN);

        if (!personErreichbar) {
            // Direkt-Empfänger: nur die E-Mail über die Outbox (kein PORTAL/Consent/Realtime).
            Zustellung z = neueZustellung(pe, Kanal.EMAIL);
            zustellService.enqueue(z, Instant.now());
            return pe;
        }

        boolean digest = einstellung.istDigest(pe.empfaengerPersonId);
        for (Kanal kanal : kanaele) {
            if (!praeferenz.erlaubt(pe.empfaengerPersonId, kanal, typ.kategorie)) {
                continue; // Kanal×Kategorie abgeschaltet (PORTAL bleibt immer erlaubt)
            }
            Zustellung z = neueZustellung(pe, kanal);
            if (kanal == Kanal.PORTAL) {
                zustellService.zustelleSofort(z); // synchron in der Geschäfts-Tx (unverlierbar)
            } else if (digest) {
                z.digestAusstehend = true; // K1b: wartet auf den gebündelten DigestScheduler-Versand
            } else {
                // K1b: Quiet-Hours/Rate-Limit verschieben den frühesten Versand (Deferred-Send).
                zustellService.enqueue(z, einstellung.faelligAb(pe.empfaengerPersonId, Instant.now()));
            }
        }
        // Echtzeit-Signal (SSE): die Person bekommt sofort einen Badge-/Feed-Hinweis (best effort).
        realtime.signalisiere(pe.empfaengerPersonId, String.valueOf(pe.id));
        return pe;
    }

    private static Zustellung neueZustellung(PersonEreignis pe, Kanal kanal) {
        Zustellung z = new Zustellung();
        z.personEreignis = pe;
        z.kanal = kanal;
        z.status = Zustellung.Status.NEU;
        z.persist();
        return z;
    }

    /** Aktuelle Prozess-/Trace-Fall-ID aus dem W3C-Baggage (vom {@code Prozessspur.schritt} gesetzt); {@code null} ohne Kontext. */
    private static String aktuellerFall() {
        String fall = Baggage.current().getEntryValue(Prozessspur.BAGGAGE_FALL);
        return (fall == null || fall.isBlank()) ? null : fall;
    }

    private String variablenJson(java.util.Map<String, Object> variablen) {
        if (variablen == null || variablen.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(variablen);
        } catch (Exception e) {
            LOG.warnf("Template-Variablen nicht serialisierbar, ignoriert: %s", e.getMessage());
            return null;
        }
    }

    /** Sichtbarer Aktivitätslog einer Person (neueste zuerst) — der Pull-Zeitstrahl im Portal. */
    public List<PersonEreignis> ereignisseFuer(Long personId) {
        return PersonEreignis.list("empfaengerPersonId = ?1 and sichtbar = true order by zeitpunkt desc", personId);
    }

    /** Anzahl ungelesener PORTAL-Zustellungen (Badge). */
    public long ungelesen(Long personId) {
        return Zustellung.count(
                "personEreignis.empfaengerPersonId = ?1 and kanal = ?2 and gelesenAm is null",
                personId, Kanal.PORTAL);
    }

    /** Markiert die PORTAL-Zustellung eines Ereignisses als gelesen (Lese-Zeitstempel). */
    @Transactional
    public void markiereGelesen(Long personEreignisId) {
        Zustellung z = Zustellung.find("personEreignis.id = ?1 and kanal = ?2", personEreignisId, Kanal.PORTAL)
                .firstResult();
        if (z != null && z.gelesenAm == null) {
            z.gelesenAm = LocalDateTime.now();
            z.status = Zustellung.Status.GELESEN;
        }
    }

    /** Pflicht-Bestätigung („zur Kenntnis genommen") mit Nachweis-Trio; beendet Erinnerung/Eskalation (K5). */
    @Transactional
    public void bestaetige(Long personEreignisId, String sub, String ip) {
        PersonEreignis pe = PersonEreignis.findById(personEreignisId);
        if (pe != null && pe.bestaetigtAm == null) {
            pe.bestaetigtAm = LocalDateTime.now();
            pe.bestaetigtVon = sub;
            pe.nachweisIp = ip;
            pe.nachweisZeit = LocalDateTime.now();
            prozess.schritt("Pflicht-Bestätigung quittieren", Prozess.Akteur.KUNDE, Prozess.System.PORTAL,
                    Prozess.Typ.USER_TASK, Prozess.Phase.PFLICHT_BESTAETIGUNG);
        }
    }

    /**
     * K5-<b>Gate</b>: noch nicht quittierte Pflicht-Kenntnisnahmen der Person (neueste zuerst). Andere
     * Domänen-Flows können hierauf blockieren („solange Pflicht-Bestätigungen offen sind, kein X").
     */
    public List<PersonEreignis> offeneBestaetigungen(Long personId) {
        return PersonEreignis.list(
                "empfaengerPersonId = ?1 and bestaetigungErforderlich = true and bestaetigtAm is null "
                        + "order by zeitpunkt desc", personId);
    }

    /** K5-Gate (hart): die Person hat mindestens eine <b>überfällige</b> Pflicht-Kenntnisnahme. */
    public boolean hatUeberfaelligeBestaetigung(Long personId) {
        return PersonEreignis.count(
                "empfaengerPersonId = ?1 and bestaetigungErforderlich = true and bestaetigtAm is null "
                        + "and bestaetigenBis is not null and bestaetigenBis < ?2",
                personId, LocalDateTime.now()) > 0;
    }
}
