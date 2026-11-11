package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.storage.fs.service.RepoStatsService;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigResponse;
import org.artifactory.ui.rest.service.admin.configuration.repositories.mapper.RepoConfigMapper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.ProjectRepoHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

public class GetRepositoryConfigServiceV2Test {

    private RepoConfigMapper repoConfigMapper;
    private RepoStatsService repoStatsService;
    private RepositoryService repositoryService;
    private SecurityService securityService;
    private RepoConfigFilterService repoConfigFilterService;
    private FederatedRepoService federatedRepoService;

    private GetRepositoryConfigServiceV2 service;

    @BeforeEach
    void setUp() {
        securityService = mock(SecurityService.class);
        when(securityService.isAdmin()).thenReturn(true);
        ProjectRepoHelper projectRepoHelper = mock(ProjectRepoHelper.class);
        repoConfigMapper = mock(RepoConfigMapper.class);
        repoStatsService = mock(RepoStatsService.class);
        repositoryService = mock(RepositoryService.class);
        repoConfigFilterService = mock(RepoConfigFilterService.class);
        federatedRepoService = mock(FederatedRepoService.class);
        RepoConfigValidator repoConfigValidator = mock(RepoConfigValidator.class);
        service = new GetRepositoryConfigServiceV2(projectRepoHelper, repoConfigMapper, repoStatsService,
                repositoryService, repoConfigFilterService, repoConfigValidator, securityService, federatedRepoService);
    }

    @Test
    void getPage_sortsByRepoKeyDescendingByDefault() throws RepoConfigException {
        RepoConfigRequest request = RepoConfigRequest.builder()
                .limit(100L)
                .build();
        RepoConfig repoA = mock(RepoConfig.class);
        when(repoA.getKey()).thenReturn("repo-a");
        RepoConfig repoB = mock(RepoConfig.class);
        when(repoB.getKey()).thenReturn("repo-b");

        RepoConfigResponse responseA = mock(RepoConfigResponse.class);
        RepoConfigResponse responseB = mock(RepoConfigResponse.class);

        when(repositoryService.getAllRepoConfigs()).thenReturn(List.of(repoA, repoB));
        when(repoConfigFilterService.match(any(), eq(request), anyMap(), anySet())).thenReturn(true);
        when(repoConfigMapper.toResponse(repoA, null, Collections.emptyMap(), Set.of()))
                .thenReturn(responseA);
        when(repoConfigMapper.toResponse(repoB, null, Collections.emptyMap(), Set.of()))
                .thenReturn(responseB);

        List<RepoConfigResponse> result = service.getPage(request);

        assertEquals(2, result.size());
        assertEquals(responseB, result.get(0));
        assertEquals(responseA, result.get(1));
    }

    @Test
    void getPage_sortsByPackageTypeAscending() throws RepoConfigException {
        RepoConfigRequest request = RepoConfigRequest.builder()
                .limit(100L)
                .sortDirection("ASC")
                .sortBy("packageType")
                .build();

        RepoConfig repoMaven = mock(RepoConfig.class);
        when(repoMaven.getKey()).thenReturn("repo-maven");
        when(repoMaven.getPackageType()).thenReturn(RepoType.Maven);
        RepoConfig repoGeneric = mock(RepoConfig.class);
        when(repoGeneric.getKey()).thenReturn("repo-generic");
        when(repoGeneric.getPackageType()).thenReturn(RepoType.Generic);

        RepoConfigResponse responseMaven = mock(RepoConfigResponse.class);
        RepoConfigResponse responseGeneric = mock(RepoConfigResponse.class);

        when(repositoryService.getAllRepoConfigs()).thenReturn(List.of(repoMaven, repoGeneric));
        when(repoConfigFilterService.match(any(), eq(request), anyMap(), anySet())).thenReturn(true);
        when(repoConfigMapper.toResponse(repoMaven, null, Collections.emptyMap(), Set.of()))
                .thenReturn(responseMaven);
        when(repoConfigMapper.toResponse(repoGeneric, null, Collections.emptyMap(), Set.of()))
                .thenReturn(responseGeneric);

        List<RepoConfigResponse> result = service.getPage(request);

        assertEquals(2, result.size());
        assertEquals(responseGeneric, result.get(0));
        assertEquals(responseMaven, result.get(1));
    }

