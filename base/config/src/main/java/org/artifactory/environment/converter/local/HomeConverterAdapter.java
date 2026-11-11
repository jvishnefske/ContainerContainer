package org.artifactory.environment.converter.local;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ArtifactoryConverterAdapter;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.environment.converter.local.version.HomeConverterVersion;
import org.artifactory.version.CompoundVersionDetails;

import jakarta.annotation.Nullable;

/**
 * Converter adapter responsible for delegating to {@link HomeConverterVersion} for various home conversions.
 *
 * @author Uriah Levy
 */
public class HomeConverterAdapter implements ArtifactoryConverterAdapter {
    private ArtifactoryHome artifactoryHome;

    public HomeConverterAdapter(ArtifactoryHome artifactoryHome) {
        this.artifactoryHome = artifactoryHome;
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        HomeConverterVersion.convert(artifactoryHome, source, target);
    }

    @Override
    public boolean isInterested(@Nullable CompoundVersionDetails source, CompoundVersionDetails target) {
        return source != null && !source.isCurrent();
    }

    @Override
    public void revert() {
        // noop
    }

    @Override
    public void backup() {
        // noop
    }

    @Override
    public void clean() {
        // noop
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // noop
    }
}

