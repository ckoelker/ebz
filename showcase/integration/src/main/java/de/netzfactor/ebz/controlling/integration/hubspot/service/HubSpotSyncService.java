package de.netzfactor.ebz.controlling.integration.hubspot.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.opentelemetry.api.baggage.Baggage;

import de.netzfactor.ebz.controlling.integration.hubspot.adapter.HubSpotMockSenke;
import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag;
import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag.Operation;
import de.netzfactor.ebz.controlling.integration.hubspot.model.HubSpotSyncAuftrag.Status;
import de.netzfactor.ebz.controlling.integration.hubspot.service.HubSpotConsentGate.Urteil;
import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke;
import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke.CompanyDto;
import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke.ConsentNachweis;
import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke.ContactDto;
import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke.ObjektTyp;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId;
import de.netzfactor.ebz.controlling.integration.party.model.ExterneId.Quelle;
import de.netzfactor.ebz.controlling.integration.party.model.Mitgliedschaft;
import de.netzfactor.ebz.controlling.integration.party.model.Organisation;
import de.netzfactor.ebz.controlling.integration.party.model.Person;
import de.netzfactor.ebz.controlling.integration.prozessdoku.Prozessspur;

/**
 * <b>Published Fassade des HubSpot-Outbound-Sync</b> + wiederverwendbare Transactional-Outbox-Mechanik
 * (Enqueue/Poll/Lock/Backoff/Dead-Letter, Muster wie {@code kommunikation.ZustellService}). Andere Module
 * rufen nur die {@code enqueue…}-Methoden; der {@code HubSpotSyncDispatcher} treibt {@link #verarbeiteFaellige}.
 * <p>
 * <b>ERASE-Vorrang ohne Sonderlogik:</b> der Dispatcher re-evaluiert beim Verarbeiten <i>immer</i> das
 * {@link HubSpotConsentGate} am aktuellen Personenzustand. Ein veraltetes UPSERT für eine inzwischen
 * gelöschte/gesperrte Person wird so automatisch zur Löschung — eine vorgemerkte Löschung kann nicht
 * „überholt" werden.
 */
@ApplicationScoped
public class HubSpotSyncService {

    private static final Logger LOG = Logger.getLogger(HubSpotSyncService.class);

    @Inject
    HubSpotMapper mapper;

    @Inject
    HubSpotConsentGate gate;

    @Inject
    HubSpotMockSenke mockSenke;

    /** Alle verfügbaren Senken; der reale Adapter ist nur bei {@code hubspot.sync.mode=real} dabei. */
    @Inject
    @jakarta.enterprise.inject.Any
    Instance<HubSpotSenke> senken;

    @ConfigProperty(name = "hubspot.sync.gdpr-delete.enabled", defaultValue = "true")
    boolean gdprDeleteEnabled;

    /** Bevorzugt den realen Adapter (wenn per Config aktiviert), sonst die Mock-Senke (immer vorhanden). */
    private HubSpotSenke senke() {
        for (HubSpotSenke s : senken) {
            if (!(s instanceof HubSpotMockSenke)) {
                return s;
            }
        }
        return mockSenke;
    }

    // ───────────────────────── Enqueue (in der Geschäfts-Tx) ─────────────────────────

    /** Reiht einen Stammdaten-Upsert des Kontakts ein (idempotent je offenem Auftrag). */
    @Transactional
    public HubSpotSyncAuftrag enqueueContact(Long personId) {
        return enqueueContact(personId, Operation.UPSERT);
    }

    /** Reiht eine zeitnahe Marketing-Entscheidungs-Spiegelung ein (Opt-in/Widerruf/Werbesperre). */
    @Transactional
    public HubSpotSyncAuftrag enqueueConsentChange(Long personId) {
        return enqueueContact(personId, Operation.CONSENT_UPDATE);
    }

    /** Reiht das Recht auf Vergessen für den Kontakt ein (Art. 17). */
    @Transactional
    public HubSpotSyncAuftrag enqueueErasure(Long personId) {
        return enqueueContact(personId, Operation.ERASE);
    }

