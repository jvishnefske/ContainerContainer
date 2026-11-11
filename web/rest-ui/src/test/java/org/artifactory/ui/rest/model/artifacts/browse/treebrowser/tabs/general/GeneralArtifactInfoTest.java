package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class GeneralArtifactInfoTest extends ArtifactoryHomeBoundJUnitTest {

    private AutoCloseable mockitoClosable;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private MutableCentralConfigDescriptor mutableCentralConfigDescriptor;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;

    @BeforeEach
    void setup() {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(ContextHelper.get().beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(ContextHelper.get().beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(ContextHelper.get().getRepositoryConfigService()).thenReturn(repositoryConfigService);
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        this.mockitoClosable.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testPopulateVirtualRepositoriesWithNullRepoKey(boolean isRemoveAssociateAndIncludedReposFromResponse) {
        GeneralArtifactInfo generalArtifactInfo = new GeneralArtifactInfo();
        generalArtifactInfo.setRepoKey(null);
        if (isRemoveAssociateAndIncludedReposFromResponse) {
            generalArtifactInfo.populateVirtualRepositories(true);
            Mockito.verify(repositoryConfigService, Mockito.times(0)).getVirtualRepoConfigsContainingRepoConfig(any());
        } else {
            generalArtifactInfo.populateVirtualRepositories(false);
            Mockito.verify(repositoryService, Mockito.times(0)).getVirtualReposContainingRepo(any());
        }
        Assertions.assertNull(generalArtifactInfo.getHasVirtualRepositoryAssociation());
    }

    @Test
    void testPopulateVirtualRepositories() {
        GeneralArtifactInfo generalArtifactInfo = new GeneralArtifactInfo();
        generalArtifactInfo.setRepoKey("hey");
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repositoryService.getRepoConfigByKey("hey")).thenReturn(repoConfig);
        when(repositoryService.hasDirectlyAssociatedVirtualRepos(repoConfig)).thenReturn(true);
        generalArtifactInfo.populateVirtualRepositories(true);
        verify(repositoryService, Mockito.times(1)).hasDirectlyAssociatedVirtualRepos(repoConfig);
        Assertions.assertEquals("true", generalArtifactInfo.getHasVirtualRepositoryAssociation());
        Assertions.assertNull(generalArtifactInfo.getVirtualRepositories());
    }

    @Test
    void testPopulateVirtualRepositoriesUsingRepositoryService() {
        GeneralArtifactInfo generalArtifactInfo = new GeneralArtifactInfo();
        generalArtifactInfo.setRepoKey("hey");
        RepoDescriptor descriptor = mock(RepoDescriptor.class);
        List<VirtualRepoDescriptor> virtualRepoDescriptorList = new ArrayList<>();
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        when(virtualRepoDescriptor.getKey()).thenReturn("hey");
        virtualRepoDescriptorList.add(virtualRepoDescriptor);
        when(repositoryService.repoDescriptorByKey("hey")).thenReturn(descriptor);
        when(repositoryService.getVirtualReposContainingRepo(descriptor)).thenReturn(virtualRepoDescriptorList);
        generalArtifactInfo.populateVirtualRepositories(false);
        assertNull(generalArtifactInfo.getHasVirtualRepositoryAssociation());
        assertEquals(1, generalArtifactInfo.getVirtualRepositories().size());
    }

    @Test
    void testPopulateVirtualRepositoriesEmpty() {
        GeneralArtifactInfo generalArtifactInfo = new GeneralArtifactInfo();
        generalArtifactInfo.setRepoKey("hey");
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repositoryService.getRepoConfigByKey("hey")).thenReturn(repoConfig);
        when(repositoryService.getVirtualRepoConfigsContainingRepoConfig(repoConfig, Integer.MAX_VALUE))
                .thenReturn(List.of());
        generalArtifactInfo.populateVirtualRepositories(true);
        verify(repositoryService).hasDirectlyAssociatedVirtualRepos(repoConfig);
        Assertions.assertEquals("false", generalArtifactInfo.getHasVirtualRepositoryAssociation());
        assertNull(generalArtifactInfo.getVirtualRepositories());
    }


    @Test
    void testPopulateVirtualRepositoriesEmptyUsingRepositoryService() {
        GeneralArtifactInfo generalArtifactInfo = new GeneralArtifactInfo();
        generalArtifactInfo.setRepoKey("hey");
        RepoDescriptor descriptor = mock(RepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey("hey")).thenReturn(descriptor);
        when(repositoryService.getVirtualReposContainingRepo(descriptor)).thenReturn(List.of());
        generalArtifactInfo.populateVirtualRepositories(false);
        verify(repositoryService, Mockito.times(1)).getVirtualReposContainingRepo(descriptor);
        assertNull(generalArtifactInfo.getHasVirtualRepositoryAssociation());
        Assertions.assertEquals(0, generalArtifactInfo.getVirtualRepositories().size());
    }

    @Test
    void testCalculateRepositoryAssociations() {
        GeneralArtifactInfo generalArtifactInfo = new GeneralArtifactInfo();
        RepoDescriptor descriptor = mock(RepoDescriptor.class);
        List<VirtualRepoDescriptor> virtualRepoDescriptorList = new ArrayList<>();
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        when(virtualRepoDescriptor.getKey()).thenReturn("hey");
        virtualRepoDescriptorList.add(virtualRepoDescriptor);
        generalArtifactInfo.populateVirtualRepositories(true);
        Mockito.verify(repositoryService, never()).getVirtualReposContainingRepo(descriptor);

    }

    @Test
    void setVirtualRepositoryAssociationCount() {
        GeneralArtifactInfo generalArtifactInfo = new GeneralArtifactInfo();
        generalArtifactInfo.setHasVirtualRepositoryAssociation("1");
        Assertions.assertEquals("1", generalArtifactInfo.getHasVirtualRepositoryAssociation());
    }

    @Test
    void populateGeneralDataForVirtualItem() {
        VirtualRepoItem virtualRepoItem = mock(VirtualRepoItem.class);
        GeneralArtifactInfo generalArtifactInfo = new GeneralArtifactInfo();
        assertThrows(UnsupportedOperationException.class,
                () -> generalArtifactInfo.populateGeneralDataForVirtualItem(virtualRepoItem));
    }
}
