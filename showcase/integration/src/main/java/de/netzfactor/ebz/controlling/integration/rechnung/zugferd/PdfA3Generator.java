package de.netzfactor.ebz.controlling.integration.rechnung.zugferd;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.xml.XmpSerializer;

/**
 * Erzeugt mit PDFBox das menschenlesbare PDF/A-3 der Rechnung (Träger des eingebetteten ZUGFeRD-XML).
 * Schlankes Layout: EBZ-Briefkopf, Empfängeranschrift, Beleg-Kopf, Positionstabelle, Gesamtbetrag,
 * Steuerbefreiungs- und Zahlungshinweis. Eingebettete TrueType-Fonts (DejaVuSans) sind für PDF/A
 * Pflicht. Frei von CDI/Persistenz → plain testbar.
 */
public class PdfA3Generator {

    private static final String FONT_REGULAR = "/fonts/DejaVuSans.ttf";
    private static final String FONT_BOLD = "/fonts/DejaVuSans-Bold.ttf";

    private static final float PAGE_H = 842f;       // A4
    private static final float LEFT = 68f;
    private static final float RIGHT = 540f;        // rechte Kante Beträge
    private static final float ROW = 15f;

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    public byte[] erzeugePdfA3(RechnungZugferdDaten d) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDFont reg = ladeFont(doc, FONT_REGULAR);
            PDFont bold = ladeFont(doc, FONT_BOLD);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                zeichne(cs, reg, bold, d);
            }
            setzeOutputIntent(doc);
            setzeXmpMetadaten(doc, d);
            setzeDokumentInfo(doc, d);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private PDFont ladeFont(PDDocument doc, String pfad) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(pfad)) {
            if (is == null) {
                throw new IllegalStateException("Schrift fehlt: src/main/resources" + pfad);
            }
            return PDType0Font.load(doc, is);
        }
    }

    private void zeichne(PDPageContentStream cs, PDFont reg, PDFont bold, RechnungZugferdDaten d)
            throws IOException {
        Verkaeufer v = d.verkaeufer();
        // Briefkopf (Verkäufer)
        text(cs, bold, 12, LEFT, 70, v.name());
        text(cs, reg, 9, LEFT, 86, v.strasse() + " · " + v.plz() + " " + v.ort());
        text(cs, reg, 9, LEFT, 98, "USt-IdNr. " + v.ustId() + " · " + v.email());

        // Empfänger (Bill-to)
        var k = d.empfaenger();
        float ay = 150;
        text(cs, reg, 10, LEFT, ay, k.name());
        ay += ROW;
        if (k.strasse() != null && !k.strasse().isBlank()) {
            text(cs, reg, 10, LEFT, ay, k.strasse());
            ay += ROW;
        }
        text(cs, reg, 10, LEFT, ay, nz(k.plz()) + " " + nz(k.ort()));

        // Beleg-Kopf
        text(cs, reg, 10, RIGHT - 200, 150, "Kundennr.:");
        textRight(cs, reg, 10, RIGHT, 150, k.debitorNr());
        text(cs, reg, 10, RIGHT - 200, 150 + ROW, "Rechnungsdatum:");
        textRight(cs, reg, 10, RIGHT, 150 + ROW, d.ausstellungsdatum().format(DATUM));

        float y = 250;
        text(cs, bold, 13, LEFT, y, "Rechnung " + d.nummer());
        y += ROW;
        if (d.zeitraumBezeichnung() != null && !d.zeitraumBezeichnung().isBlank()) {
            text(cs, reg, 10, LEFT, y, d.zeitraumBezeichnung());
            y += ROW;
        }

        // Positionstabelle
        y += 12;
        text(cs, bold, 10, LEFT, y, "Position");
        textRight(cs, bold, 10, RIGHT, y, "Betrag");
        y += 4;
        linie(cs, LEFT, y, RIGHT);
        y += ROW;
        for (RechnungZugferdDaten.Position p : d.positionen()) {
            for (String zeile : umbrechen(reg, 10, p.beschreibung(), RIGHT - LEFT - 90)) {
                text(cs, reg, 10, LEFT, y, zeile);
                y += ROW;
            }
            // Betrag auf Höhe der ersten Beschreibungszeile dieser Position
            textRight(cs, reg, 10, RIGHT, y - ROW, money(p.betragCent()));
        }
        linie(cs, LEFT, y, RIGHT);
        y += ROW;
        text(cs, bold, 11, LEFT, y, "Gesamtbetrag");
        textRight(cs, bold, 11, RIGHT, y, money(d.gesamtCent()));

        // Steuerhinweis (Befreiung) + Zahlungshinweis
        y += 2 * ROW;
        y = absatz(cs, reg, 9, y, d.befreiungsgrund());
        y += 6;
        absatz(cs, reg, 9, y, "Zahlbar innerhalb von " + d.zahlungszielTage() + " Tagen (bis "
                + d.faelligAm().format(DATUM) + ") ohne Abzug auf das Konto bei der " + v.bankName()
                + " (IBAN " + v.iban() + ", BIC " + v.bic() + "). Bitte Kunden-Nr. " + k.debitorNr()
                + " und Rechnungs-Nr. " + d.nummer() + " angeben.");
    }

    // ── Zeichenhilfen ──
    private void text(PDPageContentStream cs, PDFont font, float size, float x, float topY, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, PAGE_H - topY - size);
        cs.showText(s == null ? "" : s);
        cs.endText();
    }

    private void textRight(PDPageContentStream cs, PDFont font, float size, float rightX, float topY, String s)
            throws IOException {
        text(cs, font, size, rightX - breite(font, size, s), topY, s);
    }

    private float absatz(PDPageContentStream cs, PDFont reg, float size, float topY, String s)
            throws IOException {
        for (String zeile : umbrechen(reg, size, s, RIGHT - LEFT)) {
            text(cs, reg, size, LEFT, topY, zeile);
            topY += size + 3.5f;
        }
        return topY;
    }

    private void linie(PDPageContentStream cs, float x1, float topY, float x2) throws IOException {
        float yy = PAGE_H - topY;
        cs.setLineWidth(0.6f);
        cs.moveTo(x1, yy);
        cs.lineTo(x2, yy);
        cs.stroke();
    }

    private List<String> umbrechen(PDFont font, float size, String s, float maxWidth) throws IOException {
        List<String> zeilen = new ArrayList<>();
        StringBuilder zeile = new StringBuilder();
        for (String w : (s == null ? "" : s).split(" ")) {
            String test = zeile.length() == 0 ? w : zeile + " " + w;
            if (breite(font, size, test) > maxWidth && zeile.length() > 0) {
                zeilen.add(zeile.toString());
                zeile = new StringBuilder(w);
            } else {
                zeile = new StringBuilder(test);
            }
        }
        if (zeile.length() > 0) {
            zeilen.add(zeile.toString());
        }
        return zeilen.isEmpty() ? List.of("") : zeilen;
    }

    private static float breite(PDFont font, float size, String s) throws IOException {
        return font.getStringWidth(s == null ? "" : s) / 1000f * size;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Cent → "1.500,00 €". */
    private static String money(long cent) {
        return String.format(Locale.GERMANY, "%,.2f €", cent / 100.0);
    }

    // ── PDF/A-3-Plumbing ──
    private void setzeOutputIntent(PDDocument doc) throws IOException {
        ICC_Profile icc = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        try (InputStream iccIn = new ByteArrayInputStream(icc.getData())) {
            PDOutputIntent oi = new PDOutputIntent(doc, iccIn);
            oi.setInfo("sRGB IEC61966-2.1");
            oi.setOutputCondition("sRGB IEC61966-2.1");
            oi.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            oi.setRegistryName("http://www.color.org");
            doc.getDocumentCatalog().addOutputIntent(oi);
        }
    }

    private void setzeXmpMetadaten(PDDocument doc, RechnungZugferdDaten d) throws IOException {
        try {
            XMPMetadata xmp = XMPMetadata.createXMPMetadata();
            PDFAIdentificationSchema id = xmp.createAndAddPDFAIdentificationSchema();
            id.setPart(3);
            id.setConformance("B");
            DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
            dc.setTitle("Rechnung " + d.nummer());
            dc.addCreator(d.verkaeufer().name());
            ByteArrayOutputStream xmpOut = new ByteArrayOutputStream();
            new XmpSerializer().serialize(xmp, xmpOut, true);
            PDMetadata metadata = new PDMetadata(doc, new ByteArrayInputStream(xmpOut.toByteArray()));
            doc.getDocumentCatalog().setMetadata(metadata);
        } catch (Exception e) {
            throw new IOException("XMP-Metadaten konnten nicht geschrieben werden", e);
        }
    }

    private void setzeDokumentInfo(PDDocument doc, RechnungZugferdDaten d) {
        PDDocumentInformation info = doc.getDocumentInformation();
        info.setTitle("Rechnung " + d.nummer());
        info.setAuthor(d.verkaeufer().name());
        info.setCreationDate(Calendar.getInstance());
        info.setProducer("EBZ integration (Quarkus + PDFBox)");
    }
}
