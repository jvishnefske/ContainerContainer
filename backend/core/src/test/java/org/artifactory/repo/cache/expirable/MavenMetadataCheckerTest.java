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

package org.artifactory.repo.cache.expirable;

import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static com.jfrog.build.acl.util.BuildConstants.PROP_BUILD_NAME;
import static com.jfrog.build.acl.util.BuildConstants.PROP_BUILD_NUMBER;
import static com.jfrog.build.acl.util.BuildConstants.PROP_BUILD_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Noam Y. Tenne
 */
class MavenMetadataCheckerTest {

    private final MavenMetadataChecker mavenMetadataChecker = new MavenMetadataChecker();

    @Test
    void testIsExpirable() {
        expectExpirable("maven-metadata.xml");
        expectExpirable("org/maven-metadata.xml");
        expectExpirable("org:maven-metadata.xml");
        expectExpirable("org/artifact/maven-metadata.xml");
        expectExpirable("org/artifact:maven-metadata.xml");
        expectExpirable("org/artifact/1.0/maven-metadata.xml");
        expectExpirable("org/artifact/1.0:maven-metadata.xml");

        expectUnExpirable("another-metadata.xml");
        expectUnExpirable("org/another-metadata.xml");
        expectUnExpirable("org:another-metadata.xml");
        expectUnExpirable("org/artifact/another-metadata.xml");
        expectUnExpirable("org/artifact:another-metadata.xml");
        expectUnExpirable("org/artifact/1.0/another-metadata.xml");
        expectUnExpirable("org/artifact/1.0:another-metadata.xml");

        expectUnExpirable("bob.jar");
        expectUnExpirable("bob.pom");
        expectUnExpirable("org/bob.jar");
        expectUnExpirable("org/bob.pom");
        expectUnExpirable("org/artifact/bob.jar");
        expectUnExpirable("org/artifact/bob.pom");
        expectUnExpirable("org/artifact/1.0/bob.jar");
        expectUnExpirable("org/artifact/1.0/bob.pom");
    }


    private void expectExpirable(String artifactPath) {
        assertTrue(mavenMetadataChecker.isExpirable(RepoType.Maven, "repo", artifactPath),
                artifactPath + " Should be expirable.");
    }

    private void expectUnExpirable(String artifactPath) {
        assertFalse(mavenMetadataChecker.isExpirable(RepoType.Maven, "repo", artifactPath),
                artifactPath + " Shouldn't be expirable.");
    }

    @Test
    void testShouldExcludePropertyFromMirroring() {
        MavenIndexChecker mavenIndexChecker = new MavenIndexChecker();
        assertFalse(mavenIndexChecker.shouldExcludePropertyFromMirroring(RepoType.Maven, PROP_BUILD_NAME));
        assertFalse(mavenIndexChecker.shouldExcludePropertyFromMirroring(RepoType.Maven, PROP_BUILD_NUMBER));
        assertFalse(mavenIndexChecker.shouldExcludePropertyFromMirroring(RepoType.Maven, PROP_BUILD_TIMESTAMP));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "maven-metadata.xml",
            "org/maven-metadata.xml",
            "org:maven-metadata.xml",
            "org/artifact/maven-metadata.xml",
            "org/artifact:maven-metadata.xml",
            "org/artifact/1.0/maven-metadata.xml",
            "org/artifact/1.0:maven-metadata.xml"
    })
    void getMetadataArtifactType_shouldReturnRepoListPackages_whenPathIsMavenMetadata(String path) {
        // Act
        String result = mavenMetadataChecker.getMetadataArtifactType(RepoType.Maven, "repo", path);

        // Assert
        assertEquals(RestApiGroup.PACKAGE_INFO, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "another-metadata.xml",
            "org/another-metadata.xml",
            "org:another-metadata.xml",
            "org/artifact/another-metadata.xml",
            "org/artifact:another-metadata.xml",
            "org/artifact/1.0/another-metadata.xml",
            "org/artifact/1.0:another-metadata.xml",
            "bob.jar",
            "bob.pom",
            "org/bob.jar",
            "org/bob.pom",
            "org/artifact/bob.jar",
            "org/artifact/bob.pom",
            "org/artifact/1.0/bob.jar",
            "org/artifact/1.0/bob.pom"
    })
    void getMetadataArtifactType_shouldReturnNull_whenPathIsNotMavenMetadata(String path) {
        // Act
        String result = mavenMetadataChecker.getMetadataArtifactType(RepoType.Maven, "repo", path);

        // Assert
        assertNull(result);
    }

    @ParameterizedTest
    @CsvSource({
            "Maven,maven-repo",
            "Ivy,ivy-repo",
            "Gradle,gradle-repo",
            "P2,p2-repo",
            "SBT,sbt-repo"
    })
    void getMetadataArtifactType_shouldReturnRepoIndex_whenRepoTypeIsSupported(RepoType repoType, String repoKey) {
        // Act
        String result = mavenMetadataChecker.getMetadataArtifactType(repoType, repoKey, "maven-metadata.xml");

        // Assert
        assertEquals(RestApiGroup.PACKAGE_INFO, result);
    }

    @Test
    void testGetTypes() {
        Set<RepoType> expected = Set.of(RepoType.Maven, RepoType.Gradle, RepoType.Ivy, RepoType.P2, RepoType.SBT);
        RepoType[] actual = mavenMetadataChecker.getTypes();
        assertEquals(expected, Set.of(actual));
    }
}
