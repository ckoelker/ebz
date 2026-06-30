package de.netzfactor.ebz.controlling.integration.hubspot.adapter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.hubspot.spi.HubSpotSenke;

/**
 * <b>Default-Adapter ohne echtes HubSpot</b> — zeichnet alle Outbound-Aufrufe im Speicher auf und vergibt
 * synthetische, stabile IDs (gleiche {@code externeId} ⇒ gleiche ID ⇒ idempotent). Macht den ganzen Sync
 * ohne externes System lauf- und testbar (Stil der Quarkus-{@code MockMailbox}); die rest-assured-Tests
 * prüfen gegen {@link #aufrufe()}. Immer als Bean vorhanden; der {@code HubSpotSyncService} bevorzugt den
 * token-gateten {@code HubSpotApiSenke}, wenn {@code hubspot.sync.mode=real} ihn aktiviert.
 */
@ApplicationScoped
public class HubSpotMockSenke implements HubSpotSenke {

    private static final Logger LOG = Logger.getLogger(HubSpotMockSenke.class);

    /** Ein aufgezeichneter Outbound-Aufruf (für Test-Asserts und das Cockpit-Call-Log). */
    public record Aufruf(String methode, ObjektTyp objektTyp, String ziel, Boolean marketingErlaubt,
                         Map<String, String> properties) {
    }

    private final List<Aufruf> aufrufe = new CopyOnWriteArrayList<>();
    private final Map<String, String> contactIds = new ConcurrentHashMap<>();
    private final Map<String, String> companyIds = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public String upsertContact(ContactDto contact) {
        String id = contactIds.computeIfAbsent(contact.externeId(), k -> "mock-contact-" + seq.incrementAndGet());
        aufrufe.add(new Aufruf("upsertContact", ObjektTyp.CONTACT, contact.externeId(),
                contact.marketingErlaubt(), contact.weitere()));
        LOG.debugf("MOCK upsertContact %s → %s (marketable=%s)", contact.externeId(), id, contact.marketingErlaubt());
        return id;
    }

    @Override
    public String upsertCompany(CompanyDto company) {
        String id = companyIds.computeIfAbsent(company.externeId(), k -> "mock-company-" + seq.incrementAndGet());
        aufrufe.add(new Aufruf("upsertCompany", ObjektTyp.COMPANY, company.externeId(), null,
                Map.of("branche", nz(company.branche()), "verbaende", nz(company.verbaende()))));
        LOG.debugf("MOCK upsertCompany %s → %s", company.externeId(), id);
        return id;
    }

    @Override
    public void verknuepfe(String contactId, String companyId) {
        aufrufe.add(new Aufruf("verknuepfe", ObjektTyp.ASSOCIATION, contactId + "↔" + companyId, null, Map.of()));
    }

    @Override
    public void setzeMarketingStatus(String contactId, boolean erlaubt, ConsentNachweis nachweis) {
        aufrufe.add(new Aufruf("setzeMarketingStatus", ObjektTyp.CONTACT, contactId, erlaubt,
                Map.of("rechtsgrundlage", nachweis == null ? "" : nz(nachweis.rechtsgrundlage()))));
    }

    @Override
    public void gdprLoesche(ObjektTyp typ, String hubspotId) {
        aufrufe.add(new Aufruf("gdprLoesche", typ, hubspotId, null, Map.of()));
        LOG.debugf("MOCK gdprLoesche %s %s", typ, hubspotId);
    }

    @Override
    public void archiviere(ObjektTyp typ, String hubspotId) {
        aufrufe.add(new Aufruf("archiviere", typ, hubspotId, null, Map.of()));
    }

    // ── Test-/Cockpit-Sicht ──

    /** Alle aufgezeichneten Aufrufe (jüngste zuletzt). */
    public List<Aufruf> aufrufe() {
        return List.copyOf(aufrufe);
    }

    /** Aufrufe einer bestimmten Methode (z. B. {@code "upsertContact"}). */
    public List<Aufruf> aufrufeVon(String methode) {
        return aufrufe.stream().filter(a -> a.methode().equals(methode)).toList();
    }

    /** Setzt den Recorder zurück (Test-Isolation). */
    public void leeren() {
        aufrufe.clear();
        contactIds.clear();
        companyIds.clear();
        seq.set(0);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
