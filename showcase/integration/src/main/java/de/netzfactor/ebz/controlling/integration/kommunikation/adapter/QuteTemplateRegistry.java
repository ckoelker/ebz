package de.netzfactor.ebz.controlling.integration.kommunikation.adapter;

import java.util.EnumMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;

import de.netzfactor.ebz.controlling.integration.kommunikation.event.EreignisTyp;
import de.netzfactor.ebz.controlling.integration.kommunikation.spi.Ports.TemplatePort;

/**
 * {@link TemplatePort}-Adapter auf Basis der Quarkus-{@link Engine Qute-Engine}: eine zentrale, je
 * {@link EreignisTyp} versionierte <b>Template-Registry</b> (Betreff + Body) statt hartkodierter Strings in
 * den Versand-Adaptern. In K1 als In-Code-Templates (ein Default + typ-spezifische Texte); später leicht
 * auf Datei-/Cockpit-gepflegte, versionierte Vorlagen mit Vorschau erweiterbar — der Port bleibt gleich.
 * Verfügbare Variablen: {@code anrede}, {@code betreff} (vom Auslöser gesetzt).
 */
@ApplicationScoped
public class QuteTemplateRegistry implements TemplatePort {

    private static final String FUSS = """

            Diese Nachricht finden Sie auch jederzeit in Ihrem EBZ-Portal unter „Meine Aktivitäten".

            Viele Grüße
            Ihr EBZ-Team""";

    /** Body-Vorlage, falls für einen Typ keine spezifische hinterlegt ist. */
    private static final String DEFAULT_BODY = "{anrede},\n\n{betreff}\n" + FUSS;

    @Inject
    Engine engine;

    private final Map<EreignisTyp, Template> bodyTemplates = new EnumMap<>(EreignisTyp.class);
    private Template defaultBody;

    @PostConstruct
    void init() {
        defaultBody = engine.parse(DEFAULT_BODY);
        // Rechnungsversand (Bestands-Mail migriert): ZUGFeRD im Anhang (über den AnhangPort), Zahlungsziel.
        // Elvis-Defaults ({x ?: ''}) → robust gegen fehlende Variablen (Qute strict rendering).
        bodyTemplates.put(EreignisTyp.RECHNUNG_VERSANDT, engine.parse(
                "Guten Tag,\n\nanbei erhalten Sie Ihre Rechnung {nummer ?: ''} (Kunden-Nr. {debitorNr ?: ''}) "
                        + "als ZUGFeRD-E-Rechnung im Anhang. Bitte begleichen Sie den Betrag innerhalb von "
                        + "{zahlungszielTage ?: ''} Tagen unter Angabe der Belegnummer." + FUSS));
        bodyTemplates.put(EreignisTyp.AZUBI_VERTRAG_BESTAETIGT, engine.parse(
                "{anrede},\n\n{betreff} Bitte bestätigen Sie die Kenntnisnahme im Portal." + FUSS));
        bodyTemplates.put(EreignisTyp.EINSCHREIBUNG_AKTIV, engine.parse(
                "{anrede},\n\n{betreff} Sie können sofort mit dem Lernen beginnen." + FUSS));
        // Bestands-Mail-Migration: Anmeldebestätigung (Firma/Besteller bzw. Azubi) + Portal-Einladung.
        bodyTemplates.put(EreignisTyp.ANMELDUNG_BESTAETIGT, engine.parse(
                "Guten Tag,\n\ndie Anmeldung von {teilnehmerName ?: ''} zur Berufsschule "
                        + "({schuljahrHalbjahr ?: ''}) ist vom EBZ bestätigt. Bitte bestätigen Sie abschließend "
                        + "den Ausbildungsvertrag im Portal, damit die Abrechnung erfolgen kann." + FUSS));
        bodyTemplates.put(EreignisTyp.ANMELDUNG_BESTAETIGT_AZUBI, engine.parse(
                "Hallo {teilnehmerName ?: ''},\n\ndeine Anmeldung zur Berufsschule ({schuljahrHalbjahr ?: ''}) "
                        + "wurde vom EBZ geprüft und bestätigt. Den Zugang zum Portal hast du per separater "
                        + "Einladung erhalten." + FUSS));
        bodyTemplates.put(EreignisTyp.EINLADUNG, engine.parse(
                "Hallo {anzeigeName ?: ''},\n\nIhr Ausbildungsbetrieb ist im EBZ-Ausbildungsportal "
                        + "freigeschaltet. Bitte melden Sie sich mit dieser E-Mail-Adresse an und vergeben Sie "
                        + "beim ersten Login Ihr Passwort:\n\n{portalUrl ?: ''}\n\nAnschließend können Sie Ihre "
                        + "Auszubildenden zur Berufsschule anmelden." + FUSS));
    }

    @Override
    public Gerendert render(EreignisTyp typ, Map<String, Object> variablen) {
        Object betreff = variablen.get("betreff");
        Template body = bodyTemplates.getOrDefault(typ, defaultBody);
        return new Gerendert(betreff == null ? typ.name() : betreff.toString(), fuelle(body, variablen).render());
    }

    private static TemplateInstance fuelle(Template t, Map<String, Object> variablen) {
        TemplateInstance i = t.instance();
        variablen.forEach(i::data);
        return i;
    }
}
