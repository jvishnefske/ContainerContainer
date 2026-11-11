package org.artifactory.environment.converter.local.version;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.environment.converter.local.version.v1.RemoveMissionControlPluginsConverter;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Uriah Levy
 */
public enum HomeConverterVersion {
    v1(new RemoveMissionControlPluginsConverter()),
    ;

    private static final Logger log = LoggerFactory.getLogger(HomeConverterVersion.class);

    private BasicEnvironmentConverter[] converters;

    HomeConverterVersion(BasicEnvironmentConverter... converters) {
        this.converters = converters;
    }

    public static void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source,
            CompoundVersionDetails target) {
        for (HomeConverterVersion homeConverterVersion : HomeConverterVersion.values()) {
            for (BasicEnvironmentConverter homeConverter : homeConverterVersion.converters) {
                if (homeConverter.isInterested(artifactoryHome, source, target)) {
                    log.debug("Running '{}' converter", homeConverter.getClass().getSimpleName());
                    homeConverter.convert(artifactoryHome, source, target);
                    log.debug("Finished converter '{}'", homeConverter.getClass().getSimpleName());
                }
            }
        }
    }
}
