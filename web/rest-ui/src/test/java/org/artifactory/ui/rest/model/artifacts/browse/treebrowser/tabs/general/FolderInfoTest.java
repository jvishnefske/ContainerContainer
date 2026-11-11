package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.FolderInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class FolderInfoTest {

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

    @BeforeEach
    void setup() {
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testPopulateGeneralDataWhenTrash(boolean isLocal) {
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("auto-trashcan");
        when(repoPath.getName()).thenReturn("blala");
        FolderInfo folderInfo = new FolderInfo(repoPath, isLocal);
        assertEquals(folderInfo.getName(), "blala");
        assertNull(folderInfo.getRepositoryPath());
    }

    @Test
    void testPopulateGeneralData() {
        when(ContextHelper.get().beanForType(RepositoryService.class)).thenReturn(repositoryService);
        RepoPath repoPath = mock(RepoPath.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo);
        when(repoPath.getRepoKey()).thenReturn("repo");
        when(repoPath.getName()).thenReturn("blala");
        when(ContextHelper.get().beanForType(SecurityService.class)).thenReturn(securityService);
        when(securityService.currentUsername()).thenReturn("sec");
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(ContextHelper.get().beanForType(AddonsManager.class).addonByType(ArtifactWatchAddon.class))
                .thenReturn(artifactWatchAddon);
        when(itemInfo.getCreated()).thenReturn(1L);
        FolderInfo folderInfo = new FolderInfo(repoPath, true);
        assertEquals(folderInfo.getName(), "blala");
        assertNotNull(folderInfo.getRepositoryPath());
        assertTrue(folderInfo.getCreated().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"),
                "created should be ISO 8601 UTC, got: " + folderInfo.getCreated());
    }
}
