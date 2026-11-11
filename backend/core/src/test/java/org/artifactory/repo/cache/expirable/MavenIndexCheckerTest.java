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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Noam Y. Tenne
 */
class MavenIndexCheckerTest {

    private final MavenIndexChecker mavenIndexChecker = new MavenIndexChecker();

    @ParameterizedTest
    @ValueSource(strings = {
            "nexus-maven-repository-index",
            "nexus-maven-repository-index.tar",
            "nexus-maven-repository-index.gz",
            "nexus-maven-repository-index.zip",
            "nexus-maven-repository-index.properties",
            "org/nexus-maven-repository-index",
            "org/nexus-maven-repository-index.gz",
            "org/bob/1.0/nexus-maven-repository-index.zip"
    })
    void getMetadataArtifactType_shouldReturnRepoListPackages_whenPathIsIndexFile(String path) {
        // Act
        String result = mavenIndexChecker.getMetadataArtifactType(RepoType.Maven, "repo", path);

        // Assert
        assertEquals(RestApiGroup.PACKAGE_INFO, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "bob.zip",
            "bob.gz",
            "org/bob.zip",
            "org/artifact/bob.gz",
            "org/artifact/1.0/bob.zip",
            "org/artifact/1.0/artifact-1.0.jar"
    })
    void getMetadataArtifactType_shouldReturnNull_whenPathIsNotIndexFile(String path) {
        // Act
        String result = mavenIndexChecker.getMetadataArtifactType(RepoType.Maven, "repo", path);

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
        String result =
                mavenIndexChecker.getMetadataArtifactType(repoType, repoKey,"nexus-maven-repository-index.gz");

        // Assert
        assertEquals(RestApiGroup.PACKAGE_INFO, result);
    }

    @ParameterizedTest
    @CsvSource({
            "nexus-maven-repository-index, true",
            "nexus-maven-repository-index.tar, true",
            "nexus-maven-repository-index.gz, true",
            "nexus-maven-repository-index.zip, true",
            "nexus-maven-repository-index.bob, true",
            "nexus-maven-repository-index.properties, true",
            "org/nexus-maven-repository-index, true",
            "org/nexus-maven-repository-index.tar, true",
            "org/nexus-maven-repository-index.gz, true",
            "org/nexus-maven-repository-index.zip, true",
            "org/nexus-maven-repository-index.bob, true",
            "org/nexus-maven-repository-index.properties, true",
            "org/artifact/nexus-maven-repository-index, true",
            "org/artifact/nexus-maven-repository-index.tar, true",
            "org/artifact/nexus-maven-repository-index.gz, true",
            "org/artifact/nexus-maven-repository-index.zip, true",
            "org/artifact/nexus-maven-repository-index.bob, true",
            "org/artifact/nexus-maven-repository-index.properties, true",

            "bob.zip, false",
            "bob.gz, false",
            "org/bob.zip, false",
            "org/bob.gz, false",
            "org/artifact/bob.zip, false",
            "org/artifact/bob.gz, false",
            "org/artifact/1.0/bob.zip, false",
            "org/artifact/1.0/bob.gz, false",
    })
    void testIsExpirable(String path, boolean expected) {
        boolean actual = mavenIndexChecker.isExpirable(RepoType.Maven, "repo", path);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvSource({
            "nexus-maven-repository-index, true",
            "nexus-maven-repository-index.tar, true",
            "nexus-maven-repository-index.gz, true",
            "nexus-maven-repository-index.zip, true",
            "nexus-maven-repository-index.bob, true",
            "nexus-maven-repository-index.properties, true",
            "org/nexus-maven-repository-index, true",
            "org/nexus-maven-repository-index.tar, true",
            "org/nexus-maven-repository-index.gz, true",
            "org/nexus-maven-repository-index.zip, true",
            "org/nexus-maven-repository-index.bob, true",
            "org/nexus-maven-repository-index.properties, true",
            "org/artifact/nexus-maven-repository-index, true",
            "org/artifact/nexus-maven-repository-index.tar, true",
            "org/artifact/nexus-maven-repository-index.gz, true",
            "org/artifact/nexus-maven-repository-index.zip, true",
            "org/artifact/nexus-maven-repository-index.bob, true",
            "org/artifact/nexus-maven-repository-index.properties, true",

            "bob.zip, false",
            "bob.gz, false",
            "org/bob.zip, false",
            "org/bob.gz, false",
            "org/artifact/bob.zip, false",
            "org/artifact/bob.gz, false",
            "org/artifact/1.0/bob.zip, false",
            "org/artifact/1.0/bob.gz, false",
    })
    void testIsLocalGenerated(String path, boolean expected) {
        boolean actual = mavenIndexChecker.isLocalGenerated(RepoType.Maven, "repo", path);
        assertEquals(expected, actual);
    }

    @Test
    void testGetTypes() {
        Set<RepoType> expected = Set.of(RepoType.Maven, RepoType.Gradle, RepoType.Ivy, RepoType.P2, RepoType.SBT);
        RepoType[] actual = mavenIndexChecker.getTypes();
        assertEquals(expected, Set.of(actual));
    }
}
