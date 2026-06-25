package de.netzfactor.ebz.controlling.integration.mandant.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import de.netzfactor.ebz.controlling.integration.party.model.Organisation;

/**
 * Ein <b>Mandant</b> der mandantenfähigen eLearning-Vermarktung (PoC, OpenOLAT shared). Ein Mandant = eine
 * Top-Organisation in OpenOLAT (B3) und — für föderierte B2B — eine Keycloak-Organization (M3). Die
 * Landschaft ist <i>additiv</i> und EBZ-zentriert ({@code mandanten-vermarktung-planung/PoC-…} §1):
 * <ul>
 *   <li>{@link Vertragstyp#EBZ_CUSTOMER} — der EBZ-Kernmandant (B2C-Shop-Kunden) auf dem globalen
 *       Default-Theme, <b>ohne</b> Seat-Limit; die bestehende L0–L3-Strecke läuft hierüber unverändert.</li>
 *   <li>{@link Vertragstyp#EBZ_STAFF} — EBZ-Mitarbeiter als interne Lernende, EBZ-Branding.</li>
 *   <li>{@link Vertragstyp#ENTERPRISE_FLAT} — ein B2B-Kunde (≤ 20 in der Endausbaustufe) mit eigenem
 *       gebrokerten IdP ({@link IdpFoederation}), eigenem Branding und einem Seat-{@link Lizenzvertrag}.</li>
 * </ul>
 * Flache Entity im Schema {@code mdm} mit echter {@code @ManyToOne}-FK auf {@link Organisation} (B2B:
 * der Kunde als Party; EBZ-Kontexte optional auf die EBZ-Org). {@code openolatOrganisationKey} wird erst
 * von der Org-Projektion (M2) gesetzt. Branding-Felder speisen das per-Org-Theme (M0/Stufe 1) und den
 * visuellen Branding-Test (K3) → bewusst <b>kontrastreich</b> wählen.
 */
@Entity
@Table(name = "mandant", schema = "mdm")
public class Mandant extends PanacheEntity {

    /** Vertrags-/Tenant-Art — steuert Seat-Cap, Branding-Pfad und Landing-Regel. */
    public enum Vertragstyp {
        /** EBZ-Kernmandant: B2C-Shop-Kunden, Default-Theme, kein Seat-Limit. */
        EBZ_CUSTOMER,
        /** EBZ-Mitarbeiter als interne Lernende. */
        EBZ_STAFF,
        /** B2B-Flatrate-Mandant mit eigenem IdP, Branding und Seat-Limit. */
        ENTERPRISE_FLAT
    }

    /** Lebenszyklus des Mandanten. */
    public enum Status {
        ENTWURF, AKTIV, GESPERRT, BEENDET
    }

    @Version
    public long version;

    /** Stabiler fachlicher Schlüssel (z. B. {@code EBZ_CUSTOMER}, {@code DEMO_AG}) — Bootstrap-idempotent. */
    @Column(name = "schluessel", nullable = false, unique = true, length = 40)
    public String schluessel;

    @Column(name = "anzeige_name", nullable = false, length = 200)
    public String anzeigeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "vertragstyp", nullable = false, length = 24)
    public Vertragstyp vertragstyp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public Status status = Status.ENTWURF;

    /** Der Kunde als Party (B2B) bzw. die EBZ-Org (EBZ-Kontexte, optional). Echte FK (B5: 1 Identität = 1 Mandant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organisation_id")
    public Organisation organisation;

    /** RepositoryEntry-/Organisations-Key in OpenOLAT — von der Org-Projektion (M2) gesetzt; leer = noch nicht projiziert. */
    @Column(name = "openolat_organisation_key")
    public Long openolatOrganisationKey;

    /**
     * Keycloak-Organization-ID (UUID) — von der Keycloak-Org-Projektion (M3) für föderierte B2B-Mandanten
     * gesetzt; Quelle des Domain→Mandant-Routings und des {@code mandant}-Claims (K4). Leer = nicht
     * föderiert (EBZ-Kontexte) bzw. noch nicht projiziert.
     */
    @Column(name = "keycloak_organisation_id", length = 64)
    public String keycloakOrganizationId;

    // ── Branding (Stufe 1: Logo + Farben; visuell klar unterscheidbar für K3) ──
    @Column(name = "logo_url", length = 300)
    public String logoUrl;

    /** Primär-Markenfarbe als Hex ({@code #RRGGBB}); kontrastreich wählen (Default-Theme = EBZ). */
    @Column(name = "primaer_farbe", length = 16)
    public String primaerFarbe;

    @Column(name = "sekundaer_farbe", length = 16)
    public String sekundaerFarbe;

    @Column(name = "erstellt_am", nullable = false)
    public Instant erstelltAm;

    /** EBZ-Kernmandant (B2C) und EBZ-Staff laufen ohne Seat-Limit; nur {@link Vertragstyp#ENTERPRISE_FLAT} ist begrenzt. */
    public boolean istSeatBegrenzt() {
        return vertragstyp == Vertragstyp.ENTERPRISE_FLAT;
    }
}
