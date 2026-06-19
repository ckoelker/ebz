package de.netzfactor.ebz.controlling.integration.kommunikation.service;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;

import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation.Status;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation.Teilnehmer;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation.TeilnehmerRolle;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation.TeilnehmerTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Konversation.Typ;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.Nachricht;
import de.netzfactor.ebz.controlling.integration.kommunikation.model.PersonEreignis.KontextTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.CrmSpiegelPort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.RealtimePort;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.ThreadRealtimePort;

/**
 * Orchestriert <b>echte, zweiseitige Threads</b> ({@link Konversation}/{@link Nachricht}/{@link Teilnehmer},
 * Plan K2): Admin↔Person-Vorgänge (Cross-Realm — Mitarbeiter im Realm {@code ebz-staff}, Person in
 * {@code ebz-customers}), Person- und Staff-Antworten sowie (ab K2c) autonome/HITL-Agenten-Antworten.
 * Bewusst getrennt vom System→Person-Aktivitätslog ({@code PersonEreignis}, Modell-Entscheidung F28).
 * <p>
 * Party-frei (nur {@code Long}-IDs, split-ready): das Echtzeit-Signal läuft über den {@link RealtimePort},
 * die CRM-Spiegelung einer Staff-Nachricht („kein Doppelsystem") über den {@link CrmSpiegelPort} — beides
 * ausgehende Ports, kein direkter Party-Zugriff im Kern.
 */
@ApplicationScoped
public class KonversationService {

    @Inject
    RealtimePort realtime;

    @Inject
    ThreadRealtimePort threadRealtime;

    @Inject
    CrmSpiegelPort crmSpiegel;

    // ───────────────────────── Eröffnen / Antworten ─────────────────────────

    /** Staff eröffnet einen Vorgang an eine Person (ADMIN-Thread) und sendet die erste Nachricht. */
    @Transactional
    public Konversation eroeffneVorgang(Long mitarbeiterId, Long personId, String betreff,
            KontextTyp kontextTyp, Long kontextId, String inhaltHtml) {
        Konversation k = new Konversation();
        k.typ = Typ.ADMIN;
        k.betreff = betreff;
        k.kontextTyp = kontextTyp == null ? KontextTyp.KEINER : kontextTyp;
        k.kontextId = kontextId;
        k.status = Status.OFFEN;
        k.persist();
        teilnehmer(k, TeilnehmerTyp.MITARBEITER, mitarbeiterId, TeilnehmerRolle.ADMIN);
        teilnehmer(k, TeilnehmerTyp.PERSON, personId, TeilnehmerRolle.EMPFAENGER);
        sendeAlsStaff(k, mitarbeiterId, inhaltHtml);
        return k;
    }

    /** Staff antwortet in einem bestehenden Vorgang (übernimmt ihn bei Bedarf als Teilnehmer). */
    @Transactional
    public Nachricht antworteAlsStaff(Long konversationId, Long mitarbeiterId, String inhaltHtml) {
        Konversation k = mussKonversation(konversationId);
        teilnehmer(k, TeilnehmerTyp.MITARBEITER, mitarbeiterId, TeilnehmerRolle.ADMIN); // idempotent (Pickup)
        return sendeAlsStaff(k, mitarbeiterId, inhaltHtml);
    }

    /** Person antwortet in einem ihrer Vorgänge; eine Antwort öffnet einen geschlossenen Thread wieder. */
    @Transactional
    public Nachricht antworteAlsPerson(Long konversationId, Long personId, String inhaltHtml) {
        Konversation k = mussTeilnehmerPerson(konversationId, personId);
        Nachricht n = neueNachricht(k, TeilnehmerTyp.PERSON, personId, null, inhaltHtml, false);
        k.status = Status.OFFEN;
        signalisiereStaff(k);
        threadRealtime.signalisiereThread(k.id);
        return n;
    }

    /** Agenten-Antwort (K2c): KI-generiert markiert (EU-AI-Act Art. 50); Empfänger ist die Person. */
    @Transactional
    public Nachricht antworteAlsAgent(Long konversationId, String agentKennung, String inhaltHtml) {
        Konversation k = mussKonversation(konversationId);
        teilnehmer(k, TeilnehmerTyp.AGENT, null, agentKennung, TeilnehmerRolle.ADMIN);
        Nachricht n = neueNachricht(k, TeilnehmerTyp.AGENT, null, agentKennung, inhaltHtml, true);
        signalisierePerson(k);
        threadRealtime.signalisiereThread(k.id);
        return n;
    }

    private Nachricht sendeAlsStaff(Konversation k, Long mitarbeiterId, String inhaltHtml) {
        Nachricht n = neueNachricht(k, TeilnehmerTyp.MITARBEITER, mitarbeiterId, null, inhaltHtml, false);
        crmSpiegel.spiegleStaffNachricht(mitarbeiterId, empfaengerPersonId(k), k.betreff, n.inhaltHtml);
        signalisierePerson(k);
        threadRealtime.signalisiereThread(k.id);
        return n;
    }

