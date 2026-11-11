package org.artifactory.rest.common;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Natalie Berni
 */
public class ShadowPropertyTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void testShadowIsDisabledToAllPackagesByDefault() {
        for (RepoType repoType : RepoType.values()) {
            Assertions.assertFalse(
                    homeStub.getArtifactoryProperties().getBooleanValue("shadowing." + repoType.getType(), false),
                    "shadow for package " + repoType.getType() + " should be disabled by default");
        }
    }
}
