package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.BuildContentResponse;
import com.jfrog.build.model.api.BuildReleasableModel;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildReleasableCollector;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetContentUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "7";

    private BuildGetContentUiCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;
    private BuildReleasableCollector releasableCollector;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        releasableCollector = mock(BuildReleasableCollector.class);
        command = new BuildGetContentUiCommand(repoService, readService, releasableCollector);
        when(repoService.getPreferredBuildRepo(any(), any())).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildContent_shouldThrowNotFound_whenBuildMissing() {
        when(readService.getLatestBuildByNameAndNumber(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY))
                .thenReturn(null);

        assertThrows(NotFoundException.class, () -> command.getBuildContent(BUILD_NAME, BUILD_NUMBER, null, null, null,
                null, null, null, null, true));
    }

    @Test
    void getBuildContent_shouldUseLatestBuild_whenNoBuildStarted() {
        BuildWrapper wrapper = existingWrapper();
        when(readService.getLatestBuildByNameAndNumber(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY))
                .thenReturn(wrapper);
        when(releasableCollector.collect(wrapper, BUILDINFO_REPO_KEY)).thenReturn(List.of(releasable("a")));

        BuildContentResponse response = command.getBuildContent(BUILD_NAME, BUILD_NUMBER, null, null, null, null, null,
                null, null, true);

        assertEquals(1, response.getTotal());
        assertEquals(1, response.getReleasables().size());
        verify(readService).getLatestBuildByNameAndNumber(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildContent_shouldResolveExactRun_whenBuildStartedGiven() {
        BuildRun run = mock(BuildRun.class);
        BuildWrapper wrapper = existingWrapper();
        when(readService.getBuildRun(BUILD_NAME, BUILD_NUMBER, "1700000000000", BUILDINFO_REPO_KEY)).thenReturn(run);
        when(readService.getBuild(run)).thenReturn(wrapper);
        when(releasableCollector.collect(wrapper, BUILDINFO_REPO_KEY)).thenReturn(List.of());

        command.getBuildContent(BUILD_NAME, BUILD_NUMBER, "1700000000000", null, null, null, null, null, null, true);

        verify(readService).getBuildRun(BUILD_NAME, BUILD_NUMBER, "1700000000000", BUILDINFO_REPO_KEY);
        verify(readService).getBuild(run);
    }

    @Test
    void getBuildContent_shouldFilterBySearchTermSortAndPaginate() {
        BuildWrapper wrapper = existingWrapper();
        when(readService.getLatestBuildByNameAndNumber(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY))
                .thenReturn(wrapper);
        when(releasableCollector.collect(wrapper, BUILDINFO_REPO_KEY))
                .thenReturn(List.of(releasable("alpha"), releasable("beta"), releasable("alpine")));

        // search_term "alp" keeps alpha + alpine; order_by name asc; offset 1 limit 1 -> just "alpine".
        BuildContentResponse response = command.getBuildContent(BUILD_NAME, BUILD_NUMBER, null, null, null, "1", "1",
                "alp", "name", true);

        assertEquals(2, response.getTotal());
        assertEquals(1, response.getReleasables().size());
        assertEquals("alpine", response.getReleasables().getFirst().getName());
    }

    @Test
    void getBuildContent_shouldCapLimitAndDefaultWhenInvalid() {
        BuildWrapper wrapper = existingWrapper();
        when(readService.getLatestBuildByNameAndNumber(BUILD_NAME, BUILD_NUMBER, BUILDINFO_REPO_KEY))
                .thenReturn(wrapper);
        when(releasableCollector.collect(wrapper, BUILDINFO_REPO_KEY))
                .thenReturn(List.of(releasable("a"), releasable("b")));

        // Invalid limit/offset fall back to defaults (offset 0, limit 10) -> both returned.
        BuildContentResponse response = command.getBuildContent(BUILD_NAME, BUILD_NUMBER, null, null, null, "bad",
                "bad", null, null, true);

        assertEquals(2, response.getReleasables().size());
    }

    private static BuildWrapper existingWrapper() {
        BuildWrapper wrapper = mock(BuildWrapper.class);
        when(wrapper.isBuildExist()).thenReturn(true);
        return wrapper;
    }

    private static BuildReleasableModel releasable(String name) {
        return BuildReleasableModel.builder().name(name).packageType("generic").build();
    }
}
