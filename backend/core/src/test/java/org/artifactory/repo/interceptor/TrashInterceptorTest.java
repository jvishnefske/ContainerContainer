package org.artifactory.repo.interceptor;

import lombok.NonNull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.trash.NonTrashableItems;
import org.artifactory.repo.service.trash.TrashGCHelperImpl;
import org.artifactory.repo.service.trash.TrashServiceImpl;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.artifactory.repo.trash.TrashService.TRASH_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author TamirH
 */
@ExtendWith(MockitoExtension.class)
class TrashInterceptorTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock(lenient = true)
    PropertiesService propertiesService;
    @Mock(lenient = true)
    RepositoryService repositoryService;
    @Mock(lenient = true)
    AddonsManager addonsManager;
    @Mock(lenient = true)
    TrashGCHelperImpl trashGcHelper;
    @Mock(lenient = true)
    MutableStatusHolder mutableStatusHolder;
    TrashService trashService;
    TrashInterceptor trashInterceptor;

    @BeforeEach
    void init() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.gcEventsCleanupEnabled, "true");
        final InternalRepositoryService repoService = mock(InternalRepositoryService.class);
        final LocalConfig localConfig = mock(LocalConfig.class);
        lenient().when(localConfig.isCache()).thenReturn(false);
        lenient().when(repoService.localFederatedOrCachedRepoConfigByKey(anyString())).thenReturn(localConfig);
        final CentralConfigService configService = mock(CentralConfigService.class);
        final CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        final TrashcanConfigDescriptor trashcanConfigDescriptor = mock(TrashcanConfigDescriptor.class);
        lenient().when(trashcanConfigDescriptor.isEnabled()).thenReturn(true);
        lenient().when(centralConfigDescriptor.getTrashcanConfig()).thenReturn(trashcanConfigDescriptor);
        lenient().when(configService.getDescriptor()).thenReturn(centralConfigDescriptor);

        final NonTrashableItems nonTrashableItems = mock(NonTrashableItems.class);
        lenient().when(nonTrashableItems.skipTrash(localConfig, "test")).thenReturn(false);
        trashService =
                spy(new TrashServiceImpl(repoService, mock(SecurityService.class), configService, propertiesService,
                        mock(org.artifactory.storage.fs.service.PropertiesService.class),
                        nonTrashableItems,
                        trashGcHelper));
        lenient().doReturn(false).when(trashService).copyToTrash(any());
        trashInterceptor =
                spy(new TrashInterceptor(trashService, propertiesService, repositoryService, addonsManager,
                        trashGcHelper));
    }

    @AfterEach
    void after() {
        unbindArtifactoryHome();
    }

    @Test
    void testAfterDeleteNotCopiedToTrashMarkedForGc() {
        doReturn(true).when(trashService).shouldSkipCopyToTrash(any());
        final VfsFile<FileInfo> vfsFile = createMockVfsFile("test");
        final DeleteContext deleteContext = new DeleteContext(RepoPathFactory.create("test"));
        deleteContext.setTriggeredByMove(false);
        deleteContext.skipTrashcan(false);
        trashInterceptor.afterDelete(vfsFile, mutableStatusHolder, deleteContext);
        assertTrue(deleteContext.isMarkForEventsGc());
    }

    @Test
    void testAfterDeleteCopiedToTrashNotMarkedForGc() {
        doReturn(false).when(trashService).shouldSkipCopyToTrash(any());
        final VfsFile<FileInfo> vfsFile = createMockVfsFile("test");
        final DeleteContext deleteContext = new DeleteContext(RepoPathFactory.create("test"));
        deleteContext.setTriggeredByMove(false);
        deleteContext.skipTrashcan(false);
        trashInterceptor.afterDelete(vfsFile, mutableStatusHolder, deleteContext);
        assertFalse(deleteContext.isMarkForEventsGc());
    }

    @ParameterizedTest
    @MethodSource("deleteContextParamProvider")
    void testAfterDeleteWithContextConfiguration(boolean isTriggeredByMove, boolean isContextSkipTrash,
            boolean isTriggeredByGc, String repoKey, boolean expectedMarkForGc) {
        lenient().doReturn(false).when(trashService).copyToTrash(any());
        final VfsFile<FileInfo> vfsFile = createMockVfsFile(repoKey);
        final DeleteContext deleteContext = new DeleteContext(RepoPathFactory.create(repoKey, "path"));
        deleteContext.setTriggeredByMove(isTriggeredByMove);
        deleteContext.triggeredByGc(isTriggeredByGc);
        deleteContext.skipTrashcan(isContextSkipTrash);
        trashInterceptor.afterDelete(vfsFile, mutableStatusHolder, deleteContext);
        assertEquals(expectedMarkForGc, deleteContext.isMarkForEventsGc());
    }

    public static Stream<Arguments> deleteContextParamProvider() {
        return Stream.of(
                Arguments.of(true, false, false, TRASH_KEY, true),
                Arguments.of(true, false, false, "test", false),
                Arguments.of(false, true, false, "test", true),
                Arguments.of(false, false, true, TRASH_KEY, false),
                Arguments.of(true, true, true, "test", false),
                Arguments.of(false, false, false, "test", false),
                Arguments.of(false, false, false, TRASH_KEY, true)
        );
    }

    @Test
    void testEventsGcOverTrashBinariesFlag() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.gcTrashBinariesCleanupEnabled, "true");
        final VfsFile<FileInfo> vfsFile = createMockVfsFile(TRASH_KEY);
        final DeleteContext deleteContext = new DeleteContext(RepoPathFactory.create(TRASH_KEY, "path"));
        deleteContext.triggeredByGc(false);
        deleteContext.setTriggeredByMove(false);
        trashInterceptor.afterDelete(vfsFile, mutableStatusHolder, deleteContext);
        assertTrue(deleteContext.isMarkForEventsGc());
    }

    @Test
    void testBeforeCopyFiles() {
        lenient().doNothing().when(trashService).copyFilesToTrash(any());
        BulkCopyMoveContext deleteContext = mock(BulkCopyMoveContext.class);
        trashInterceptor.beforeCopyFiles(deleteContext);
        verify(trashService, times(1)).copyFilesToTrash(any());
    }

    @Test
    void testEventsGcFlagOff() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.gcEventsCleanupEnabled, "false");
        final VfsFile<FileInfo> vfsFile = createMockVfsFile(TRASH_KEY);
        final DeleteContext deleteContext = new DeleteContext(RepoPathFactory.create(TRASH_KEY, "path"));
        deleteContext.triggeredByGc(false);
        deleteContext.setTriggeredByMove(false);
        trashInterceptor.afterDelete(vfsFile, mutableStatusHolder, deleteContext);
        assertFalse(deleteContext.isMarkForEventsGc());
    }

    @Test
    void testAfterMove_EvidencePath() {
        VfsFile<FileInfo> src = mock(VfsFile.class);
        lenient().when(src.getRepoKey()).thenReturn(TRASH_KEY);
        lenient().when(src.getRepoPath()).thenReturn(RepoPathFactory.create(TRASH_KEY, "generic-local/.evidence"));
        VfsFile<FileInfo> dst =  mock(VfsFile.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext moveCopyContext = mock(InterceptorMoveCopyContext.class);
        trashInterceptor.afterMove(src, dst, mutableStatusHolder, properties, moveCopyContext);
        verify(propertiesService, never()).addPropertyRecursivelyMultiple(any(), any(), any(), anyBoolean());
    }

    @Test
    void testAfterMove_NotEvidencePath() {
        VfsFile<FileInfo> src = mock(VfsFile.class);
        lenient().when(src.getRepoKey()).thenReturn(TRASH_KEY);
        lenient().when(src.getRepoPath()).thenReturn(RepoPathFactory.create(TRASH_KEY, "generic-local/notevidence"));
        VfsFile<FileInfo> dst =  mock(VfsFile.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext moveCopyContext = mock(InterceptorMoveCopyContext.class);
        trashInterceptor.afterMove(src, dst, mutableStatusHolder, properties, moveCopyContext);
        verify(propertiesService).addPropertyRecursivelyMultiple(any(), any(), any(), anyBoolean());
    }

    @NonNull
    private VfsFile<FileInfo> createMockVfsFile(String repoKey) {
        final VfsFile<FileInfo> vfsFile = mock(VfsFile.class);
        when(vfsFile.isFile()).thenReturn(true);
        lenient().when(vfsFile.getRepoPath()).thenReturn(RepoPathFactory.create(repoKey, "path"));
        return vfsFile;
    }
}