package de.netzfactor.ebz.controlling.integration.enrichment;

import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import de.netzfactor.ebz.controlling.integration.model.DealClassification;
import de.netzfactor.ebz.controlling.integration.model.DeliveryType;
import de.netzfactor.ebz.controlling.integration.model.HubSpotDeal;
import de.netzfactor.ebz.controlling.integration.model.SeminarKategorie;

/**
 * Reichert einen {@link de.netzfactor.ebz.controlling.integration.model.RawDeal} um
 * Klassifikationsfelder an.
 * <p>
 * Primär via {@link DealClassifier} (LangChain4j/OpenAI). Ist das LLM deaktiviert oder nicht
 * erreichbar (oder liefert ungültige Werte), greift ein <b>robuster regelbasierter Fallback</b>
 * (enrichedBy=FALLBACK) — der Showcase bleibt ohne LLM lauffähig. Setzt stets Provenance.
 */
@ApplicationScoped
public class DealEnricher {

    private static final Logger LOG = Logger.getLogger(DealEnricher.class);

    /** Versionsstempel für Provenance/Reproduzierbarkeit (L10). Bei Prompt-Änderung erhöhen. */
    public static final String PROMPT_VERSION = "v1";

    private static final Pattern LEGAL_FORM = Pattern.compile(
            "\\b(gmbh|mbh|mbb|ag|kg|ohg|gbr|ug|se|sa|e\\.?\\s?v\\.?|co\\.?|&|und partner|partner|aktiengesellschaft|rechtsanw[aä]lte)\\b\\.?",
            Pattern.CASE_INSENSITIVE);

    @Inject
    DealClassifier classifier;

    @ConfigProperty(name = "ingestion.llm.enabled", defaultValue = "true")
    boolean llmEnabled;

    @ConfigProperty(name = "ingestion.review-threshold", defaultValue = "0.6")
    double reviewThreshold;

    @ConfigProperty(name = "quarkus.langchain4j.openai.chat-model.model-name", defaultValue = "gpt-4o-mini")
    String modelId;

    /** Angereicherte Klassifikation + Provenance, fertig zum Persistieren. */
    public record Enrichment(
            String normalizedCompany,
            SeminarKategorie kategorie,
            DeliveryType deliveryType,
            double konfidenz,
            HubSpotDeal.EnrichedBy enrichedBy,
            String modelId,
            boolean reviewRequired) {
    }

    public Enrichment enrich(de.netzfactor.ebz.controlling.integration.model.RawDeal deal) {
        if (llmEnabled) {
            try {
                DealClassification c = classifier.classify(
                        nz(deal.dealName()), nz(deal.company()), nz(deal.description()));
                Enrichment e = validate(deal, c);
                if (e != null) {
                    return e;
                }
                LOG.warnf("LLM-Antwort für Deal %s ungültig → Fallback", deal.id());
            } catch (RuntimeException ex) {
                LOG.warnf("LLM nicht erreichbar/fehlerhaft für Deal %s (%s) → Fallback",
                        deal.id(), ex.getClass().getSimpleName());
            }
        }
        return fallback(deal);
    }

    /** L9: Enum-Allowlist + Konfidenz-Range prüfen; halluzinierte/leere Werte verwerfen. */
    private Enrichment validate(de.netzfactor.ebz.controlling.integration.model.RawDeal deal, DealClassification c) {
        if (c == null || c.seminarKategorie() == null || c.deliveryType() == null) {
            return null;
        }
        if (c.seminarKategorie() == SeminarKategorie.UNBEKANNT
                || c.deliveryType() == DeliveryType.UNBEKANNT) {
            return null;
        }
        double konf = Math.max(0.0, Math.min(1.0, c.konfidenz()));
        String norm = (c.normalisierterFirmenname() == null || c.normalisierterFirmenname().isBlank())
                ? normalizeCompany(deal.company())
                : c.normalisierterFirmenname().trim();
        return new Enrichment(norm, c.seminarKategorie(), c.deliveryType(), konf,
                HubSpotDeal.EnrichedBy.LLM, modelId, konf < reviewThreshold);
    }

    /** Regelbasiert: Pipeline → deliveryType, Keyword-Match → Kategorie, Regex → Firmenname. */
    private Enrichment fallback(de.netzfactor.ebz.controlling.integration.model.RawDeal deal) {
        DeliveryType dt = "inhouse".equalsIgnoreCase(deal.pipeline())
                || contains(deal.dealName(), "inhouse")
                ? DeliveryType.INHOUSE : DeliveryType.OFFEN;
        SeminarKategorie kat = keywordCategory(
                (nz(deal.dealName()) + " " + nz(deal.description())).toLowerCase(Locale.GERMAN));
        double konf = 0.4; // bewusst niedrig → reviewRequired
        return new Enrichment(normalizeCompany(deal.company()), kat, dt, konf,
                HubSpotDeal.EnrichedBy.FALLBACK, "rule-based", konf < reviewThreshold);
    }

    private SeminarKategorie keywordCategory(String text) {
        if (containsAny(text, "führung", "leadership", "mitarbeiterführung", "management")) return SeminarKategorie.FUEHRUNG;
        if (containsAny(text, "compliance", "dsgvo", "datenschutz", "geldwäsche", "audit")) return SeminarKategorie.COMPLIANCE;
        if (containsAny(text, "it-", "security", "cyber", "phishing", "digital", "software")) return SeminarKategorie.IT_DIGITAL;
        if (containsAny(text, "sprach", "englisch", "business english", "language")) return SeminarKategorie.SPRACHEN;
        if (containsAny(text, "fach", "projektmanagement", "buchhaltung", "vertrieb", "technik")) return SeminarKategorie.FACHKOMPETENZ;
        return SeminarKategorie.SONSTIGE;
    }

    static String normalizeCompany(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = LEGAL_FORM.matcher(raw).replaceAll(" ");
        // Mehrfach-Whitespace kollabieren + verbliebene Rand-Satzzeichen/& entfernen
        return s.replaceAll("\\s+", " ")
                .replaceAll("^[\\s&.,/-]+|[\\s&.,/-]+$", "")
                .trim();
    }

    private static boolean contains(String hay, String needle) {
        return hay != null && hay.toLowerCase(Locale.GERMAN).contains(needle);
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n)) return true;
        }
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
