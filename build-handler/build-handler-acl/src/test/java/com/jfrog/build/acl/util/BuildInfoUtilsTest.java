package com.jfrog.build.acl.util;

import com.jfrog.build.acl.model.BuildCoordinate;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.extractCoordinatesFromPath;
import static com.jfrog.build.acl.util.BuildInfoUtils.getAssociatedProjectKey;
import static com.jfrog.build.acl.util.BuildInfoUtils.getProjectKeyFromBuildInfoRepoKey;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildInfoUtilsTest {

    @Test
    void getAssociatedProjectKey_Success() {
        assertEquals("dummy-project", getAssociatedProjectKey("dummy-project-build-info"));
        assertEquals(StringUtils.EMPTY, getAssociatedProjectKey("invalid_build_info"));
        assertEquals(StringUtils.EMPTY, getAssociatedProjectKey(StringUtils.EMPTY));
    }

    @Test
    public void getProjectKeyFromBuildInfoRepoKey_Success() {
        assertEquals(DEFAULT_PROJECT, getProjectKeyFromBuildInfoRepoKey(BUILDINFO_REPO_KEY));
        assertEquals(DEFAULT_PROJECT, getProjectKeyFromBuildInfoRepoKey(BUILDINFO_REPO_KEY + "-" + 1));
        assertEquals(DEFAULT_PROJECT, getProjectKeyFromBuildInfoRepoKey(BUILDINFO_REPO_KEY + "-" + 2));
        assertEquals("project", getProjectKeyFromBuildInfoRepoKey("project-build-info"));
        assertEquals("project-build", getProjectKeyFromBuildInfoRepoKey("project-build"));
    }

    @Test
    void extractCoordinatesFromPath_Success() {
        BuildCoordinate coordinate = extractCoordinatesFromPath("abc-build/2-1592480449869.json", BUILDINFO_REPO_KEY);
        assertNotNull(coordinate);
        assertEquals("abc-build", coordinate.getBuildName());
        assertEquals("2", coordinate.getBuildNumber());
        assertEquals(1592480449869L, coordinate.getDate());
        coordinate = extractCoordinatesFromPath("artifactory-build-info:blabla.json", BUILDINFO_REPO_KEY);
        assertNull(coordinate);
    }
}