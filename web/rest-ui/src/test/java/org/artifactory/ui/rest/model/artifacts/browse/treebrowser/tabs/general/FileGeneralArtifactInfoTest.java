package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.FileInfoUiModel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class FileGeneralArtifactInfoTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactWatchAddon artifactWatchAddon;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private Properties properties;
    @Mock
    private AuthorizationService authService;

    @BeforeEach
    void setup() {
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testPopulateGeneralData() {
        FileGeneralArtifactInfo fileGeneralArtifactInfo = new FileGeneralArtifactInfo();
        LocalRepoDescriptor mockLocalRepoDescriptor = mock(LocalRepoDescriptor.class);
        fileGeneralArtifactInfo.setRepoKey("hey");
        fileGeneralArtifactInfo.setPath("there");
        RepoPath repoPath = fileGeneralArtifactInfo.retrieveRepoPath();
        FileInfo fileInfo = mock(FileInfo.class);
        when(ContextHelper.get().beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(fileInfo);
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey(repoPath.getRepoKey())).thenReturn(
                mockLocalRepoDescriptor);
        when(fileInfo.getCreated()).thenReturn(1L);
        ModuleInfo moduleInfo = mock(ModuleInfo.class);
        when(repositoryService.getItemModuleInfo(repoPath)).thenReturn(moduleInfo);
        when(fileInfo.isFolder()).thenReturn(true);
        when(ContextHelper.get().beanForType(SecurityService.class)).thenReturn(securityService);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(ContextHelper.get().beanForType(AddonsManager.class).addonByType(ArtifactWatchAddon.class))
                .thenReturn(artifactWatchAddon);
        ChecksumsInfo checksumInfo = mock(ChecksumsInfo.class);
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumInfo);
        when(securityService.currentUsername()).thenReturn("sec");
        when(ContextHelper.get().beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(propertiesService.getProperties(fileInfo)).thenReturn(properties);
        when(propertiesService.getProperties(repoPath)).thenReturn(properties);
        when(mockLocalRepoDescriptor.getType()).thenReturn(RepoType.OCI);
        when(ContextHelper.get().getRepositoryService()).thenReturn(repositoryService);
        when(ContextHelper.get().getAuthorizationService()).thenReturn(authService);
        fileGeneralArtifactInfo.populateGeneralData();
        assertNull(fileGeneralArtifactInfo.getTrash());
        verify(repositoryService, times(1)).getItemInfo(repoPath);

        FileInfoUiModel info = (FileInfoUiModel) fileGeneralArtifactInfo.getInfo();
        String isoPattern = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";
        assertTrue(info.getCreated().matches(isoPattern),
                "created should be ISO 8601 UTC, got: " + info.getCreated());
        assertTrue(info.getLastModified().matches(isoPattern),
                "lastModified should be ISO 8601 UTC, got: " + info.getLastModified());
    }

    @Test
    void testPopulateGeneralData_pathIsFolder() {
        FileGeneralArtifactInfo fileGeneralArtifactInfo = new FileGeneralArtifactInfo();
        LocalRepoDescriptor mockLocalRepoDescriptor = mock(LocalRepoDescriptor.class);
        fileGeneralArtifactInfo.setRepoKey("hey");
        fileGeneralArtifactInfo.setPath("there");
        RepoPath repoPath = fileGeneralArtifactInfo.retrieveRepoPath();
        when(ContextHelper.get().beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey(repoPath.getRepoKey()))
                .thenReturn(mockLocalRepoDescriptor);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(mock(FolderInfo.class));

        fileGeneralArtifactInfo.populateGeneralData();

        assertNull(fileGeneralArtifactInfo.getInfo());
        assertNull(fileGeneralArtifactInfo.getChecksums());
        assertNull(fileGeneralArtifactInfo.getDependencyDeclaration());
        assertNull(fileGeneralArtifactInfo.getTrash());
        verify(repositoryService, never()).getFileInfo(any());
    }

    @Test
    void testPopulateGeneralDataTrash() {
        FileGeneralArtifactInfo fileGeneralArtifactInfo = new FileGeneralArtifactInfo();
        fileGeneralArtifactInfo.setRepoKey("auto-trashcan");
        fileGeneralArtifactInfo.setPath("there");
        RepoPath repoPath = fileGeneralArtifactInfo.retrieveRepoPath();
        when(ContextHelper.get().beanForType(PropertiesService.class)).thenReturn(propertiesService);
        when(propertiesService.getProperties(repoPath)).thenReturn(properties);
        fileGeneralArtifactInfo.populateGeneralData();
        assertNotNull(fileGeneralArtifactInfo.getTrash());
    }
}