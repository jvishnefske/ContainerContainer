package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.api.BuildGeneralInfo;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class BuildGetGeneralInfoUiCommandTest {

    private BuildGetGeneralInfoUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        command = new BuildGetGeneralInfoUiCommand(repoService, moduleService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildGeneralInfo_shouldReturnInfo_whenDateIsBlank() {
        when(moduleService.getBuildGeneralInfo(eq("my-build"), eq("1"), isNull(), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(BuildGeneralInfo.builder().buildName("my-build").buildNumber("1").build());

        GeneralBuildInfo result = command.getBuildGeneralInfo("my-build", "1", null, BUILDINFO_REPO_KEY);

        assertEquals("my-build", result.getBuildName());
        assertEquals("1", result.getBuildNumber());
        verify(repoService).normaliseBuildRepoName(BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildGeneralInfo_shouldFormatDateToBuildStarted_whenDateProvided() {
        String date = "1700000000000";
        String expectedStarted = formatBuildTime(Long.parseLong(date));
        when(moduleService.getBuildGeneralInfo(eq("my-build"), eq("1"), eq(expectedStarted), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(BuildGeneralInfo.builder().buildName("my-build").buildNumber("1").build());

        command.getBuildGeneralInfo("my-build", "1", date, BUILDINFO_REPO_KEY);

        verify(moduleService).getBuildGeneralInfo("my-build", "1", expectedStarted, BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildGeneralInfo_shouldThrowNotFound_whenBuildMissing() {
        when(moduleService.getBuildGeneralInfo(eq("missing"), eq("9"), isNull(), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(null);

        NotFoundException thrown = assertThrows(NotFoundException.class,
                () -> command.getBuildGeneralInfo("missing", "9", null, BUILDINFO_REPO_KEY));

        assertTrue(thrown.getMessage().contains("missing"));
        assertTrue(thrown.getMessage().contains("9"));
    }

    @Test
    void getBuildGeneralInfo_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(moduleService.getBuildGeneralInfo(eq("my-build"), eq("1"), isNull(), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(BuildGeneralInfo.builder().buildName("my-build").buildNumber("1").build());

        command.getBuildGeneralInfo("my-build", "1", null, null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(moduleService).getBuildGeneralInfo("my-build", "1", null, BUILDINFO_REPO_KEY);
    }
}
