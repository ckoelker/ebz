package de.netzfactor.ebz.controlling.integration.party.service;

import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import io.quarkus.runtime.StartupEvent;

import de.netzfactor.ebz.controlling.integration.party.model.Lookups;

/**
 * Befüllt die CRM-Lookup-Tabellen ({@link Lookups}) beim Start idempotent mit immobilienwirtschaftlich
 * realistischen Stammwerten — nur wenn die jeweilige Tabelle leer ist. Damit existieren die FK-Ziele
 * (Rolle, Branche, Land, Sprache …), bevor Person/Organisation/Mitgliedschaft sie referenzieren.
 * <p>
 * Bei den Rollen sind die früheren {@code Mitgliedschaft.Rolle}-Enum-Werte als {@code code} enthalten
 * ({@code AUSBILDER}, {@code AZUBI} …), damit der Umstieg Enum→Lookup verlustfrei mappt; ergänzt um die
 * im Plan (A4) genannten Immobilien-Funktionen (Geschäftsführung, WEG-/Mietverwalter, Objektmanager …).
 */
@ApplicationScoped
public class LookupSeeder {

    @Transactional
    void seed(@Observes StartupEvent ev) {
        seedRollen();
        seedVerbaende();
        seedUnternehmenstypen();
        seedSchwerpunkte();
        seedBeziehungstypen();
        seedBranchen();
        seedLaender();
        seedSprachen();
        seedLeadQuellen();
        seedAktivitaetstypen();
        seedKammern();
    }

    // ── Rollen (A4) — Legacy-Enum-Codes + Immobilien-Funktionen ──
    private void seedRollen() {
        if (Lookups.Rolle.count() > 0) return;
        int[] s = {0};
        Consumer<Object[]> add = a -> {
            Lookups.Rolle r = new Lookups.Rolle();
            r.code = (String) a[0];
            r.bezeichnung = (String) a[1];
            r.buchungDefault = (boolean) a[2];
            r.sortierung = s[0]++;
            r.persist();
        };
        add.accept(new Object[]{"GESCHAEFTSFUEHRUNG", "Geschäftsführung", true});
        add.accept(new Object[]{"VORSTAND", "Vorstand", true});
        add.accept(new Object[]{"PROKURIST", "Prokurist:in", true});
        add.accept(new Object[]{"AUFSICHTSRAT", "Aufsichtsrat", false});
        add.accept(new Object[]{"WEG_VERWALTER", "WEG-Verwalter:in", false});
        add.accept(new Object[]{"MIET_VERWALTER", "Mietverwalter:in", false});
        add.accept(new Object[]{"OBJEKTMANAGER", "Objektmanager:in", false});
        add.accept(new Object[]{"VERMIETUNG", "Vermietung", false});
        add.accept(new Object[]{"TECHNIK", "Technik / Instandhaltung", false});
        add.accept(new Object[]{"AUSBILDER", "Ausbilder:in", true});
        add.accept(new Object[]{"ANSPRECHPARTNER_STUDIUM", "Ansprechpartner:in Studium", true});
        add.accept(new Object[]{"SEMINAR_BUCHER", "Seminarbucher:in", true});
        add.accept(new Object[]{"AZUBI", "Auszubildende:r", false});
        add.accept(new Object[]{"STUDENT", "Student:in", false});
    }

    private void seedVerbaende() {
        if (Lookups.Verband.count() > 0) return;
        int[] s = {0};
        Consumer<String[]> add = a -> {
            Lookups.Verband v = new Lookups.Verband();
            v.code = a[0];
            v.kuerzel = a[1];
            v.bezeichnung = a[2];
            v.website = a[3];
            v.sortierung = s[0]++;
            v.persist();
        };
        add.accept(new String[]{"GDW", "GdW", "GdW Bundesverband deutscher Wohnungs- und Immobilienunternehmen", "https://www.gdw.de"});
        add.accept(new String[]{"VNW", "VNW", "Verband norddeutscher Wohnungsunternehmen", "https://www.vnw.de"});
        add.accept(new String[]{"VDW", "vdw", "Verband der Wohnungs- und Immobilienwirtschaft", "https://www.vdw-online.de"});
        add.accept(new String[]{"BFW", "BFW", "Bundesverband Freier Immobilien- und Wohnungsunternehmen", "https://www.bfw-bund.de"});
        add.accept(new String[]{"IVD", "IVD", "Immobilienverband Deutschland", "https://ivd.net"});
        add.accept(new String[]{"BVI", "BVI", "Bundesfachverband der Immobilienverwalter", "https://bvi-verwalter.de"});
        add.accept(new String[]{"DDIV", "DDIV", "Verband der Immobilienverwalter Deutschland", "https://ddiv.de"});
        add.accept(new String[]{"HAUS_GRUND", "Haus & Grund", "Haus & Grund Deutschland", "https://www.hausundgrund.de"});
    }

