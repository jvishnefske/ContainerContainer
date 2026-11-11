/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.environment.converter.local.version;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.environment.converter.local.version.v3.V550UpgradePrerequisiteVerifier;
import org.artifactory.environment.converter.local.version.v7.V700PreInitReplaceLogbackConverter;
import org.artifactory.environment.converter.local.version.v7.V700UpgradePrerequisiteVerifier;
import org.artifactory.version.CompoundVersionDetails;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Local environment converters ALWAYS RUN - If you happen to write one make it smart so it knows if it should run!
 * This enum maps to the {@see ConverterType#PRE_INIT} map
 *
 * @author Dan Feldman
 */
public enum PreInitEnvironmentVersion {

    /**
     * Old versions are kept here for reference, upgrading to 7.x requires a minimal version 6.0.0 thus rendering these
     * converters obsolete.
     * The marker file based converters are a special case since they are resumable.
     */
    //v5.0.0
    /*v1(new NoNfsNewDbPropertiesConverter(),
            new NoNfsArtifactorySystemPropertiesConverter(),
            new NoNfsArtifactoryEncryptionKeysConverter()),
    //v5.4.0
    v2(new NoNfsArtifactoryPropertiesConverter()),*/
    //v.7.0.0
    v1(new V700UpgradePrerequisiteVerifier()), // have to verify version before running any other converter
    //v5.5.0
    v3(new V550UpgradePrerequisiteVerifier()),
    v7(new V700PreInitReplaceLogbackConverter());
    private final BasicEnvironmentConverter[] converters;

    /**
     * @param converters the converters to run in order to bring the environment to the expected state of this
     *                   environment version
     */
    PreInitEnvironmentVersion(BasicEnvironmentConverter... converters) {
        this.converters = converters;
    }

    public static void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source,
            CompoundVersionDetails target) {
        for (PreInitEnvironmentVersion environmentVersion : values()) {
            for (BasicEnvironmentConverter basicEnvironmentConverter : environmentVersion.converters) {
                if (basicEnvironmentConverter.isInterested(artifactoryHome, source, target)) {
                    basicEnvironmentConverter.convert(artifactoryHome, source, target);
                }
            }
        }
    }

    /**
     * Assert that the all the converters meet their preconditions before starting the conversions
     */
    public static void assertPreConditions(ArtifactoryHome artifactoryHome, CompoundVersionDetails source,
            CompoundVersionDetails target) {
        Arrays.stream(PreInitEnvironmentVersion.values())
                .flatMap(version -> Stream.of(version.converters))
                .filter(converter -> converter.isInterested(artifactoryHome, source, target))
                .forEach(converter -> converter.assertConversionPreconditions(artifactoryHome));
    }
}