    /** Reiht einen Firmen-Upsert ein (Stammdaten + Marketing-Merkmale Branche/Verband/…). */
    @Transactional
    public HubSpotSyncAuftrag enqueueCompany(Long organisationId) {
        Organisation o = Organisation.findById(organisationId);
        if (o == null) {
            throw new IllegalArgumentException("Unbekannte Organisation " + organisationId);
        }
        HubSpotSyncAuftrag offen = offenerAuftrag(ObjektTyp.COMPANY, null, organisationId);
        return offen != null ? offen : persistAuftrag(ObjektTyp.COMPANY, Operation.UPSERT, null, o);
    }

    /**
     * Backfill: reiht alle aktiven Parteien (Kontakte + Firmen) idempotent ein. Liefert die Anzahl je Typ.
     * Das Consent-Gate entscheidet beim Verarbeiten, was tatsächlich nach HubSpot geht.
     */
    @Transactional
    public Map<String, Integer> reconcileAlle() {
        int kontakte = 0;
        for (Person p : Person.<Person>list("loeschStatus", Person.LoeschStatus.AKTIV)) {
            if (offenerAuftrag(ObjektTyp.CONTACT, p.id, null) == null) {
                persistAuftrag(ObjektTyp.CONTACT, Operation.UPSERT, p, null);
                kontakte++;
            }
        }
        int firmen = 0;
        for (Organisation o : Organisation.<Organisation>list("status", Organisation.Status.AKTIV)) {
            if (offenerAuftrag(ObjektTyp.COMPANY, null, o.id) == null) {
                persistAuftrag(ObjektTyp.COMPANY, Operation.UPSERT, null, o);
                firmen++;
            }
        }
        return Map.of("kontakte", kontakte, "firmen", firmen);
    }

    /** Manueller Neuversuch eines fehlgeschlagenen/toten Auftrags (Cockpit-HITL). */
    @Transactional
    public HubSpotSyncAuftrag neuVersuch(Long auftragId) {
        HubSpotSyncAuftrag a = HubSpotSyncAuftrag.findById(auftragId);
        if (a == null || a.status == Status.ERLEDIGT) {
            return a;
        }
        a.status = Status.NEU;
        a.naechsterVersuchAm = Instant.now();
        return a;
    }

    /** Reiht die Verknüpfung Kontakt↔Firma aus einer Mitgliedschaft ein (Association). */
    @Transactional
    public HubSpotSyncAuftrag enqueueAssociation(Long mitgliedschaftId) {
        Mitgliedschaft m = Mitgliedschaft.findById(mitgliedschaftId);
        if (m == null) {
            throw new IllegalArgumentException("Unbekannte Mitgliedschaft " + mitgliedschaftId);
        }
        HubSpotSyncAuftrag offen = offenerAssoziation(m.person.id, m.organisation.id);
        return offen != null ? offen : persistAuftrag(ObjektTyp.ASSOCIATION, Operation.UPSERT, m.person, m.organisation);
    }

    private HubSpotSyncAuftrag enqueueContact(Long personId, Operation op) {
        Person p = Person.findById(personId);
        if (p == null) {
            throw new IllegalArgumentException("Unbekannte Person " + personId);
        }
        HubSpotSyncAuftrag offen = offenerAuftrag(ObjektTyp.CONTACT, personId, null);
        if (offen != null) {
            // Bestehender offener Auftrag: eine vorgemerkte ERASE bleibt; sonst auf die „stärkere" Operation heben.
            if (offen.operation != Operation.ERASE && op == Operation.ERASE) {
                offen.operation = Operation.ERASE;
            }
            return offen;
        }
        return persistAuftrag(ObjektTyp.CONTACT, op, p, null);
    }

    private HubSpotSyncAuftrag persistAuftrag(ObjektTyp typ, Operation op, Person p, Organisation o) {
        HubSpotSyncAuftrag a = new HubSpotSyncAuftrag();
        a.objektTyp = typ;
        a.operation = op;
        a.person = p;
        a.organisation = o;
        a.status = Status.NEU;
        a.erstelltAm = Instant.now();
        a.naechsterVersuchAm = a.erstelltAm;
        a.idempotenzSchluessel = op + ":" + typ + ":"
                + (p != null ? "p" + p.id : "") + (o != null ? "o" + o.id : "")
                + ":" + a.erstelltAm.toEpochMilli();
        a.prozessFall = aktuellerFall();
        a.persist();
        return a;
    }

