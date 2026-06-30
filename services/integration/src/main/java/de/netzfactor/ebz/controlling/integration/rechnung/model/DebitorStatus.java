package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Lebenszustand eines Debitors in der Nummernkreis-Hoheit (R3). {@code AKTIV} = gültiger Golden-Record;
 * {@code ZUSAMMENGEFUEHRT} = Dublette, die in einen anderen Debitor gemergt wurde — bleibt als Historie
 * stehen, zeigt per {@code goldenDebitorId} auf den überlebenden Datensatz, und seine alte Nummer lebt
 * als {@link DebitorAlias} weiter.
 */
public enum DebitorStatus {
    AKTIV, ZUSAMMENGEFUEHRT
}
