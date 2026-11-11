package org.artifactory.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactoryServiceVersionProviderTest {

    @Test
    void testGetRevisionFromVersionMinorLowerThan100() {
        assertEquals(99999900, ArtifactoryServiceVersionProvider.getRevisionFromVersion("9.99.99"));
    }

    @Test
    void testGetRevisionFromVersionMinorHigherThan100() {
        assertEquals(999999900, ArtifactoryServiceVersionProvider.getRevisionFromVersion("9.999.99"));
    }

    @Test
    void testGetRevisionFromVersionMinorHigherThan1000ExceptionThrown() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ArtifactoryServiceVersionProvider.getRevisionFromVersion("9.9999.99"));
        assertEquals("Unable to parse version 9.9999.99", exception.getMessage());
    }

    // --- fixBuggyRevision tests ---

    /**
     * Buggy CI script overflows minor-version hundreds digit into the major position when minor >= 100.
     * Formula: major*10^7 + minor*10^5 + patch*10^3 + suffix  (should be major*10^8).
     * The fix recomputes the correct revision from the version string.
     */
    @ParameterizedTest(name = "version={0}, buggyRevision={1} => correctedRevision={2}")
    @CsvSource({
            // version,    buggy revision, expected corrected revision
            "7.112.2,      81202900,       711202900",   // carry 1: minor 12 -> 112, major 7+1=8 in buggy
            "7.141.2,      84102900,       714102900",   // carry 1: minor 41 -> 141, major 7+1=8 in buggy
            "7.134.3,      83403900,       713403900",   // carry 1: minor 34 -> 134
            "7.100.0,      80000900,       710000900",   // edge: minor exactly 100
    })
    void testFixBuggyRevision_appliesFix(String version, String buggyRevision, long expectedRevision) {
        assertEquals(expectedRevision, ArtifactoryServiceVersionProvider.fixBuggyRevision(version, buggyRevision));
    }

    @ParameterizedTest(name = "version={0}, revision={1} => unchanged")
    @CsvSource({
            // Correct revisions — must not be modified
            "7.45.2,   74502900,   74502900",    // minor < 100: no bug possible
            "7.99.1,   70990100,   70990100",    // minor < 100 (boundary)
            "8.12.2,   81202900,   81202900",    // first digit of revision matches major 8
            "7.139.0,  713900900,  713900900",   // correct 9-digit revision, first digit matches
            "7.141.0,  714100900,  714100900",   // correct 9-digit revision for 3-digit minor
    })
    void testFixBuggyRevision_noFixNeeded(String version, String revision, long expectedRevision) {
        assertEquals(expectedRevision, ArtifactoryServiceVersionProvider.fixBuggyRevision(version, revision));
    }

    /**
     * End-to-end: a remote running 7.141.2 with a buggy revision must NOT be considered
     * "before" the federation minimum version 7.139.0.
     */
    @Test
    void testFindArtifactoryVersion_buggyRevisionPassesFederationCheck() {
        ArtifactoryServiceVersion remoteVersion =
                ArtifactoryServiceVersionProvider.findArtifactoryVersion("7.141.2", "84102900");
        assertFalse(remoteVersion.before(ArtifactoryServiceVersionProvider.v71390.get()),
                "7.141.2 with buggy revision should NOT be before 7.139.0 after fix");
    }

    /**
     * End-to-end: a remote running 7.112.2 (genuinely too old) must still be rejected
     * even with a buggy revision.
     */
    @Test
    void testFindArtifactoryVersion_buggyRevisionTooOldVersionStillRejected() {
        ArtifactoryServiceVersion remoteVersion =
                ArtifactoryServiceVersionProvider.findArtifactoryVersion("7.112.2", "81202900");
        assertTrue(remoteVersion.before(ArtifactoryServiceVersionProvider.v71390.get()));
    }
}