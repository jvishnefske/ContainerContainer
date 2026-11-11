package com.jfrog.build.command.ui;

import com.jfrog.build.model.BuildProps;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.model.api.BuildPropsModel;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetSystemBuildPropsUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "1";
    private static final String BUILD_STARTED = "1700000000000";

    private BuildGetSystemBuildPropsUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        command = new BuildGetSystemBuildPropsUiCommand(repoService, moduleService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getSystemBuildProps_shouldBuildParamsWithoutEnvFlagAndMapProps() {
        when(moduleService.getBuildPropsData(any(BuildParams.class)))
                .thenReturn(List.of(new BuildProps("java.version", "25", null)));

        PagingModel<BuildPropsModel> result =
                command.getSystemBuildProps(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);

        ArgumentCaptor<BuildParams> paramsCaptor = ArgumentCaptor.forClass(BuildParams.class);
        verify(moduleService).getBuildPropsData(paramsCaptor.capture());
        BuildParams params = paramsCaptor.getValue();
        assertEquals(BUILD_NAME, params.getBuildName());
        assertEquals(BUILD_NUMBER, params.getCurrBuildNum());
        assertEquals(Long.parseLong(BUILD_STARTED), params.getCurrBuildDate());
        assertFalse(params.isEnvProps());
        assertEquals(BUILDINFO_REPO_KEY, params.getBuildRepo());

        assertEquals("0", result.getTotalItems());
        assertEquals(1, result.getPagingData().size());
        BuildPropsModel model = result.getPagingData().getFirst();
        assertEquals("java.version", model.getKey());
        assertEquals("25", model.getValue());
    }

    @Test
    void getSystemBuildProps_shouldReturnEmptyPagingModel_whenNoProps() {
        when(moduleService.getBuildPropsData(any(BuildParams.class))).thenReturn(List.of());

        PagingModel<BuildPropsModel> result =
                command.getSystemBuildProps(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);

        assertEquals("0", result.getTotalItems());
        assertTrue(result.getPagingData().isEmpty());
    }

    @Test
    void getSystemBuildProps_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(moduleService.getBuildPropsData(any(BuildParams.class))).thenReturn(List.of());

        command.getSystemBuildProps(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, null);

        verify(repoService).normaliseBuildRepoName(null);
        ArgumentCaptor<BuildParams> paramsCaptor = ArgumentCaptor.forClass(BuildParams.class);
        verify(moduleService).getBuildPropsData(paramsCaptor.capture());
        assertEquals(BUILDINFO_REPO_KEY, paramsCaptor.getValue().getBuildRepo());
    }
}