    private void seedUnternehmenstypen() {
        if (Lookups.Unternehmenstyp.count() > 0) return;
        seedEinfach(new String[][]{
                {"WOHNUNGSUNTERNEHMEN", "Wohnungsunternehmen"},
                {"GENOSSENSCHAFT", "Wohnungsgenossenschaft"},
                {"MAKLER", "Immobilienmakler"},
                {"VERWALTER", "Hausverwaltung / Verwalter"},
                {"BAUTRAEGER", "Bauträger"},
                {"PROJEKTENTWICKLER", "Projektentwickler"},
                {"SACHVERSTAENDIGER", "Sachverständiger / Gutachter"},
                {"KOMMUNAL", "Kommunales Unternehmen"},
        }, () -> new Lookups.Unternehmenstyp());
    }

    private void seedSchwerpunkte() {
        if (Lookups.Taetigkeitsschwerpunkt.count() > 0) return;
        seedEinfach(new String[][]{
                {"BESTANDSHALTUNG", "Bestandshaltung"},
                {"VERMIETUNG", "Vermietung"},
                {"WEG_VERWALTUNG", "WEG-Verwaltung"},
                {"MIET_VERWALTUNG", "Mietverwaltung"},
                {"VERKAUF", "An- und Verkauf"},
                {"BEWERTUNG", "Bewertung / Gutachten"},
                {"NEUBAU", "Neubau / Entwicklung"},
                {"FACILITY", "Facility-Management"},
        }, () -> new Lookups.Taetigkeitsschwerpunkt());
    }

    private void seedBeziehungstypen() {
        if (Lookups.Beziehungstyp.count() > 0) return;
        int[] s = {0};
        Consumer<Object[]> add = a -> {
            Lookups.Beziehungstyp b = new Lookups.Beziehungstyp();
            b.code = (String) a[0];
            b.bezeichnung = (String) a[1];
            b.sorgerecht = (boolean) a[2];
            b.sortierung = s[0]++;
            b.persist();
        };
        add.accept(new Object[]{"ERZIEHUNGSBERECHTIGT", "Erziehungsberechtigt", true});
        add.accept(new Object[]{"NOTFALLKONTAKT", "Notfallkontakt", false});
        add.accept(new Object[]{"EHEPARTNER", "Ehe-/Lebenspartner:in", false});
        add.accept(new Object[]{"KOLLEGE", "Kolleg:in", false});
    }

    private void seedBranchen() {
        if (Lookups.Branche.count() > 0) return;
        int[] s = {0};
        Consumer<String[]> add = a -> {
            Lookups.Branche b = new Lookups.Branche();
            b.code = a[0];
            b.wzCode = a[1];
            b.bezeichnung = a[2];
            b.sortierung = s[0]++;
            b.persist();
        };
        add.accept(new String[]{"VERMIETUNG_WOHNEN", "68.20.1", "Vermietung/Verpachtung eigener Wohngrundstücke"});
        add.accept(new String[]{"VERMIETUNG_GEWERBE", "68.20.2", "Vermietung/Verpachtung eigener Nichtwohngrundstücke"});
        add.accept(new String[]{"MAKLER", "68.31", "Vermittlung von Grundstücken/Gebäuden/Wohnungen"});
        add.accept(new String[]{"HAUSVERWALTUNG", "68.32", "Verwaltung von Grundstücken/Gebäuden/Wohnungen"});
        add.accept(new String[]{"BAUTRAEGER", "41.10", "Erschließung von Grundstücken; Bauträger"});
        add.accept(new String[]{"HOCHBAU", "41.20", "Bau von Gebäuden"});
        add.accept(new String[]{"INGENIEURBUERO", "71.12", "Ingenieurbüros / technische Beratung"});
        add.accept(new String[]{"BILDUNG", "85.59", "Sonstiger Unterricht (Weiterbildung)"});
    }

