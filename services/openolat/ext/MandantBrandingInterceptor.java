package de.netzfactor.ebz.olat.branding;

import java.util.List;

import org.olat.basesecurity.OrganisationRoles;
import org.olat.basesecurity.OrganisationService;
import org.olat.core.CoreSpringFactory;
import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.Component;
import org.olat.core.gui.components.panel.Panel;
import org.olat.core.gui.control.ChiefController;
import org.olat.core.gui.control.Event;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.gui.control.controller.BasicController;
import org.olat.core.id.Identity;
import org.olat.core.id.Organisation;
import org.olat.core.logging.Tracing;
import org.olat.login.SupportsAfterLoginInterceptor;

/**
 * EBZ-Mandanten-Branding über den OFFIZIELLEN OpenOLAT-Erweiterungsweg (kein Core-Fork):
 * ein AfterLogin-Interceptor (registriert via {@code _spring/mandantBrandingContext.xml} →
 * {@code afterLoginInterceptionManager}). Er liest die {@code cssClass} der Organisation(en) des
 * angemeldeten Nutzers — die das Integration-Backend bereits als {@code mandant-<schlüssel>} setzt
 * (OrganisationVO.cssClass) — und hängt sie über die öffentliche API
 * {@link ChiefController#addBodyCssClass(String)} an das {@code <body>}. Das extern geladene
 * EBZ-Theme targetet dann {@code .mandant-<schlüssel> …} → sichtbare per-Mandant-CI, EBZ-Default
 * unberührt.
 *
 * <p>{@link #isUserInteractionRequired(UserRequest)} liefert {@code false}: die Body-Klasse ist im
 * Konstruktor bereits am (session-langen) ChiefController gesetzt, ein Modal wird nie gezeigt.
 */
public class MandantBrandingInterceptor extends BasicController implements SupportsAfterLoginInterceptor {

    public MandantBrandingInterceptor(UserRequest ureq, WindowControl wControl) {
        super(ureq, wControl);
        wendeMandantBrandingAn(ureq, wControl);
        putInitialPanel(new Panel("ebz_mandant_branding")); // unsichtbar; die Arbeit ist oben passiert
    }

    private void wendeMandantBrandingAn(UserRequest ureq, WindowControl wControl) {
        try {
            Identity identity = ureq.getIdentity();
            if (identity == null || wControl.getWindowBackOffice() == null) {
                return;
            }
            ChiefController chief = wControl.getWindowBackOffice().getChiefController();
            if (chief == null) {
                return;
            }
            OrganisationService orgService = CoreSpringFactory.getImpl(OrganisationService.class);
            List<Organisation> orgs = orgService.getOrganisations(identity, OrganisationRoles.user);
            for (Organisation org : orgs) {
                String cssClass = org.getCssClass();
                if (cssClass != null && !cssClass.isBlank()) {
                    chief.addBodyCssClass(cssClass);
                    Tracing.createLoggerFor(MandantBrandingInterceptor.class)
                            .info("EBZ-Mandanten-Branding: Body-CSS '" + cssClass + "' für "
                                    + identity.getKey() + " (Org " + org.getDisplayName() + ")");
                }
            }
        } catch (RuntimeException e) {
            // Branding ist nie kritisch: niemals den Login blockieren.
            Tracing.createLoggerFor(MandantBrandingInterceptor.class)
                    .warn("EBZ-Mandanten-Branding übersprungen: " + e.getMessage());
        }
    }

    @Override
    public boolean isUserInteractionRequired(UserRequest ureq) {
        return false; // nie ein Modal zeigen — die Body-Klasse ist bereits gesetzt
    }

    @Override
    protected void event(UserRequest ureq, Component source, Event event) {
        // keine Interaktion
    }

    @Override
    protected void doDispose() {
        // nichts freizugeben (die Body-Klasse lebt am ChiefController weiter)
    }
}
