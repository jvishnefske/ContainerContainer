package org.artifactory.ui.utils;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.includedrepositories.Repository;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.virtualrepositories.VirtualRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneralArtifactsInfoUtilsTest {
    private static final String BASE_URL = "http://localhost:8081/artifactory/";
    private static final String REPO_KEY = "repoKey";
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AuthorizationService authorizationService;

    private static MockedStatic<RequestThreadLocal> requestThreadLocalMock;


    @BeforeAll
    static void setUp() {
        requestThreadLocalMock = mockStatic(RequestThreadLocal.class);
        requestThreadLocalMock.when(RequestThreadLocal::getBaseUrl).thenReturn(BASE_URL);
    }

    @AfterAll
    static void tearDown() {
        requestThreadLocalMock.close();
    }

    @Test
    void testPopulateVirtualRepositories() {
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey(REPO_KEY)).thenReturn(repoDescriptor);

        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        when(virtualRepoDescriptor.getKey()).thenReturn(REPO_KEY);
        when(repositoryService.getVirtualReposContainingRepo(repoDescriptor)).thenReturn(
                List.of(virtualRepoDescriptor));

        List<VirtualRepository> result =
                GeneralArtifactsInfoUtils.populateVirtualRepositories(repositoryService, REPO_KEY);

        assertEquals(1, result.size());
        assertEquals(REPO_KEY, result.get(0).getRepoKey());
        assertEquals(BASE_URL + REPO_KEY, result.get(0).getLinkUrl());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUpdateVirtualIncludedRepositories(boolean canRead) {
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        if (canRead) {
            when(repoDescriptor.getKey()).thenReturn(REPO_KEY);
        }
        when(virtualRepoDescriptor.getRepositories()).thenReturn(List.of(repoDescriptor));
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any(), any(), any())).thenReturn(canRead);

        List<Repository> result = GeneralArtifactsInfoUtils
                .updateVirtualIncludedRepositories(virtualRepoDescriptor, authorizationService);

        if (canRead) {
            assertEquals(1, result.size());
            assertEquals(REPO_KEY, result.get(0).getRepoKey());
            assertEquals(BASE_URL + REPO_KEY, result.get(0).getLinkUrl());
        } else {
            assertEquals(0, result.size());
        }
    }

}