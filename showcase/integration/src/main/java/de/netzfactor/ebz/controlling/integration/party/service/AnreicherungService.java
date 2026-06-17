package de.netzfactor.ebz.controlling.integration.party.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.party.model.OrgVorschlag;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * Firmen-Online-Anreicherung (Plan A15): orchestriert „Daten online ziehen" und streamt den Fortschritt
 * Schritt für Schritt als JSON über einen WebSocket. Ablauf: VIES-Prüfung → Impressum-/Website-Abruf →
 * <b>LLM-Extraktion</b> ({@link AnreicherungsExtraktor}) → ein {@link OrgVorschlag} zur Übernahme.
 *
 * <p><b>Showcase-Grenzen:</b> VIES und Impressum-Fetch sind hier <i>gemockt</i> (deterministisch,
 * reproduzierbar). In Produktion sind das echte Calls — dann gilt für den Website-Fetch zwingend
 * <b>SSRF-Schutz</b> (Allow-Liste/Timeouts/keine internen Redirects, Plan-Fallstrick 6). Die
 * LLM-Extraktion ist real (quarkus-langchain4j, Phoenix-Tracing); ohne KI/Key greift ein
 * deterministischer Heuristik-Fallback, damit der Flow auch offline/Test funktioniert.
 */
@ApplicationScoped
public class AnreicherungService {

    private static final Logger LOG = Logger.getLogger(AnreicherungService.class);

    @Inject
    AnreicherungsExtraktor extraktor;

    @ConfigProperty(name = "crm.anreicherung.ki.enabled", defaultValue = "true")
    boolean kiEnabled;

    /** Eingang des „Daten online ziehen": bekannte Anhaltspunkte aus der Anlage-Maske. */
    public record AnreicherungsAnfrage(String name, String website, String ustId) {
    }

    /**
     * Ein Stream-Element. {@code typ}: {@code schritt} (Fortschritt), {@code vorschlag} (Endergebnis)
     * oder {@code fehler}. Bei {@code schritt} sind {@code schritt}/{@code status}/{@code detail} gesetzt,
     * bei {@code vorschlag} der {@code vorschlag}.
     */
    public record AnreicherungsEvent(String typ, String schritt, String status, String detail,
            OrgVorschlag vorschlag) {

        static AnreicherungsEvent schritt(String schritt, String status, String detail) {
            return new AnreicherungsEvent("schritt", schritt, status, detail, null);
        }

        static AnreicherungsEvent vorschlag(OrgVorschlag v) {
            return new AnreicherungsEvent("vorschlag", null, null, null, v);
        }

        static AnreicherungsEvent fehler(String detail) {
            return new AnreicherungsEvent("fehler", null, "fehler", detail, null);
        }
    }

