package org.artifactory.repo.interceptor;


import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.storage.BeforeCopyAction;
import org.artifactory.addon.plugin.storage.BeforeMoveAction;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.storage.db.fs.model.DbMutableItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Amir Avidan
 */
class PluginsInterceptorTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private PluginsAddon pluginsAddon;
    @Mock
    private RepositoryService repositoryService;
    private AutoCloseable mocks;
    private PluginsInterceptor pluginsInterceptor;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        pluginsInterceptor = spy(new PluginsInterceptor());
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginsAddon);
        ReflectionTestUtils.setField(pluginsInterceptor, "addonsManager", addonsManager);
        ReflectionTestUtils.setField(pluginsInterceptor, "repoService", repositoryService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testNotSupportedPluginForCopyAction() {
        BulkCopyMoveContext bulkCopyMoveContext = mock(BulkCopyMoveContext.class);
        when(pluginsAddon.isPluginSupported(BeforeCopyAction.class)).thenReturn(false);
        pluginsInterceptor.beforeCopyFiles(bulkCopyMoveContext);
        verify(bulkCopyMoveContext, times(0)).getSourceItems();
    }

    @Test
    void testNotSupportedPluginForMoveAction() {
        BulkCopyMoveContext bulkCopyMoveContext = mock(BulkCopyMoveContext.class);
        when(pluginsAddon.isPluginSupported(BeforeMoveAction.class)).thenReturn(false);
        pluginsInterceptor.beforeMoveFiles(bulkCopyMoveContext);
        verify(bulkCopyMoveContext, times(0)).getSourceItems();
    }

    @Test
    void testBeforeMoveFilesWhenPluginExists() {
        BulkCopyMoveContext bulkCopyMoveContext = mock(BulkCopyMoveContext.class);
        when(pluginsAddon.isPluginSupported(BeforeMoveAction.class)).thenReturn(true);
        DbMutableItem dbMutableItem = mock(DbMutableItem.class);
        Properties properties = mock(Properties.class);
        when(dbMutableItem.getProperties()).thenReturn(properties);
        when(bulkCopyMoveContext.getSourceItems()).thenReturn(List.of(dbMutableItem));
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(dbMutableItem.getRepoKey())).thenReturn(
                localRepoDescriptor);
        RepoPath repoPath = mock(RepoPath.class);
        when(bulkCopyMoveContext.getTargetRepoPaths()).thenReturn(List.of(repoPath));
        pluginsInterceptor.beforeMoveFiles(bulkCopyMoveContext);
        verify(bulkCopyMoveContext, times(5)).getSourceItems();
        verify(dbMutableItem, times(1)).getProperties();
    }

    @Test
    void testBeforeCopyFilesWhenPluginExists() {
        BulkCopyMoveContext bulkCopyMoveContext = mock(BulkCopyMoveContext.class);
        when(pluginsAddon.isPluginSupported(BeforeCopyAction.class)).thenReturn(true);
        DbMutableItem dbMutableItem = mock(DbMutableItem.class);
        Properties properties = mock(Properties.class);
        when(dbMutableItem.getProperties()).thenReturn(properties);
        when(bulkCopyMoveContext.getSourceItems()).thenReturn(List.of(dbMutableItem));
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(dbMutableItem.getRepoKey())).thenReturn(
                localRepoDescriptor);
        RepoPath repoPath = mock(RepoPath.class);
        when(bulkCopyMoveContext.getTargetRepoPaths()).thenReturn(List.of(repoPath));
        pluginsInterceptor.beforeCopyFiles(bulkCopyMoveContext);
        verify(bulkCopyMoveContext, times(5)).getSourceItems();
        verify(dbMutableItem, times(1)).getProperties();
    }
}