    // ───────────────────────── Lesen / Sichten ─────────────────────────

    /** Threads einer Person (neueste Aktivität zuerst). */
    public List<Konversation> konversationenFuerPerson(Long personId) {
        return Konversation.list("id in (select t.konversation.id from KonversationsTeilnehmer t "
                + "where t.personId = ?1) order by erstelltAm desc", personId);
    }

    /** Alle Admin-Vorgänge (Support-Pool des Backoffice), neueste zuerst. */
    public List<Konversation> konversationenFuerStaff() {
        return Konversation.list("typ = ?1 order by erstelltAm desc", Typ.ADMIN);
    }

    /** Nachrichten eines Threads (chronologisch). */
    public List<Nachricht> nachrichten(Long konversationId) {
        return Nachricht.list("konversation.id = ?1 order by zeitpunkt asc", konversationId);
    }

    /** Anzahl Threads mit ungelesenen (fremden) Nachrichten für die Person (Badge). */
    public long ungelesenFuerPerson(Long personId) {
        long n = 0;
        for (Teilnehmer t : eigeneTeilnahmen(TeilnehmerTyp.PERSON, personId)) {
            if (hatUngelesene(t)) {
                n++;
            }
        }
        return n;
    }

    /** Markiert einen Thread für die Person als gelesen (Read-Receipt: gelesen-bis = jetzt). */
    @Transactional
    public void markiereGelesenPerson(Long konversationId, Long personId) {
        Konversation k = mussTeilnehmerPerson(konversationId, personId);
        teilnehmerSatz(k, TeilnehmerTyp.PERSON, personId).gelesenBis = LocalDateTime.now();
    }

    /** Markiert einen Thread für den Mitarbeiter als gelesen (legt den Teilnehmer bei Bedarf an). */
    @Transactional
    public void markiereGelesenStaff(Long konversationId, Long mitarbeiterId) {
        Konversation k = mussKonversation(konversationId);
        teilnehmer(k, TeilnehmerTyp.MITARBEITER, mitarbeiterId, TeilnehmerRolle.ADMIN)
                .gelesenBis = LocalDateTime.now();
    }

    /** Hat der Teilnehmer ungelesene fremde Nachrichten in seinem Thread? */
    public boolean hatUngelesene(Teilnehmer t) {
        if (t.gelesenBis == null) {
            return Nachricht.count("konversation.id = ?1 and absenderTyp <> ?2",
                    t.konversation.id, t.teilnehmerTyp) > 0;
        }
        return Nachricht.count("konversation.id = ?1 and absenderTyp <> ?2 and zeitpunkt > ?3",
                t.konversation.id, t.teilnehmerTyp, t.gelesenBis) > 0;
    }

    /** Letzte Nachricht eines Threads (für die Listen-Vorschau); {@code null}, wenn leer. */
    public Nachricht letzteNachricht(Long konversationId) {
        return Nachricht.find("konversation.id = ?1 order by zeitpunkt desc", konversationId).firstResult();
    }

    public boolean istTeilnehmerPerson(Long konversationId, Long personId) {
        return Teilnehmer.count("konversation.id = ?1 and personId = ?2", konversationId, personId) > 0;
    }

    /** Hat die Person in diesem Thread ungelesene fremde Nachrichten? (Listen-Flag) */
    public boolean ungelesenImThreadPerson(Long konversationId, Long personId) {
        Teilnehmer t = Teilnehmer.find("konversation.id = ?1 and personId = ?2", konversationId, personId)
                .firstResult();
        return t != null && hatUngelesene(t);
    }

    /** Hat der Mitarbeiter in diesem Thread ungelesene fremde Nachrichten? (Admin-Listen-Flag) */
    public boolean ungelesenImThreadStaff(Long konversationId, Long mitarbeiterId) {
        Teilnehmer t = Teilnehmer.find("konversation.id = ?1 and mitarbeiterId = ?2", konversationId, mitarbeiterId)
                .firstResult();
        // Mitarbeiter ohne Teilnahme (noch nicht „gepickt") gilt als ungelesen, sobald es Personen-Nachrichten gibt.
        if (t == null) {
            return Nachricht.count("konversation.id = ?1 and absenderTyp = ?2", konversationId,
                    TeilnehmerTyp.PERSON) > 0;
        }
        return hatUngelesene(t);
    }

    // ───────────────────────── intern ─────────────────────────

    private Nachricht neueNachricht(Konversation k, TeilnehmerTyp typ, Long personId, String agentKennung,
            String inhaltHtml, boolean kiGeneriert) {
        Nachricht n = new Nachricht();
        n.konversation = k;
        n.absenderTyp = typ;
        if (typ == TeilnehmerTyp.MITARBEITER) {
            n.mitarbeiterId = personId; // bei MITARBEITER trägt der Parameter die Mitarbeiter-ID
        } else if (typ == TeilnehmerTyp.PERSON) {
            n.personId = personId;
        } else {
            n.agentKennung = agentKennung;
        }
        n.inhaltHtml = sanitize(inhaltHtml);
        n.kiGeneriert = kiGeneriert;
        n.persist();
        return n;
    }

