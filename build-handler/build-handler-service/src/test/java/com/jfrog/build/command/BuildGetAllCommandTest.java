package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.model.api.Builds;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.jfrog.build.service.BuildUrlService.getBuildRelativeHref;
import static com.jfrog.build.service.BuildUrlService.getBuildRepoRelativeHref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetAllCommandTest {

    private BuildGetAllCommand command;

    private BuildUrlService urlService;
    private BuildRepoService repoService;
    private BuildDatabaseService databaseService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        urlService = mock(BuildUrlService.class);
        repoService = mock(BuildRepoService.class);
        databaseService = mock(BuildDatabaseService.class);
        permissionService = mock(BuildPermissionService.class);
        command = new BuildGetAllCommand(urlService, repoService, databaseService, permissionService);
    }

    @Test
    void getAllBuilds_resolvesRepoAndPopulatesReadableBuilds() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(urlService.getBaseBuildsHref(request)).thenReturn("https://host/artifactory/api/build");
        BuildRunImpl buildRun = new BuildRunImpl(
                "some-build-name",
                "some-build-number",
                "2024-01-01T00:00:00.000+0000");
        when(databaseService.getLatestBuildsByName("artifactory-build-info")).thenReturn(Set.of(buildRun));
        when(permissionService.isBuildBasicRead(buildRun)).thenReturn(true);

        Builds result = command.getAllBuilds(request, null, null);

        assertEquals(
                "https://host/artifactory/api/build" + getBuildRepoRelativeHref("artifactory-build-info"),
                result.slf);
        assertEquals(1, result.builds.size());
        assertEquals(getBuildRelativeHref("some-build-name"), result.builds.getFirst().uri);
        assertEquals(buildRun.getStarted(), result.builds.getFirst().lastStarted);
        verify(repoService).getPreferredBuildRepo(null, null);
    }

    @Test
    void getAllBuilds_whenNoReadableBuilds_throwsNotFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(any(), any())).thenReturn("artifactory-build-info");
        when(urlService.getBaseBuildsHref(request)).thenReturn("https://host/api/build");
        BuildRunImpl buildRun = new BuildRunImpl(
                "some-unreadable-build-name",
                "some-build-number",
                "2024-01-01T00:00:00.000+0000");
        when(databaseService.getLatestBuildsByName("artifactory-build-info")).thenReturn(Set.of(buildRun));
        when(permissionService.isBuildBasicRead(buildRun)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> command.getAllBuilds(request, null, null));
    }

    @Test
    void getAllBuilds_passesQueryParamsToPreferredRepo() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(eq("explicit-build-repo-query"), eq("phoenix")))
                .thenReturn("phoenix-build-info");
        when(urlService.getBaseBuildsHref(request)).thenReturn("https://host/api/build");
        BuildRunImpl buildRun = new BuildRunImpl(
                "some-build-label",
                "some-build-number",
                "2024-01-01T00:00:00.000+0000");
        when(databaseService.getLatestBuildsByName("phoenix-build-info")).thenReturn(Set.of(buildRun));
        when(permissionService.isBuildBasicRead(buildRun)).thenReturn(true);

        command.getAllBuilds(request, "explicit-build-repo-query", "phoenix");

        verify(repoService).getPreferredBuildRepo("explicit-build-repo-query", "phoenix");
    }
}
