package org.artifactory.common.home;

import java.io.File;

/**
 * An {@link ArtifactoryHome} class for test usage
 *
 * @author Dan Feldman
 */
public class TestArtifactoryHome extends ArtifactoryHome {

    public TestArtifactoryHome(File varDir) {
        super(varDir.getAbsolutePath());
    }

    @Override
    public void initArtifactorySystemProperties() {
        super.initArtifactorySystemPropertiesInternal();
    }
}
