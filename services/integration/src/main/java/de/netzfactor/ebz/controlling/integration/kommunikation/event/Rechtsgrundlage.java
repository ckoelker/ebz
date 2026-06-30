package de.netzfactor.ebz.controlling.integration.kommunikation.event;

/**
 * DSGVO-Rechtsgrundlage (Art. 6) eines Ereignistyps — bewusst eine <b>modul-eigene</b> Aufzählung, damit
 * der Kommunikations-Kern keinerlei Compile-Abhängigkeit auf den Party-Kern ({@code Einwilligung}) trägt
 * (split-ready). Inhaltlich deckungsgleich mit {@code party.model.Einwilligung.Rechtsgrundlage}; ein
 * Adapter mappt bei Bedarf. {@link #VERTRAG_6_1_B} (transaktional) umgeht das Marketing-Opt-In.
 */
public enum Rechtsgrundlage {
    EINWILLIGUNG_6_1_A,
    VERTRAG_6_1_B,
    BERECHTIGTES_INTERESSE_6_1_F
}
