package org.artifactory.repo.interceptor;

import jakarta.annotation.Nonnull;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.UnifiedEventService;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnifiedEventsInterceptorTest extends ArtifactoryHomeBoundJUnitTest {


    @Mock
    private AddonsManager addonsManager;
    @Mock
    private WorkersAddon addon;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private UnifiedEventService unifiedEventService;
    @InjectMocks
    private UnifiedEventsInterceptor interceptor;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        when(addonsManager.addonByType(WorkersAddon.class)).thenReturn(addon);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    public static Stream<Arguments> afterCreateSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1),
                Arguments.of(null, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("afterCreateSource")
    void testAfterCreate(LocalRepoDescriptor desc, int expectedTimes) {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(desc);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.AFTER_CREATE, vfsItem, null, null);

        interceptor.afterCreate(vfsItem, null, null);

        verify(unifiedEventService, times(expectedTimes))
                .sendStorageEvent(SupportedUnifiedEvents.AFTER_CREATE, vfsItem, null, null);
    }

    @ParameterizedTest
    @MethodSource("beforeCreateSource")
    void testBeforeCreate(LocalRepoDescriptor desc, int expectedTimes) {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(desc);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_CREATE, vfsItem, null, null);

        interceptor.beforeCreate(vfsItem, null);

        verify(unifiedEventService, times(expectedTimes)).sendStorageEvent(SupportedUnifiedEvents.BEFORE_CREATE,
                vfsItem,
                null, null);
    }

    public static Stream<Arguments> beforeCreateSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1),
                Arguments.of(null, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("afterCopySource")
    void testAfterCopy(LocalRepoDescriptor src, LocalRepoDescriptor dest, int expectedTimes, boolean isTargetNull) {
        String srcRepoKey = "srcRepoKey";
        VfsItem<ItemInfo> srcItem = getVfsItem(srcRepoKey);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(srcRepoKey)).thenReturn(src);
        String dstRepoKey = "dstRepoKey";
        VfsItem<ItemInfo> dstItem = null;
        RepoPath targetPath = null;
        if (!isTargetNull) {
            dstItem = getVfsItem(dstRepoKey);
            targetPath = dstItem.getInfo().getRepoPath();
        }
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(dstRepoKey)).thenReturn(dest);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.AFTER_COPY, srcItem, null, targetPath);

        interceptor.afterCopy(srcItem, dstItem, null, null, null);

        verify(unifiedEventService, times(expectedTimes))
                .sendStorageEvent(SupportedUnifiedEvents.AFTER_COPY, srcItem, null, targetPath);
    }

    @ParameterizedTest
    @MethodSource("afterMoveSource")
    void testAfterMove(LocalRepoDescriptor src, LocalRepoDescriptor dest, int expectedTimes) {
        String srcRepoKey = "srcRepoKey";
        VfsItem<ItemInfo> srcItem = getVfsItem(srcRepoKey);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(srcRepoKey)).thenReturn(src);

        String dstRepoKey = "dstRepoKey";
        VfsItem<ItemInfo> dstItem = getVfsItem(dstRepoKey);
        RepoPath targetPath = dstItem.getInfo().getRepoPath();
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(dstRepoKey)).thenReturn(dest);

        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.AFTER_MOVE, srcItem, null, targetPath);

        interceptor.afterMove(srcItem, dstItem, null, null, null);

        verify(unifiedEventService, times(expectedTimes))
                .sendStorageEvent(SupportedUnifiedEvents.AFTER_MOVE, srcItem, null, targetPath);
    }

    @ParameterizedTest
    @MethodSource("beforeMoveSource")
    void testBeforeMove(LocalRepoDescriptor src, int expectedTimes) {
        String srcRepoKey = "srcRepoKey";
        VfsItem<ItemInfo> srcItem = getVfsItem(srcRepoKey);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(srcRepoKey)).thenReturn(src);
        RepoPath targetRepoPath = mock(RepoPath.class);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_MOVE, srcItem, null, targetRepoPath);

        interceptor.beforeMove(srcItem, targetRepoPath, null, null);

        verify(unifiedEventService, times(expectedTimes))
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_MOVE, srcItem, null, targetRepoPath);
    }

    @ParameterizedTest
    @MethodSource("beforeMoveFilesSource")
    void testBeforeMoveFiles(LocalRepoDescriptor src, int expectedTimes) {
        String srcRepoKey = "srcRepoKey";
        VfsItem<ItemInfo> srcItem = getVfsItem(srcRepoKey);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(srcRepoKey)).thenReturn(src);
        RepoPath targetRepoPath = mock(RepoPath.class);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_MOVE, srcItem, null, targetRepoPath);

        List<VfsItem<? extends ItemInfo>> sourceItems = new ArrayList<>();
        sourceItems.add(srcItem);
        List<RepoPath> targetRepoPaths = new ArrayList<>();
        targetRepoPaths.add(targetRepoPath);
        BulkCopyMoveContext ctx = new BulkCopyMoveContext(sourceItems,
                targetRepoPaths, mock(MutableStatusHolder.class));

        interceptor.beforeMoveFiles(ctx);

        verify(unifiedEventService, times(expectedTimes))
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_MOVE, srcItem, null, targetRepoPath);
    }

    @ParameterizedTest
    @MethodSource("beforeCopyFilesSource")
    void testBeforeCopyFiles(LocalRepoDescriptor src, LocalRepoDescriptor dest, int expectedTimes) {
        String srcRepoKey = "srcRepoKey";
        VfsItem<ItemInfo> srcItem = getVfsItem(srcRepoKey);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(srcRepoKey)).thenReturn(src);
        String dstRepoKey = "dstRepoKey";
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(dstRepoKey)).thenReturn(dest);
        // Mock properties for source item
        org.artifactory.md.Properties properties = mock(org.artifactory.md.Properties.class);
        when(srcItem.getProperties()).thenReturn(properties);
        // Prepare context
        List<VfsItem<? extends ItemInfo>> sourceItems = new ArrayList<>();
        sourceItems.add(srcItem);
        List<RepoPath> targetRepoPaths = new ArrayList<>();
        RepoPath targetRepoPath = mock(RepoPath.class);
        targetRepoPaths.add(targetRepoPath);
        BulkCopyMoveContext ctx = new BulkCopyMoveContext(sourceItems,
                targetRepoPaths, mock(MutableStatusHolder.class));
        interceptor.beforeCopyFiles(ctx);
        verify(unifiedEventService, times(expectedTimes))
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_COPY, srcItem, null, targetRepoPath);
    }

    @ParameterizedTest
    @MethodSource("beforePropertyCreateSource")
    void testBeforePropertyCreate(LocalRepoDescriptor desc, int expectedTimes) {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(desc);
        List<String> propertyValues = List.of("propertyValue");
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService).sendPropertyEvent(
                SupportedUnifiedEvents.BEFORE_PROPERTY_CREATE, vfsItem, "propertyKey", propertyValues);

        interceptor.beforePropertyCreate(vfsItem, null, "propertyKey", "propertyValue");

        verify(unifiedEventService, times(expectedTimes)).sendPropertyEvent(
                SupportedUnifiedEvents.BEFORE_PROPERTY_CREATE, vfsItem, "propertyKey", propertyValues);
    }

    public static Stream<Arguments> beforePropertyCreateSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1),
                Arguments.of(null, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("beforePropertyDeleteSource")
    void testBeforePropertyDelete(LocalRepoDescriptor desc, int expectedTimes) {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(desc);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService).sendPropertyEvent(
                SupportedUnifiedEvents.BEFORE_PROPERTY_DELETE, vfsItem, "propertyKey", null);

        interceptor.beforePropertyDelete(vfsItem, null, "propertyKey");

        verify(unifiedEventService, times(expectedTimes)).sendPropertyEvent(
                SupportedUnifiedEvents.BEFORE_PROPERTY_DELETE, vfsItem, "propertyKey", null);
    }

    public static Stream<Arguments> beforePropertyDeleteSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1),
                Arguments.of(null, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("afterPropertyDeleteSource")
    void testAfterPropertyDelete(LocalRepoDescriptor desc, int expectedTimes) {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(desc);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService).sendPropertyEvent(
                SupportedUnifiedEvents.AFTER_PROPERTY_DELETE, vfsItem, "propertyKey", null);
        interceptor.afterPropertyDelete(vfsItem, null, "propertyKey");
        verify(unifiedEventService, times(expectedTimes)).sendPropertyEvent(
                SupportedUnifiedEvents.AFTER_PROPERTY_DELETE, vfsItem, "propertyKey", null);
    }

    public static Stream<Arguments> afterPropertyDeleteSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1),
                Arguments.of(null, 0)
        );
    }

    @ParameterizedTest
    @MethodSource("beforeDeleteSource")
    void testBeforeDelete(LocalRepoDescriptor desc, int expectedTimes) {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        DeleteContext deleteContext = mock(DeleteContext.class);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(desc);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_DELETE, vfsItem, deleteContext, null);

        interceptor.beforeDelete(vfsItem, null, deleteContext);

        verify(unifiedEventService, times(expectedTimes)).sendStorageEvent(SupportedUnifiedEvents.BEFORE_DELETE,
                vfsItem,
                deleteContext, null);
    }

    @Test
    void testBeforeCreate_exception() {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(
                mock(LocalRepoDescriptor.class));
        UnifiedEventException ex = new UnifiedEventException(HttpStatus.SC_BAD_REQUEST, false, "fail");
        doThrow(ex).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_CREATE, vfsItem, null, null);
        assertThrows(UnifiedEventException.class, () -> interceptor.beforeCreate(vfsItem, null));
    }

    @Test
    void testBeforeDelete_exception() {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        DeleteContext deleteContext = mock(DeleteContext.class);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(
                mock(LocalRepoDescriptor.class));
        UnifiedEventException ex = new UnifiedEventException(HttpStatus.SC_BAD_REQUEST, false, "fail");
        doThrow(ex).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.BEFORE_DELETE, vfsItem, deleteContext, null);
        assertThrows(UnifiedEventException.class, () -> interceptor.beforeDelete(vfsItem, null, deleteContext));
    }

    @ParameterizedTest
    @MethodSource("afterPropertyCreateSource")
    void testAfterPropertyCreate(LocalRepoDescriptor desc, int expectedTimes) {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(desc);
        List<String> propertyValues = List.of("propertyValue");
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService).sendPropertyEvent(
                SupportedUnifiedEvents.AFTER_PROPERTY_CREATE, vfsItem, "propertyKey", propertyValues);
        interceptor.afterPropertyCreate(vfsItem, null, "propertyKey", "propertyValue");
        verify(unifiedEventService, times(expectedTimes)).sendPropertyEvent(
                SupportedUnifiedEvents.AFTER_PROPERTY_CREATE, vfsItem, "propertyKey", propertyValues);
    }

    @ParameterizedTest
    @MethodSource("afterDeleteSource")
    void testAfterDelete(LocalRepoDescriptor desc, int expectedTimes) {
        String key = "repoKey";
        VfsItem<ItemInfo> vfsItem = getVfsItem(key);
        DeleteContext deleteContext = mock(DeleteContext.class);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(key)).thenReturn(desc);
        doReturn(new DispatcherResponse(true, "message", null)).when(unifiedEventService)
                .sendStorageEvent(SupportedUnifiedEvents.AFTER_DELETE, vfsItem, deleteContext, null);

        interceptor.afterDelete(vfsItem, null, deleteContext);

        verify(unifiedEventService, times(expectedTimes))
                .sendStorageEvent(SupportedUnifiedEvents.AFTER_DELETE, vfsItem, deleteContext, null);
    }

    public static Stream<Arguments> afterPropertyCreateSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1),
                Arguments.of(null, 0)
        );
    }

    public static Stream<Arguments> afterDeleteSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1),
                Arguments.of(null, 0)
        );
    }

    public static Stream<Arguments> beforeDeleteSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1),
                Arguments.of(null, 0)
        );
    }

    @Nonnull
    private static VfsItem<ItemInfo> getVfsItem(String key) {
        ItemInfo itemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();
        properties.put("key", "value");
        properties.put("key2", "value2");
        VfsItem<ItemInfo> vfsItem = mock(VfsItem.class);
        when(vfsItem.getInfo()).thenReturn(itemInfo);
        when(vfsItem.getRepoKey()).thenReturn(key);
        when(itemInfo.getRepoPath()).thenReturn(null);
        when(vfsItem.getPath()).thenReturn("path");
        when(vfsItem.getProperties()).thenReturn(properties);
        return vfsItem;
    }

    public static Stream<Arguments> afterCopySource() {
        return Stream.of(
                Arguments.of(null, null, 0, false),
                Arguments.of(mock(LocalRepoDescriptor.class), null, 0, false),
                Arguments.of(mock(LocalRepoDescriptor.class), mock(LocalRepoDescriptor.class), 1, false),
                // this is if target is null
                Arguments.of(mock(LocalRepoDescriptor.class), mock(LocalRepoDescriptor.class), 0, true),
                Arguments.of(null, mock(LocalRepoDescriptor.class), 0, false)
        );
    }

    public static Stream<Arguments> afterMoveSource() {
        return Stream.of(
                Arguments.of(null, null, 0),
                Arguments.of(null, mock(LocalRepoDescriptor.class), 0),
                Arguments.of(mock(LocalRepoDescriptor.class), null, 0),
                Arguments.of(mock(LocalRepoDescriptor.class), mock(LocalRepoDescriptor.class), 1)
        );
    }

    public static Stream<Arguments> beforeMoveSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1)
        );
    }

    public static Stream<Arguments> beforeMoveFilesSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), 1)
        );
    }

    public static Stream<Arguments> beforeCopyFilesSource() {
        return Stream.of(
                Arguments.of(mock(LocalRepoDescriptor.class), mock(LocalRepoDescriptor.class), 1)
        );
    }
}