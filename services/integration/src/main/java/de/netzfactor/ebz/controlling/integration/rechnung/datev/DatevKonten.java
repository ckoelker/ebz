package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Steuerfall;

/**
 * DATEV-Kontierung + EXTF-Stammdaten (Präfix {@code datev}, per Env überschreibbar). <b>Platzhalter
 * für den Showcase</b> — die echten Erlöskonten/BU-Schlüssel je SKR03/04 legt der Steuerberater fest
 * (R0-Klärung B2: EBZ = gemeinnützig, teils §4-UStG-befreit). {@code modus} schaltet den aktiven
 * {@code DatevUebergabe}-Weg (EXTF-CSV-Brücke vs. DATEV-Cloud-Mock = B1 „DATEV Unternehmen online").
 */
@ConfigMapping(prefix = "datev")
public interface DatevKonten {

    /** Aktiver Übergabeweg: {@code extf} (CSV-Buchungsstapel) oder {@code cloud-mock} (Buchungsdatenservice). */
    @WithDefault("extf")
    String modus();

    @WithDefault("SKR03")
    String skr();

    /** Erlöskonto steuerfreie Bildungsleistung (§4 UStG); SKR03-Platzhalter. */
    @WithDefault("8120")
    String erloesBefreit();

    /** Erlöskonto 19 % USt; SKR03-Platzhalter (8400 = Erlöse 19 %). */
    @WithDefault("8400")
    String erloesStandard();

    /** Erlöskonto 7 % USt; SKR03-Platzhalter (8300 = Erlöse 7 %). */
    @WithDefault("8300")
    String erloesErmaessigt();

    /** BU-/Steuerschlüssel je Steuerfall (leer = ohne Steuer); Platzhalter. Optional, da steuerfrei = leer. */
    @WithDefault("")
    Optional<String> buBefreit();

    @WithDefault("3")
    String buStandard();

    @WithDefault("2")
    String buErmaessigt();

    /** EXTF-Kopf: DATEV-Beraternummer (Showcase-Platzhalter). */
    @WithDefault("1001")
    String beraternummer();

    /** EXTF-Kopf: Mandantennummer. */
    @WithDefault("1")
    String mandantennummer();

    /** EXTF-Kopf: Beginn des Wirtschaftsjahres (yyyyMMdd). */
    @WithDefault("20260101")
    String wirtschaftsjahrBeginn();

    /** EXTF-Kopf: Sachkontenlänge. */
    @WithDefault("4")
    int sachkontenLaenge();

    default String erloeskonto(Steuerfall fall) {
        return switch (fall) {
            case BEFREIT -> erloesBefreit();
            case STANDARD -> erloesStandard();
            case ERMAESSIGT -> erloesErmaessigt();
        };
    }

    default String buSchluessel(Steuerfall fall) {
        return switch (fall) {
            case BEFREIT -> buBefreit().orElse("");
            case STANDARD -> buStandard();
            case ERMAESSIGT -> buErmaessigt();
        };
    }
}