    /** Stellt (idempotent) einen Teilnehmer sicher und liefert ihn. */
    private Teilnehmer teilnehmer(Konversation k, TeilnehmerTyp typ, Long partyId, TeilnehmerRolle rolle) {
        return teilnehmer(k, typ, partyId, null, rolle);
    }

    private Teilnehmer teilnehmer(Konversation k, TeilnehmerTyp typ, Long partyId, String agentKennung,
            TeilnehmerRolle rolle) {
        Teilnehmer vorhanden = teilnehmerSatzOderNull(k, typ, partyId, agentKennung);
        if (vorhanden != null) {
            return vorhanden;
        }
        Teilnehmer t = new Teilnehmer();
        t.konversation = k;
        t.teilnehmerTyp = typ;
        t.rolle = rolle;
        if (typ == TeilnehmerTyp.MITARBEITER) {
            t.mitarbeiterId = partyId;
        } else if (typ == TeilnehmerTyp.PERSON) {
            t.personId = partyId;
        } else {
            t.agentKennung = agentKennung;
        }
        t.persist();
        return t;
    }

    private static Teilnehmer teilnehmerSatzOderNull(Konversation k, TeilnehmerTyp typ, Long partyId,
            String agentKennung) {
        return switch (typ) {
            case PERSON -> Teilnehmer.find("konversation.id = ?1 and personId = ?2", k.id, partyId).firstResult();
            case MITARBEITER -> Teilnehmer.find("konversation.id = ?1 and mitarbeiterId = ?2", k.id, partyId)
                    .firstResult();
            case AGENT -> Teilnehmer.find("konversation.id = ?1 and agentKennung = ?2", k.id, agentKennung)
                    .firstResult();
        };
    }

    private static Teilnehmer teilnehmerSatz(Konversation k, TeilnehmerTyp typ, Long partyId) {
        return teilnehmerSatzOderNull(k, typ, partyId, null);
    }

    private static List<Teilnehmer> eigeneTeilnahmen(TeilnehmerTyp typ, Long partyId) {
        String feld = typ == TeilnehmerTyp.PERSON ? "personId" : "mitarbeiterId";
        return Teilnehmer.list(feld, partyId);
    }

    private static Long empfaengerPersonId(Konversation k) {
        Teilnehmer t = Teilnehmer.find("konversation.id = ?1 and teilnehmerTyp = ?2",
                k.id, TeilnehmerTyp.PERSON).firstResult();
        return t == null ? null : t.personId;
    }

    private void signalisierePerson(Konversation k) {
        Long pid = empfaengerPersonId(k);
        if (pid != null) {
            realtime.signalisiere(pid, "konversation:" + k.id);
        }
    }

    /** Echtzeit-Signal an alle Mitarbeiter-Teilnehmer (Admin-Inbox-Refresh); best effort. */
    private void signalisiereStaff(Konversation k) {
        List<Teilnehmer> staff = Teilnehmer.list("konversation.id = ?1 and teilnehmerTyp = ?2",
                k.id, TeilnehmerTyp.MITARBEITER);
        for (Teilnehmer t : staff) {
            realtime.signalisiere(t.mitarbeiterId, "konversation:" + k.id);
        }
    }

    private static Konversation mussKonversation(Long id) {
        Konversation k = Konversation.findById(id);
        if (k == null) {
            throw new jakarta.ws.rs.NotFoundException("Konversation nicht gefunden: " + id);
        }
        return k;
    }

    private static Konversation mussTeilnehmerPerson(Long konversationId, Long personId) {
        Konversation k = mussKonversation(konversationId);
        if (Teilnehmer.count("konversation.id = ?1 and personId = ?2", konversationId, personId) == 0) {
            throw new ForbiddenException("Diese Konversation gehört nicht zu Ihrem Konto.");
        }
        return k;
    }

    /**
     * Minimaler XSS-Schutz für gespeichertes Rich-Text-HTML (Showcase): entfernt {@code <script>}/
     * {@code <style>}/{@code <iframe>}-Blöcke, {@code javascript:}-URLs und Inline-Event-Handler.
     * Produktiv gehört hier ein erprobter Sanitizer (OWASP Java HTML Sanitizer) hin.
     */
    static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return html
                .replaceAll("(?is)<\\s*(script|style|iframe)[^>]*>.*?<\\s*/\\s*\\1\\s*>", "")
                .replaceAll("(?is)<\\s*(script|style|iframe)[^>]*/?>", "")
                .replaceAll("(?i)\\son\\w+\\s*=\\s*\"[^\"]*\"", "")
                .replaceAll("(?i)\\son\\w+\\s*=\\s*'[^']*'", "")
                .replaceAll("(?i)javascript:", "");
    }
}