    private static HubSpotSyncAuftrag offenerAuftrag(ObjektTyp typ, Long personId, Long orgId) {
        return HubSpotSyncAuftrag.find(
                "objektTyp = ?1 and status in ?2 and "
                        + (personId != null ? "person.id = ?3" : "organisation.id = ?3"),
                typ, List.of(Status.NEU, Status.FEHLER), personId != null ? personId : orgId)
                .firstResult();
    }

    private static HubSpotSyncAuftrag offenerAssoziation(Long personId, Long orgId) {
        return HubSpotSyncAuftrag.find(
                "objektTyp = ?1 and status in ?2 and person.id = ?3 and organisation.id = ?4",
                ObjektTyp.ASSOCIATION, List.of(Status.NEU, Status.FEHLER), personId, orgId)
                .firstResult();
    }

    // ───────────────────────── Dispatch (async, eigene Tx) ─────────────────────────

    /** IDs aller fälligen, offenen Aufträge (eigene Lese-Tx für den Dispatcher). */
    @Transactional
    public List<Long> faelligeIds(int limit) {
        return HubSpotSyncAuftrag.<HubSpotSyncAuftrag>find(
                        "status in ?1 and naechsterVersuchAm <= ?2 order by id",
                        List.of(Status.NEU, Status.FEHLER), Instant.now())
                .page(0, limit).list()
                .stream().map(a -> a.id).toList();
    }

    /** Zieht alle fälligen Aufträge und verarbeitet sie einzeln. Liefert die Anzahl. */
    public int verarbeiteFaellige(int limit) {
        List<Long> ids = faelligeIds(limit);
        for (Long id : ids) {
            verarbeite(id);
        }
        return ids.size();
    }

