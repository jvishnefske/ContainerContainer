package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general;

import java.util.Set;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
public class VirtualRemoteRepoGeneralArtifactInfoTest extends ArtifactoryHomeBoundJUnitTest {

    private AutoCloseable mockitoClosable;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private MutableCentralConfigDescriptor mutableCentralConfigDescriptor;
    @Mock
    private AuthorizationService authorizationService;

    @BeforeEach
    void setup() {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(ContextHelper.get().beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        this.mockitoClosable.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateVirtualIncludedRepositories(boolean flag) {
        VirtualRepoDescriptor repoDescriptor = mock(VirtualRepoDescriptor.class);
        VirtualRemoteRepoGeneralArtifactInfo virtualRemoteRepoGeneralArtifactInfo =
                new VirtualRemoteRepoGeneralArtifactInfo();
        List<RepoDescriptor> repositories = new ArrayList<>();
        repositories.add(repoDescriptor);
        when(repoDescriptor.getRepositories()).thenReturn(repositories);
        when(repoDescriptor.getKey()).thenReturn("key");
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq(repoDescriptor.getKey()),
                eq(Set.of(ArtifactoryPermission.READ)), anyMap())).thenReturn(true);
        virtualRemoteRepoGeneralArtifactInfo.setRemoveAssociateAndIncludedReposFromResponse(!flag);
        virtualRemoteRepoGeneralArtifactInfo.updateVirtualIncludedRepositories(repoDescriptor, authorizationService);
        if (flag) {
            assertEquals(1, virtualRemoteRepoGeneralArtifactInfo.getIncludedRepositories().size());
            assertNull(virtualRemoteRepoGeneralArtifactInfo.getHasVirtualRepositoryAssociation());
        } else {
            virtualRemoteRepoGeneralArtifactInfo
                    .updateVirtualIncludedRepositories(repoDescriptor, authorizationService);
            assertNull(virtualRemoteRepoGeneralArtifactInfo.getIncludedRepositories());
            assertEquals("true", virtualRemoteRepoGeneralArtifactInfo.getHasIncludedRepositories());
        }
    }

    @Test
    void updateVirtualIncludedRepositoriesWhenEmpty() {
        VirtualRepoDescriptor repoDescriptor = mock(VirtualRepoDescriptor.class);
        VirtualRemoteRepoGeneralArtifactInfo virtualRemoteRepoGeneralArtifactInfo =
                new VirtualRemoteRepoGeneralArtifactInfo();
        virtualRemoteRepoGeneralArtifactInfo.updateVirtualIncludedRepositories(repoDescriptor, authorizationService);
        assertEquals(0, virtualRemoteRepoGeneralArtifactInfo.getIncludedRepositories().size());
        assertNull(virtualRemoteRepoGeneralArtifactInfo.getHasVirtualRepositoryAssociation());
        verify(repoDescriptor, times(1)).getRepositories();
    }

    @Test
    void populateGeneralDataForVirtualItem() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);

        when(ContextHelper.get().beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(ContextHelper.get().beanForType(AuthorizationService.class)).thenReturn(authorizationService);

        VirtualRepoItem vRepoItem = mock(VirtualRepoItem.class);
        VirtualRemoteRepoGeneralArtifactInfo virtualRemoteRepoGeneralArtifactInfo =
                Mockito.spy(new VirtualRemoteRepoGeneralArtifactInfo());
        virtualRemoteRepoGeneralArtifactInfo.setRepoKey("key");
        virtualRemoteRepoGeneralArtifactInfo.setPath("path");

        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey("key")).thenReturn(repoDescriptor);

        virtualRemoteRepoGeneralArtifactInfo.populateGeneralDataForVirtualItem(vRepoItem);

        verify(virtualRemoteRepoGeneralArtifactInfo, times(1))
                .updateVirtualIncludedRepositories(any(), any());
    }


}
