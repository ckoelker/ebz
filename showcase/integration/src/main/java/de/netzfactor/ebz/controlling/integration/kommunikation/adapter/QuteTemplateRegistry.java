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
        bodyTemplates.put(EreignisTyp.RECHNUNG_VERSANDT, engine.parse(
                "{anrede},\n\n{betreff} Bitte begleichen Sie den Betrag fristgerecht unter Angabe der "
                        + "Belegnummer." + FUSS));
        bodyTemplates.put(EreignisTyp.AZUBI_VERTRAG_BESTAETIGT, engine.parse(
                "{anrede},\n\n{betreff} Bitte bestätigen Sie die Kenntnisnahme im Portal." + FUSS));
        bodyTemplates.put(EreignisTyp.EINSCHREIBUNG_AKTIV, engine.parse(
                "{anrede},\n\n{betreff} Sie können sofort mit dem Lernen beginnen." + FUSS));
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
