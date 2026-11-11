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

package org.artifactory.storage.db.environment.converter.shared.version;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.mime.version.converter.MimeTypeConverter;
import org.artifactory.storage.db.environment.converter.shared.version.v2.ConfigurationManagerCleanupConverter;
import org.artifactory.version.CompoundVersionDetails;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @author Gidi Shabat
 */
public enum SharedEnvironmentVersion {

    v1(new MimeTypeConverter()),
    v2(new ConfigurationManagerCleanupConverter()),
    ;

    private final BasicEnvironmentConverter[] converters;

    SharedEnvironmentVersion(BasicEnvironmentConverter... converter) {
        this.converters = converter;
    }

    public static void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source,
            CompoundVersionDetails target) {
        for (SharedEnvironmentVersion environmentVersion : values()) {
            for (BasicEnvironmentConverter basicEnvironmentConverter : environmentVersion.converters) {
                if (basicEnvironmentConverter.isInterested(artifactoryHome, source, target)) {
                    basicEnvironmentConverter.convert(artifactoryHome, source, target);
                }
            }
        }
    }

    /**
     * Assert that all the post init converters that should run have all their preconditions met before running them
     */
    public static void assertPreConditions(ArtifactoryHome artifactoryHome, CompoundVersionDetails source,
            CompoundVersionDetails target) {
        Arrays.stream(SharedEnvironmentVersion.values())
                .flatMap(version -> Stream.of(version.converters))
                .filter(converter -> converter.isInterested(artifactoryHome, source, target))
                .forEach(converter -> converter.assertConversionPreconditions(artifactoryHome));
    }
}
