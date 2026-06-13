package de.netzfactor.ebz.controlling.integration.rechnung.gobd;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.minio.MinioClient;

/**
 * Stellt den {@link MinioClient} als CDI-Bean bereit (Konfiguration {@code minio.*}). Bewusst das
 * plain MinIO-SDK statt der Quarkiverse-Extension, die auf eine ältere Quarkus-Version zielt
 * (Versions-Mismatch vermeiden). Der Client ist lazy: er wird erst erzeugt, wenn das GoBD-Archiv
 * tatsächlich genutzt wird (bei deaktiviertem Archiv fällt keine MinIO-Verbindung an).
 */
@ApplicationScoped
public class MinioProducer {

    @ConfigProperty(name = "minio.url", defaultValue = "http://localhost:9000")
    String url;

    @ConfigProperty(name = "minio.access-key", defaultValue = "minioadmin")
    String accessKey;

    @ConfigProperty(name = "minio.secret-key", defaultValue = "minioadmin")
    String secretKey;

    @Produces
    @ApplicationScoped
    public MinioClient minioClient() {
        return MinioClient.builder().endpoint(url).credentials(accessKey, secretKey).build();
    }
}
