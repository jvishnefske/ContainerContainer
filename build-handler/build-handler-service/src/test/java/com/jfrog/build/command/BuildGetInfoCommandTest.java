package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.BuildInfo;
import com.jfrog.build.model.api.diff.BuildsDiff;
import com.jfrog.build.service.BuildDiffService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.Build;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetInfoCommandTest {

    private BuildGetInfoCommand command;

    private BuildUrlService urlService;
    private BuildRepoService repoService;
    private BuildReadService readService;
    private BuildDiffService diffService;

    @BeforeEach
    void setUp() {
        urlService = mock(BuildUrlService.class);
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        diffService = mock(BuildDiffService.class);
        command = new BuildGetInfoCommand(urlService, repoService, readService, diffService);
    }

    @Test
    void getBuildInfo_returnsBuildInfoWithSlf() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        Build build = new Build();
        build.setName("some-build-name");
        build.setNumber("some-build-number");
        build.setModules(List.of());
        BuildWrapper wrapper = new BuildWrapper(build, "artifactory-build-info");
        when(readService.getBuild(
                "some-build-name", "some-build-number", null, "artifactory-build-info")).thenReturn(wrapper);
        when(urlService.getBuildInfoHref(
                request, "some-build-name", "some-build-number", "artifactory-build-info"))
                .thenReturn(
                        "https://host/api/build/some-build-name/some-build-number?buildRepo=artifactory-build-info");

        Serializable result = command.getBuildInfo(
                request, "some-build-name", "some-build-number", null, null, null, null, false);

        BuildInfo entity = (BuildInfo) result;
        assertEquals(
                "https://host/api/build/some-build-name/some-build-number?buildRepo=artifactory-build-info",
                entity.slf);
        assertEquals(build, entity.buildInfo);
    }

    @Test
    void getBuildInfo_whenSlim_clearsModules() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(any(), any())).thenReturn("artifactory-build-info");
        Build build = new Build();
        build.setName("some-build-name");
        build.setNumber("some-build-number");
        build.setModules(List.of(new org.jfrog.build.api.Module()));
        BuildWrapper wrapper = new BuildWrapper(build, "artifactory-build-info");
        when(readService.getBuild(
                "some-build-name", "some-build-number", null, "artifactory-build-info")).thenReturn(wrapper);
        when(urlService.getBuildInfoHref(any(), any(), any(), any())).thenReturn("some-info-href");

        command.getBuildInfo(request, "some-build-name", "some-build-number", null, null, null, null, true);

        assertTrue(build.getModules().isEmpty());
    }

    @Test
    void getBuildInfo_whenMissingBuild_throwsNotFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(any(), any())).thenReturn("artifactory-build-info");
        when(readService.getBuild(
                "some-build-name", "some-build-number", null, "artifactory-build-info")).thenReturn(null);

        assertThrows(
                NotFoundException.class,
                () -> command.getBuildInfo(
                        request, "some-build-name", "some-build-number", null, null, null, null, false));
    }

    @Test
    void getBuildInfo_withDiffQuery_returnsBuildsDiff() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(any(), any())).thenReturn("artifactory-build-info");
        Build primary = new Build();
        primary.setName("some-build-name-for-diff");
        primary.setNumber("some-primary-build-number");
        primary.setStarted("2023-02-01T00:00:00.000Z");
        Build other = new Build();
        other.setName("some-build-name-for-diff");
        other.setNumber("some-other-build-number");
        other.setStarted("2023-01-01T00:00:00.000Z");
        BuildWrapper wPrimary = new BuildWrapper(primary, "artifactory-build-info");
        BuildWrapper wOther = new BuildWrapper(other, "artifactory-build-info");
        when(readService.getBuild(
                "some-build-name-for-diff", "some-primary-build-number", null, "artifactory-build-info"))
                .thenReturn(wPrimary);
        when(readService.getLatestBuildByNameAndNumber(
                "some-build-name-for-diff", "some-other-build-number", "artifactory-build-info"))
                .thenReturn(wOther);
        BuildRun runPrimary = mock(BuildRun.class);
        when(runPrimary.getStartedDate()).thenReturn(new Date(2_000_000L));
        BuildRun runOther = mock(BuildRun.class);
        when(runOther.getStartedDate()).thenReturn(new Date(1_000_000L));
        when(readService.getBuildRunInternally(
                "some-build-name-for-diff", "some-primary-build-number", "artifactory-build-info"))
                .thenReturn(runPrimary);
        when(readService.getBuildRunInternally(
                "some-build-name-for-diff", "some-other-build-number", "artifactory-build-info"))
                .thenReturn(runOther);
        BuildsDiff diff = mock(BuildsDiff.class);
        when(diffService.getBuildsDiff(primary, other)).thenReturn(diff);

        Serializable result = command.getBuildInfo(
                request,
                "some-build-name-for-diff",
                "some-primary-build-number",
                null,
                null,
                null,
                "some-other-build-number",
                false);

        assertEquals(diff, result);
        verify(diffService).getBuildsDiff(primary, other);
    }
}
