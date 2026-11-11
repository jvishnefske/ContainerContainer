package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class ReorderRepositoriesServiceTest {

    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private ArtifactoryRestResponse response;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @InjectMocks
    private ReorderRepositoriesService reorderRepositoriesService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(response.error(anyString())).thenReturn(response);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testReorderReposNullRepoTypeReturnErrorResponse() {
        when(request.getPathParamByKey("repoType")).thenReturn(null);

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorMsgCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).responseCode(statusCodeCaptor.capture());
        verify(response).error(errorMsgCaptor.capture());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
        Assertions.assertEquals("The type of repositories to reorder must be specified", errorMsgCaptor.getValue());
        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderReposBlankRepoTypeReturnErrorResponse() {
        when(request.getPathParamByKey("repoType")).thenReturn(StringUtils.EMPTY);

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorMsgCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).responseCode(statusCodeCaptor.capture());
        verify(response).error(errorMsgCaptor.capture());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
        Assertions.assertEquals("The type of repositories to reorder must be specified", errorMsgCaptor.getValue());
        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderReposNullRepoOrderListReturnErrorResponse() {
        when(request.getPathParamByKey("repoType")).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(request.getModels()).thenReturn(null);

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorMsgCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).responseCode(statusCodeCaptor.capture());
        verify(response).error(errorMsgCaptor.capture());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
        Assertions.assertEquals("No list to reorder by was sent.", errorMsgCaptor.getValue());
        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderReposEmptyRepoOrderListReturnErrorResponse() {
        when(request.getPathParamByKey("repoType")).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(request.getModels()).thenReturn(List.of());

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorMsgCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).responseCode(statusCodeCaptor.capture());
        verify(response).error(errorMsgCaptor.capture());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
        Assertions.assertEquals("No list to reorder by was sent.", errorMsgCaptor.getValue());
        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderReposInvalidRepoTypeReturnErrorResponse() {
        when(request.getPathParamByKey("repoType")).thenReturn(RepoDetailsType.DISTRIBUTION_REPO);
        when(request.getModels()).thenReturn(List.of("repoKey"));

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorMsgCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).responseCode(statusCodeCaptor.capture());
        verify(response).error(errorMsgCaptor.capture());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
        Assertions.assertEquals("Invalid repository type given: " + RepoDetailsType.DISTRIBUTION_REPO,
                errorMsgCaptor.getValue());
        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderReposNoCurrentReposDoNothing() {
        when(request.getPathParamByKey("repoType")).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(request.getModels()).thenReturn(List.of("repoKey"));
        when(repositoryService.getLocalRepoConfigsIncludingInfoRepos()).thenReturn(List.of());

        reorderRepositoriesService.execute(request, response);

        verify(response, times(0)).responseCode(anyInt());
        verify(response, times(0)).error(anyString());
        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderIllegalOrderMoreNewKeysThanOldReturnErrorResponse() {
        when(request.getPathParamByKey("repoType")).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(request.getModels()).thenReturn(List.of("repoKey1", "repoKey2"));
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig.getKey()).thenReturn("repoKey");
        when(repositoryService.getLocalRepoConfigsIncludingInfoRepos()).thenReturn(List.of(repoConfig));

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errMsgCaptor = ArgumentCaptor.forClass(String.class);


        verify(response).responseCode(statusCodeCaptor.capture());
        verify(response).error(errMsgCaptor.capture());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
        Assertions.assertEquals("The size of the list to order by does not match the size of the current repo list, " +
                "aborting.", errMsgCaptor.getValue());

        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderIllegalOrderLessNewKeysThanOldReturnErrorResponse() {
        when(request.getPathParamByKey("repoType")).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(request.getModels()).thenReturn(List.of("repoKey1"));
        LocalRepoConfig repoConfig1 = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig1.getKey()).thenReturn("repoKey1");
        LocalRepoConfig repoConfig2 = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig2.getKey()).thenReturn("repoKey2");
        when(repositoryService.getLocalRepoConfigsIncludingInfoRepos()).thenReturn(List.of(repoConfig1, repoConfig2));

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errMsgCaptor = ArgumentCaptor.forClass(String.class);

        verify(response).responseCode(statusCodeCaptor.capture());
        verify(response).error(errMsgCaptor.capture());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
        Assertions.assertEquals("The size of the list to order by does not match the size of the current repo list, " +
                "aborting.", errMsgCaptor.getValue());

        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderNewKeysNotContainedInOldReturnErrorResponse() {
        when(request.getPathParamByKey("repoType")).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(request.getModels()).thenReturn(List.of("repoKey1", "repoKey2"));
        LocalRepoConfig repoConfig1 = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig1.getKey()).thenReturn("repoKey3");
        LocalRepoConfig repoConfig2 = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig2.getKey()).thenReturn("repoKey4");
        when(repositoryService.getLocalRepoConfigsIncludingInfoRepos()).thenReturn(List.of(repoConfig1, repoConfig2));

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errMsgCaptor = ArgumentCaptor.forClass(String.class);

        verify(response).responseCode(statusCodeCaptor.capture());
        verify(response).error(errMsgCaptor.capture());

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, statusCodeCaptor.getValue());
        Assertions.assertEquals("The new order list is missing \\ has excess repositories that are currently saved." +
                RepoDetailsType.LOCAL_REPO, errMsgCaptor.getValue());

        verify(repositoryConfigService, times(0)).bulkUpsertRepoConfigs(anyList(), anyBoolean());
    }

    @Test
    void testReorderSuccess() {
        when(request.getPathParamByKey("repoType")).thenReturn(RepoDetailsType.LOCAL_REPO);
        when(request.getModels()).thenReturn(List.of("repoKey1", "repoKey2", "repoKey3", "repoKey4"));
        LocalRepoConfig repoConfig1 = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig1.getKey()).thenReturn("repoKey1");
        LocalRepoConfig repoConfig2 = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig2.getKey()).thenReturn("repoKey2");
        LocalRepoConfig repoConfig3 = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig3.getKey()).thenReturn("repoKey3");
        LocalRepoConfig repoConfig4 = mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
        when(repoConfig4.getKey()).thenReturn("repoKey4");
        when(repositoryService.getLocalRepoConfigsIncludingInfoRepos()).thenReturn(
                List.of(repoConfig2, repoConfig1, repoConfig4, repoConfig3));

        reorderRepositoriesService.execute(request, response);

        ArgumentCaptor<List<RepoConfig>> repoConfigsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Boolean> reloadCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(repositoryConfigService).bulkUpsertRepoConfigs(repoConfigsCaptor.capture(),
                reloadCaptor.capture());
        List<RepoConfig> orderedRepoConfigs = repoConfigsCaptor.getValue();

        Assertions.assertEquals("repoKey1", orderedRepoConfigs.get(0).getKey());
        Assertions.assertEquals("repoKey2", orderedRepoConfigs.get(1).getKey());
        Assertions.assertEquals("repoKey3", orderedRepoConfigs.get(2).getKey());
        Assertions.assertEquals("repoKey4", orderedRepoConfigs.get(3).getKey());
        Assertions.assertTrue(reloadCaptor.getValue());
    }
}