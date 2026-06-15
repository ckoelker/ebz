package de.netzfactor.ebz.controlling.integration.rechnung.model;

/**
 * Versandstatus eines festgeschriebenen Belegs. {@code NICHT_VERSENDET} (Default, auch {@code null} auf
 * Altbeständen) → mit erfolgreichem Mailversand der ZUGFeRD-E-Rechnung an den Debitor {@code VERSENDET};
 * {@code FEHLGESCHLAGEN}, wenn die Erzeugung/Validierung des Belegs vor dem Versand scheitert.
 */
public enum RechnungVersandStatus {
    NICHT_VERSENDET, VERSENDET, FEHLGESCHLAGEN
}
