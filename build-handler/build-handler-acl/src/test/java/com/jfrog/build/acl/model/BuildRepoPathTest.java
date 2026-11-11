package com.jfrog.build.acl.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildRepoPathTest {

    @Test
    void getId_shouldReturnRepoKeyAndPathSeparatedBySlash_whenPathNotEmpty() {
        BuildRepoPath buildRepoPath = new BuildRepoPath("my-repo", "path/to/artifact");
        assertEquals("my-repo/path/to/artifact", buildRepoPath.toString());
    }

    @Test
    void getId_shouldReturnRepoKeyWithTrailingSlash_whenPathEmpty() {
        BuildRepoPath buildRepoPath = new BuildRepoPath("my-repo", "");
        assertEquals("my-repo/", buildRepoPath.toString());
    }

    @Test
    void toPath_shouldReturnSameAsGetId() {
        BuildRepoPath buildRepoPath = new BuildRepoPath("my-repo", "foo/bar");
        assertEquals(buildRepoPath.toString(), buildRepoPath.toString());
    }

    @Test
    void toString_shouldReturnSameAsGetId() {
        BuildRepoPath buildRepoPath = new BuildRepoPath("my-repo", "foo");
        assertEquals(buildRepoPath.toString(), buildRepoPath.toString());
    }

    @ParameterizedTest
    @CsvSource({
            "path, path",
            "'  path  ', path",
            "'/path', path",
            "'path/', path",
            "'/path/', path",
            "'//path//', path"
    })
    void constructor_shouldTrimAndNormalizePath_whenGivenPath(String path, String expectedPathSegment) {
        BuildRepoPath buildRepoPath = new BuildRepoPath("repo", path);
        assertEquals("repo", buildRepoPath.getRepoKey());
        assertEquals(expectedPathSegment, buildRepoPath.getRelativePath());
    }

    @Test
    void constructor_shouldTrimRepoKey_whenRepoKeyHasWhitespace() {
        BuildRepoPath buildRepoPath = new BuildRepoPath("  my-repo  ", "path");
        assertEquals("my-repo", buildRepoPath.getRepoKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void constructor_shouldThrow_whenRepoKeyBlank(String repoKey) {
        Executable target = () -> new BuildRepoPath(repoKey, "path");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, target);
        assertEquals("Repository key cannot be empty", thrown.getMessage());
    }
}