    @Test
    void getPage_sortsByLastActivity() throws RepoConfigException {
        RepoConfigRequest request = RepoConfigRequest.builder()
                .limit(100L)
                .sortBy("lastActivity")
                .sortDirection("DESC")
                .build();

        RepoConfig repo1 = mock(RepoConfig.class);
        when(repo1.getKey()).thenReturn("repo1");
        RepoConfig repo2 = mock(RepoConfig.class);
        when(repo2.getKey()).thenReturn("repo2");

        RepoStatDto stat1 = mock(RepoStatDto.class);
        when(stat1.getRepoKey()).thenReturn("repo1");
        when(stat1.getLastDownloaded()).thenReturn(100L);
        when(stat1.getLastUploaded()).thenReturn(50L); // last activity = 100

        RepoStatDto stat2 = mock(RepoStatDto.class);
        when(stat2.getRepoKey()).thenReturn("repo2");
        when(stat2.getLastDownloaded()).thenReturn(0L);
        when(stat2.getLastUploaded()).thenReturn(200L); // last activity = 200

        RepoConfigResponse response1 = mock(RepoConfigResponse.class);
        RepoConfigResponse response2 = mock(RepoConfigResponse.class);

        when(repositoryService.getAllRepoConfigs()).thenReturn(List.of(repo1, repo2));
        when(repoConfigFilterService.match(any(), eq(request), anyMap(), anySet())).thenReturn(true);
        when(repoStatsService.getByKeys(Set.of("repo1", "repo2"))).thenReturn(List.of(stat1, stat2));
        when(repoConfigMapper.toResponse(repo1, stat1, Collections.emptyMap(), Set.of())).thenReturn(response1);
        when(repoConfigMapper.toResponse(repo2, stat2, Collections.emptyMap(), Set.of())).thenReturn(response2);

        List<RepoConfigResponse> result = service.getPage(request);

        assertEquals(2, result.size());
        assertEquals(response2, result.get(0));
        assertEquals(response1, result.get(1));
    }

    @Test
    void getPage_keepsRepoKeysOrderWhenRequested() throws RepoConfigException {
        RepoConfigRequest request = RepoConfigRequest.builder()
                .limit(100L)
                .keepRepoKeysOrder(true)
                .repoKeys(List.of("repo-c", "repo-a", "repo-b"))
                .build();
        RepoConfig repoA = mock(RepoConfig.class);
        when(repoA.getKey()).thenReturn("repo-a");
        RepoConfig repoB = mock(RepoConfig.class);
        when(repoB.getKey()).thenReturn("repo-b");
        RepoConfig repoC = mock(RepoConfig.class);
        when(repoC.getKey()).thenReturn("repo-c");

        RepoConfigResponse responseA = mock(RepoConfigResponse.class);
        RepoConfigResponse responseB = mock(RepoConfigResponse.class);
        RepoConfigResponse responseC = mock(RepoConfigResponse.class);

        when(repositoryService.getAllRepoConfigs()).thenReturn(List.of(repoA, repoB, repoC));
        when(repoConfigFilterService.match(any(), eq(request), anyMap(), anySet())).thenReturn(true);
        when(repoConfigMapper.toResponse(repoA, null, Collections.emptyMap(), Set.of()))
                .thenReturn(responseA);
        when(repoConfigMapper.toResponse(repoB, null, Collections.emptyMap(), Set.of()))
                .thenReturn(responseB);
        when(repoConfigMapper.toResponse(repoC, null, Collections.emptyMap(), Set.of()))
                .thenReturn(responseC);

        List<RepoConfigResponse> result = service.getPage(request);

        assertEquals(3, result.size());
        assertEquals(responseC, result.get(0));
        assertEquals(responseA, result.get(1));
        assertEquals(responseB, result.get(2));
    }

    @Test
    void getPage_ProjectContextWithoutPermissions_ThrowsException() {
        var projectKey = "project-1";
        when(securityService.isProjectAdmin(projectKey)).thenReturn(false);
        RepoConfigRequest request = RepoConfigRequest.builder()
                .limit(100L)
                .keepRepoKeysOrder(true)
                .projectKeys(Set.of(projectKey))
                .projectContext(true)
                .build();
        when(securityService.isProjectAdmin(projectKey)).thenReturn(false);
        Assertions.assertThrows(AuthorizationRestException.class, () -> service.getPage(request));
    }
}