    /**
     * Verarbeitet genau einen Auftrag in eigener Tx (Pessimistic-Lock gegen Doppelverarbeitung). Re-evaluiert
     * das Consent-Gate am aktuellen Personenzustand und ruft die passende {@link HubSpotSenke}-Operation.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void verarbeite(Long auftragId) {
        HubSpotSyncAuftrag a = HubSpotSyncAuftrag.findById(auftragId, LockModeType.PESSIMISTIC_WRITE);
        if (a == null || (a.status != Status.NEU && a.status != Status.FEHLER)) {
            return;
        }
        a.versuche++;
        try {
            switch (a.objektTyp) {
                case CONTACT -> verarbeiteContact(a);
                case COMPANY -> verarbeiteCompany(a);
                case ASSOCIATION -> verarbeiteAssociation(a);
            }
            if (a.status == Status.NEU) { // verarbeiteContact darf MANUELL gesetzt haben
                a.status = Status.ERLEDIGT;
            }
            a.erledigtAm = Instant.now();
            a.letzterFehler = null;
        } catch (RuntimeException e) {
            a.letzterFehler = kurz(e.getMessage());
            if (a.versuche >= HubSpotSyncAuftrag.MAX_VERSUCHE) {
                a.status = Status.TOT;
                LOG.errorf("HubSpot-Sync-Auftrag %d eskaliert nach %d Versuchen: %s",
                        a.id, a.versuche, a.letzterFehler);
            } else {
                a.status = Status.FEHLER;
                a.naechsterVersuchAm = Instant.now().plus(backoff(a.versuche));
                LOG.warnf("HubSpot-Sync-Auftrag %d Versuch %d fehlgeschlagen (%s) → Retry %s",
                        a.id, a.versuche, a.letzterFehler, a.naechsterVersuchAm);
            }
        }
    }

    private void verarbeiteContact(HubSpotSyncAuftrag a) {
        Person p = a.person;
        if (p == null) {
            return; // nichts zu tun
        }
        Urteil u = gate.beurteile(p);
        ExterneId map = mapping(Quelle.HUBSPOT, p);

        // Löschpfad: explizites Recht auf Vergessen, gelöschte/gesperrte Person, oder Auskunftssperre mit Altbestand.
        if (u == Urteil.ERASE || (u == Urteil.NIE && map != null)) {
            if (map == null) {
                return; // nie synchronisiert → nichts in HubSpot
            }
            if (gdprDeleteEnabled) {
                senke().gdprLoesche(ObjektTyp.CONTACT, map.externeId);
                map.delete();
            } else {
                // Fallback: nicht permanent löschen, Marketing aus, manuelle Löschung im Cockpit anstoßen.
                senke().archiviere(ObjektTyp.CONTACT, map.externeId);
                senke().setzeMarketingStatus(map.externeId, false, nachweis(u));
                a.status = Status.MANUELL;
            }
            return;
        }
        if (u == Urteil.NIE) {
            return; // Auskunftssperre ohne Altbestand → nichts senden
        }

        boolean marketable = (u == Urteil.SYNC_MARKETABLE);
        // Schneller Consent-Pfad: nur Status spiegeln, wenn der Kontakt schon existiert.
        if (a.operation == Operation.CONSENT_UPDATE && map != null) {
            senke().setzeMarketingStatus(map.externeId, marketable, nachweis(u));
            return;
        }
        ContactDto dto = mapper.zuContact(p);
        if (dto.email() == null && map == null) {
            a.letzterFehler = "keine primäre E-Mail — Contact übersprungen";
            return; // ERLEDIGT mit Vermerk; kein anlegbarer Kontakt
        }
        String hubspotId = senke().upsertContact(dto);
        if (map == null) {
            ExterneId neu = new ExterneId();
            neu.quelle = Quelle.HUBSPOT;
            neu.externeId = hubspotId;
            neu.person = p;
            neu.persist();
        } else {
            map.externeId = hubspotId;
        }
        senke().setzeMarketingStatus(hubspotId, marketable, nachweis(u));
    }

    private void verarbeiteCompany(HubSpotSyncAuftrag a) {
        Organisation o = a.organisation;
        if (o == null) {
            return;
        }
        // Firmen sind keine personenbezogenen Daten → kein Consent-Gate; Marketing-Merkmale gehen mit.
        ExterneId map = mappingOrg(Quelle.HUBSPOT, o);
        CompanyDto dto = mapper.zuCompany(o);
        String hubspotId = senke().upsertCompany(dto);
        if (map == null) {
            ExterneId neu = new ExterneId();
            neu.quelle = Quelle.HUBSPOT;
            neu.externeId = hubspotId;
            neu.organisation = o;
            neu.persist();
        } else {
            map.externeId = hubspotId;
        }
    }

    private void verarbeiteAssociation(HubSpotSyncAuftrag a) {
        Person p = a.person;
        Organisation o = a.organisation;
        if (p == null || o == null) {
            return;
        }
        ExterneId contactMap = mapping(Quelle.HUBSPOT, p);
        ExterneId companyMap = mappingOrg(Quelle.HUBSPOT, o);
        if (contactMap == null || companyMap == null) {
            // Reihenfolge: Contact/Company müssen zuerst gemappt sein → Retry (Backoff), bis das Mapping steht.
            throw new IllegalStateException("Association: Mapping fehlt (Contact/Company noch nicht synchronisiert)");
        }
        senke().verknuepfe(contactMap.externeId, companyMap.externeId);
    }

    private static ExterneId mapping(Quelle quelle, Person p) {
        return ExterneId.find("quelle = ?1 and person.id = ?2", quelle, p.id).firstResult();
    }

    private static ExterneId mappingOrg(Quelle quelle, Organisation o) {
        return ExterneId.find("quelle = ?1 and organisation.id = ?2", quelle, o.id).firstResult();
    }

    private static ConsentNachweis nachweis(Urteil u) {
        String grund = switch (u) {
            case SYNC_MARKETABLE -> "EINWILLIGUNG_6_1_A";
            case SYNC_NICHT_MARKETABLE -> "WIDERRUF_ODER_WERBESPERRE";
            default -> "ART_17";
        };
        return new ConsentNachweis(grund, Instant.now().toString(), "MDM");
    }

    private static Duration backoff(int versuch) {
        long sek = Math.min(30L * (1L << (versuch - 1)), 3600L);
        return Duration.ofSeconds(sek);
    }

    private static String kurz(String s) {
        if (s == null) {
            return "(ohne Meldung)";
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    private static String aktuellerFall() {
        String fall = Baggage.current().getEntryValue(Prozessspur.BAGGAGE_FALL);
        return (fall == null || fall.isBlank()) ? null : fall;
    }
}
