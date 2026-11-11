package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.PromotionStatusModel;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.release.PromotionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetReleaseHistoryUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "1";
    private static final String BUILD_STARTED_EPOCH = "1700000000000";
    private static final String BUILD_STARTED_FORMATTED = formatBuildTime(Long.parseLong(BUILD_STARTED_EPOCH));

    private BuildGetReleaseHistoryUiCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        command = new BuildGetReleaseHistoryUiCommand(repoService, readService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getReleaseHistory_shouldSortStatusesByTimestampDescendingAndMap() {
        Build build = new Build();
        build.setStatuses(List.of(
                promotionStatus("Staged", "stage-local", "2024-01-01T10:00:00.000+0000"),
                promotionStatus("Released", "release-local", "2024-03-01T10:00:00.000+0000")));
        when(readService.getBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY))
                .thenReturn(new BuildWrapper(build, BUILDINFO_REPO_KEY));

        List<PromotionStatusModel> result = command.getReleaseHistory(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH,
                BUILDINFO_REPO_KEY);

        verify(readService).getBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY);
        assertEquals(2, result.size());
        // Most recent promotion first.
        assertEquals("Released", result.getFirst().getStatus());
        assertEquals("release-local", result.getFirst().getRepository());
        assertEquals("Staged", result.get(1).getStatus());
    }

    @Test
    void getReleaseHistory_shouldReturnEmptyList_whenNoStatuses() {
        Build build = new Build();
        build.setStatuses(null);
        when(readService.getBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY))
                .thenReturn(new BuildWrapper(build, BUILDINFO_REPO_KEY));

        List<PromotionStatusModel> result = command.getReleaseHistory(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH,
                BUILDINFO_REPO_KEY);

        assertTrue(result.isEmpty());
    }

    @Test
    void getReleaseHistory_shouldThrowNotFound_whenBuildMissing() {
        when(readService.getBuild(eq(BUILD_NAME), eq(BUILD_NUMBER), eq(BUILD_STARTED_FORMATTED),
                eq(BUILDINFO_REPO_KEY))).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> command.getReleaseHistory(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH, BUILDINFO_REPO_KEY));
    }

    @Test
    void getReleaseHistory_shouldPassNullDate_whenBuildStartedIsBlank() {
        Build build = new Build();
        when(readService.getBuild(eq(BUILD_NAME), eq(BUILD_NUMBER), isNull(), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(new BuildWrapper(build, BUILDINFO_REPO_KEY));

        command.getReleaseHistory(BUILD_NAME, BUILD_NUMBER, "", BUILDINFO_REPO_KEY);

        verify(readService).getBuild(BUILD_NAME, BUILD_NUMBER, null, BUILDINFO_REPO_KEY);
    }

    private static PromotionStatus promotionStatus(String status, String repository, String timestamp) {
        return new PromotionStatus(status, "comment", repository, timestamp, "user", "ciUser");
    }
}
