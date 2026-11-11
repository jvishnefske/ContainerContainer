package org.artifactory.ui.rest.service.utils.repositories;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.jfrog.commons.continues.ContinueResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Npm;
import static org.artifactory.descriptor.repo.RepoType.Pypi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

class GetAllRepositoriesPagedServiceTest {
    @Mock
    private RepositoryService repoService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ArtifactoryRestRequest request;
    private ArtifactoryRestResponse response;
    private AutoCloseable autoCloseable;

    private GetAllRepositoriesPagedService service;

    @BeforeEach
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        service = new GetAllRepositoriesPagedService();
        service.setRepoService(repoService);
        service.setRepositoryConfigService(repositoryConfigService);
        response = new ArtifactoryRestResponse();
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testPagingForAllRepos() {
        List<RealRepoConfig> allRepos = List.of(
                generateRepositoryDescriptor(LocalRepoConfig.class, "localRepo_1", Generic),
                generateRepositoryDescriptor(LocalRepoConfig.class, "localRepo_2", Generic),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "remoteRepo_3", Generic),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "remoteRepo_4", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "fedRepo_5", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "fedRepo_6", Generic)
        );
        when(repositoryConfigService.getRepoConfigKeysByProjectAndType(any(), any())).thenReturn(
                allRepos.stream().map(RepoConfig::getKey).toList());
        when(repoService.getLocalFederatedAndRemoteRepoConfigsByProject(any())).thenReturn(allRepos);
        setMockingOfRequestParams(false, false, false, "", Collections.emptyList(), "3");

        List<RepoKeyType> repos = new ArrayList<>();
        String continueState = "0";
        executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 6);
        List<String> names = repos.stream().map(RepoKeyType::getRepoKey).toList();
        assertTrue(names.containsAll(Arrays.asList(
                "localRepo_1", "localRepo_2", "remoteRepo_3", "remoteRepo_4", "fedRepo_5", "fedRepo_6")));
    }

    @Test
    void testPagingForLocalRepos() {
        List<LocalConfig> localRepos = List.of(
                generateRepositoryConfig(LocalRepoConfig.class, "localRepo_1"),
                generateRepositoryConfig(LocalRepoConfig.class, "localRepo_2"),
                generateRepositoryConfig(LocalRepoConfig.class, "localRepo_3")
        );
        when(repositoryConfigService.getRepoConfigKeysByProjectAndType(any(), any())).thenReturn(
                localRepos.stream().map(RepoConfig::getKey).toList());
        when(repoService.getLocalRepoConfigsByProject(any())).thenReturn(localRepos);
        setMockingOfRequestParams(true, false, false, "", Collections.emptyList(), "2");

        List<RepoKeyType> repos = new ArrayList<>();
        String continueState = "0";
        executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 3);
        List<String> names = repos.stream().map(RepoKeyType::getRepoKey).toList();
        assertTrue(names.containsAll(Arrays.asList("localRepo_1", "localRepo_2", "localRepo_3")));
    }

    @Test
    void testPagingForRemoteRepos() {
        List<RepoConfig> remoteRepos = List.of(
                generateRepositoryDescriptor(RemoteRepoConfig.class, "remoteRepo_1", Generic),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "remoteRepo_2", Generic),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "remoteRepo_3", Generic)
        );
        when(repositoryConfigService.getRepoConfigKeysByProjectAndType(any(), any())).thenReturn(
                remoteRepos.stream().map(RepoConfig::getKey).toList());
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(), any())).thenReturn(remoteRepos);
        setMockingOfRequestParams(false, true, false, "", Collections.emptyList(), "2");

        List<RepoKeyType> repos = new ArrayList<>();
        String continueState = "0";
        executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 3);
        List<String> names = repos.stream().map(RepoKeyType::getRepoKey).toList();
        assertTrue(names.containsAll(Arrays.asList("remoteRepo_1", "remoteRepo_2", "remoteRepo_3")));
    }

    @Test
    void testPagingForFederatedRepos() {
        List<RepoConfig> fedRepos = List.of(
                generateRepositoryDescriptor(FederatedRepoConfig.class, "fedRepo_1", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "fedRepo_2", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "fedRepo_3", Generic)
        );

        when(repositoryConfigService.getRepoConfigKeysByProjectAndType(any(), any())).thenReturn(
                fedRepos.stream().map(RepoConfig::getKey).toList());
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(), any())).thenReturn(fedRepos);
        setMockingOfRequestParams(false, false, true, "", Collections.emptyList(), "2");

        List<RepoKeyType> repos = new ArrayList<>();
        String continueState = "0";
        executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 3);
        List<String> names = repos.stream().map(RepoKeyType::getRepoKey).toList();
        assertTrue(names.containsAll(Arrays.asList("fedRepo_1", "fedRepo_2", "fedRepo_3")));
    }

    @Test
    void testRepoKeyFilteringForAllRepos() {
        List<RealRepoConfig> allRepos = List.of(
                generateRepositoryDescriptor(LocalRepoConfig.class, "localRepo_1", Generic),
                generateRepositoryDescriptor(LocalRepoConfig.class, "repoToBeFilteredIn_1", Generic),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "repoToBeFilteredIn_2", Generic),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "remoteRepo_4", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "fedRepo_5", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "repoToBeFilteredIn_3", Generic)
        );
        when(repositoryConfigService.getRepoConfigKeysByProjectAndType(any(), any())).thenReturn(
                allRepos.stream().map(RepoConfig::getKey).toList());
        when(repoService.getLocalFederatedAndRemoteRepoConfigsByProject(any())).thenReturn(allRepos);
        setMockingOfRequestParams(false, false, false, "FilteredIn", Collections.emptyList(), "4");

        List<RepoKeyType> repos = new ArrayList<>();
        String continueState = "0";
        executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 3);
        List<String> names = repos.stream().map(RepoKeyType::getRepoKey).toList();
        assertTrue(names.containsAll(Arrays.asList(
                "repoToBeFilteredIn_1", "repoToBeFilteredIn_2", "repoToBeFilteredIn_3")));
    }

    @Test
    void testPackageTypeFilteringForAllRepos() {
        List<RealRepoConfig> allRepos = List.of(
                generateRepositoryDescriptor(LocalRepoConfig.class, "localRepo_1", Generic),
                generateRepositoryDescriptor(LocalRepoConfig.class, "repoToBeFilteredIn_1", Npm),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "repoToBeFilteredIn_2", Pypi),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "remoteRepo_4", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "fedRepo_5", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "repoToBeFilteredIn_3", Npm)
        );
        when(repositoryConfigService.getRepoConfigKeysByProjectAndType(any(), any())).thenReturn(
                allRepos.stream().map(RepoConfig::getKey).toList());
        when(repoService.getLocalFederatedAndRemoteRepoConfigsByProject(any())).thenReturn(allRepos);
        setMockingOfRequestParams(false, false, false, "", List.of("npm", "pypi"), "4");

        List<RepoKeyType> repos = new ArrayList<>();
        String continueState = "0";
        executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 3);
        List<String> names = repos.stream().map(RepoKeyType::getRepoKey).toList();
        assertTrue(names.containsAll(Arrays.asList(
                "repoToBeFilteredIn_1", "repoToBeFilteredIn_2", "repoToBeFilteredIn_3")));
    }

    private LocalConfig generateRepositoryConfig(Class<? extends RealRepoConfig> clazz, String repoKey) {
        if (LocalCacheRepoConfig.class.equals(clazz) || RemoteRepoConfig.class.equals(clazz)) {
            return LocalCacheRepoConfig.create(RemoteRepoConfig.create(repoKey, Generic));
        } else if (FederatedRepoConfig.class.equals(clazz)) {
            return FederatedRepoConfig.create(repoKey, Generic);
        } else {
            return LocalRepoConfig.create(repoKey, Generic);
        }
    }

    private RealRepoConfig generateRepositoryDescriptor(Class<? extends RealRepoConfig> clazz, String repoKey,
            RepoType packageType) {
        if (RemoteRepoConfig.class.equals(clazz)) {
            return RemoteRepoConfig.create(repoKey, packageType);
        } else if (FederatedRepoDescriptor.class.equals(clazz)) {
            return FederatedRepoConfig.create(repoKey, packageType);
        }
        return LocalRepoConfig.create(repoKey, packageType);
    }

    private void setMockingOfRequestParams(boolean isAnyLocalRepo, boolean isAnyRemoteRepo, boolean isAnyFederatedRepo,
            String repoSearchStr, List<String> packageTypeSearchStrs, String limit) {
        when(request.getQueryParamByKey("isAnyLocalRepo")).thenReturn(String.valueOf(isAnyLocalRepo));
        when(request.getQueryParamByKey("isAnyRemoteRepo")).thenReturn(String.valueOf(isAnyRemoteRepo));
        when(request.getQueryParamByKey("isAnyFederatedRepo")).thenReturn(String.valueOf(isAnyFederatedRepo));
        when(request.getQueryParamByKey("repoSearchStr")).thenReturn(repoSearchStr);
        when(request.getQueryParamListByKey("repoType")).thenReturn(packageTypeSearchStrs);
        when(request.getQueryParamByKey("limit")).thenReturn(limit);
    }

    private void executeAndAssertPaging(String continueState, List<RepoKeyType> repos) {
        long totalRequiredRepos = 0;

        when(request.getQueryParamByKey("continueState")).thenReturn(continueState);
        while (continueState != null) {
            service.execute(request, response);

            assertEquals(response.buildResponse().getStatus(), SC_OK);
            assertTrue(response.getIModel() instanceof ContinueResult);

            ContinueResult<RepoKeyType> continueResult = (ContinueResult<RepoKeyType>) response.getIModel();
            Long currentExecutionTotalRequiredRepos = continueResult.getTotalRequiredDataAmount();
            if ("0".equals(continueState) || "".equals(continueState)) {
                assertNotNull(currentExecutionTotalRequiredRepos);
                totalRequiredRepos = currentExecutionTotalRequiredRepos;
            } else {
                assertNull(currentExecutionTotalRequiredRepos);
            }

            continueState = continueResult.getContinueState();
            when(request.getQueryParamByKey("continueState")).thenReturn(continueState);
            repos.addAll(continueResult.getData());
        }

        assertEquals(totalRequiredRepos, repos.size());
    }

    @Test
    void testPagingForAllReposWithBlankContinueState() {
        List<RealRepoConfig> allRepos = List.of(
                generateRepositoryDescriptor(LocalRepoConfig.class, "localRepo", Generic),
                generateRepositoryDescriptor(RemoteRepoConfig.class, "remoteRepo", Generic),
                generateRepositoryDescriptor(FederatedRepoConfig.class, "fedRepo", Generic)
        );
        when(repoService.getLocalFederatedAndRemoteRepoConfigsByProject(any())).thenReturn(allRepos);
        setMockingOfRequestParams(false, false, false, "", Collections.emptyList(), "2");

        List<RepoKeyType> repos = new ArrayList<>();
        String continueState = "";
        executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 3);
        List<String> names = repos.stream().map(RepoKeyType::getRepoKey).toList();
        assertTrue(names.containsAll(Arrays.asList("localRepo", "remoteRepo", "fedRepo")));
    }
}
