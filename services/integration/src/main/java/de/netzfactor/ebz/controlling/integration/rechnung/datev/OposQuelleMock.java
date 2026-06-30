package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default-/Showcase-Implementierung der {@link OposQuelle}: liefert nichts. Der reale Zahlungsstatus käme
 * aus <b>DATEVconnect (on-prem, Kanzlei)</b> — dafür gibt es keine Cloud-Sandbox. Der Regelkreis ist damit
 * gebaut und getestet (Test ersetzt diese Quelle per {@code @io.quarkus.test.Mock}); der DATEVconnect-
 * Adapter tritt später an dieselbe Stelle.
 */
@ApplicationScoped
public class OposQuelleMock implements OposQuelle {

    @Override
    public List<Posten> ausgeglichenePosten(int max) {
        return List.of();
    }
}
