package org.artifactory.repo.cleanup;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.system.load.event.SystemLoadState;
import org.artifactory.system.load.monitor.SystemLoadProvider;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.config.diff.DataDiff;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.artifactory.common.ConstantValues.emptyFolderScanBatchSize;
import static org.artifactory.common.ConstantValues.emptyFolderScanBatchLimit;
import static org.artifactory.common.ConstantValues.emptyFolderScanEnabled;
import static org.artifactory.common.ConstantValues.folderPruningMaxQueueSize;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EmptyFolderScanServiceImpl}
 *
 * @author annakv
 */
public class EmptyFolderScanServiceImplTest extends ArtifactoryHomeBoundTest {
    private static final String TEST_REPO = "test-repo";
    @Mock
    private NodesDao nodesDao;
    @Mock
    private FolderPruningService folderPruningService;
    @Mock
    private SystemLoadProvider systemLoadProvider;
    @Mock
    private TaskService taskService;
    @Mock
    private ConfigsService configsService;
    private EmptyFolderScanServiceImpl emptyFolderScanService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        mocks = MockitoAnnotations.openMocks(this);
        emptyFolderScanService = new EmptyFolderScanServiceImpl(nodesDao, folderPruningService,
                systemLoadProvider, taskService, configsService, meterRegistry);
        homeStub.setProperty(emptyFolderScanEnabled, "true");
        homeStub.setProperty(emptyFolderScanBatchSize, "20");
        homeStub.setProperty(emptyFolderScanBatchLimit, "10");
        homeStub.setProperty(folderPruningMaxQueueSize, "100");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testInitWhenDisabled() {
        homeStub.setProperty(emptyFolderScanEnabled, "false");
        emptyFolderScanService.init();
        verify(taskService, never()).startTask(any(Task.class), anyBoolean());
    }

    @Test
    public void testInitWhenEnabled() {
        when(taskService.startTask(any(Task.class), anyBoolean())).thenReturn("task-id");
        emptyFolderScanService.init();
        verify(taskService, times(1)).startTask(any(Task.class), anyBoolean());
    }

    @Test
    public void testDestroy() {
        emptyFolderScanService.destroy();
        verify(taskService, times(1)).cancelTasks(any(Class.class), eq(true));
    }

    @Test
    public void testReload() {
        CentralConfigDescriptor oldDescriptor = mock(CentralConfigDescriptor.class);
        List<DataDiff<?>> configDiff = new ArrayList<>();
        emptyFolderScanService.reload(oldDescriptor, configDiff);
        // reload is a noop, so no verification needed
    }

    @Test
    public void testScanEmptyFoldersWhenDisabled() throws SQLException {
        homeStub.setProperty(emptyFolderScanEnabled, "false");
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, never()).findLastNodeId();
        verify(nodesDao, never()).getTrashFoldersByNodeIdRange(anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testScanEmptyFoldersWhenSystemNotHealthy() throws SQLException {
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.OVERLOADED);
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, never()).findLastNodeId();
        verify(nodesDao, never()).getTrashFoldersByNodeIdRange(anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testScanEmptyFoldersWhenQueueAtMaxCapacity() throws SQLException {
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(100); // equals maxQueueSize
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, never()).findLastNodeId();
        verify(nodesDao, never()).getTrashFoldersByNodeIdRange(anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testScanEmptyFolders() throws SQLException {
        emptyFolderScanService.init();
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(0);
        when(nodesDao.findLastNodeId()).thenReturn(100L);
        Node folderNode1 = Node.builder()
                .nodeId(1L)
                .file(false)
                .repo(TEST_REPO)
                .path("test")
                .name("path")
                .depth((short) 1)
                .created(System.currentTimeMillis())
                .createdBy("test")
                .modified(System.currentTimeMillis())
                .modifiedBy("test")
                .updated(System.currentTimeMillis())
                .build();
        Node folderNode2 = Node.builder()
                .nodeId(2L)
                .file(false)
                .repo(TEST_REPO)
                .path("test")
                .name("path2")
                .depth((short) 1)
                .created(System.currentTimeMillis())
                .createdBy("test")
                .modified(System.currentTimeMillis())
                .modifiedBy("test")
                .updated(System.currentTimeMillis())
                .build();
        List<Node> folderNodes = List.of(folderNode1, folderNode2);
        when(nodesDao.getTrashFoldersByNodeIdRange(eq(1L), eq(20L), eq(10L))).thenReturn(folderNodes);
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, times(1)).findLastNodeId();
        verify(nodesDao, times(1)).getTrashFoldersByNodeIdRange(eq(1L), eq(20L), eq(10L));
        verify(folderPruningService, times(2)).prune(any(RepoPath.class));
        verify(configsService, times(1)).addOrUpdateConfig(eq(ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID),
                eq("21"), anyLong());
    }

    @Test
    public void testScanEmptyFoldersReachesMaxNodeId() throws SQLException {
        emptyFolderScanService.init();
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(0);
        when(nodesDao.findLastNodeId()).thenReturn(100L);
        Node folderNode = Node.builder()
                .nodeId(100L)
                .file(false)
                .repo(TEST_REPO)
                .path("test")
                .name("path")
                .depth((short) 1)
                .created(System.currentTimeMillis())
                .createdBy("test")
                .modified(System.currentTimeMillis())
                .modifiedBy("test")
                .updated(System.currentTimeMillis())
                .build();
        List<Node> folderNodes = List.of(folderNode);
        when(nodesDao.getTrashFoldersByNodeIdRange(eq(1L), eq(20L), eq(10L))).thenReturn(folderNodes);
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, times(1)).findLastNodeId();
        verify(folderPruningService, times(1)).prune(any(RepoPath.class));
        verify(configsService, times(1)).addOrUpdateConfig(eq(ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID),
                eq("21"), anyLong());
    }

