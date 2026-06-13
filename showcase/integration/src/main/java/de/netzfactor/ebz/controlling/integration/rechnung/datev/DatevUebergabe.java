package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.util.List;

/**
 * Port für die Buchungsübergabe an DATEV (R4, Konzept §4 Säule 5: „DATEV hinter Interface"). Genau
 * eine Implementierung ist aktiv (Config {@code datev.modus}); so lässt sich die EXTF-CSV-Brücke
 * später gegen den echten DATEV-Datenservice (Buchungsdatenservice/DATEVconnect) tauschen, ohne dass
 * die Belegerzeugung sich ändert.
 */
public interface DatevUebergabe {

    /**
     * Übergibt die Buchungssätze und liefert ein Protokoll.
     * {@code artefakt} trägt – falls erzeugt – die EXTF-Datei (sonst {@code null}, z. B. beim Cloud-Weg).
     */
    Protokoll uebergeben(List<Buchungssatz> saetze, ExtfBuchungsstapel.Kopf kopf);

    record Protokoll(String modus, String referenz, int anzahlBuchungen, byte[] artefakt, String hinweis) {
    }
}
