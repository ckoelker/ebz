package de.netzfactor.ebz.controlling.integration.rechnung.zugferd;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import jakarta.enterprise.context.ApplicationScoped;

import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDExporter;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;
import org.mustangproject.validator.ZUGFeRDValidator;

/**
 * Erzeugt aus {@link RechnungZugferdDaten} eine ZUGFeRD-Rechnung: PDF/A-3 (via {@link PdfA3Generator})
 * + eingebettetes EN-16931-XML (Mustang, Profil {@code EN16931}) und <b>validiert pflichtgemäß</b>
 * (PDF/A- + EN-16931-Prüfung). Nur ein valides Dokument darf ausgeliefert/archiviert werden — der
 * Validator ist das harte Tor (Konzept §4b). Frei von CDI/Persistenz → plain testbar.
 * <p>
 * R2a: Berufsschule, alle Positionen steuerbefreit (Kategorie {@code E}, §4 UStG). Steuersätze/
 * Korrekturbeleg-Typen (381) folgen später.
 */
@ApplicationScoped
public class ZugferdService {

    private final PdfA3Generator pdfGenerator = new PdfA3Generator();

    /** Ergebnis: ZUGFeRD-PDF + Validierungs-Status/-Report. */
    public record Ergebnis(byte[] pdf, boolean valide, String report) {
    }

    public Ergebnis erzeugeUndValidiere(RechnungZugferdDaten d) throws Exception {
        byte[] pdfA3 = pdfGenerator.erzeugePdfA3(d);
        byte[] zugferd = einbetten(pdfA3, d);
        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        String report = validator.validate(zugferd, "rechnung-" + d.nummer() + ".pdf");
        return new Ergebnis(zugferd, validator.wasCompletelyValid(), report);
    }

    private byte[] einbetten(byte[] pdfA3, RechnungZugferdDaten d) throws Exception {
        Invoice invoice = baueInvoice(d);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (IZUGFeRDExporter exporter = new ZUGFeRDExporterFromA3()
                .setProducer("EBZ integration (Quarkus)")
                .setCreator(d.verkaeufer().name())
                .setProfile(Profiles.getByName("EN16931"))
                .load(pdfA3)) {
            exporter.setTransaction(invoice);
            exporter.export(out);
        }
        return out.toByteArray();
    }

    private Invoice baueInvoice(RechnungZugferdDaten d) {
        Verkaeufer v = d.verkaeufer();
        TradeParty sender = new TradeParty(v.name(), v.strasse(), v.plz(), v.ort(), v.land())
                .addVATID(v.ustId())
                .setContact(new Contact(v.kontaktName(), v.kontaktTelefon(), v.email()))
                .addBankDetails(new BankDetails(v.iban(), v.bic()));

        var k = d.empfaenger();
        TradeParty recipient = new TradeParty(k.name(), nz(k.strasse()), nz(k.plz()), nz(k.ort()),
                k.land() == null ? "DE" : k.land());
        if (k.ustId() != null && !k.ustId().isBlank()) {
            recipient.addVATID(k.ustId());
        }

        Invoice invoice = new Invoice()
                .setNumber(d.nummer())
                .setIssueDate(toDate(d.ausstellungsdatum()))
                .setDueDate(toDate(d.faelligAm()))
                .setSender(sender)
                .setRecipient(recipient)
                .setPaymentTermDescription("Zahlbar innerhalb von " + d.zahlungszielTage()
                        + " Tagen ohne Abzug. Kunden-Nr. " + k.debitorNr()
                        + ", Rechnungs-Nr. " + d.nummer() + " angeben.");
        if (d.zeitraumBezeichnung() != null && !d.zeitraumBezeichnung().isBlank()) {
            invoice.addNote(d.zeitraumBezeichnung());
        }

        for (RechnungZugferdDaten.Position p : d.positionen()) {
            // Steuerbefreit (§4 UStG): Kategorie E, 0 %, mit Befreiungsgrund.
            Product product = new Product(p.beschreibung(), p.beschreibung(), "C62", BigDecimal.ZERO);
            product.setTaxCategoryCode("E");
            product.setTaxExemptionReason(d.befreiungsgrund());
            invoice.addItem(new Item(product, cent(p.betragCent()), BigDecimal.ONE));
        }
        return invoice;
    }

    private static BigDecimal cent(long cent) {
        return BigDecimal.valueOf(cent, 2); // Cent → Euro mit Skala 2
    }

    private static Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
