package org.artifactory.api.artifact;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class AlpineArtifactInfoTest {
    @Test
    void testArtifactType() {
        AlpineArtifactInfo artifactInfo = new AlpineArtifactInfo("test/path");
        Assertions.assertEquals("alpine", artifactInfo.getArtifactType());
    }

    @Test
    void testisMavenArtifact() {
        AlpineArtifactInfo artifactInfo = new AlpineArtifactInfo("test/path");
        Assertions.assertFalse(artifactInfo.isMavenArtifact());
    }

    @Test
    void testIsValid() {
        AlpineArtifactInfo artifactInfo = new AlpineArtifactInfo("test/path");
        Assertions.assertTrue(artifactInfo.isValid());
    }
}