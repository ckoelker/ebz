package de.netzfactor.ebz.controlling.integration.shop;

import java.util.List;

/**
 * Die Beispieldaten des Shop-Showcases als editierbare Java-Records — der Feinjustier-Punkt
 * (Nutzerwunsch: Initialisierung in Java, damit nachjustierbar). {@link ShopInitService} mappt
 * sie idempotent auf die Vendure-Admin-API (generierter GraphQL-Client, §A0).
 * <p>
 * Stufe 1 (P1): je Veranstaltungsart 1–2 voll ausgestattete Beispiele — alle Detailblöcke gefüllt,
 * damit die spätere Storefront vollumfänglich demonstrierbar ist. Drei davon sind an echten
 * ebz-training.de-Angeboten verifiziert ({@code SVA015729}, {@code LEG001614}, {@code SVA015717}).
 * Stufe 2 (später): Komplettimport aus Bildungs-PDF + Sitemap (eigener Importer, gemeinsamer
 * Upsert-Kern).
 * <p>
 * Nummernpräfixe an ebz-training.de angelehnt: Seminar/Tagung {@code SVA}, Zertifikatslehrgang
 * {@code LEG}, Studium {@code STU}, E-Learning {@code WBT}, Arbeitskreis {@code AK},
 * Führungsforum {@code FF}, Kompaktstudiengang {@code KS}, Berufsschuljahr {@code BSJ}.
 * Vertragsangebote (Berufsschule/Studium) sind {@code bestellbar=false} → die Storefront zeigt
 * statt Warenkorb einen {@code anmeldungUrl}-Deeplink in den bestehenden Anmelde-/Vertragsprozess.
 * Beträge in Cent, netto (Vendure-Minor-Units); Bildung ist i. d. R. USt-befreit (§4 Nr. 21 UStG).
 */
public final class KatalogBeispiele {

    private KatalogBeispiele() {
    }

    /** Steuerklasse → wird in {@link ShopInitService} auf die passende Vendure-TaxCategory gemappt. */
    public enum Steuer {
        BEFREIT, STANDARD, ERMAESSIGT
    }

    public enum Format {
        PRAESENZ, ONLINE, HYBRID
    }

    /** Ansprechpartner:in (CRM-Sync, Schlüssel {@code crmPersonId}); {@code fotoDatei} = Platzhalter-Ressource. */
    public record Person(String crmPersonId, String name, String email, String telefon, String fotoDatei) {
    }

    /** Dozent:in/Referent:in (CRM-Sync, Schlüssel {@code crmPersonId}). */
    public record Referent(String crmPersonId, String name, String vita, String fotoDatei) {
    }

    /** Eine buchbare Durchführung (= Vendure-Variante): SKU=Terminnummer, Termin/Ort/Format/Preis. */
    public record Durchfuehrung(String sku, String terminIso, String ort, Format format, long preisCent) {
    }

    /** Produkt-Rezension (Stern-Rating 1–5). */
    public record Rezension(String autor, String text, int sterne, String datumIso) {
    }

    /** Rich-Text-Detailblöcke (HTML); leere Felder rendert die Storefront nicht. */
    public record Texte(String inhalte, String lernziele, String nutzen, String methodik,
                        String voraussetzungen, String foerderhinweis, String ablauf,
                        String leistungen, String faq) {
    }

    /** Ein vollständiges Beispielprodukt mit allen Detailblöcken, Facetten, Personen und Durchführungen. */
    public record Produkt(
            String angebotsnummer, String slug, String name, String kurzbeschreibung,
            String veranstaltungsart, String thema, String branche, String region,
            Steuer steuer, boolean bestellbar, String anmeldungUrl,
            Texte texte, String zielgruppe, String abschluss,
            Integer dauerUE, String studienform, Integer regelstudienzeitSemester, String akkreditierungBisIso,
            String ansprechpartnerCrmId, List<String> dozentCrmIds, List<String> verwandteSlugs,
            String bildDatei, List<String> downloadDateien,
            List<Durchfuehrung> durchfuehrungen, List<Rezension> rezensionen) {
    }

