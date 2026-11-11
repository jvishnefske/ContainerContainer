package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.BuildRunComparators;
import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.model.api.BuildsByName;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.SortedSet;
import java.util.TreeSet;

import static com.jfrog.build.service.BuildUrlService.getBuildNumberRelativeHref;
import static com.jfrog.build.service.BuildUrlService.getBuildRelativeHref;
import static com.jfrog.build.service.BuildUrlService.getBuildRepoRelativeHref;
import static java.util.Collections.reverseOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetByNameCommandTest {

    private BuildGetByNameCommand command;

    private BuildUrlService urlService;
    private BuildRepoService repoService;
    private BuildReadService readService;

    @BeforeEach
    void setUp() {
        urlService = mock(BuildUrlService.class);
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        command = new BuildGetByNameCommand(urlService, repoService, readService);
    }

    @Test
    void getBuildsByName_returnsBuildsByNameByNameWhenReadableRunsExist() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(urlService.getBaseBuildsHref(request)).thenReturn("https://host/artifactory/api/build");
        BuildRunImpl run = new BuildRunImpl(
                "some-build-name",
                "some-build-number",
                "2024-01-01T00:00:00.000+0000");
        SortedSet<BuildRun> runs = new TreeSet<>(reverseOrder(BuildRunComparators.getBuildStartDateComparator()));
        runs.add(run);
        when(readService.searchBuildsByName("some-build-name", "artifactory-build-info")).thenReturn(runs);

        BuildsByName result = command.getBuildsByName(request, "some-build-name", null, null);

        assertEquals(
                "https://host/artifactory/api/build" + getBuildRelativeHref("some-build-name")
                        + getBuildRepoRelativeHref("artifactory-build-info"),
                result.slf);
        assertEquals(1, result.buildsNumbers.size());
        assertEquals(getBuildNumberRelativeHref("some-build-number"), result.buildsNumbers.getFirst().uri);
        assertEquals(run.getStarted(), result.buildsNumbers.getFirst().started);
        verify(repoService).getPreferredBuildRepo(null, null);
    }

    @Test
    void getBuildsByName_throwsNotFoundWhenNoRuns() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(eq(null), eq("phoenix"))).thenReturn("phoenix-build-info");
        when(urlService.getBaseBuildsHref(request)).thenReturn("https://host/api/build");
        SortedSet<BuildRun> empty = new TreeSet<>(reverseOrder(BuildRunComparators.getBuildStartDateComparator()));
        when(readService.searchBuildsByName("some-missing-build-name", "phoenix-build-info")).thenReturn(empty);

        assertThrows(
                NotFoundException.class,
                () -> command.getBuildsByName(request, "some-missing-build-name", null, "phoenix"));
    }
}
