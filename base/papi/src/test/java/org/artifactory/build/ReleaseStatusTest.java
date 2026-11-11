package org.artifactory.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReleaseStatusTest {

    @Test
    void getters_shouldReturnValuesFromConstructor_whenGivenAllParameters() {
        ReleaseStatus status = new ReleaseStatus(
                "released",
                "Promoted to release",
                "release-repo",
                "ci-user",
                "artifactory-user"
        );
        assertEquals("released", status.getStatus());
        assertEquals("Promoted to release", status.getComment());
        assertEquals("release-repo", status.getRepository());
        assertEquals("ci-user", status.getCiUser());
        assertEquals("artifactory-user", status.getArtifactoryUser());
        assertNotNull(status.getTimestamp());
        assertNotNull(status.getTimestampDate());
    }

    @Test
    void getters_shouldReturnNullForOptionalFields_whenGivenOnlyStatus() {
        ReleaseStatus status = new ReleaseStatus("staged", null, null, null, null);

        assertEquals("staged", status.getStatus());
        assertNull(status.getComment());
        assertNull(status.getRepository());
        assertNull(status.getCiUser());
        assertNull(status.getArtifactoryUser());
        assertNotNull(status.getTimestamp());
        assertNotNull(status.getTimestampDate());
    }
}