    // ───────────────────────── Personen (CRM-Sync) ─────────────────────────

    public static final List<Person> ANSPRECHPARTNER = List.of(
            new Person("crm-2001", "Sabine Brinkmann", "s.brinkmann@ebz-training.de", "+492311089800", "foto-brinkmann.png"),
            new Person("crm-2002", "Thomas Reuter", "t.reuter@ebz-training.de", "+492311089810", "foto-reuter.png"));

    public static final List<Referent> DOZENTEN = List.of(
            new Referent("crm-3001", "Prof. Dr. Andrea Lindner",
                    "Professorin für Immobilienwirtschaft an der EBZ Business School; Schwerpunkte Bewertung und "
                            + "Wohnungswirtschaft, langjährige Beratungs- und Gutachtertätigkeit.", "foto-lindner.png"),
            new Referent("crm-3002", "Dr. Markus Heller",
                    "Rechtsanwalt mit Schwerpunkt Miet- und WEG-Recht; Fachautor und gefragter Referent in der "
                            + "wohnungswirtschaftlichen Weiterbildung.", "foto-heller.png"),
            new Referent("crm-3003", "Claudia Voss",
                    "Diplom-Kauffrau, Trainerin für Führung und Kommunikation in der Immobilienbranche.",
                    "foto-voss.png"));

    // ───────────────────────── Beispielprodukte ─────────────────────────

