package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import org.jfrog.commons.continues.ContinueResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BuildGetAllNamesPagedUiCommandTest {

    private static final String DEFAULT_PROJECT = "default";

    private BuildGetAllNamesPagedUiCommand command;

    private BuildRepoService repoService;
    private BuildSearchService searchService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        searchService = mock(BuildSearchService.class);
        permissionService = mock(BuildPermissionService.class);
        command = new BuildGetAllNamesPagedUiCommand(repoService, searchService, permissionService);
    }

    @Test
    void getAllBuildNamesPaged_shouldReturnBuildNamesWithTotalAndContinueState() {
        Map<String, String[]> params = Map.of("limit", new String[] {"2"});
        when(searchService.getDistinctBuildNamesPaging(any(ContinueBuildFilter.class)))
                .thenReturn(List.of("build-a", "build-b"));
        when(searchService.getDistinctBuildNamesCount(any(ContinueBuildFilter.class))).thenReturn(5);

        ContinueResult<String> result = command.getAllBuildNamesPaged(params);

        verify(permissionService).assertCanViewBuildPermissions(DEFAULT_PROJECT);
        assertEquals(List.of("build-a", "build-b"), result.getData());
        assertEquals(5L, result.getTotalRequiredDataAmount());
        assertNotNull(result.getContinueState());
    }

    @Test
    void getAllBuildNamesPaged_shouldNotSetContinueState_whenFewerResultsThanLimit() {
        Map<String, String[]> params = Map.of("limit", new String[] {"10"});
        when(searchService.getDistinctBuildNamesPaging(any(ContinueBuildFilter.class)))
                .thenReturn(List.of("build-a", "build-b"));
        when(searchService.getDistinctBuildNamesCount(any(ContinueBuildFilter.class))).thenReturn(2);

        ContinueResult<String> result = command.getAllBuildNamesPaged(params);

        assertNull(result.getContinueState());
        assertEquals(2L, result.getTotalRequiredDataAmount());
    }

    @Test
    void getAllBuildNamesPaged_shouldUseProjectKeyFromParamsForPermissionCheck() {
        Map<String, String[]> params = Map.of("projectKey", new String[] {"my-project"});
        when(repoService.getBuildInfoRepoKeyByProject("my-project", true)).thenReturn("my-project-build-info");
        when(searchService.getDistinctBuildNamesPaging(any(ContinueBuildFilter.class))).thenReturn(List.of());
        when(searchService.getDistinctBuildNamesCount(any(ContinueBuildFilter.class))).thenReturn(0);

        command.getAllBuildNamesPaged(params);

        verify(permissionService).assertCanViewBuildPermissions("my-project");
    }

    @Test
    void getAllBuildNamesPaged_shouldPropagateAndSkipQuery_whenNotAuthorized() {
        Map<String, String[]> params = Map.of("limit", new String[] {"10"});
        doThrow(new RuntimeException("forbidden"))
                .when(permissionService).assertCanViewBuildPermissions(anyString());

        assertThrows(RuntimeException.class, () -> command.getAllBuildNamesPaged(params));

        verifyNoInteractions(searchService);
    }
}