    private void seedLaender() {
        if (Lookups.Land.count() > 0) return;
        int[] s = {0};
        Consumer<String[]> add = a -> {
            Lookups.Land l = new Lookups.Land();
            l.code = a[0];
            l.iso3 = a[1];
            l.bezeichnung = a[2];
            l.plzRegex = a[3];
            l.sortierung = s[0]++;
            l.persist();
        };
        // DACH zuerst (UX-Vorgabe), strenge PLZ-Regeln; danach gängige Nachbarn.
        add.accept(new String[]{"DE", "DEU", "Deutschland", "^\\d{5}$"});
        add.accept(new String[]{"AT", "AUT", "Österreich", "^\\d{4}$"});
        add.accept(new String[]{"CH", "CHE", "Schweiz", "^\\d{4}$"});
        add.accept(new String[]{"LU", "LUX", "Luxemburg", "^\\d{4}$"});
        add.accept(new String[]{"NL", "NLD", "Niederlande", "^\\d{4}\\s?[A-Za-z]{2}$"});
        add.accept(new String[]{"FR", "FRA", "Frankreich", "^\\d{5}$"});
        add.accept(new String[]{"BE", "BEL", "Belgien", "^\\d{4}$"});
        add.accept(new String[]{"IT", "ITA", "Italien", "^\\d{5}$"});
        add.accept(new String[]{"PL", "POL", "Polen", "^\\d{2}-\\d{3}$"});
    }

    private void seedSprachen() {
        if (Lookups.Sprache.count() > 0) return;
        seedEinfach(new String[][]{
                {"de", "Deutsch"},
                {"en", "Englisch"},
                {"fr", "Französisch"},
                {"it", "Italienisch"},
                {"pl", "Polnisch"},
                {"tr", "Türkisch"},
        }, () -> new Lookups.Sprache());
    }

    private void seedLeadQuellen() {
        if (Lookups.LeadQuelle.count() > 0) return;
        seedEinfach(new String[][]{
                {"WEBSITE", "Website / Kontaktformular"},
                {"TELEFON", "Telefonische Anfrage"},
                {"EMPFEHLUNG", "Empfehlung"},
                {"MESSE", "Messe / Veranstaltung"},
                {"NEWSLETTER", "Newsletter"},
                {"BESTANDSKUNDE", "Bestandskunde"},
                {"VERBAND", "Verband / Partner"},
        }, () -> new Lookups.LeadQuelle());
    }

    private void seedAktivitaetstypen() {
        if (Lookups.Aktivitaetstyp.count() > 0) return;
        seedEinfach(new String[][]{
                {"TELEFONAT", "Telefonat"},
                {"EMAIL", "E-Mail"},
                {"TERMIN", "Termin / Besuch"},
                {"NOTIZ", "Notiz"},
                {"BRIEF", "Brief / Postversand"},
        }, () -> new Lookups.Aktivitaetstyp());
    }

    private void seedKammern() {
        if (Lookups.IhkKammer.count() > 0) return;
        seedEinfach(new String[][]{
                {"IHK_DORTMUND", "IHK zu Dortmund"},
                {"IHK_BOCHUM", "IHK Mittleres Ruhrgebiet (Bochum)"},
                {"IHK_ESSEN", "IHK für Essen, Mülheim, Oberhausen"},
                {"IHK_DUESSELDORF", "IHK Düsseldorf"},
                {"IHK_KOELN", "IHK Köln"},
        }, () -> new Lookups.IhkKammer());
    }

    // ── Helfer für reine Bezeichnungslisten (code, bezeichnung) ──
    private void seedEinfach(String[][] werte, java.util.function.Supplier<? extends Lookups.LookupBase> factory) {
        int sortierung = 0;
        for (String[] w : werte) {
            Lookups.LookupBase e = factory.get();
            e.code = w[0];
            e.bezeichnung = w[1];
            e.sortierung = sortierung++;
            e.persist();
        }
    }
}
