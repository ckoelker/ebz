package de.netzfactor.ebz.controlling.integration.rechnung.datev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.common.annotation.Identifier;

/**
 * D1: Der reale {@code cloud}-Weg (DATEV-Buchungsdatenservice) gegen den WireMock-Stub. Belegt den ganzen
 * Pfad ohne DB: Access-Token holen (Refresh-Grant) → EXTF hochladen mit Pflicht-Header
 * {@code X-DATEV-Client-Id} → Job pollen → Protokoll mit Job-Referenz. Der Import-Stub matcht nur mit den
 * Pflicht-Headern, d. h. fehlten sie, würde der Test rot. {@code @WithTestResource} ist per-Klasse
 * restriktiv (Default), hält also die {@code datev.modus=cloud}-Overrides von den übrigen Tests
 * (z. B. {@code DatevExportTest}, Default {@code extf}) fern.
 */
@QuarkusTest
@WithTestResource(DatevWireMockResource.class)
class DatevCloudUebergabeTest {

    @Inject
    @Identifier("cloud")
    DatevUebergabe cloud;

    @Test
    void uebergabe_laedtHoch_pollt_undGibtJobReferenz() {
        List<Buchungssatz> saetze = List.of(
                new Buchungssatz(280000L, "S", "10001", "8120", "", LocalDate.now(), "RE-CLOUD-1", "Rechnung RE-CLOUD-1"));
        ExtfBuchungsstapel.Kopf kopf = new ExtfBuchungsstapel.Kopf("1001", "1", "20260101", 4,
                LocalDate.now(), LocalDate.now(), "Buchungsstapel SKR03");

        DatevUebergabe.Protokoll p = cloud.uebergeben(saetze, kopf);

        assertEquals("CLOUD", p.modus(), "Cloud-Weg aktiv");
        assertEquals("JOB-1", p.referenz(), "Job-Referenz aus dem Location-Header");
        assertEquals(1, p.anzahlBuchungen());
        assertTrue(p.hinweis().contains("completed"), "Job-Status im Protokoll: " + p.hinweis());
    }
}
