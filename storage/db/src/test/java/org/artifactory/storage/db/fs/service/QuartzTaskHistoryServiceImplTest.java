package org.artifactory.storage.db.fs.service;

import ch.qos.logback.classic.Level;
import com.jfrog.test.LogTestUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.quartz.task.history.QuartzTaskHistoryInfo;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryDao;
import org.artifactory.storage.db.fs.entity.QuartzTaskHistoryRecord;
import org.artifactory.storage.db.servers.model.ArtifactoryServerInfo;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.skyscreamer.jsonassert.JSONAssert;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.quartzTaskRunExpirationTimeoutSeconds;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.EXPIRED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.ORPHANED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.PENDING_EXPIRED_CHECK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class QuartzTaskHistoryServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private QuartzTaskHistoryDao dao;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private ArtifactoryServersCommonService serversCommonService;
    @InjectMocks
    private QuartzTaskHistoryServiceImpl quartzTaskHistoryService;

    private MockitoSession mockitoSession;

    @BeforeAll
    static void beforeAll() {
        LogTestUtils.setLoggingLevel(QuartzTaskHistoryServiceImpl.class, Level.DEBUG);
    }

    @AfterAll
    static void afterAll() {
        LogTestUtils.setLoggingLevel(QuartzTaskHistoryServiceImpl.class, Level.INFO);
    }

    @BeforeEach
    void setUp() {
        mockitoSession = mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .startMocking();
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        mockitoSession.finishMocking();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void insertSuccess() throws SQLException {
        when(context.getServerId()).thenReturn("serverId");
        when(dao.insert(any(QuartzTaskHistoryRecord.class))).thenReturn(1);
        assertEquals(1,
                quartzTaskHistoryService.insert("externalId", "taskType", "taskId", true,
                        "username"));
    }

    @Test
    void insertNoStatusMessageSuccess() throws SQLException {
        when(context.getServerId()).thenReturn("serverId");
        when(dao.insert(any(QuartzTaskHistoryRecord.class))).thenReturn(1);
        assertEquals(1,
                quartzTaskHistoryService.insert("externalId", "taskType", "taskId", true,
                        "username"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void insertInvalidTaskTypeFailure(String taskType) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> quartzTaskHistoryService.insert("externalId", taskType, "taskId", true, "username"));
        assertEquals("taskType cannot be blank.", exception.getMessage());
    }

    @Test
    void insertFailure() throws SQLException {
        when(context.getServerId()).thenReturn("serverId");
        when(dao.insert(any())).thenThrow(new IllegalStateException("Thrown by test"));
        StorageException storageException =
                assertThrows(StorageException.class,
                        () -> quartzTaskHistoryService.insert("externalId", "taskType", "taskId", true,
                                "username"));
        assertEquals("java.lang.IllegalStateException: Thrown by test", storageException.getMessage());
    }

    @Test
    void insertNullExternalIdSuccess() throws SQLException {
        when(context.getServerId()).thenReturn("serverId");
        when(dao.insert(any())).thenReturn(1);
        assertEquals(1, quartzTaskHistoryService.insert(null, "taskType", "taskId", true,
                "username"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void insertInvalidTaskIdFailure(String taskId) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> quartzTaskHistoryService.insert("externalId", "taskType", taskId, true,
                                "username"));
        assertEquals("taskId cannot be blank.", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void insertInvalidUsernameFailure(String username) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                        () -> quartzTaskHistoryService.insert("externalId", "taskType", "taskId", true,
                                username));
        assertEquals("username cannot be blank.", exception.getMessage());
    }

    @Test
    void getSuccess() throws SQLException, JSONException {
        QuartzTaskHistoryRecord quartzTaskHistoryRecord = QuartzTaskHistoryRecord.builder().build();
        when(dao.get(1)).thenReturn(quartzTaskHistoryRecord);
        QuartzTaskHistoryInfo quartzTaskHistoryInfo = quartzTaskHistoryService.get(1);
        JSONAssert.assertEquals(JsonUtils.getInstance().valueToString(quartzTaskHistoryRecord),
                JsonUtils.getInstance().valueToString(quartzTaskHistoryInfo), true);
    }

    @Test
    void getFailure() throws SQLException {
        when(dao.get(1)).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException =
                assertThrows(StorageException.class, () -> quartzTaskHistoryService.get(1));
        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }

    @Test
    void getNotFound() {
        assertNull(quartzTaskHistoryService.get(1));
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void updateStatusSuccess(Status status) throws SQLException {
        when(dao.updateStatus(1, status)).thenReturn(true);
        assertTrue(quartzTaskHistoryService.updateStatus(1, status));
    }

    @Test
    void updateStatusNullFails() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> quartzTaskHistoryService.updateStatus(1, null));
        assertEquals("status cannot be null.", exception.getMessage());
    }

    @Test
    void updateStatusMessageNullSuccess() throws SQLException {
        quartzTaskHistoryService.updateStatus(1, COMPLETED, null);
        verify(dao).updateStatus(1, COMPLETED);
    }

    @Test
    void updateStatusMessageStatusNullFails() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> quartzTaskHistoryService.updateStatus(1, null, "statusMsg"));
        assertEquals("status cannot be null.", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void updateStatusNotFound(Status status) {
        assertFalse(quartzTaskHistoryService.updateStatus(1, status));
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void updateStatusFailure(Status status) throws SQLException {
        when(dao.updateStatus(1, status)).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException =
                assertThrows(StorageException.class, () -> quartzTaskHistoryService.updateStatus(1, status));
        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void updateStatusWithMessageSuccess(Status status) throws SQLException {
        when(dao.updateStatus(1, status, "statusMessage")).thenReturn(true);
        assertTrue(quartzTaskHistoryService.updateStatus(1,
                status, "statusMessage"), "statusMessage");
    }

    @Test
    void updateSuccess() throws SQLException {
        QuartzTaskHistoryInfo taskHistoryInfo = QuartzTaskHistoryInfo.builder()
                .runId(1)
                .startedDate(1L)
                .finishedDate(2L)
                .status(IN_PROGRESS)
                .statusMessage("msg")
                .lastModifiedDate(2L)
                .build();
        quartzTaskHistoryService.setTimeSupplier(() -> 2L);
                
        quartzTaskHistoryService.update(taskHistoryInfo);

        verify(dao).update(toUpdateRecord(taskHistoryInfo));
    }

    @Test
    void updateFailure() throws SQLException {
        QuartzTaskHistoryInfo taskHistoryInfo = QuartzTaskHistoryInfo.builder()
                .runId(1)
                .startedDate(1L)
                .finishedDate(2L)
                .status(IN_PROGRESS)
                .statusMessage("msg")
                .lastModifiedDate(2L)
                .build();
        when(dao.update(toUpdateRecord(taskHistoryInfo))).thenThrow(new SQLException("Thrown by test"));
        quartzTaskHistoryService.setTimeSupplier(() -> 2L);

        StorageException storageException =
                assertThrows(StorageException.class, () -> quartzTaskHistoryService.update(taskHistoryInfo));

        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }
    
    private QuartzTaskHistoryRecord toUpdateRecord(QuartzTaskHistoryInfo taskHistoryInfo) {
        return QuartzTaskHistoryRecord.builder()
                .runId(taskHistoryInfo.runId())
                .startedDate(taskHistoryInfo.startedDate())
                .finishedDate(taskHistoryInfo.finishedDate())
                .status(taskHistoryInfo.status())
                .statusMessage(taskHistoryInfo.statusMessage())
                .lastModifiedDate(taskHistoryInfo.lastModifiedDate())
                .build();
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void updateStatusWithMessageNotFound(Status status) {
        assertFalse(quartzTaskHistoryService.updateStatus(1, status, "statusMessage"));
    }

    @ParameterizedTest
    @EnumSource(Status.class)
    void updateStatusWithMessageFailure(Status status) throws SQLException {
        when(dao.updateStatus(1, status, "statusMessage")).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException =
                assertThrows(StorageException.class, () -> quartzTaskHistoryService.updateStatus(1,
                        status, "statusMessage"));
        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }

    @Test
    void hardDeleteSuccess() throws SQLException {
        when(dao.hardDelete(1)).thenReturn(true);
        assertTrue(quartzTaskHistoryService.hardDelete(1));
    }

    @Test
    void hardDeleteNotFound() {
        assertFalse(quartzTaskHistoryService.hardDelete(1));
    }

    @Test
    void hardDeleteFailure() throws SQLException {
        when(dao.hardDelete(1)).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException =
                assertThrows(StorageException.class, () -> quartzTaskHistoryService.hardDelete(1));
        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }

    @Test
    void softDeleteSuccess() throws SQLException {
        when(dao.softDelete(1)).thenReturn(true);
        assertTrue(quartzTaskHistoryService.softDelete(1));
    }

    @Test
    void softDeleteNotFound() {
        assertFalse(quartzTaskHistoryService.softDelete(1));
    }

    @Test
    void softDeleteFailure() throws SQLException {
        when(dao.softDelete(1)).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException =
                assertThrows(StorageException.class, () -> quartzTaskHistoryService.softDelete(1));
        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }

    @Test
    void getAllNonDeletedSuccess() throws SQLException, JSONException {
        QuartzTaskHistoryRecord quartzTaskHistoryRecord1 = QuartzTaskHistoryRecord.builder().build();
        when(dao.getAllNonDeleted()).thenReturn(Set.of(quartzTaskHistoryRecord1));

        Set<QuartzTaskHistoryInfo> allNonDeleted = quartzTaskHistoryService.getAllNonDeleted();
        assertEquals(1, allNonDeleted.size());
        String expectedJson = JsonUtils.getInstance().valueToString(quartzTaskHistoryRecord1);
        String actualJson = JsonUtils.getInstance().valueToString(allNonDeleted.iterator().next());
        JSONAssert.assertEquals(expectedJson, actualJson, true);
    }

    @Test
    void getAllNonDeletedFailure() throws SQLException {
        when(dao.getAllNonDeleted()).thenThrow(new SQLException("Thrown by test"));
        StorageException storageException =
                assertThrows(StorageException.class, () -> quartzTaskHistoryService.getAllNonDeleted());
        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }

    @Test
    void deleteAllFailure() throws SQLException {
        doThrow(new SQLException("Thrown by test")).when(dao).deleteAll();
        StorageException storageException =
                assertThrows(StorageException.class, () -> quartzTaskHistoryService.deleteAll());
        assertEquals("java.sql.SQLException: Thrown by test", storageException.getMessage());
    }

    @Test
    void deleteAllSuccess() throws SQLException {
        quartzTaskHistoryService.deleteAll();
        verify(dao).deleteAll();
    }

    @Test
    void findRunningTaskByExternalIdBlankFailure() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> quartzTaskHistoryService.findRunningTaskByExternalId(StringUtils.EMPTY));
        assertEquals("externalId cannot be blank", exception.getMessage());
    }

    @Test
    void findRunningTaskByExternalIdNotFoundReturnNull() throws SQLException {
        when(dao.findByExternalIdAndStatusIsOneOf("externalId", Status.getRunningStatuses())).thenReturn(null);
        assertNull(quartzTaskHistoryService.findRunningTaskByExternalId("externalId"));
    }

    @Test
    void findRunningTaskByExternalIdSqlException() throws SQLException {
        when(dao.findByExternalIdAndStatusIsOneOf("externalId", Status.getRunningStatuses())).thenThrow(
                new SQLException("Thrown by test"));
        StorageException exception = assertThrows(StorageException.class,
                () -> quartzTaskHistoryService.findRunningTaskByExternalId("externalId"));
        assertEquals("java.sql.SQLException: Thrown by test", exception.getMessage());
    }

    @Test
    void findRunningTaskByExternalIdSuccess() throws SQLException, JSONException {
        QuartzTaskHistoryRecord record = QuartzTaskHistoryRecord.builder()
                .runId(1)
                .externalId("externalId")
                .taskType("taskType")
                .taskId("taskId")
                .username("username")
                .serverId("serverId")
                .status(IN_PROGRESS)
                .build();
        when(dao.findByExternalIdAndStatusIsOneOf("externalId", Status.getRunningStatuses())).thenReturn(record);

        QuartzTaskHistoryInfo taskHistoryInfo =
                quartzTaskHistoryService.findRunningTaskByExternalId("externalId");

        String expected = JsonUtils.getInstance().valueToString(record);
        String actual = JsonUtils.getInstance().valueToString(taskHistoryInfo);
        JSONAssert.assertEquals(expected, actual, true);
    }

    @Test
    void testMarkExpiredTasks() throws SQLException {
        quartzTaskHistoryService.markExpiredTasks();
        verify(dao).updateToStatusWhereStatus(EXPIRED, PENDING_EXPIRED_CHECK);
    }

    @Test
    void markPendingExpiredCheckTasks() throws SQLException {
        quartzTaskHistoryService.setTimeSupplier(
                () -> TimeUnit.SECONDS.toMillis(quartzTaskRunExpirationTimeoutSeconds.getLong()));
        quartzTaskHistoryService.markPendingExpiredCheckTasks();
        verify(dao).updateToStatusWhereInStatusesAndLastModifiedBefore(PENDING_EXPIRED_CHECK,
                Status.getRunningStatuses(), 0);
    }

    @Test
    void testMarkOrphanedTasksNoRunningTasksDoNothing() throws SQLException {
        when(dao.findByStatuses(Status.getRunningStatuses())).thenReturn(Set.of());
        quartzTaskHistoryService.markOrphanedTasks();
        verifyNoInteractions(serversCommonService);
        verify(dao).findByStatuses(Status.getRunningStatuses());
        verifyNoMoreInteractions(dao);
    }

    @Test
    void testMarkOrphanedTasksNoOrphanedFoundDoNothing() throws SQLException {
        QuartzTaskHistoryRecord record1 = QuartzTaskHistoryRecord.builder()
                .runId(1)
                .status(IN_PROGRESS)
                .serverId("serverId1")
                .build();
        QuartzTaskHistoryRecord record2 = QuartzTaskHistoryRecord.builder()
                .runId(2)
                .status(IN_PROGRESS)
                .serverId("serverId2")
                .build();
        when(dao.findByStatuses(Status.getRunningStatuses())).thenReturn(Set.of(record1, record2));

        ArtifactoryServerInfo serverInfo1 = mock(ArtifactoryServerInfo.class);
        when(serverInfo1.getServerId()).thenReturn("serverId1");
        when(serverInfo1.isActive()).thenReturn(true);
        ArtifactoryServerInfo serverInfo2 = mock(ArtifactoryServerInfo.class);
        when(serverInfo2.getServerId()).thenReturn("serverId2");
        when(serverInfo2.isActive()).thenReturn(true);

        when(serversCommonService.getAllArtifactoryServersInfo()).thenReturn(List.of(serverInfo1, serverInfo2));

        quartzTaskHistoryService.markOrphanedTasks();

        verify(dao).findByStatuses(Status.getRunningStatuses());
        verifyNoMoreInteractions(dao);
    }

    @Test
    void testMarkOrphanedTasksNoServersFoundDoNothing() throws SQLException {
        QuartzTaskHistoryRecord record1 = QuartzTaskHistoryRecord.builder()
                .runId(1)
                .status(IN_PROGRESS)
                .build();
        QuartzTaskHistoryRecord record2 = QuartzTaskHistoryRecord.builder()
                .runId(2)
                .status(IN_PROGRESS)
                .build();
        when(dao.findByStatuses(Status.getRunningStatuses())).thenReturn(Set.of(record1, record2));
        when(serversCommonService.getAllArtifactoryServersInfo()).thenReturn(List.of());

        quartzTaskHistoryService.markOrphanedTasks();

        verify(dao).findByStatuses(Status.getRunningStatuses());
        verifyNoMoreInteractions(dao);
    }

    @Test
    void testMarkOrphanedTasksOneInactive() throws SQLException {
        QuartzTaskHistoryRecord record1 = QuartzTaskHistoryRecord.builder()
                .runId(1)
                .status(IN_PROGRESS)
                .serverId("serverId1")
                .build();
        QuartzTaskHistoryRecord record2 = QuartzTaskHistoryRecord.builder()
                .runId(2)
                .status(IN_PROGRESS)
                .serverId("serverId2")
                .build();
        when(dao.findByStatuses(Status.getRunningStatuses())).thenReturn(Set.of(record1, record2));

        ArtifactoryServerInfo serverInfo1 = mock(ArtifactoryServerInfo.class);
        when(serverInfo1.getServerId()).thenReturn("serverId1");
        when(serverInfo1.isActive()).thenReturn(false);
        ArtifactoryServerInfo serverInfo2 = mock(ArtifactoryServerInfo.class);
        when(serverInfo2.getServerId()).thenReturn("serverId2");
        when(serverInfo2.isActive()).thenReturn(true);

        when(serversCommonService.getAllArtifactoryServersInfo()).thenReturn(List.of(serverInfo1, serverInfo2));

        quartzTaskHistoryService.markOrphanedTasks();

        verify(dao).updateStatusWhereRunIdIn(ORPHANED, Set.of(1));
    }

    @Test
    void getNotFinishedTasks() throws SQLException {
        QuartzTaskHistoryRecord record1 = QuartzTaskHistoryRecord.builder()
                .runId(1)
                .status(ORPHANED)
                .serverId("serverId1")
                .build();
        QuartzTaskHistoryRecord record2 = QuartzTaskHistoryRecord.builder()
                .runId(2)
                .status(EXPIRED)
                .serverId("serverId2")
                .build();
        when(dao.findByStatuses(any())).thenReturn(Set.of(record1, record2));

        Set<QuartzTaskHistoryInfo> actual = quartzTaskHistoryService.getNotFinishedTasks();

        assertEquals(2, actual.size());
        assertTrue(actual.stream().anyMatch(c -> c.status().equals(ORPHANED) && c.runId() == 1));
        assertTrue(actual.stream().anyMatch(c -> c.status().equals(EXPIRED) && c.runId() == 2));
    }
}