    public static final List<Produkt> PRODUKTE = List.of(
            // 1) Online-Seminar — verifiziert (SVA015729, 495 €, schlank: 1 Durchführung, kein Referent/Bild)
            new Produkt("SVA015729", "online-seminar-betriebskostenabrechnung",
                    "Online-Seminar: Die Betriebskostenabrechnung rechtssicher erstellen",
                    "Kompakt und praxisnah: So erstellen Sie eine formell und materiell korrekte Betriebskostenabrechnung.",
                    "Seminar", "Wohnungswirtschaft", "Immobilienverwaltung", "Online",
                    Steuer.BEFREIT, true, null,
                    new Texte(
                            "<p>Sie lernen, eine Betriebskostenabrechnung Schritt für Schritt rechtssicher zu erstellen — "
                                    + "von der Kostenerfassung über die Umlageschlüssel bis zur fristgerechten Zustellung.</p>",
                            "<ul><li>Umlagefähige Kostenarten sicher abgrenzen</li><li>Korrekte Verteilerschlüssel anwenden</li>"
                                    + "<li>Formelle Anforderungen und Fristen einhalten</li></ul>",
                            "<p>Sie vermeiden typische Abrechnungsfehler und reduzieren das Risiko von Widersprüchen und Prozessen.</p>",
                            "<p>Live-Online-Vortrag mit Praxisbeispielen und Gelegenheit für Ihre Fragen.</p>",
                            "<p>Keine besonderen Voraussetzungen — Grundkenntnisse in der Verwaltung sind hilfreich.</p>",
                            "<p>Förderfähig über den Bildungsscheck NRW (sofern Voraussetzungen erfüllt).</p>",
                            "", "", "<p><strong>Erhalte ich eine Aufzeichnung?</strong> Nein, das Seminar findet live statt.</p>"),
                    "Verwalter:innen, Mitarbeitende der Wohnungswirtschaft", "Teilnahmebescheinigung",
                    null, null, null, null,
                    "crm-2001", List.of(), List.of("zertifikatslehrgang-gepruefter-immobilienverwalter"),
                    null, List.of(),
                    List.of(new Durchfuehrung("SVA015729", "2026-09-15T09:00:00.000Z", "Online", Format.ONLINE, 49500)),
                    List.of(new Rezension("M. Krause", "Sehr praxisnah, ich konnte alles direkt anwenden.", 5, "2026-03-02"),
                            new Rezension("T. Albers", "Gute Struktur, etwas knapp bei den Fragen.", 4, "2026-04-11"))),

            // 2) Zertifikatslehrgang — verifiziert (LEG001614, 2.800 €, 6 Module + Dozenten + Flyer-PDF, 1 buchbare Einheit)
            new Produkt("LEG001614", "zertifikatslehrgang-gepruefter-immobilienverwalter",
                    "Zertifikatslehrgang: Geprüfte:r Immobilienverwalter:in (EBZ)",
                    "Berufsbegleitender Lehrgang in sechs Modulen — der anerkannte Einstieg in die professionelle Immobilienverwaltung.",
                    "Zertifikatslehrgang", "Immobilienverwaltung", "Immobilienverwaltung", "Bochum",
                    Steuer.BEFREIT, true, null,
                    new Texte(
                            "<p>Der Lehrgang vermittelt das gesamte Handwerkszeug der modernen Immobilienverwaltung — "
                                    + "rechtlich, kaufmännisch und technisch.</p>",
                            "<ul><li>Miet- und WEG-Verwaltung souverän führen</li><li>Abrechnungen und Wirtschaftspläne erstellen</li>"
                                    + "<li>Eigentümerversammlungen rechtssicher vorbereiten</li></ul>",
                            "<p>Sie qualifizieren sich umfassend und schließen mit einem anerkannten EBZ-Zertifikat ab.</p>",
                            "<p>Präsenzunterricht mit Fallstudien, Gruppenarbeiten und Prüfungsvorbereitung.</p>",
                            "<p>Kaufmännische Grundkenntnisse; Berufserfahrung in der Immobilienwirtschaft empfohlen.</p>",
                            "<p>Förderfähig über Bildungsscheck NRW und Aufstiegs-BAföG (je nach Voraussetzungen).</p>",
                            "<h3>Module</h3><ol><li>Grundlagen & Recht</li><li>Mietverwaltung</li><li>WEG-Verwaltung</li>"
                                    + "<li>Kaufmännische Verwaltung</li><li>Technische Verwaltung</li><li>Kommunikation & Prüfung</li></ol>",
                            "", "<p><strong>Wie hoch ist der Präsenzanteil?</strong> Der Lehrgang findet überwiegend in Präsenz in Bochum statt.</p>"),
                    "Einsteiger:innen und Quereinsteiger:innen der Immobilienverwaltung", "EBZ-Zertifikat",
                    240, null, null, null,
                    "crm-2001", List.of("crm-3001", "crm-3002"), List.of("online-seminar-betriebskostenabrechnung"),
                    "bild-lehrgang.png", List.of("flyer-lehrgang-immobilienverwalter.pdf"),
                    List.of(new Durchfuehrung("LEG001614", "2026-10-05T08:30:00.000Z", "Bochum, EBZ-Campus", Format.PRAESENZ, 280000)),
                    List.of(new Rezension("S. Wagner", "Top Dozenten, sehr fundiert. Klare Empfehlung.", 5, "2025-12-10"),
                            new Rezension("P. Neumann", "Anspruchsvoll, aber es lohnt sich.", 5, "2026-02-20"))),

            // 3) Fachtagung — verifiziert (SVA015717, 990 €, mehrtägiges Programm + Speaker + Übernachtung im Preis)
            new Produkt("SVA015717", "fachtagung-wohnungswirtschaft-2026",
                    "Fachtagung Wohnungswirtschaft 2026",
                    "Zwei Tage Impulse, Praxis und Netzwerk — die Jahrestagung für Entscheider:innen der Wohnungswirtschaft.",
                    "Fachtagung", "Wohnungswirtschaft", "Wohnungswirtschaft", "Bochum",
                    Steuer.BEFREIT, true, null,
                    new Texte(
                            "<p>Die Fachtagung beleuchtet aktuelle Entwicklungen in Recht, Klimaschutz und Digitalisierung "
                                    + "der Wohnungswirtschaft.</p>",
                            "<ul><li>Aktuelle Rechtsprechung einordnen</li><li>Klimapfad und Förderkulisse verstehen</li>"
                                    + "<li>Digitalisierungsstrategien bewerten</li></ul>",
                            "<p>Sie nehmen konkrete Handlungsempfehlungen mit und erweitern Ihr Netzwerk.</p>",
                            "<p>Vorträge, Podiumsdiskussionen und vertiefende Foren über zwei Tage.</p>",
                            "<p>Richtet sich an Fach- und Führungskräfte; keine besonderen Voraussetzungen.</p>",
                            "",
                            "<h3>Programm</h3><p><strong>Tag 1:</strong> Recht & Politik, Abendveranstaltung.<br>"
                                    + "<strong>Tag 2:</strong> Klima & Digitalisierung, Praxisforen.</p>",
                            "<p>Im Preis enthalten: Tagungsunterlagen, Verpflegung an beiden Tagen, Abendveranstaltung "
                                    + "sowie eine Übernachtung im Tagungshotel.</p>",
                            "<p><strong>Ist die Übernachtung enthalten?</strong> Ja, eine Übernachtung inkl. Frühstück ist im Preis enthalten.</p>"),
                    "Fach- und Führungskräfte der Wohnungswirtschaft", "Teilnahmebescheinigung",
                    null, null, null, null,
                    "crm-2002", List.of("crm-3001", "crm-3003"), List.of(),
                    "bild-fachtagung.png", List.of("programm-fachtagung-2026.pdf"),
                    List.of(new Durchfuehrung("SVA015717", "2026-11-12T09:00:00.000Z", "Bochum, Tagungshotel", Format.PRAESENZ, 99000)),
                    List.of(new Rezension("Dr. K. Sommer", "Hochkarätige Speaker, exzellente Organisation.", 5, "2025-11-20"))),

            // 4) Arbeitskreis (AK) — regelmäßiger Erfahrungsaustausch, mehrere Termine
            new Produkt("AK000204", "arbeitskreis-vermietung-leerstand",
                    "Arbeitskreis: Vermietung & Leerstandsmanagement",
                    "Moderierter Erfahrungsaustausch für Vermietungsverantwortliche — viermal jährlich.",
                    "Arbeitskreis", "Vermarktung", "Wohnungswirtschaft", "Dortmund",
                    Steuer.BEFREIT, true, null,
                    new Texte(
                            "<p>Im Arbeitskreis diskutieren Sie aktuelle Herausforderungen der Vermietung und tauschen "
                                    + "Best Practices aus.</p>",
                            "<ul><li>Leerstand systematisch reduzieren</li><li>Vermarktungskanäle vergleichen</li></ul>",
                            "<p>Sie profitieren vom kollegialen Austausch und konkreten Lösungsansätzen.</p>",
                            "<p>Moderierte Diskussion mit kurzen Impulsvorträgen.</p>",
                            "<p>Für Verantwortliche aus der Vermietung.</p>", "", "", "", ""),
                    "Vermietungsverantwortliche", "Teilnahmebescheinigung",
                    null, null, null, null,
                    "crm-2002", List.of("crm-3003"), List.of(),
                    null, List.of(),
                    List.of(new Durchfuehrung("AK000204-Q3", "2026-09-22T14:00:00.000Z", "Dortmund", Format.PRAESENZ, 19000),
                            new Durchfuehrung("AK000204-Q4", "2026-12-01T14:00:00.000Z", "Dortmund", Format.PRAESENZ, 19000)),
                    List.of(new Rezension("J. Hoffmann", "Sehr wertvoller Austausch unter Kolleg:innen.", 5, "2026-03-25"))),

            // 5) Führungsforum (FF) — Premium-Format für Führungskräfte
            new Produkt("FF000087", "fuehrungsforum-immobilienmanagement",
                    "Führungsforum Immobilienmanagement",
                    "Exklusives Forum für Führungskräfte: Strategie, Führung und Transformation in der Immobilienwirtschaft.",
                    "Führungsforum", "Führung", "Immobilienwirtschaft", "Düsseldorf",
                    Steuer.BEFREIT, true, null,
                    new Texte(
                            "<p>Das Führungsforum verbindet strategische Impulse mit intensivem Peer-Austausch auf Leitungsebene.</p>",
                            "<ul><li>Führungsrolle im Wandel reflektieren</li><li>Transformationsprojekte steuern</li></ul>",
                            "<p>Sie schärfen Ihr Führungsprofil und erweitern Ihr strategisches Netzwerk.</p>",
                            "<p>Impulsvorträge, moderierte Workshops und exklusives Networking.</p>",
                            "<p>Für Führungskräfte mit Personalverantwortung.</p>", "", "",
                            "<p>Im Preis enthalten: Tagungsunterlagen und Verpflegung.</p>", ""),
                    "Führungskräfte der Immobilienwirtschaft", "Teilnahmebescheinigung",
                    null, null, null, null,
                    "crm-2002", List.of("crm-3003"), List.of(),
                    "bild-fuehrungsforum.png", List.of(),
                    List.of(new Durchfuehrung("FF000087", "2026-10-20T09:30:00.000Z", "Düsseldorf", Format.PRAESENZ, 145000)),
                    List.of()),

            // 6) E-Learning (WBT) — bestehender OpenOLAT-Pfad (P7), hier als Katalogprodukt mit ONLINE-Durchführung
            new Produkt("WBT000045", "e-learning-grundlagen-mietrecht",
                    "E-Learning: Grundlagen des Mietrechts",
                    "Web Based Training — jederzeit starten, im eigenen Tempo lernen, mit Abschlusstest.",
                    "E-Learning", "Recht", "Immobilienverwaltung", "Online",
                    Steuer.BEFREIT, true, null,
                    new Texte(
                            "<p>Dieses WBT führt Sie kompakt durch die Grundlagen des Mietrechts — mit interaktiven Übungen.</p>",
                            "<ul><li>Mietvertragstypen unterscheiden</li><li>Rechte und Pflichten kennen</li></ul>",
                            "<p>Sie erwerben sicheres Grundlagenwissen — flexibel und ortsunabhängig.</p>",
                            "<p>Selbstlernkurs (SCORM) in der EBZ-Lernplattform, ca. 4 Stunden.</p>",
                            "<p>Keine Voraussetzungen.</p>", "", "", "",
                            "<p><strong>Wie lange habe ich Zugriff?</strong> Der Zugang ist 12 Monate ab Buchung gültig.</p>"),
                    "Einsteiger:innen", "Abschlusstest / Zertifikat",
                    null, null, null, null,
                    "crm-2001", List.of(), List.of("online-seminar-betriebskostenabrechnung"),
                    null, List.of(),
                    List.of(new Durchfuehrung("WBT000045", "2026-07-01T00:00:00.000Z", "Online (jederzeit)", Format.ONLINE, 9900)),
                    List.of(new Rezension("L. Berg", "Ideal neben dem Job, gut gemacht.", 4, "2026-05-02"))),

            // 7) Kompaktstudiengang (KS) — verdichtetes Studienformat
            new Produkt("KS000019", "kompaktstudiengang-immobilienoekonomie",
                    "Kompaktstudiengang Immobilienökonomie (EBZ)",
                    "Akademisches Wissen verdichtet — berufsbegleitend in zwei Semestern.",
                    "Kompaktstudiengang", "Immobilienwirtschaft", "Immobilienwirtschaft", "Bochum",
                    Steuer.BEFREIT, true, null,
                    new Texte(
                            "<p>Der Kompaktstudiengang vermittelt immobilienökonomische Kernkompetenzen auf akademischem Niveau.</p>",
                            "<ul><li>Investitions- und Finanzierungsrechnung</li><li>Portfolio- und Assetmanagement</li></ul>",
                            "<p>Sie erweitern Ihre Kompetenz für anspruchsvolle Fach- und Führungsaufgaben.</p>",
                            "<p>Blended Learning: Präsenzphasen in Bochum plus begleitete Online-Phasen.</p>",
                            "<p>Abgeschlossene Berufsausbildung und einschlägige Berufserfahrung.</p>",
                            "<p>Förderfähig über Aufstiegs-BAföG (je nach Voraussetzungen).</p>", "", "", ""),
                    "Fachkräfte mit Berufserfahrung", "EBZ-Zertifikat",
                    null, "berufsbegleitend", 2, null,
                    "crm-2002", List.of("crm-3001"), List.of("studiengang-bachelor-real-estate"),
                    "bild-kompaktstudium.png", List.of("modulhandbuch-immobilienoekonomie.pdf"),
                    List.of(new Durchfuehrung("KS000019", "2026-10-01T08:00:00.000Z", "Bochum, EBZ Business School", Format.HYBRID, 480000)),
                    List.of()),

            // 8) Studiengang (STU) — Vertragsangebot: NICHT bestellbar → Anmelde-/Vertrags-Deeplink
            new Produkt("STU000003", "studiengang-bachelor-real-estate",
                    "Bachelor of Arts: Real Estate (B.A.)",
                    "Das grundständige Immobilienstudium der EBZ Business School — berufsbegleitend zum akkreditierten Bachelor.",
                    "Studiengang", "Immobilienwirtschaft", "Immobilienwirtschaft", "Bochum",
                    Steuer.BEFREIT, false, "https://portal.localhost/anmeldung/studiengang/STU000003",
                    new Texte(
                            "<p>Der akkreditierte Bachelorstudiengang verbindet Immobilienwirtschaft, Recht und Management "
                                    + "über sieben Semester berufsbegleitend.</p>",
                            "<ul><li>Immobilienwirtschaftliche Wertschöpfung verstehen</li><li>Management- und Methodenkompetenz aufbauen</li></ul>",
                            "<p>Sie erwerben einen akkreditierten akademischen Abschluss neben dem Beruf.</p>",
                            "<p>Blended Learning mit Präsenzphasen an der EBZ Business School.</p>",
                            "<p>Hochschulzugangsberechtigung oder beruflich Qualifizierte nach NRW-Regelung.</p>",
                            "<p>Förderfähig je nach individueller Situation.</p>", "", "",
                            "<p><strong>Wie melde ich mich an?</strong> Die Einschreibung läuft über das Studierendenportal.</p>"),
                    "Berufstätige mit Hochschulzugangsberechtigung", "Bachelor of Arts (B.A.)",
                    null, "berufsbegleitend", 7, "2030-09-30T00:00:00.000Z",
                    "crm-2002", List.of("crm-3001"), List.of("kompaktstudiengang-immobilienoekonomie"),
                    "bild-bachelor.png", List.of("studienfuehrer-real-estate.pdf"),
                    List.of(),
                    List.of()),

            // 9) Berufsschuljahr (BSJ) — Vertragsangebot: NICHT bestellbar → Anmelde-/Vertrags-Deeplink
            new Produkt("BSJ002601", "berufsschuljahr-immobilienkaufleute-2026",
                    "Berufsschuljahr Immobilienkaufleute 2026/2027",
                    "Der schulische Teil der Ausbildung zur/zum Immobilienkauffrau/-mann am EBZ-Berufskolleg.",
                    "Berufsschuljahr", "Ausbildung", "Immobilienwirtschaft", "Bochum",
                    Steuer.BEFREIT, false, "https://portal.localhost/anmeldung/berufsschule/BSJ002601",
                    new Texte(
                            "<p>Das Berufsschuljahr begleitet die betriebliche Ausbildung mit dem schulischen Fachunterricht "
                                    + "nach Lehrplan NRW.</p>",
                            "<ul><li>Immobilienwirtschaftliche Prozesse</li><li>Rechnungswesen und Recht</li></ul>",
                            "<p>Solide schulische Grundlage für die Abschlussprüfung vor der IHK.</p>",
                            "<p>Präsenzunterricht im Blockmodell am EBZ-Berufskolleg.</p>",
                            "<p>Bestehendes Ausbildungsverhältnis als Immobilienkauffrau/-mann.</p>", "", "", "", ""),
                    "Auszubildende Immobilienkaufleute", "Berufsschulabschluss",
                    null, null, null, null,
                    "crm-2001", List.of(), List.of(),
                    null, List.of(),
                    List.of(),
                    List.of()));
}
