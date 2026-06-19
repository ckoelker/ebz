package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.event.KommunikationsEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Zustellung.Kanal;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ErreichbarkeitPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.RealtimePort;

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

    /**
     * Projiziert ein Ereignis: legt (idempotent, nur wenn {@link EreignisTyp#sichtbar}) ein
     * {@link PersonEreignis} an und fächert die <b>erlaubten</b> Push-Kanäle auf (PORTAL synchron; EMAIL/SMS
     * in die Outbox). Nicht sichtbare Typen (interne Vermerke) sowie unbekannte Personen erzeugen nichts.
     * Liefert das Log-Ereignis bzw. {@code null}.
     */
    @Transactional
    public PersonEreignis protokolliere(KommunikationsEreignis ev) {
        EreignisTyp typ = ev.ereignisTyp();
        if (!typ.sichtbar) {
            return null; // interner Vermerk → gehört in das staff-interne CRM-Log (Aktivitaet), nicht hierher
        }
        if (ev.idempotenzSchluessel() != null) {
            PersonEreignis vorhanden = PersonEreignis.find("idempotenzSchluessel", ev.idempotenzSchluessel()).firstResult();
            if (vorhanden != null) {
                return vorhanden; // Dedupe (Retry/Re-Delivery)
            }
        }
        Set<Kanal> kanaele = erreichbarkeit.erlaubteKanaele(ev.empfaengerPersonId(), typ);
        if (kanaele.isEmpty()) {
            LOG.warnf("Kommunikations-Ereignis ohne erreichbare/auflösbare Person %d verworfen",
                    ev.empfaengerPersonId());
            return null;
        }

        PersonEreignis pe = new PersonEreignis();
        pe.empfaengerPersonId = ev.empfaengerPersonId();
        pe.ereignisTyp = typ;
        pe.betreff = ev.betreff();
        pe.kontextTyp = ev.kontextTyp() == null ? PersonEreignis.KontextTyp.KEINER : ev.kontextTyp();
        pe.kontextId = ev.kontextId();
        pe.prozessFall = ev.prozessFall();
        pe.sichtbar = true;
        pe.bestaetigungErforderlich = typ.bestaetigungErforderlich;
        pe.idempotenzSchluessel = ev.idempotenzSchluessel();
        pe.persist();

        boolean digest = einstellung.istDigest(pe.empfaengerPersonId);
        for (Kanal kanal : kanaele) {
            if (!praeferenz.erlaubt(pe.empfaengerPersonId, kanal, typ.kategorie)) {
                continue; // Kanal×Kategorie abgeschaltet (PORTAL bleibt immer erlaubt)
            }
            Zustellung z = new Zustellung();
            z.personEreignis = pe;
            z.kanal = kanal;
            z.status = Zustellung.Status.NEU;
            z.persist();
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

    /** Pflicht-Bestätigung („zur Kenntnis genommen") mit Nachweis-Trio (Felder jetzt; Durchsetzung K5). */
    @Transactional
    public void bestaetige(Long personEreignisId, String sub, String ip) {
        PersonEreignis pe = PersonEreignis.findById(personEreignisId);
        if (pe != null && pe.bestaetigtAm == null) {
            pe.bestaetigtAm = LocalDateTime.now();
            pe.bestaetigtVon = sub;
            pe.nachweisIp = ip;
            pe.nachweisZeit = LocalDateTime.now();
        }
    }
}
