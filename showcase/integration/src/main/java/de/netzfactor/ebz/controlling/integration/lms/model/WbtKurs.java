package de.netzfactor.ebz.controlling.integration.lms.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import de.netzfactor.ebz.controlling.integration.bildung.model.AngebotStatus;

/**
 * Web-Based-Training als MDM-Katalogeintrag (LMS-Anbindung L1). Flache Entity im Schema {@code mdm}
 * (analog {@code bildungsangebot}); MDM ist Katalog-SoR, Vendure = Commerce, OpenOLAT = Delivery.
 * <p>
 * Die drei Welten sind über zwei Schlüssel verklammert: {@code openolatKey} = RepositoryEntry-Key der
 * importierten SCORM-Ressource in OpenOLAT (Delivery-Referenz, vgl. {@code lms-import-seed.sh}),
 * {@code vendureProductId}/{@code vendureVariantId} = das projizierte Shop-Produkt (Commerce). So
 * mappt der Einschreibungs-Dispatcher (L2) eine bezahlte Vendure-Order über die Produkt-ID auf den
 * OpenOLAT-Kurs — ohne ein eigenes Produkt-Custom-Field in Vendure (Mapping liegt hier im MDM).
 * <p>
 * Persistenz-only: KEINE Bean-Validation hier — die liegt allein im {@code WbtKursDto} (Single Source,
 * Stack B → smallrye-openapi → /q/openapi → orval). Schema {@code mdm} explizit am {@code @Table}.
 */
@Entity
@Table(name = "wbt_kurs", schema = "mdm")
public class WbtKurs extends PanacheEntity {

    /** Optimistic Locking (wie {@code Bildungsangebot}). */
    @Version
    public long version;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    public String code;

    @Column(name = "titel", nullable = false, length = 200)
    public String titel;

    @Column(name = "kurzbeschreibung", length = 2000)
    public String kurzbeschreibung;

    /** Delivery-Referenz: RepositoryEntry-Key der SCORM-Ressource in OpenOLAT (leer = noch nicht importiert). */
    @Column(name = "openolat_key")
    public Long openolatKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "scorm_version", length = 16)
    public ScormVersion scormVersion;

    /**
     * Anrechenbare Soll-Stunden (Weiterbildungs-/Kreditstunden) dieses WBT — die <b>rechtlich maßgebliche</b>
     * Zählung des Nachweises (F1/F2). Bewusst statt der unzuverlässigen SCORM-{@code session_time}
     * (write-only, „kein Beweis echter Lernzeit"): bei Completion fließt dieser Wert in den
     * {@code LernleistungsFakt}. Leer = (noch) nicht anrechenbar. Die §34d/§34c/§34i-Anrechnung wird separat
     * final geprüft (offener Punkt).
     */
    @Column(name = "soll_stunden_anrechenbar", precision = 5, scale = 2)
    public BigDecimal sollStundenAnrechenbar;

    /** Listenpreis in Cent (Vendure-Minor-Units); leer = (noch) kein verkaufbarer Preis. */
    @Column(name = "preis_cent")
    public Integer preisCent;

    @Column(name = "shop_verkauf", nullable = false)
    public boolean shopVerkauf;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    public AngebotStatus status;

    /** Naht zu Vendure: projiziertes Produkt + Variante (Idempotenz der Re-Projektion). */
    @Column(name = "vendure_product_id", length = 64)
    public String vendureProductId;

    @Column(name = "vendure_variant_id", length = 64)
    public String vendureVariantId;
}
