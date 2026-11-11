package org.artifactory.ui.rest.service.utils.repositories;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.AutoCompleteRepoConfigRecord;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.RepositoryConfigServiceImpl;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.commons.continues.ContinueResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jfrog.access.client.project.EnvironmentEntity;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Gradle;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_CONTINUE_STATE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_LIMIT_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_PACKAGE_TYPE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_STAGES_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_PROJECTS_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_REPO_TYPE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_SEARCH_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoCompleteRepositoriesServiceTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private RepositoryConfigServiceImpl repositoryConfigService;
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private ProjectsService projectsService;
    private ArtifactoryRestResponse response;
    private AutoCloseable autoCloseable;
    private AutoCompleteRepositoriesServiceImpl service;

    @BeforeEach
    public void setUp() throws IOException {
        autoCloseable = MockitoAnnotations.openMocks(this);
        service = new AutoCompleteRepositoriesServiceImpl();
        service.setRepositoryConfigService(repositoryConfigService, projectsService);
        response = new ArtifactoryRestResponse();
        setFederationCleanupFlag(true);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testPagingWhenLimitNotNull() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("localRepo_1", Generic),
                LocalRepoConfig.create("localRepo_2", Generic),
                LocalRepoConfig.create("localRepo_3", Generic),
                LocalRepoConfig.create("localRepo_4", Generic),
                LocalRepoConfig.create("localRepo_5", Generic),
                LocalRepoConfig.create("localRepo_6", Generic));


        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(
                allRepos);
        service.setRepositoryConfigService(repositoryConfigService, null);
        long limit = 100;
        setMockingOfRequestParams("local", List.of(), List.of(), String.valueOf(limit),
                "", "");

        List<AutoCompleteRepoConfigRecord> repos = new ArrayList<>();
        String continueState = "0";
        long pagesNumber = executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 6);
        long size = allRepos.size();
        long expectedNumberOfPages = limit == 0 ? size : allRepos.size() >= limit ?
                (size % limit == 0 ? size / limit : size / limit + 1) : 1;
        assertEquals(pagesNumber, expectedNumberOfPages);
        List<String> names = repos.stream().map(AutoCompleteRepoConfigRecord::repoKey).toList();
        assertTrue(names.containsAll(Arrays.asList(
                "localRepo_1", "localRepo_2", "localRepo_3", "localRepo_4", "localRepo_5", "localRepo_6")));
    }

    @Test
    void testContinueStateIsNotNumeric() {
        setMockingOfRequestParams("local", List.of(), List.of(), "", "abc", "");
        service.execute(request, response);
        assertEquals(response.buildResponse().getStatus(), SC_BAD_REQUEST);
    }

    @Test
    void testContinueStateIsNegative() {
        setMockingOfRequestParams("local", List.of(), List.of(), "", "-1", "");
        service.execute(request, response);
        assertEquals(response.buildResponse().getStatus(), SC_BAD_REQUEST);
    }

    @Test
    void testPagingWhenLimitIsNotNumeric() {
        setMockingOfRequestParams("local", List.of(), List.of(), "abc", "", "");
        service.execute(request, response);
        assertEquals(response.buildResponse().getStatus(), SC_BAD_REQUEST);
    }

    @Test
    void testPagingWhenLimitIsNegative() {
        setMockingOfRequestParams("local", List.of(), List.of(), "-1", "", "");
        service.execute(request, response);
        assertEquals(response.buildResponse().getStatus(), SC_BAD_REQUEST);
    }

    @Test
    void testPagingWhenLimitNotDefined() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("localRepo_1", Generic),
                LocalRepoConfig.create("localRepo_2", Generic),
                LocalRepoConfig.create("localRepo_3", Generic));

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(
                allRepos);
        service.setRepositoryConfigService(repositoryConfigService, null);
        setMockingOfRequestParams("local", List.of(), List.of(), "", "", "");

        List<AutoCompleteRepoConfigRecord> repos = new ArrayList<>();
        String continueState = "0";
        long pagesNumber = executeAndAssertPaging(continueState, repos);

        assertEquals(repos.size(), 3);
        long expectedNumberOfPages = allRepos.size() / 50 + 1;
        assertEquals(pagesNumber, expectedNumberOfPages);
        List<String> names = repos.stream().map(AutoCompleteRepoConfigRecord::repoKey).toList();
        assertTrue(names.containsAll(Arrays.asList(
                "localRepo_1", "localRepo_2", "localRepo_3")));
    }

    @Test
    void testReposFilterByRepoType() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("rep_local", BuildInfo),
                LocalRepoConfig.create("repoToBeFilteredIn_1_local", BuildInfo),
                RemoteRepoConfig.create("repoToBeFilteredIn_2", Generic),
                RemoteRepoConfig.create("remoteRepo_4", Gradle),
                LocalRepoConfig.create("local", Generic),
                LocalRepoConfig.create("local_22", Gradle)
        );

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(
                allRepos);
        service.setRepositoryConfigService(repositoryConfigService, null);
        setMockingOfRequestParams("local", List.of("local"), List.of(), "", "", "");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> continueResult =
                (ContinueResult<AutoCompleteRepoConfigRecord>) response.getIModel();

        assertEquals(response.buildResponse().getStatus(), SC_OK);
        assertEquals(4, continueResult.getTotalRequiredDataAmount());
    }

    @Test
    void testLocalAndFederatedReposReturnedWhenFederatedCleanupFeatureIsEnabled() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("picked_local_repo_1", BuildInfo),
                RemoteRepoConfig.create("skipped_remote_repo_1", Gradle),
                FederatedRepoConfig.create("picked_federated_repo_1", Generic),
                FederatedRepoConfig.create("picked_federated_repo_1", Gradle)
        );

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(
                allRepos);
        service.setRepositoryConfigService(repositoryConfigService, null);
        setMockingOfRequestParams("picked_", List.of("local", "federated"), List.of(), "", "", "");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> continueResult =
                (ContinueResult<AutoCompleteRepoConfigRecord>) response.getIModel();

        assertEquals(response.buildResponse().getStatus(), SC_OK);
        assertEquals(3, continueResult.getTotalRequiredDataAmount());
    }

    @Test
    void testFederatedReposSkippedWhenFederatedCleanupFeatureIsDisabled() {
        setFederationCleanupFlag(false);
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("picked_local_repo_1", BuildInfo),
                RemoteRepoConfig.create("picked_remote_repo_1", Gradle),
                FederatedRepoConfig.create("picked_federated_repo_1", Generic),
                FederatedRepoConfig.create("skipped_federated_repo_1", Gradle)
        );

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(
                allRepos);
        service.setRepositoryConfigService(repositoryConfigService, null);
        setMockingOfRequestParams("picked_", List.of("local", "remote", "federated"), List.of(), "", "", "");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> continueResult =
                (ContinueResult<AutoCompleteRepoConfigRecord>) response.getIModel();

        assertEquals(SC_OK, response.buildResponse().getStatus());
        assertEquals(2, continueResult.getTotalRequiredDataAmount());
    }

    @Test
    void testReposFilterByPackageType() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("rep_local", BuildInfo),
                LocalRepoConfig.create("repoToBeFilteredIn_1_local", BuildInfo),
                RemoteRepoConfig.create("repoToBeFilteredIn_2", Generic),
                RemoteRepoConfig.create("remoteRepo_4", Gradle),
                LocalRepoConfig.create("local", Generic),
                LocalRepoConfig.create("local_22", Gradle)
        );

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(
                allRepos);
        service.setRepositoryConfigService(repositoryConfigService, null);
        setMockingOfRequestParams("local", List.of(""), List.of("generic"), "", "", "");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> continueResult =
                (ContinueResult<AutoCompleteRepoConfigRecord>) response.getIModel();

        assertEquals(SC_OK, response.buildResponse().getStatus());
        assertEquals(2, continueResult.getTotalRequiredDataAmount());
    }

    @Test
    void testReposFilterByPackageTypeAndRepoType() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("rep_local", BuildInfo),
                LocalRepoConfig.create("repoToBeFilteredIn_1_local", BuildInfo),
                RemoteRepoConfig.create("repoToBeFilteredIn_2", Generic),
                RemoteRepoConfig.create("remoteRepo_4", Gradle),
                LocalRepoConfig.create("local", Generic),
                LocalRepoConfig.create("local_22", Gradle)
        );

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(
                allRepos);
        service.setRepositoryConfigService(repositoryConfigService, null);
        setMockingOfRequestParams("local", List.of("local"), List.of("generic"), "", "", "");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> continueResult =
                (ContinueResult<AutoCompleteRepoConfigRecord>) response.getIModel();

        assertEquals(response.buildResponse().getStatus(), SC_OK);
        assertEquals(1, continueResult.getTotalRequiredDataAmount());
    }

    @Test
    void testReposFilterByProjectList() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("rep_local", BuildInfo),
                LocalRepoConfig.create("repoToBeFilteredIn_1_local", BuildInfo),
                RemoteRepoConfig.create("repoToBeFilteredIn_2", Generic),
                RemoteRepoConfig.create("remoteRepo_4", Gradle),
                LocalRepoConfig.create("local", Generic),
                LocalRepoConfig.create("local_22", Gradle)
        );
        Map<String, String> projectRepos = Map.of(
                "local", "project1",
                "local_22", "project2"
        );

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(allRepos);

        ProjectsService projectsService = mock(ProjectsService.class);
        when(projectsService.isProjectAdmin(any())).thenReturn(true);
        when(projectsService.getRepoKeyToProjectMap(any(), anyBoolean())).thenReturn(projectRepos);

        service.setRepositoryConfigService(repositoryConfigService, projectsService);
        setMockingOfRequestParams("local", List.of(), List.of(), "", "", "project1,project2");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> continueResult =
                (ContinueResult<AutoCompleteRepoConfigRecord>) response.getIModel();

        assertEquals(response.buildResponse().getStatus(), SC_OK);
        assertEquals(2, continueResult.getTotalRequiredDataAmount());
    }

    @Test
    void testReposFilterByProjectListRepoTypeAndPackageType() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("rep_local", BuildInfo),
                LocalRepoConfig.create("repoToBeFilteredIn_1_local", BuildInfo),
                RemoteRepoConfig.create("repoToBeFilteredIn_2", Generic),
                RemoteRepoConfig.create("remoteRepo_4", Gradle),
                LocalRepoConfig.create("local", Generic),
                LocalRepoConfig.create("local_22", Gradle)
        );
        Map<String, String> projectRepos = Map.of(
                "local", "project1",
                "local_22", "project2",
                "repoToBeFilteredIn_2", "project1"
        );

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(allRepos);

        ProjectsService projectsService = mock(ProjectsService.class);
        when(projectsService.isProjectAdmin(any())).thenReturn(true);
        when(projectsService.getRepoKeyToProjectMap(any(), anyBoolean())).thenReturn(projectRepos);

        service.setRepositoryConfigService(repositoryConfigService, projectsService);
        setMockingOfRequestParams("local", List.of("local"), List.of("generic"), "", "", "project1,project2");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> continueResult =
                (ContinueResult<AutoCompleteRepoConfigRecord>) response.getIModel();
        AutoCompleteRepoConfigRecord expectedRecord =
                new AutoCompleteRepoConfigRecord(
                        "local", RepoDetailsType.LOCAL, Generic, null);
        AutoCompleteRepoConfigRecord actualRecord = continueResult.getData().get(0);

        assertEquals(response.buildResponse().getStatus(), SC_OK);
        assertEquals(1, continueResult.getTotalRequiredDataAmount());
        assertEquals(expectedRecord, actualRecord);
    }

    @Test
    void testWhenNonProjectAdminCallWithProjectRepo() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("rep_local", BuildInfo),
                LocalRepoConfig.create("repoToBeFilteredIn_1_local", BuildInfo),
                RemoteRepoConfig.create("repoToBeFilteredIn_2", Generic),
                RemoteRepoConfig.create("remoteRepo_4", Gradle),
                LocalRepoConfig.create("local", Generic),
                LocalRepoConfig.create("local_22", Gradle)
        );
        Map<String, String> projectRepos = Map.of(
                "local", "project1",
                "local_22", "project2"
        );

        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigsBySearchPattern(any())).thenReturn(allRepos);

        ProjectsService projectsService = mock(ProjectsService.class);
        when(projectsService.isProjectAdmin(eq("project1"))).thenReturn(false);
        when(projectsService.getRepoKeyToProjectMap(any(), anyBoolean())).thenReturn(projectRepos);

        service.setRepositoryConfigService(repositoryConfigService, projectsService);
        setMockingOfRequestParams("local", List.of(), List.of(), "", "", "project1,project2");

        Exception exception = Assertions.assertThrows(AuthorizationRestException.class, () -> {
            service.execute(request, response);
        });

        // Assert that the exception message is as expected
        assertEquals("HTTP 403 Forbidden", exception.getMessage());
    }

    @Test
    void testReposFilterByEnvironments() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("dev-repo", Generic),
                LocalRepoConfig.create("prod-repo", Generic),
                LocalRepoConfig.create("unassigned-repo", Generic)
        );

        RepositoryConfigService repoConfigService =
                mock(RepositoryConfigService.class);
        when(repoConfigService.getAllRepoConfigsBySearchPattern(any()))
                .thenReturn(allRepos);

        ProjectsService projService = mock(ProjectsService.class);
        when(projService.getEnvironmentEntities("dev-repo"))
                .thenReturn(List.of(envEntity("DEV")));
        when(projService.getEnvironmentEntities("prod-repo"))
                .thenReturn(List.of(envEntity("PROD")));
        when(projService.getEnvironmentEntities("unassigned-repo"))
                .thenReturn(List.of());

        service.setRepositoryConfigService(repoConfigService, projService);
        setMockingOfRequestParams(
                "", List.of(), List.of(), "", "", "", "DEV");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> result =
                (ContinueResult<AutoCompleteRepoConfigRecord>)
                        response.getIModel();

        assertEquals(SC_OK, response.buildResponse().getStatus());
        assertEquals(1, result.getTotalRequiredDataAmount());
        assertEquals("dev-repo", result.getData().get(0).repoKey());
        assertTrue(
                result.getData().get(0).stages().contains("dev"));
    }

    @Test
    void testReposFilterByMultipleEnvironments() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("dev-repo", Generic),
                LocalRepoConfig.create("prod-repo", Generic),
                LocalRepoConfig.create("staging-repo", Generic)
        );

        RepositoryConfigService repoConfigService =
                mock(RepositoryConfigService.class);
        when(repoConfigService.getAllRepoConfigsBySearchPattern(any()))
                .thenReturn(allRepos);

        ProjectsService projService = mock(ProjectsService.class);
        when(projService.getEnvironmentEntities("dev-repo"))
                .thenReturn(List.of(envEntity("DEV")));
        when(projService.getEnvironmentEntities("prod-repo"))
                .thenReturn(List.of(envEntity("PROD")));
        when(projService.getEnvironmentEntities("staging-repo"))
                .thenReturn(List.of(envEntity("STAGING")));

        service.setRepositoryConfigService(repoConfigService, projService);
        setMockingOfRequestParams(
                "", List.of(), List.of(), "", "", "", "DEV,PROD");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> result =
                (ContinueResult<AutoCompleteRepoConfigRecord>)
                        response.getIModel();

        assertEquals(SC_OK, response.buildResponse().getStatus());
        assertEquals(2, result.getTotalRequiredDataAmount());
        List<String> repoKeys = result.getData().stream()
                .map(AutoCompleteRepoConfigRecord::repoKey).toList();
        assertTrue(repoKeys.contains("dev-repo"));
        assertTrue(repoKeys.contains("prod-repo"));
    }

    @Test
    void testEmptyEnvironmentsReturnsAllRepos() {
        List<RepoConfig> allRepos = List.of(
                LocalRepoConfig.create("repo-a", Generic),
                LocalRepoConfig.create("repo-b", Generic)
        );

        RepositoryConfigService repoConfigService =
                mock(RepositoryConfigService.class);
        when(repoConfigService.getAllRepoConfigsBySearchPattern(any()))
                .thenReturn(allRepos);

        service.setRepositoryConfigService(repoConfigService, null);
        setMockingOfRequestParams(
                "", List.of(), List.of(), "", "", "", "");
        service.execute(request, response);

        ContinueResult<AutoCompleteRepoConfigRecord> result =
                (ContinueResult<AutoCompleteRepoConfigRecord>)
                        response.getIModel();

        assertEquals(SC_OK, response.buildResponse().getStatus());
        assertEquals(2, result.getTotalRequiredDataAmount());
    }

    private EnvironmentEntity envEntity(String name) {
        return EnvironmentEntity.builder().name(name).build();
    }

    private void setMockingOfRequestParams(
            String prefix, List<String> repoType, List<String> packageType,
            String limit, String continueState, String projectsKeysList) {
        setMockingOfRequestParams(
                prefix, repoType, packageType, limit,
                continueState, projectsKeysList, "");
    }

    private void setMockingOfRequestParams(
            String prefix, List<String> repoType, List<String> packageType,
            String limit, String continueState,
            String projectsKeysList, String stages) {
        when(request.getQueryParamByKey(AUTO_COMPLETE_SEARCH_PARAM))
                .thenReturn(prefix);
        when(request.getQueryParamListByKey(AUTO_COMPLETE_REPO_TYPE_PARAM))
                .thenReturn(repoType);
        when(request.getQueryParamListByKey(AUTO_COMPLETE_PACKAGE_TYPE_PARAM))
                .thenReturn(packageType);
        when(request.getQueryParamByKey(AUTO_COMPLETE_LIMIT_PARAM))
                .thenReturn(limit);
        when(request.getQueryParamByKey(AUTO_COMPLETE_CONTINUE_STATE_PARAM))
                .thenReturn(continueState);
        when(request.getQueryParamByKey(AUTO_COMPLETE_PROJECTS_PARAM))
                .thenReturn(projectsKeysList);
        when(request.getQueryParamByKey(AUTO_COMPLETE_STAGES_PARAM))
                .thenReturn(stages);
    }

    private long executeAndAssertPaging(String continueState, List<AutoCompleteRepoConfigRecord> repos) {
        long totalRequiredRepos = 0;
        long pagesNumber = 0;
        long max_pages_number = repos.size() + 1;

        when(request.getQueryParamByKey(AUTO_COMPLETE_CONTINUE_STATE_PARAM)).thenReturn(continueState);

        while (continueState != null && pagesNumber <= max_pages_number) {
            service.execute(request, response);
            assertEquals(response.buildResponse().getStatus(), SC_OK);
            assertTrue(response.getIModel() instanceof ContinueResult);

            ContinueResult<AutoCompleteRepoConfigRecord> continueResult =
                    (ContinueResult<AutoCompleteRepoConfigRecord>) response.getIModel();
            Long currentExecutionTotalRequiredRepos = continueResult.getTotalRequiredDataAmount();
            if ("0".equals(continueState) || "".equals(continueState)) {
                assertNotNull(currentExecutionTotalRequiredRepos);
                totalRequiredRepos = currentExecutionTotalRequiredRepos;
            } else {
                assertNull(currentExecutionTotalRequiredRepos);
            }

            continueState = continueResult.getContinueState();
            when(request.getQueryParamByKey(AUTO_COMPLETE_CONTINUE_STATE_PARAM)).thenReturn(continueState);
            repos.addAll(continueResult.getData());
            if (continueResult.getData().size() > 0) {
                pagesNumber++;
            }
        }
        assertEquals(totalRequiredRepos, repos.size());
        return pagesNumber;
    }

    private void setFederationCleanupFlag(boolean status) {
        setStringSystemProperty(ConstantValues.policyCleanupFederatedPackageEnabled, String.valueOf(status));
    }
}