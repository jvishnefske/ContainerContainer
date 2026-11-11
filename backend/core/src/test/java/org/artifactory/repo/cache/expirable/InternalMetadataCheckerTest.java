package org.artifactory.repo.cache.expirable;

import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalMetadataCheckerTest {

    private final InternalMetadataChecker internalMetadataChecker = new InternalMetadataChecker();

    @ParameterizedTest
    @ValueSource(strings = {
            ".jfrog",
            ".jfrog/index",
            ".jfrog/org/artifact/1.0/maven-metadata.xml",
            ".jfrog//index"
    })
    void isInternalMetadata_shouldReturnTrue_whenPathStartsWithJfrog(String path) {
        // Act
        boolean result = InternalMetadataChecker.isInternalMetadata(path);

        // Assert
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            ".",
            "normal.file",
            "יוניקוד.xls",
            ".jfrogextra",
            ".jfrog-other/index",
            "/.jfrog/index",
            "repo/.jfrog/index"
    })
    void isInternalMetadata_shouldReturnFalse_whenPathDoesNotStartWithJfrog(String path) {
        // Act
        boolean result = InternalMetadataChecker.isInternalMetadata(path);

        // Assert
        assertFalse(result);
    }

    @Test
    void isInternalMetadata_shouldReturnFalse_whenPathIsNull() {
        // Act
        boolean result = InternalMetadataChecker.isInternalMetadata(null);

        // Assert
        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ".jfrog/maven-metadata.xml",
            ".jfrog/org/maven-metadata.xml",
            ".jfrog/org:maven-metadata.xml",
            ".jfrog/org/artifact/maven-metadata.xml",
            ".jfrog/org/artifact:maven-metadata.xml",
            ".jfrog/org/artifact/1.0/maven-metadata.xml",
            ".jfrog/org/artifact/1.0:maven-metadata.xml"
    })
    void getMetadataArtifactType_shouldReturnRepoListPackages_whenPathIsInternalMavenMetadata(String path) {
        // Act
        String result = internalMetadataChecker.getMetadataArtifactType(RepoType.Maven, "repo", path);

        // Assert
        assertEquals(RestApiGroup.PACKAGE_INFO, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "maven-metadata.xml",
            "org/maven-metadata.xml",
            "org/artifact/maven-metadata.xml",
            ".jfrog/another-metadata.xml",
            ".jfrog/org/another-metadata.xml",
            ".jfrog/bob.jar",
            ".jfrog/bob.pom",
            "normal/path/maven-metadata.xml",
            "another-metadata.xml",
            "bob.jar",
            "bob.pom"
    })
    void getMetadataArtifactType_shouldReturnNull_whenPathIsNotInternalMavenMetadata(String path) {
        // Act
        String result = internalMetadataChecker.getMetadataArtifactType(RepoType.Maven, "repo", path);

        // Assert
        assertNull(result);
    }

    @ParameterizedTest
    @CsvSource({
            "Maven,maven-repo",
            "Ivy,ivy-repo",
            "Gradle,gradle-repo",
            "Npm,npm-repo",
            "Docker,docker-repo"
    })
    void getMetadataArtifactType_shouldReturnRepoListPackages_whenRepoTypeIsAnyType(RepoType repoType, String repoKey) {
        // Act
        String result = internalMetadataChecker.getMetadataArtifactType(repoType, repoKey, ".jfrog/maven-metadata.xml");

        // Assert
        assertEquals(RestApiGroup.PACKAGE_INFO, result);
    }
}
