package de.netzfactor.ebz.controlling.integration.kommunikation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.jupiter.api.Test;

/**
 * <b>Fitness-Functions</b> für die Modulgrenzen des Kommunikations-Moduls (Plan K0): heute gibt es im
 * Bestand kein Modulith-/ArchUnit-Enforcement — die Grenzen waren reine Konvention. Diese Regeln sichern
 * sie ab und laufen als normaler Test in {@code mvn test}.
 * <ul>
 *   <li><b>ACL/Decoupling:</b> {@code kommunikation} koppelt NICHT an die volatilen Domänenmodule
 *       {@code rechnung}/{@code bildung} (Anbindung nur über serialisierbare Events + IDs) — Gegenbeispiel
 *       wäre die hart an {@code Anmeldung} gebundene {@code OutboxAuftrag}.</li>
 *   <li><b>Split-ready Kern:</b> der Kern (model/event/service/web/spi) kennt den Party-Kern gar nicht —
 *       Personen sind nur {@code Long}-IDs; einzig der {@code adapter} (ACL) berührt {@code party}. So wird
 *       der Communication-Core mit eigenem Schema/eigener DB später ein Adapter-Austausch, kein Rewrite.</li>
 *   <li><b>Ports&Adapter:</b> die SPI/Ports hängen an nichts Internem (reine Verträge); Adapter kennen
 *       weder die Web- noch die Service-Schicht; die Web-Schicht ruft nur die Fassade, nie Adapter direkt.</li>
 * </ul>
 */
class KommunikationArchTest {

    private static final String BASIS = "de.netzfactor.ebz.controlling.integration";
    private static final String KOMM = BASIS + ".kommunikation";
    /** Der reine Kern (ohne adapter/ACL und ohne den Demo-Seeder im Wurzelpaket). */
    private static final String[] KERN = {
            KOMM + ".model..", KOMM + ".event..", KOMM + ".service..", KOMM + ".web..", KOMM + ".spi.."
    };

    private final JavaClasses klassen = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(KOMM);

    @Test
    void koppeltNichtAnVolatileDomaenenmodule() {
        ArchRule regel = noClasses()
                .that().resideInAPackage(KOMM + "..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(BASIS + ".rechnung..", BASIS + ".bildung..")
                .because("kommunikation bindet rechnung/bildung nur über serialisierbare Events + IDs an (ACL), "
                        + "nicht über Compile-Kopplung (Gegenbeispiel: Anmeldung-gebundene OutboxAuftrag)");
        regel.check(klassen);
    }

    @Test
    void kernKenntDenPartyKernNicht() {
        ArchRule regel = noClasses()
                .that().resideInAnyPackage(KERN)
                .should().dependOnClassesThat()
                .resideInAPackage(BASIS + ".party..")
                .because("der split-ready Kern kennt Personen nur als Long-IDs; nur der adapter (ACL) berührt party");
        regel.check(klassen);
    }

    @Test
    void portsBleibenReineVertraege() {
        ArchRule regel = noClasses()
                .that().resideInAPackage(KOMM + ".spi..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(KOMM + ".service..", KOMM + ".adapter..", KOMM + ".web..")
                .because("die Ports/SPI sind reine Verträge ohne Abhängigkeit auf Implementierung/Web");
        regel.check(klassen);
    }

    @Test
    void adapterKennenWederWebNochService() {
        ArchRule regel = noClasses()
                .that().resideInAPackage(KOMM + ".adapter..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(KOMM + ".web..", KOMM + ".service..")
                .because("Adapter sind Blätter am Port — sie kennen weder die Web- noch die Orchestrierungs-Schicht");
        regel.check(klassen);
    }

    @Test
    void webRuftNurDieFassadeNichtAdapter() {
        ArchRule regel = noClasses()
                .that().resideInAPackage(KOMM + ".web..")
                .should().dependOnClassesThat()
                .resideInAPackage(KOMM + ".adapter..")
                .because("die Web-Schicht ruft die veröffentlichte Fassade (service), nie Kanal-Adapter direkt");
        regel.check(klassen);
    }
}
