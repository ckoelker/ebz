package de.netzfactor.ebz.controlling.integration.rechnung.zugferd;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * EBZ-Verkäuferstammdaten aus der Konfiguration (Präfix {@code ebz.verkaeufer}, per Env
 * überschreibbar). Die Defaults sind Showcase-Platzhalter; produktiv mit echten Stamm-/Steuer-/
 * Bankdaten füllen. {@link #ustId()} ist im Default eine Platzhalter-USt-IdNr, damit das EN-16931-XML
 * eine Steuer-Identität trägt (auch bei Steuerbefreiung verlangt EN 16931 eine Verkäufer-Kennung).
 */
@ConfigMapping(prefix = "ebz.verkaeufer")
public interface EbzVerkaeufer {

    @WithDefault("EBZ Bildungs- und Forschungszentrum gGmbH")
    String name();

    @WithDefault("Musterstraße 1")
    String strasse();

    @WithDefault("45657")
    String plz();

    @WithDefault("Recklinghausen")
    String ort();

    @WithDefault("DE")
    String land();

    @WithDefault("DE123456789")
    String ustId();

    @WithDefault("rechnung@ebz.example")
    String email();

    @WithDefault("Rechnungswesen EBZ")
    String kontaktName();

    @WithDefault("+49 2361 0000-0")
    String kontaktTelefon();

    @WithDefault("Sparkasse Vest Recklinghausen")
    String bankName();

    @WithDefault("DE02426501500000123456")
    String iban();

    @WithDefault("WELADED1REK")
    String bic();

    /** Plain-Snapshot für die CDI-/Config-freien Erzeuger. */
    default Verkaeufer snapshot() {
        return new Verkaeufer(name(), strasse(), plz(), ort(), land(), ustId(), email(),
                kontaktName(), kontaktTelefon(), bankName(), iban(), bic());
    }
}
