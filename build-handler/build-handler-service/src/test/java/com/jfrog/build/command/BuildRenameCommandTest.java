package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRenameService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildRenameCommandTest {

    private BuildRenameCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;
    private BuildRenameService renameService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        renameService = mock(BuildRenameService.class);
        permissionService = mock(BuildPermissionService.class);
        command = new BuildRenameCommand(repoService, readService, renameService, permissionService);
    }

    @Test
    void renameBuilds_whenValid_delegatesToRenameService() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(permissionService.canDeleteBuildName("some-from-name", "artifactory-build-info")).thenReturn(true);
        Comparator<BuildRun> order =
                Comparator.comparing(BuildRun::getNumber).thenComparing(BuildRun::getStarted);
        SortedSet<BuildRun> runs = new TreeSet<>(order);
        runs.add(new BuildRunImpl(
                "some-from-name",
                "some-build-number",
                "2024-01-01T00:00:00.000+0000",
                "artifactory-build-info"));
        when(readService.searchBuildsByName("some-from-name", "artifactory-build-info")).thenReturn(runs);

        command.renameBuilds(request, "some-from-name", "some-to-name", null, null);

        verify(renameService).renameBuildsAsync("some-from-name", "some-to-name", "artifactory-build-info");
    }

    @Test
    void renameBuilds_whenBlankFromName_throwsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");

        assertThrows(
                BadRequestException.class,
                () -> command.renameBuilds(request, "  ", "some-to-name", null, null));
    }

    @Test
    void renameBuilds_whenBlankNewName_throwsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");

        assertThrows(
                BadRequestException.class,
                () -> command.renameBuilds(request, "some-from-name", "  ", null, null));
    }

    @Test
    void renameBuilds_whenSameFromAndTo_throwsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");

        assertThrows(
                BadRequestException.class,
                () -> command.renameBuilds(request, "same", "same", null, null));
    }

    @Test
    void renameBuilds_whenCannotDeleteBuildName_throwsForbidden() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(permissionService.canDeleteBuildName("some-from-name", "artifactory-build-info")).thenReturn(false);

        assertThrows(
                ForbiddenException.class,
                () -> command.renameBuilds(request, "some-from-name", "some-to-name", null, null));
    }

    @Test
    void renameBuilds_whenNoBuilds_throwsNotFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, "phoenix")).thenReturn("phoenix-build-info");
        when(permissionService.canDeleteBuildName("missing-name", "phoenix-build-info")).thenReturn(true);
        Comparator<BuildRun> order =
                Comparator.comparing(BuildRun::getNumber).thenComparing(BuildRun::getStarted);
        when(readService.searchBuildsByName("missing-name", "phoenix-build-info")).thenReturn(new TreeSet<>(order));

        assertThrows(
                NotFoundException.class,
                () -> command.renameBuilds(request, "missing-name", "some-to-name", null, "phoenix"));
    }

    @Test
    void renameBuilds_whenLegacyBuildClient_decodesBuildNameBeforeLookup() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(USER_AGENT)).thenReturn("ArtifactoryBuildClient/2.0.10");
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(permissionService.canDeleteBuildName("a b", "artifactory-build-info")).thenReturn(true);
        Comparator<BuildRun> order =
                Comparator.comparing(BuildRun::getNumber).thenComparing(BuildRun::getStarted);
        SortedSet<BuildRun> runs = new TreeSet<>(order);
        runs.add(new BuildRunImpl("a b", "1", "2024-01-01T00:00:00.000+0000", "artifactory-build-info"));
        when(readService.searchBuildsByName(eq("a b"), eq("artifactory-build-info"))).thenReturn(runs);

        command.renameBuilds(request, "a%20b", "new-name", null, null);

        verify(readService).searchBuildsByName("a b", "artifactory-build-info");
        verify(renameService).renameBuildsAsync("a b", "new-name", "artifactory-build-info");
    }
}
