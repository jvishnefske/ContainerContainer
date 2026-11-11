package org.artifactory.storage.db.fs.model;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.storage.fs.VfsException;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.NodeMetaInfoService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.storage.fs.service.WatchesService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbMutableItemDeleteHelperTest extends ArtifactoryHomeBoundJUnitTest {

    private final FileService fileService = mock(FileService.class);
    private final StatsService statsService = mock(StatsService.class);
    private final PropertiesService propertiesService = mock(PropertiesService.class);
    private final NodeMetaInfoService metaInfoService = mock(NodeMetaInfoService.class);
    private final WatchesService watchesService = mock(WatchesService.class);
    private final DbMutableItemDeleteHelper mutableItemDeleteHelper = spy(new DbMutableItemDeleteHelper());
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private ArtifactoryHome artifactoryHome;
    private AutoCloseable closable;

    @BeforeEach
    void setUp() {
        closable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        doReturn(fileService).when(mutableItemDeleteHelper).getFileService();
        doReturn(propertiesService).when(mutableItemDeleteHelper).getPropertiesService();
        doReturn(metaInfoService).when(mutableItemDeleteHelper).getNodeMetaInfoService();
        doReturn(watchesService).when(mutableItemDeleteHelper).getWatchesService();
        doReturn(statsService).when(mutableItemDeleteHelper).getStatsService();
        doNothing().when(metaInfoService).deleteMetaInfo(anyLong());
        when(propertiesService.deleteProperties(anyLong())).thenReturn(1);
        when(statsService.deleteStats(anyLong())).thenReturn(true);
        when(watchesService.deleteWatches(anyLong())).thenReturn(1);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() throws Exception {
        closable.close();
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    @Test
    void testDeleteItemWithFlagSetToFalse() throws SQLException {
        var resultSet = mock(ResultSet.class);
        when(fileService.lockNodeForWrite(anyLong())).thenReturn(resultSet);
        mutableItemDeleteHelper.deleteItem(anyLong());
        verify(fileService, times(1)).deleteItem(anyLong());
        verify(statsService, times(1)).deleteStats(anyLong());
        verify(watchesService, times(1)).deleteWatches(anyLong());
        verify(propertiesService, times(1)).deleteProperties(anyLong());
        verify(metaInfoService, times(1)).deleteMetaInfo(anyLong());
        verify(resultSet, times(0)).close();
        verify(fileService, times(0)).lockNodeForWrite(anyLong());
    }

    @Test
    void testDeleteItemWithFlagSetToTrue() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.lockForSelectBeforeDeletingFromNodesEnabled)).thenReturn(true);
        var resultSet = mock(ResultSet.class);
        when(fileService.lockNodeForWrite(anyLong())).thenReturn(resultSet);
        mutableItemDeleteHelper.deleteItem(anyLong());
        verify(fileService, times(1)).deleteItem(anyLong());
        verify(statsService, times(1)).deleteStats(anyLong());
        verify(watchesService, times(1)).deleteWatches(anyLong());
        verify(propertiesService, times(1)).deleteProperties(anyLong());
        verify(metaInfoService, times(1)).deleteMetaInfo(anyLong());
        verify(resultSet, times(1)).close();
        verify(fileService, times(1)).lockNodeForWrite(anyLong());
    }

    @Test
    void testDeleteItemException() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.lockForSelectBeforeDeletingFromNodesEnabled)).thenReturn(true);
        doThrow(SQLException.class).when(fileService).lockNodeForWrite(anyLong());
        assertThrowsExactly(VfsException.class, () -> mutableItemDeleteHelper.deleteItem(anyLong()),
                "Failed to delete item");
    }

    @Test
    void deleteItemCheckRetry() {
        when(fileService.deleteItem(anyLong())).thenThrow(new VfsException(""));
        Exception exception = assertThrows(VfsException.class, () -> mutableItemDeleteHelper.deleteItem(1));
        String actualMessage = exception.getMessage();
        String expectedMessage =
                "org.jfrog.common.ExecutionFailed: Delete item has failed due to: Failed to delete item with id '1': " +
                        ". Not trying again";
        assertEquals(expectedMessage, actualMessage);
        verify(fileService, times(3)).deleteItem(anyLong());
        verify(statsService, times(3)).deleteStats(anyLong());
        verify(watchesService, times(1)).deleteWatches(anyLong());
        verify(propertiesService, times(1)).deleteProperties(anyLong());
        verify(metaInfoService, times(1)).deleteMetaInfo(anyLong());
    }
}
