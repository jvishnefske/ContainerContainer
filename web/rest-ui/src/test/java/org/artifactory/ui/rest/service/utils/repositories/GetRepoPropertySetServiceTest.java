package org.artifactory.ui.rest.service.utils.repositories;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.ui.rest.service.utils.repoPropertySet.GetRepoPropertySetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetRepoPropertySetServiceTest {

    private static final String REPO_KEY = "example-repo";
    private static final String PATH = "example-path";

    @Mock
    private RepositoryService repoService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryRestRequest<?> request;
    @Mock
    private ArtifactoryRestResponse<?> response;
    private AutoCloseable closeable;
    @InjectMocks
    private GetRepoPropertySetService getRepoPropertySetService;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void teardown() throws Exception {
        closeable.close();
    }

    @Test
    void testExecuteLocalWithAuthorizedUser() {
        RepoPath repoPath = setupRequestAndRepoPath(REPO_KEY, PATH);
        mockAuthorization(repoPath, true);
        mockLocalRepoDescriptor(REPO_KEY, List.of());

        getRepoPropertySetService.execute(request, response);

        verify(response).iModelList(anyList());
    }

    @Test
    void testExecuteVirtualWithAuthorizedUser() {
        String virtualRepoKey = "virtual-example-repo";
        String virtualPath = "virtual-example-path";
        String localRepoKey = "local-example-repo";
        String localPath = "local-example-path";

        RepoPath virtualRepoPath = setupRequestAndRepoPath(virtualRepoKey, virtualPath);
        ItemInfo itemInfo = mockVirtualRepo(virtualRepoKey, virtualRepoPath, localRepoKey, localPath);
        mockAuthorization(itemInfo.getRepoPath(), true);
        mockLocalRepoDescriptor(localRepoKey, List.of());

        getRepoPropertySetService.execute(request, response);

        verify(response).iModelList(anyList());
    }

    @Test
    void testExecuteWithMissingRepository() {
        RepoPath repoPath = setupRequestAndRepoPath(REPO_KEY, PATH);
        mockAuthorization(repoPath, true);
        when(repoService.localFederatedCachedRepoDescriptorByKey(REPO_KEY)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> getRepoPropertySetService.execute(request, response));
    }

    @Test
    void testExecuteWithUnauthorizedUser() {
        RepoPath repoPath = setupRequestAndRepoPath(REPO_KEY, PATH);
        mockAuthorization(repoPath, false);
        when(response.responseCode(SC_FORBIDDEN)).thenReturn(response);

        getRepoPropertySetService.execute(request, response);

        verify(response).responseCode(SC_FORBIDDEN);
    }

    private RepoPath setupRequestAndRepoPath(String repoKey, String path) {
        when(request.getQueryParamByKey("repoKey")).thenReturn(repoKey);
        when(request.getQueryParamByKey("path")).thenReturn(path);
        return InternalRepoPathFactory.create(repoKey, path);
    }

    private void mockAuthorization(RepoPath repoPath, boolean canRead) {
        when(authorizationService.canRead(repoPath)).thenReturn(canRead);
    }

    private void mockLocalRepoDescriptor(String repoKey, List<PropertySet> propertySets) {
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(localRepoDescriptor.getPropertySets()).thenReturn(propertySets);
        when(repoService.localFederatedCachedRepoDescriptorByKey(repoKey)).thenReturn(localRepoDescriptor);
    }

    private ItemInfo mockVirtualRepo(String virtualRepoKey, RepoPath virtualRepoPath, String localRepoKey,
            String localPath) {
        when(repoService.isVirtualRepoExist(virtualRepoKey)).thenReturn(true);
        ItemInfo itemInfo = mock(ItemInfo.class);
        RepoPath localRepoPath = InternalRepoPathFactory.create(localRepoKey, localPath);
        when(itemInfo.getRepoPath()).thenReturn(localRepoPath);
        when(repoService.getVirtualItemInfo(virtualRepoPath)).thenReturn(itemInfo);
        return itemInfo;
    }
}