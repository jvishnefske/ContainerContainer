package org.artifactory.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartRepoUrlUtilsTest {

    @Test
    void isSmartRepoUrlRequired_detectsArtifactoryPathAndPrefix() {
        String artifactoryUrl = "https://example.com/artifactory/my-repo";
        assertTrue(SmartRepoUrlUtils.isSmartRepoUrlRequired(artifactoryUrl, "maven"));

        // prefix null -> not required
        assertFalse(SmartRepoUrlUtils.isSmartRepoUrlRequired(artifactoryUrl, null));

        String other = "https://example.com/some/path/repo";
        assertFalse(SmartRepoUrlUtils.isSmartRepoUrlRequired(other, "maven"));
    }

    @Test
    void buildSmartRepoUrl_returnsOriginalForInvalidUrl() {
        String invalid = "https://%";
        String result = SmartRepoUrlUtils.buildSmartRepoUrl(invalid, "npm");
        assertNull(result, "Invalid URL should return null");
    }

    @Test
    void buildSmartRepoUrl_buildsExpectedSmartUrl() {
        String remote = "https://example.com/artifactory/my-repo";
        String smart = SmartRepoUrlUtils.buildSmartRepoUrl(remote, "npm");
        assertEquals("https://example.com/artifactory/api/npm/my-repo", smart);
    }

    @Test
    void buildSmartRepoUrl_returnsOriginalWhenAlreadySmartUrl() {
        String remoteSmart = "https://example.com/artifactory/api/npm/my-repo";
        String result = SmartRepoUrlUtils.buildSmartRepoUrl(remoteSmart, "npm");
        assertEquals(remoteSmart, result);
    }

    @Test
    void buildSmartRepoUrl_handlesTrailingSlashInRemote() {
        String remoteWithSlash = "https://example.com/artifactory/my-repo/";
        String smart = SmartRepoUrlUtils.buildSmartRepoUrl(remoteWithSlash, "npm");
        assertEquals("https://example.com/artifactory/api/npm/my-repo", smart);
    }

    @Test
    void buildSmartRepoUrl_returnsOriginalWhenNoPrefix() {
        String remote = "https://example.com/artifactory/my-repo";
        String result = SmartRepoUrlUtils.buildSmartRepoUrl(remote, null);
        assertEquals(remote, result, "No prefix should return original");
    }

    @Test
    void buildSmartRepoUrl_returnsOriginalWhenNotArtifactoryUrl() {
        String remote = "https://example.com/some/path/my-repo";
        String result = SmartRepoUrlUtils.buildSmartRepoUrl(remote, "npm");
        assertEquals(remote, result, "Non-Artifactory URL should return as is");
    }

    @Test
    void isSmartRepoUrlRequired_emptyPrefix_isAllowedAndReturnsTrue() {
        String artifactoryUrl = "https://example.com/artifactory/my-repo";
        // empty string is not null, so requirement should be true
        assertTrue(SmartRepoUrlUtils.isSmartRepoUrlRequired(artifactoryUrl, ""));
    }

    @Test
    void isSmartRepoUrlRequired_shouldReturnFalse_whenUrlIsInvalid() {
        String invalidUrl = "https://%";
        // Invalid URI should be logged and return false
        assertFalse(SmartRepoUrlUtils.isSmartRepoUrlRequired(invalidUrl, "npm"));
    }

    @Test
    void isSmartRepoUrlRequired_shouldReturnFalse_whenUrlIsNull() {
        assertFalse(SmartRepoUrlUtils.isSmartRepoUrlRequired(null, "npm"));
    }

    @Test
    void buildSmartRepoUrl_queryAndFragment_keepsOriginalUrl() {
        String remoteUrl = "https://example.com/artifactory/my-repo?param=1#section";
        String smart = SmartRepoUrlUtils.buildSmartRepoUrl(remoteUrl, "npm");
        assertEquals(remoteUrl, smart);
    }

    @Test
    void buildSmartRepoUrl_emptyPrefixProducesDoubleSlash() {
        String remote = "https://example.com/artifactory/my-repo";
        String result = SmartRepoUrlUtils.buildSmartRepoUrl(remote, "");
        // an empty prefix results in the api path having an empty segment
        assertEquals("https://example.com/artifactory/api//my-repo", result);
    }

    @Test
    void matchesStandardUrlFormat_shouldReturnTrue_whenUrlMatchesStandardFormat() {
        assertTrue(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "https://example.com/artifactory/my-repo"));
        assertTrue(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "http://localhost:8081/artifactory/my-repo"));
        assertTrue(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "https://artifactory.example.com/artifactory/repo-key"));
    }

    @Test
    void matchesStandardUrlFormat_shouldReturnFalse_whenUrlHasQueryParametersOrFragment() {
        assertFalse(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "https://example.com/artifactory/my-repo?param=value"));
        assertFalse(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "http://localhost:8081/artifactory/repo?query=test#fragment"));
        assertFalse(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "https://example.com/artifactory/my-repo#fragment"));
    }

    @Test
    void matchesStandardUrlFormat_shouldReturnFalse_whenUrlDoesNotMatchStandardFormat() {
        assertFalse(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "https://example.com/artifactory/npm/my-repo"));
        assertFalse(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "https://example.com/some/path/repo"));
        assertFalse(SmartRepoUrlUtils.matchesStandardUrlFormat(
                "https://example.com/artifactory/"));
        assertFalse(SmartRepoUrlUtils.matchesStandardUrlFormat(null));
    }

    @Test
    void matchesSmartUrlFormat_shouldReturnTrue_whenUrlMatchesSmartFormat() {
        assertTrue(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/artifactory/api/npm/my-repo", "npm"));
        assertTrue(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "http://localhost:8081/artifactory/api/maven/repo-key", "maven"));
        assertTrue(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://artifactory.example.com/artifactory/api/docker/v2", "docker"));
    }

    @Test
    void matchesSmartUrlFormat_shouldReturnFalse_whenUrlHasQueryParametersOrFragment() {
        assertFalse(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/artifactory/api/npm/my-repo?version=1.0.0", "npm"));
        assertFalse(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "http://localhost:8081/artifactory/api/maven/repo?param=value#fragment", "maven"));
        assertFalse(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/artifactory/api/npm/my-repo#fragment", "npm"));
    }

    @Test
    void matchesSmartUrlFormat_shouldReturnFalse_whenUrlDoesNotMatchSmartFormat() {
        assertFalse(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/artifactory/my-repo", "npm"));
        assertFalse(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/artifactory/api/wrong-prefix/package", "npm"));
        assertFalse(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/some/path/repo", "maven"));
        assertFalse(SmartRepoUrlUtils.matchesSmartUrlFormat(null, "npm"));
        assertFalse(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/artifactory/api/npm/package", null));
    }

    @Test
    void matchesSmartUrlFormat_shouldHandleSpecialCharactersInPrefix() {
        assertTrue(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/artifactory/api/npm+/my-repo", "npm+"));
        assertTrue(SmartRepoUrlUtils.matchesSmartUrlFormat(
                "https://example.com/artifactory/api/maven.central/repo", "maven.central"));
    }
}