    @Test
    public void testScanEmptyFoldersCurrentStartNodeIdExceedsMax() throws SQLException {
        emptyFolderScanService.init();
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(0);
        when(nodesDao.findLastNodeId()).thenReturn(50L);

        // Simulate that scanStartNodeId is already set to a value greater than maxNodeId
        emptyFolderScanService.scanEmptyFolders();
        // First call sets it to 0, second call should reset it
        emptyFolderScanService.scanEmptyFolders();

        verify(nodesDao, times(2)).findLastNodeId();
    }

    @Test
    public void testScanEmptyFoldersHandlesItemNotFoundRuntimeException() throws SQLException {
        emptyFolderScanService.init();
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(0);
        when(nodesDao.findLastNodeId()).thenReturn(100L);
        Node folderNode = Node.builder()
                .nodeId(1L)
                .file(false)
                .repo(TEST_REPO)
                .path("test")
                .name("path")
                .depth((short) 1)
                .created(System.currentTimeMillis())
                .createdBy("test")
                .modified(System.currentTimeMillis())
                .modifiedBy("test")
                .updated(System.currentTimeMillis())
                .build();
        List<Node> folderNodes = List.of(folderNode);
        when(nodesDao.getTrashFoldersByNodeIdRange(eq(1L), eq(20L), eq(10L))).thenReturn(folderNodes);
        doThrow(new ItemNotFoundRuntimeException("Folder not found")).when(folderPruningService)
                .prune(any(RepoPath.class));
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, times(1)).findLastNodeId();
        verify(folderPruningService, times(1)).prune(any(RepoPath.class));
        verify(configsService, times(1)).addOrUpdateConfig(eq(ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID),
                eq("21"), anyLong());
    }

    @Test
    public void testScanEmptyFoldersHandlesGeneralException() throws SQLException {
        emptyFolderScanService.init();
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(0);
        when(nodesDao.findLastNodeId()).thenReturn(100L);
        Node folderNode = Node.builder()
                .nodeId(1L)
                .file(false)
                .repo(TEST_REPO)
                .path("test")
                .name("path")
                .depth((short) 1)
                .created(System.currentTimeMillis())
                .createdBy("test")
                .modified(System.currentTimeMillis())
                .modifiedBy("test")
                .updated(System.currentTimeMillis())
                .build();
        List<Node> folderNodes = List.of(folderNode);
        when(nodesDao.getTrashFoldersByNodeIdRange(eq(1L), eq(20L), eq(10L))).thenReturn(folderNodes);
        doThrow(new RuntimeException("Unexpected error")).when(folderPruningService).prune(any(RepoPath.class));
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, times(1)).findLastNodeId();
        verify(folderPruningService, times(1)).prune(any(RepoPath.class));
        verify(configsService, times(1)).addOrUpdateConfig(eq(ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID),
                eq("21"), anyLong());
    }

    @Test
    public void testScanEmptyFoldersHandlesSQLException() throws SQLException {
        emptyFolderScanService.init();
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(0);
        when(nodesDao.findLastNodeId()).thenThrow(new SQLException("Database error"));
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, times(1)).findLastNodeId();
        verify(nodesDao, never()).getTrashFoldersByNodeIdRange(anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testScanEmptyFoldersWithAdvancingPastMaxNodeId() throws SQLException {
        emptyFolderScanService.init();
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(0);
        when(nodesDao.findLastNodeId()).thenReturn(5L);
        when(nodesDao.getTrashFoldersByNodeIdRange(eq(1L), eq(20L), eq(10L))).thenReturn(Collections.emptyList());
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, times(1)).findLastNodeId();
        verify(nodesDao, times(1)).getTrashFoldersByNodeIdRange(eq(1L), eq(20L), eq(10L));
        verify(configsService, times(1)).addOrUpdateConfig(eq(ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID),
                eq("1"), anyLong());
    }

    @Test
    public void testScanEmptyFoldersWithPersistedNodeId() throws SQLException {
        when(systemLoadProvider.getSystemLoadState()).thenReturn(SystemLoadState.HEALTHY);
        when(folderPruningService.getQueueSize()).thenReturn(0);
        when(configsService.getConfig(ARTIFACTORY_EMPTY_FOLDER_SCAN_START_NODE_ID)).thenReturn("50");
        when(nodesDao.findLastNodeId()).thenReturn(100L);
        when(nodesDao.getTrashFoldersByNodeIdRange(eq(50L), eq(20L), eq(10L))).thenReturn(Collections.emptyList());
        emptyFolderScanService.init();
        emptyFolderScanService.scanEmptyFolders();
        verify(nodesDao, times(1)).getTrashFoldersByNodeIdRange(eq(50L), eq(20L), eq(10L));
    }

}

