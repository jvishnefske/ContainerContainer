package org.artifactory.storage.db.fs.model;


import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.exception.CancelException;
import org.artifactory.factory.InfoFactory;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.model.fs.ItemInfoUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.NodeMetaInfoService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rom Kurtz
 */
class DbMutableItemTest {

    private ArtifactoryContext artifactoryContext;
    private PropertiesService propertiesService;
    private NodeMetaInfoService metaInfoService;
    private InfoFactory infoFactory;
    private FileInfoImpl fileInfo;

    @BeforeEach
    void setUp() {
        metaInfoService = mock(NodeMetaInfoService.class);
        doNothing().when(metaInfoService).updateNodePropertiesModified(anyLong(), anyLong());
        artifactoryContext = mock(ArtifactoryContext.class);
        when(artifactoryContext.beanForType(NodeMetaInfoService.class)).thenReturn(metaInfoService);
        propertiesService = mock(PropertiesService.class);
        when(artifactoryContext.beanForType(PropertiesService.class)).thenReturn(propertiesService);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.currentUsername()).thenReturn("admin");
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        infoFactory = mock(InfoFactory.class);
        RepoPathImpl repoPath = new RepoPathImpl("fake-key", "fake/path");
        fileInfo = new FileInfoImpl(repoPath);
        when(infoFactory.copyFileInfo(fileInfo)).thenReturn(fileInfo);
        when(infoFactory.copyItemInfo(fileInfo)).thenReturn(fileInfo);
    }

    @Test
    void testSaveMarkForDeletion() {
        StoringRepo storingRepo = mock(StoringRepo.class);
        DbMutableFile dbMutableFile = new DbMutableFile(storingRepo, 1L, fileInfo);
        dbMutableFile.markForDeletion = true;
        DbMutableFile spiedDbMutableFile = spy(dbMutableFile);
        try (MockedStatic<ContextHelper> contextHelperMockedStatic = mockStatic(ContextHelper.class)) {
            contextHelperMockedStatic.when(ContextHelper::get).thenReturn(artifactoryContext);
            DbMutableItemDeleteHelper deleteHelper = mock(DbMutableItemDeleteHelper.class);
            doNothing().when(deleteHelper).deleteItem(anyLong());
            when(artifactoryContext.beanForType(DbMutableItemDeleteHelper.class)).thenReturn(deleteHelper);
            assertTrue(spiedDbMutableFile.save());
            verify(deleteHelper).deleteItem(anyLong());
        }
    }

    @Test
    void testDeleteDbMutableItems() {
        DeleteContext deleteContext = new DeleteContext(new RepoPathImpl("repoKey", "path"));

        DbMutableItem dbMutableFolder = Mockito.mock(DbMutableFolder.class);
        Mockito.doCallRealMethod().when(dbMutableFolder).delete(deleteContext);
        dbMutableFolder.delete(deleteContext);
        verify(dbMutableFolder, times(1)).delete(any(), any());

        DbMutableItem mutableFsFile = Mockito.mock(DbMutableFile.class);
        Mockito.doCallRealMethod().when(mutableFsFile).delete(deleteContext);
        mutableFsFile.delete(deleteContext);
        verify(mutableFsFile, times(1)).delete(any(), any());
    }

    @Test
    void fileDeleteUsesProvidedDeleteContextForCanDelete() {
        DeleteContext deleteContext = new DeleteContext(fileInfo.getRepoPath());
        try (MockedStatic<InfoFactoryHolder> infoFactoryHolderMockedStatic = mockStatic(InfoFactoryHolder.class)) {
            infoFactoryHolderMockedStatic.when(InfoFactoryHolder::get).thenReturn(infoFactory);
            StoringRepo storingRepo = mock(StoringRepo.class);
            DbMutableFile file = spy(new DbMutableFile(storingRepo, 1L, fileInfo));
            doReturn(false).when(file).isNew();
            doReturn(false).when(file).canDelete(deleteContext);

            file.delete(deleteContext, new BasicStatusHolder());

            verify(file).canDelete(deleteContext);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true, false",
            "false, false",
            "true, true",
            "false, true",
    })
    void canDelete(boolean canDelete, boolean expectError) {
        try (MockedStatic<StorageContextHelper> storageContextHelperStatic = mockStatic(StorageContextHelper.class);
                MockedStatic<DbMutableItem> itemMockedStatic = mockStatic(DbMutableItem.class)) {
            ArtifactoryStorageContext storageContext = mock(ArtifactoryStorageContext.class);
            storageContextHelperStatic.when(StorageContextHelper::get).thenReturn(storageContext);
            StorageInterceptors storageInterceptors = mock(StorageInterceptors.class);
            when(storageInterceptors.canDelete(any(), any(), any())).thenReturn(canDelete);
            DbMutableItem item = Mockito.mock(DbMutableItem.class, Mockito.CALLS_REAL_METHODS);
            RepoPath repoPath = new RepoPathImpl("repo", "path");
            doReturn(repoPath).when(item).getRepoPath();
            when(storageContext.beanForType(StorageInterceptors.class)).thenReturn(storageInterceptors);
            BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);
            itemMockedStatic.when(DbMutableItem::getStatusHolder).thenReturn(statusHolder);
            if (expectError) {
                when(statusHolder.isError()).thenReturn(true);
                when(statusHolder.getCancelException()).thenReturn(new CancelException("error", 400));
                assertThrows(CancelException.class, () -> item.canDelete());
            } else {
                when(statusHolder.isError()).thenReturn(false);
                boolean result = assertDoesNotThrow(() -> item.canDelete());
                assertEquals(canDelete, result);
            }
        }
    }

    @Test
    void testFillInfo() {
        try (MockedStatic<ItemInfoUtils> itemInfoUtilsMockedStatic = mockStatic(ItemInfoUtils.class)) {
            itemInfoUtilsMockedStatic
                    .when(ItemInfoUtils::isUpdateCreatedTimestampOnCopyOrMoveEnabled).thenReturn(true);
            var dbMutableItemMock = Mockito.mock(DbMutableItem.class);
            doCallRealMethod().when(dbMutableItemMock).fillInfo(any());
            mockTimeSupplier(dbMutableItemMock);
            var itemInfoMock = createItemInfoMock();
            dbMutableItemMock.fillInfo(itemInfoMock);
            verify(dbMutableItemMock).setCreated(2L);

            itemInfoUtilsMockedStatic
                    .when(ItemInfoUtils::isUpdateCreatedTimestampOnCopyOrMoveEnabled).thenReturn(false);
            dbMutableItemMock.fillInfo(itemInfoMock);
            verify(dbMutableItemMock).setCreated(itemInfoMock.getCreated());
        }
    }

    private ItemInfo createItemInfoMock() {
        ItemInfo itemInfoMock = mock(ItemInfo.class);
        when(itemInfoMock.getCreated()).thenReturn(1L);
        return itemInfoMock;
    }

    private void mockTimeSupplier(DbMutableItem dbMutableItemMock) {
        LongSupplier timeSupplierMock = mock(LongSupplier.class);
        when(timeSupplierMock.getAsLong()).thenReturn(2L);
        dbMutableItemMock.currentTimeSupplier = timeSupplierMock;
    }
}