    /** Streamt die Anreicherung; blockierende Schritte laufen auf dem Worker-Pool (Multi = non-blocking). */
    public Multi<AnreicherungsEvent> anreichern(AnreicherungsAnfrage a) {
        return Multi.createFrom().<AnreicherungsEvent>emitter(em -> {
            try {
                String name = a == null ? null : leer(a.name());
                if (name == null) {
                    em.emit(AnreicherungsEvent.fehler("Bitte zuerst einen Firmennamen eingeben."));
                    em.complete();
                    return;
                }

                // 1) VIES-Prüfung der USt-IdNr. (gemockt)
                em.emit(AnreicherungsEvent.schritt("VIES", "laeuft", "Prüfe USt-IdNr. gegen VIES …"));
                pause(350);
                String ustId = leer(a.ustId());
                if (ustId != null && ustId.matches("(?i)DE\\d{9}")) {
                    em.emit(AnreicherungsEvent.schritt("VIES", "ok",
                            "USt-IdNr. " + ustId.toUpperCase() + " ist gültig (VIES-bestätigt)."));
                } else {
                    em.emit(AnreicherungsEvent.schritt("VIES", "uebersprungen",
                            "Keine gültige USt-IdNr. angegeben — Schritt übersprungen."));
                }

                // 2) Impressum-/Website-Abruf (gemockt; Prod: SSRF-geschützter Fetch)
                String website = leer(a.website());
                em.emit(AnreicherungsEvent.schritt("IMPRESSUM", "laeuft",
                        "Lade Impressum" + (website != null ? " von " + website : "") + " …"));
                pause(450);
                String impressum = mockImpressum(name, website, ustId);
                em.emit(AnreicherungsEvent.schritt("IMPRESSUM", "ok", "Impressum geladen, extrahiere Felder …"));

                // 3) LLM-Extraktion (real; Fallback heuristisch)
                em.emit(AnreicherungsEvent.schritt("KI-EXTRAKTION", "laeuft", "KI liest die Stammdaten aus …"));
                OrgVorschlag vorschlag = extrahiere(impressum, name, website, ustId);
                em.emit(AnreicherungsEvent.schritt("KI-EXTRAKTION",
                        kiEnabled ? "ok" : "fallback",
                        kiEnabled ? "Stammdaten extrahiert." : "Heuristik-Extraktion (KI deaktiviert)."));

                em.emit(AnreicherungsEvent.vorschlag(vorschlag));
                em.complete();
            } catch (RuntimeException ex) {
                LOG.warnf("Anreicherung fehlgeschlagen: %s", ex.toString());
                em.emit(AnreicherungsEvent.fehler("Anreicherung fehlgeschlagen: " + ex.getMessage()));
                em.complete();
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /** LLM-Extraktion mit robustem Fallback (KI aus/nicht erreichbar → deterministische Heuristik). */
    private OrgVorschlag extrahiere(String impressum, String name, String website, String ustId) {
        if (kiEnabled) {
            try {
                OrgVorschlag v = extraktor.extrahiere(impressum);
                if (v != null && v.name() != null) {
                    return v;
                }
                LOG.warn("KI-Anreicherung lieferte Unbrauchbares → Heuristik-Fallback");
            } catch (RuntimeException ex) {
                LOG.warnf("KI-Anreicherung nicht erreichbar (%s) → Heuristik-Fallback",
                        ex.getClass().getSimpleName());
            }
        }
        return heuristik(impressum, name, website, ustId);
    }

    // ───────────────────────── Mock-Quellen (Showcase) ─────────────────────────

    /** Erzeugt einen realistischen Impressum-Textblock (Immobilien-Kontext) aus den Anhaltspunkten. */
    private static String mockImpressum(String name, String website, String ustId) {
        String ust = ustId != null && ustId.matches("(?i)DE\\d{9}") ? ustId.toUpperCase() : "DE811234567";
        String web = website != null ? website : "www." + name.toLowerCase().replaceAll("[^a-z0-9]", "") + ".de";
        String rechtsform = rechtsformAus(name);
        return """
                Impressum

                %s
                Anbieter und inhaltlich Verantwortlicher gemäß § 5 TMG / § 18 MStV.

                Anschrift:
                Königswall 21
                44137 Dortmund

                Kontakt:
                Telefon: +49 231 555-0
                E-Mail: info@%s

                Vertretungsberechtigt: die Geschäftsführung.
                Registergericht: Amtsgericht Dortmund, HRB 28140
                Umsatzsteuer-Identifikationsnummer: %s
                Website: %s

                Tätigkeitsschwerpunkt: Verwaltung und Vermietung eigener und fremder Wohnimmobilien
                sowie Bauträgertätigkeit in der Wohnungswirtschaft.
                Rechtsform: %s
                """.formatted(name, web.replaceFirst("^https?://", "").replaceFirst("^www\\.", ""),
                ust, web, rechtsform);
    }

    // ───────────────────────── Heuristik-Fallback (deterministisch) ─────────────────────────

    private static final Pattern P_UST = Pattern.compile("(?i)\\b(DE\\d{9})\\b");
    private static final Pattern P_PLZ_ORT = Pattern.compile("\\b(\\d{5})\\s+([A-ZÄÖÜ][\\wäöüß.\\- ]+)");
    private static final Pattern P_STRASSE = Pattern.compile("(?m)^([A-ZÄÖÜ][\\wäöüß.\\- ]+\\s+\\d+[a-z]?)\\s*$");
    private static final Pattern P_WEB = Pattern.compile("(?i)\\b((?:https?://)?(?:www\\.)?[a-z0-9.\\-]+\\.[a-z]{2,})\\b");

    private static OrgVorschlag heuristik(String text, String name, String website, String ustId) {
        String ust = ustId != null ? ustId.toUpperCase() : ersteGruppe(P_UST, text, 1);
        String web = website != null ? website : ersteGruppe(P_WEB, text, 1);
        Matcher po = P_PLZ_ORT.matcher(text);
        String plz = null;
        String ort = null;
        if (po.find()) {
            plz = po.group(1);
            ort = po.group(2).trim();
        }
        String strasse = ersteGruppe(P_STRASSE, text, 1);
        String branche = text.contains("Wohnimmobilien") || text.contains("Wohnungswirtschaft")
                ? "Wohnungs-/Immobilienwirtschaft (Verwaltung/Vermietung)" : null;
        return new OrgVorschlag(name, rechtsformAus(name), ust, web, strasse, plz, ort, branche);
    }

    private static String rechtsformAus(String name) {
        String n = name.toLowerCase();
        if (n.contains("gmbh & co. kg") || n.contains("gmbh & co kg")) return "GmbH & Co. KG";
        if (n.contains("gmbh")) return "GmbH";
        if (n.endsWith(" ag") || n.contains(" ag ")) return "AG";
        if (n.contains("eg") || n.contains("genossenschaft")) return "eG";
        if (n.contains("kg")) return "KG";
        if (n.contains("ohg")) return "OHG";
        if (n.contains("e.v") || n.contains("e. v")) return "e.V.";
        return null;
    }

    private static String ersteGruppe(Pattern p, String text, int gruppe) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(gruppe).trim() : null;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String leer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
