package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Rendert einen DATEV-Buchungsstapel im EXTF-Format (Format 700, Kategorie 21) — die CSV-„Brücke"
 * (EXTF = Export-Textformat), die in DATEV importiert werden kann. CDI-frei → plain testbar.
 * <p>
 * Aufbau: Kopfzeile (Formatkennung + Berater/Mandant/Wirtschaftsjahr), Spaltenüberschriften und je
 * Buchungssatz eine Datenzeile. Es wird die für den Beleg-Import maßgebliche Spaltenuntermenge des
 * EXTF-Buchungsstapels erzeugt (Umsatz, S/H, Konto, Gegenkonto, BU, Belegdatum, Belegfeld 1,
 * Buchungstext). DATEV erwartet Windows-1252 → {@link #bytes(List, Kopf)} kodiert entsprechend.
 */
public final class ExtfBuchungsstapel {

    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final DateTimeFormatter ERZEUGT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter TTMM = DateTimeFormatter.ofPattern("ddMM");

    /** EXTF-Kopfdaten (Berater/Mandant/Wirtschaftsjahr + Zeitraum + Bezeichnung des Stapels). */
    public record Kopf(String beraternummer, String mandantennummer, String wirtschaftsjahrBeginn,
            int sachkontenLaenge, LocalDate von, LocalDate bis, String bezeichnung) {
    }

    private ExtfBuchungsstapel() {
    }

    public static byte[] bytes(List<Buchungssatz> saetze, Kopf kopf) {
        return render(saetze, kopf).getBytes(CP1252);
    }

    public static String render(List<Buchungssatz> saetze, Kopf kopf) {
        StringBuilder sb = new StringBuilder();
        sb.append(kopfzeile(kopf)).append("\r\n");
        sb.append(spaltenkopf()).append("\r\n");
        for (Buchungssatz s : saetze) {
            sb.append(datenzeile(s)).append("\r\n");
        }
        return sb.toString();
    }

    private static String kopfzeile(Kopf k) {
        // Format 700, Kategorie 21 (Buchungsstapel), Formatname "Buchungsstapel".
        return String.join(";",
                q("EXTF"), "700", "21", q("Buchungsstapel"), "13",
                LocalDateTime.now().format(ERZEUGT), "", q(""), q(""), q(""),
                k.beraternummer(), k.mandantennummer(), k.wirtschaftsjahrBeginn(),
                String.valueOf(k.sachkontenLaenge()),
                k.von().format(jjjjMMtt()), k.bis().format(jjjjMMtt()),
                q(kuerze(k.bezeichnung(), 30)), q(""), "1", "0", "0", q("EUR"));
    }

    private static String spaltenkopf() {
        return String.join(";",
                "Umsatz (ohne Soll/Haben-Kz)", "Soll/Haben-Kennzeichen", "WKZ Umsatz", "Kurs",
                "Basis-Umsatz", "WKZ Basis-Umsatz", "Konto", "Gegenkonto (ohne BU-Schlüssel)",
                "BU-Schlüssel", "Belegdatum", "Belegfeld 1", "Belegfeld 2", "Skonto", "Buchungstext");
    }

    private static String datenzeile(Buchungssatz s) {
        return String.join(";",
                betrag(s.umsatzCent()), q(s.sollHaben()), q("EUR"), "", "", "",
                s.konto(), s.gegenkonto(), q(nz(s.buSchluessel())),
                s.belegdatum().format(TTMM), q(kuerze(nz(s.belegfeld1()), 36)), q(""), "",
                q(kuerze(nz(s.buchungstext()), 60)));
    }

    private static DateTimeFormatter jjjjMMtt() {
        return DateTimeFormatter.ofPattern("yyyyMMdd");
    }

    private static String betrag(long cent) {
        return String.format(Locale.GERMANY, "%.2f", cent / 100.0); // 4300,00
    }

    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "''")) + "\"";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String kuerze(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
