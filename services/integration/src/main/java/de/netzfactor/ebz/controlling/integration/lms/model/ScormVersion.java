package de.netzfactor.ebz.controlling.integration.lms.model;

/**
 * SCORM-Version eines WBT-Pakets. Entscheidet bei der Lemon-Migration (L5b) über die Ziel-Plattform
 * (OpenOLAT spielt SCORM 1.2; SCORM-2004-only-Kurse → Open-edX-Fallback). Seed-Kurse sind 1.2.
 */
public enum ScormVersion {
    SCORM_12, SCORM_2004
}
