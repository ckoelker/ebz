package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Bootet einen WireMock-Stub der DATEV-Cloud (Token-Endpoint + Buchungsdatenservice) und verdrahtet die
 * {@code datev-*}-REST-Clients darauf. Deterministisch/offline — die echte Sandbox bleibt dem opt-in-Smoke
 * vorbehalten. Der Import-Stub matcht <b>nur mit den Pflicht-Headern</b> ({@code Authorization} +
 * {@code X-DATEV-Client-Id}); fehlen sie, gibt es keinen Match (404) → der Adapter wirft, der Test wird rot.
 */
public class DatevWireMockResource implements QuarkusTestResourceLifecycleManager {

    static final String MANDANT = "12345-1";
    static final String OAUTH_CLIENT = "test-client";
    private static final String BASIS = "/platform-sandbox/v3/clients/" + MANDANT + "/extf-files";

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(options().dynamicPort());
        server.start();

        // OAuth: refresh_token → access_token (+ rotierter refresh_token).
        server.stubFor(post(urlPathEqualTo("/token")).willReturn(okJson(
                "{\"access_token\":\"AT-test\",\"refresh_token\":\"RT-rotated\",\"token_type\":\"bearer\",\"expires_in\":900}")));

        // EXTF-Import — nur mit Pflicht-Headern → 201 + Location auf den Job.
        server.stubFor(post(urlPathEqualTo(BASIS + "/import"))
                .withHeader("Authorization", matching("Bearer .+"))
                .withHeader("X-DATEV-Client-Id", equalTo(OAUTH_CLIENT))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Location", BASIS + "/import/jobs/JOB-1")));

        // Job-Status: sofort fertig.
        server.stubFor(get(urlPathEqualTo(BASIS + "/import/jobs/JOB-1"))
                .withHeader("X-DATEV-Client-Id", equalTo(OAUTH_CLIENT))
                .willReturn(okJson("{\"id\":\"JOB-1\",\"status\":\"completed\"}")));

        // D2 Belegbilder: PUT documents/{guid} (selbst erzeugte GUID) — nur mit Pflicht-Headern → 201.
        server.stubFor(put(urlPathMatching("/platform-sandbox/v2/clients/" + MANDANT + "/documents/.+"))
                .withHeader("Authorization", matching("Bearer .+"))
                .withHeader("X-DATEV-Client-Id", equalTo(OAUTH_CLIENT))
                .willReturn(aResponse().withStatus(201)));

        String url = server.baseUrl();
        return Map.of(
                "datev.modus", "cloud",
                "quarkus.rest-client.datev-token.url", url,
                "quarkus.rest-client.datev-extf.url", url,
                "quarkus.rest-client.datev-documents.url", url,
                "datev.cloud.client-id", OAUTH_CLIENT,
                "datev.cloud.client-secret", "test-secret",
                "datev.cloud.refresh-token", "test-refresh",
                "datev.cloud.mandant", MANDANT);
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
