package de.netzfactor.ebz.controlling.integration.model;

/**
 * Ergebnis der KI-Klassifikation eines Deals — ausschließlich <b>Text-/Kategorie-Merkmale</b>.
 * <p>
 * L8: Hier stehen <b>niemals</b> Beträge, Daten oder Pipeline-Wahrscheinlichkeiten — die bleiben
 * deterministisch aus dem Rohdatensatz. {@code konfidenz} ist die <i>Selbsteinschätzung der
 * Klassifikationsgüte</i> (für L10-Review-Routing), nicht die Umsatz-Eintrittswahrscheinlichkeit
 * (die kommt in M3 aus dem dbt-Seed, L7).
 */
public record DealClassification(
        String normalisierterFirmenname,
        SeminarKategorie seminarKategorie,
        DeliveryType deliveryType,
        double konfidenz) {
}
