package org.artifactory.api.artifact;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * @author Shashank Taliwal
 */
public class DebianSourceArtifactInfoTest extends ArtifactoryHomeBoundJUnitTest {
    private final DebianSourceArtifactInfo debianSourceArtifactInfo = new DebianSourceArtifactInfo("test/path");

    @Test
    void testIsMavenArtifact() {
        Assertions.assertFalse(debianSourceArtifactInfo.isMavenArtifact());
    }

    @Test
    void testIsValid() {
        Assertions.assertTrue(debianSourceArtifactInfo.isValid());
    }

    @Test
    void testSetPath() {
        debianSourceArtifactInfo.setPath("new/test/path");
        Assertions.assertEquals("new/test/path", debianSourceArtifactInfo.getPath());
    }

    @Test
    void testGetArtifactType() {
        Assertions.assertEquals("debianSource", debianSourceArtifactInfo.getArtifactType());
    }
}
