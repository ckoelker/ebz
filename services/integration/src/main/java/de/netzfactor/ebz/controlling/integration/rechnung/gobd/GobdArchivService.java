package de.netzfactor.ebz.controlling.integration.rechnung.gobd;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import de.netzfactor.ebz.controlling.integration.rechnung.model.Rechnung;
import de.netzfactor.ebz.controlling.integration.rechnung.service.RegelVerletzung;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdDaten;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.RechnungZugferdMapper;
import de.netzfactor.ebz.controlling.integration.rechnung.zugferd.ZugferdService;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.messages.Retention;
import io.minio.messages.RetentionMode;

/**
 * GoBD-Archiv (Konzept §4, Säule 4): legt das <b>validierte</b> ZUGFeRD jedes festgeschriebenen
 * Belegs revisionssicher in MinIO ab — Bucket mit Object-Lock (WORM), je Objekt eine Retention
 * (Standard: COMPLIANCE, 10 Jahre = GoBD-Aufbewahrungsfrist), die auch root nicht verkürzen kann.
 * <p>
 * Aufruf bei der Festschreibung (Ausstellen/Storno/Gutschrift/Nachberechnung). Ist die ZUGFeRD-
 * Validierung nicht bestanden, wird <b>nicht archiviert und damit die Festschreibung verhindert</b>
 * ({@link RegelVerletzung} → 409): kein ausgestellter Beleg ohne revisionssicheren E-Rechnungs-Beleg.
 */
@ApplicationScoped
public class GobdArchivService {

    private static final Logger LOG = Logger.getLogger(GobdArchivService.class);

    @Inject
    MinioClient minio;

    @Inject
    ZugferdService zugferd;

    @Inject
    RechnungZugferdMapper mapper;

    @ConfigProperty(name = "rechnung.gobd.archiv.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "rechnung.gobd.bucket", defaultValue = "gobd-rechnungen")
    String bucket;

    @ConfigProperty(name = "rechnung.gobd.retention-mode", defaultValue = "COMPLIANCE")
    RetentionMode retentionMode;

    @ConfigProperty(name = "rechnung.gobd.retention-days", defaultValue = "3650")
    long retentionDays;

    /** Ergebnis der Archivierung — Ort + WORM-Retention (für Audit/Verifikation). */
    public record ArchivErgebnis(String bucket, String objektName, String modus, ZonedDateTime aufbewahrtBis) {
    }

    /** Archiviert nur, wenn das Archiv aktiv ist (Schalter {@code rechnung.gobd.archiv.enabled}). */
    public Optional<ArchivErgebnis> archiviereWennAktiv(Rechnung r) {
        if (!enabled) {
            LOG.debugf("GoBD-Archiv deaktiviert — Beleg %s nicht archiviert.", r.nummer);
            return Optional.empty();
        }
        return Optional.of(archiviere(r));
    }

    /** Erzeugt + validiert das ZUGFeRD und legt es revisionssicher (WORM) ab. */
    public ArchivErgebnis archiviere(Rechnung r) {
        if (r.nummer == null) {
            throw new IllegalStateException("Nur festgeschriebene Belege werden archiviert (Nummer fehlt).");
        }
        RechnungZugferdDaten daten = mapper.baue(r);
        ZugferdService.Ergebnis erg;
        try {
            erg = zugferd.erzeugeUndValidiere(daten);
        } catch (Exception e) {
            throw new RuntimeException("ZUGFeRD-Erzeugung fürs GoBD-Archiv fehlgeschlagen: " + e.getMessage(), e);
        }
        if (!erg.valide()) {
            throw new RegelVerletzung("Beleg " + r.nummer + " nicht archiviert — ZUGFeRD-Validierung fehlgeschlagen. Report: " + erg.report());
        }
        try {
            ensureBucket();
            String key = objektName(r);
            ZonedDateTime aufbewahrtBis = ZonedDateTime.now().plusDays(retentionDays);
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(erg.pdf()), erg.pdf().length, -1)
                    .contentType("application/pdf")
                    .retention(new Retention(retentionMode, aufbewahrtBis))
                    .build());
            LOG.infof("GoBD-Archiv: Beleg %s → %s/%s (WORM %s bis %s)", r.nummer, bucket, key,
                    retentionMode, aufbewahrtBis.toLocalDate());
            return new ArchivErgebnis(bucket, key, retentionMode.name(), aufbewahrtBis);
        } catch (RegelVerletzung e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("GoBD-Archivierung in MinIO fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Legt den WORM-Bucket bei Bedarf an (Object-Lock muss bei der Erstellung gesetzt werden). */
    private void ensureBucket() throws Exception {
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).objectLock(true).build());
            LOG.infof("GoBD-Bucket %s mit Object-Lock (WORM) angelegt.", bucket);
        }
    }

    /** Stabiler, sprechender Objektschlüssel: {@code <bereich>/<jahr>/<belegnummer>.pdf}. */
    private static String objektName(Rechnung r) {
        int jahr = r.ausstellungsdatum != null ? r.ausstellungsdatum.getYear() : ZonedDateTime.now().getYear();
        return r.bereich.name().toLowerCase() + "/" + jahr + "/" + r.nummer + ".pdf";
    }
}
