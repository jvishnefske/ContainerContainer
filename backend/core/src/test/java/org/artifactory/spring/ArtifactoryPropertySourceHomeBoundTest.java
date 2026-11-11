package org.artifactory.spring;

import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ArtifactoryPropertySourceHomeBoundTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void getProperty() {
        ArtifactoryHomeStub home = this.getOrCreateArtifactoryHomeStub();
        home.setProperty(ConstantValues.artifactoryProductVersion.getPropertyName(), null);
        ArtifactoryPropertySource propSource = new ArtifactoryPropertySource("artifactory", home);
        Assertions.assertEquals(null,
                propSource.getProperty(ConstantValues.artifactoryProductVersion.getPropertyName()));
    }
}
