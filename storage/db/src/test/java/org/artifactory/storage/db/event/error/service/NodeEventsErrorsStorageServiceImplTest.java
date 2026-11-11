package org.artifactory.storage.db.event.error.service;


import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.event.EventType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.event.error.dao.NodeEventsErrorsDAO;
import org.artifactory.storage.db.event.error.factory.NodeEventsErrorInfoFactory;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.artifactory.storage.db.event.error.model.RepoAndCategory;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.SqlDaoHelper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class NodeEventsErrorsStorageServiceImplTest {

    private MockitoSession mockitoSession;

    @Mock
    private NodeEventsErrorsDAO nodeEventsErrorsDaoMock;
    @Mock
    private InternalDbService internalDbServiceMock;

    private NodeEventsErrorsStorageServiceImpl nodeEventsErrorsStorageService;

    @DataProvider
    public static Object[][] eventCategoryBlankProvider() {
        return new Object[][] {
                {null},
                {StringUtils.EMPTY}
        };
    }

    @DataProvider
    public static Object[][] eventCategoryAndOperationKeyBlankProvider() {
        return new Object[][] {
                {null, "opKey"},
                {StringUtils.EMPTY, "opKey"},
                {null, null},
                {StringUtils.EMPTY, StringUtils.EMPTY},
        };
    }

    @DataProvider
    public static Object[][] eventCategoryEventTypeAndPathBlankProvider() {
        return new Object[][] {
                {null, EventType.create, "path"},
                {StringUtils.EMPTY, EventType.create, "path"},
                {"EventCategory", null, "path"},
                {"EventCategory", EventType.create, null},
                {"EventCategory", EventType.create, StringUtils.EMPTY},
        };
    }

    @DataProvider
    public static Object[][] eventCategoryEventTypePathAndOperationKeyBlankProvider() {
        return new Object[][] {
                {null, EventType.create, "path", "opKey"},
                {StringUtils.EMPTY, EventType.create, "path", "opKey"},
                {"EventCategory", null, "path", "opKey"},
                {"EventCategory", EventType.create, null, "opKey"},
                {"EventCategory", EventType.create, StringUtils.EMPTY, "opKey"},
        };
    }

    @DataProvider
    public static Object[][] deleteByEventCategoryEventTypeAndPathBlankProvider() {
        return new Object[][] {
                {null, EventType.create, "path"},
                {StringUtils.EMPTY, EventType.create, "path"},
                {"EventCategory", null, "path"},
                {"EventCategory", EventType.create, null},
                {"EventCategory", EventType.create, StringUtils.EMPTY}
        };
    }

    @DataProvider
    public static Object[][] deleteByEventCategoryEventTypePathAndOperationKeyBlankProvider() {
        return new Object[][] {
                {null, EventType.create, "path", "opKey"},
                {StringUtils.EMPTY, EventType.create, "path", "opKey"},
                {"EventCategory", null, "path", "opKey"},
                {"EventCategory", EventType.create, null, "opKey"},
                {"EventCategory", EventType.create, StringUtils.EMPTY, "opKey"},
        };
    }

    @DataProvider
    public static Object[][] deleteAllByEventCategoryAndOperationKeyBlankProvider() {
        return new Object[][] {
                {null, "opKey"},
                {StringUtils.EMPTY, "opKey"},
                {"EventCategory", null},
                {"EventCategory", StringUtils.EMPTY}
        };
    }

    @DataProvider
    public static Object[][] updateIllegalArgumentProvider() {
        return new Object[][] {
                {createErrorInfo(StringUtils.EMPTY, EventType.update, RepoPathFactory.create("repo", "dir/file.jar"),
                        "opKey", 123L, 1, "errMsg", 123L, 123L)},
                {createErrorInfo("EventCategory", EventType.update, RepoPathFactory.create("repo", "dir/file.jar"),
                        "opKey", -1L, 1, "errMsg", 123L, 123L)},
                {createErrorInfo("EventCategory", EventType.update, RepoPathFactory.create("repo", "dir/file.jar"),
                        "opKey", 123L, -1, "errMsg", 123L, 123L)},
                {createErrorInfo("EventCategory", EventType.update, RepoPathFactory.create("repo", "dir/file.jar"),
                        "opKey", 123L, 1, StringUtils.EMPTY, 123L, 123L)},
                {createErrorInfo("EventCategory", EventType.update, RepoPathFactory.create("repo", "dir/file.jar"),
                        "opKey", 123L, 1, "errMsg", -1L, 123L)},
                {createErrorInfo("EventCategory", EventType.update, RepoPathFactory.create("repo", "dir/file.jar"),
                        "opKey", 123L, 1, "errMsg", 123L, -1)},
        };
    }

    private static NodeEventsErrorInfo createErrorInfo(int errCount, String errMsg, long lastErrTime) {
        return createErrorInfo(
                "eventCategory",
                EventType.update,
                RepoPathFactory.create("repoKey", "folder/file.jar")
                , errCount, errMsg, lastErrTime);
    }

    private static NodeEventsErrorInfo createErrorInfo(String eventCategory, EventType eventType, RepoPath repoPath) {
        return createErrorInfo(eventCategory, eventType, repoPath, 1, "errMsg", 456);
    }

    private static NodeEventsErrorInfo createErrorInfo(String eventCategory, EventType eventType, RepoPath repoPath,
            int errorCount, String errMsg, long lastErrTime) {
        return createErrorInfo(eventCategory, eventType, repoPath, "opKey", 123L, errorCount, errMsg, 123L,
                lastErrTime);
    }

    private static NodeEventsErrorInfo createErrorInfo(String eventCategory, EventType eventType, RepoPath repoPath,
            String operationKey, long eventTime, int errorCount, String errMsg, long firstErrTime, long lastErrTime) {
        return NodeEventsErrorInfoFactory
                .create(eventCategory, eventType, repoPath, operationKey, null, eventTime, errorCount, errMsg,
                        firstErrTime, lastErrTime);
    }

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();

        nodeEventsErrorsStorageService =
                new NodeEventsErrorsStorageServiceImpl(nodeEventsErrorsDaoMock);
        nodeEventsErrorsStorageService.setDbService(internalDbServiceMock);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testGetErroneousRepos() throws SQLException{
        // Given
        when(nodeEventsErrorsDaoMock.getErroneousRepos())
                .thenReturn(Sets.newHashSet(new RepoAndCategory("repo1", "category1")));

        // When
        Set<RepoAndCategory> repos = nodeEventsErrorsStorageService.getErroneousRepos();

        // Then
        verify(nodeEventsErrorsDaoMock, times(1)).getErroneousRepos();
        Assert.assertEquals(repos.size(), 1);
        Assert.assertEquals(repos.iterator().next().getRepoKey(), "repo1");
        Assert.assertEquals(repos.iterator().next().getEventCategory(), "category1");
    }

    @Test
    public void testAdd() throws SQLException {
        when(internalDbServiceMock.nextId()).thenReturn(1L);

        String eventCategory = "EventCategory";
        EventType eventType = EventType.create;
        RepoPath repoPath = RepoPathFactory.create("repoKey", "folder/file.jar");
        NodeEventsErrorInfo errorInfo = createErrorInfo(eventCategory, eventType, repoPath);

        nodeEventsErrorsStorageService.add(errorInfo);

        NodeEventsErrorsDBO expectedDBO = ErrorInfoConverter.toDBO(1, errorInfo);
        verify(nodeEventsErrorsDaoMock).insert(expectedDBO);
    }

    @Test
    public void testGetErrorsByEventCategory() throws SQLException {
        String eventCategory = "EventCategory";
        NodeEventsErrorsDBO dbo = createErrorDBO(1, eventCategory, 1);
        NodeEventsErrorsDBO dbo2 = createErrorDBO(2, eventCategory, 3);
        NodeEventsErrorsDBO dbo3 = createErrorDBO(3, eventCategory, 2);
        NodeEventsErrorsDBO dbo4 = createErrorDBO(4, eventCategory, 3);

        when(nodeEventsErrorsDaoMock.selectAll(eventCategory)).thenReturn(Lists.newArrayList(dbo, dbo2, dbo3, dbo4));

        // Should sort by event time first and then by error ID
        List<NodeEventsErrorInfo> actualErrorInfos = nodeEventsErrorsStorageService.getErrors(eventCategory);

        Assert.assertNotNull(actualErrorInfos);
        Assert.assertEquals(actualErrorInfos.size(), 4);

        Assert.assertEquals(actualErrorInfos.get(0), ErrorInfoConverter.toErrorInfo(dbo));
        Assert.assertEquals(actualErrorInfos.get(1), ErrorInfoConverter.toErrorInfo(dbo3));
        Assert.assertEquals(actualErrorInfos.get(2), ErrorInfoConverter.toErrorInfo(dbo2));
        Assert.assertEquals(actualErrorInfos.get(3), ErrorInfoConverter.toErrorInfo(dbo4));
    }

    @Test(dataProvider = "eventCategoryBlankProvider", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "eventCategory param must be non-.*")
    public void testGetErrorsByEventCategoryBlank(String eventCategory) {
        nodeEventsErrorsStorageService.getErrors(eventCategory);
    }

    @Test
    public void testGetErrorsByEventCategoryAndOperationKey() throws SQLException {
        String eventCategory = "EventCategory";
        String operationKey = "opKey";
        NodeEventsErrorsDBO dbo = createErrorDBO(1, eventCategory, operationKey, 1);
        NodeEventsErrorsDBO dbo2 = createErrorDBO(2, eventCategory, operationKey, 3);
        NodeEventsErrorsDBO dbo3 = createErrorDBO(3, eventCategory, operationKey, 2);
        NodeEventsErrorsDBO dbo4 = createErrorDBO(4, eventCategory, operationKey, 3);

        when(nodeEventsErrorsDaoMock.select(eventCategory, operationKey))
                .thenReturn(Lists.newArrayList(dbo, dbo2, dbo3, dbo4));

        when(nodeEventsErrorsDaoMock.select(eventCategory, operationKey, 1000))
                .thenReturn(Lists.newArrayList(dbo, dbo2, dbo3, dbo4));

        // Should sort by event time first and then by error ID
        List<NodeEventsErrorInfo> actualErrorInfos =
                nodeEventsErrorsStorageService.getErrors(eventCategory, operationKey, null);

        Assert.assertEquals(actualErrorInfos.get(0), ErrorInfoConverter.toErrorInfo(dbo));
        Assert.assertEquals(actualErrorInfos.get(1), ErrorInfoConverter.toErrorInfo(dbo3));
        Assert.assertEquals(actualErrorInfos.get(2), ErrorInfoConverter.toErrorInfo(dbo2));
        Assert.assertEquals(actualErrorInfos.get(3), ErrorInfoConverter.toErrorInfo(dbo4));

        actualErrorInfos =
                nodeEventsErrorsStorageService.getErrors(eventCategory, operationKey, 1000);


        Assert.assertEquals(actualErrorInfos.get(0), ErrorInfoConverter.toErrorInfo(dbo));
        Assert.assertEquals(actualErrorInfos.get(1), ErrorInfoConverter.toErrorInfo(dbo3));
        Assert.assertEquals(actualErrorInfos.get(2), ErrorInfoConverter.toErrorInfo(dbo2));
        Assert.assertEquals(actualErrorInfos.get(3), ErrorInfoConverter.toErrorInfo(dbo4));
    }

    @Test(dataProvider = "eventCategoryAndOperationKeyBlankProvider", expectedExceptions =
            IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* param must be non-.*")
    public void testGetErrorsByEventCategoryAndOperationKeyBlank(String eventCategory, String operationKey) {
        nodeEventsErrorsStorageService.getErrors(eventCategory, operationKey, 1000);
    }

    @Test
    public void testGetErrorByEventCategoryEventTypeAndPath()
            throws SQLException {
        String eventCategory = "EventCategory";
        String eventPath = "path";
        EventType eventType = EventType.create;
        NodeEventsErrorsDBO dbo = createErrorDBO(1, eventCategory, eventPath, "opkey", 1);

        when(nodeEventsErrorsDaoMock.select(eventCategory, eventType, eventPath, null)).thenReturn(dbo);

        Optional<NodeEventsErrorInfo> actualErrorInfo =
                nodeEventsErrorsStorageService.getError(eventCategory, eventType, eventPath);

        Assert.assertTrue(actualErrorInfo.isPresent());
        Assert.assertEquals(actualErrorInfo.get(), ErrorInfoConverter.toErrorInfo(dbo));
    }

    @Test(dataProvider = "eventCategoryEventTypeAndPathBlankProvider", expectedExceptions =
            IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* param must be non-.*")
    public void testGetErrorByEventCategoryEventTypeAndPathBlank(String eventCategory, EventType eventType,
            String eventPath) {
        nodeEventsErrorsStorageService.getError(eventCategory, eventType, eventPath);
    }

    @Test
    public void testGetErrorByEventCategoryEventTypePathAndOperationKey()
            throws SQLException {
        String eventCategory = "EventCategory";
        String eventPath = "path";
        EventType eventType = EventType.create;
        String operationKey = "operationKey";
        NodeEventsErrorsDBO dbo = createErrorDBO(1, eventCategory, eventPath, operationKey, 1);

        when(nodeEventsErrorsDaoMock.select(eventCategory, eventType, eventPath, operationKey)).thenReturn(dbo);

        Optional<NodeEventsErrorInfo> actualErrorInfo =
                nodeEventsErrorsStorageService.getError(eventCategory, eventType, eventPath, operationKey);

        Assert.assertTrue(actualErrorInfo.isPresent());
        Assert.assertEquals(actualErrorInfo.get(), ErrorInfoConverter.toErrorInfo(dbo));

        // Operation key is optional
        operationKey = null;
        dbo = createErrorDBO(1, eventCategory, eventPath, operationKey, 1);

        when(nodeEventsErrorsDaoMock.select(eventCategory, eventType, eventPath, operationKey)).thenReturn(dbo);

        actualErrorInfo =
                nodeEventsErrorsStorageService.getError(eventCategory, eventType, eventPath, operationKey);

        Assert.assertTrue(actualErrorInfo.isPresent());
        Assert.assertEquals(actualErrorInfo.get(), ErrorInfoConverter.toErrorInfo(dbo));
    }

    @Test(dataProvider = "eventCategoryEventTypePathAndOperationKeyBlankProvider", expectedExceptions =
            IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* param must be non-.*")
    public void testGetErrorByEventCategoryEventTypePathAndOperationKeyBlank(String eventCategory, EventType eventType,
            String eventPath, String operationKey) {
        nodeEventsErrorsStorageService.getError(eventCategory, eventType, eventPath, operationKey);
    }

    @Test
    public void testDeleteByNodeEventsErrorInfoOperationKeyNull() throws SQLException {
        String eventCategory = "EventCategory";
        String eventPath = "repoKey/path";
        EventType eventType = EventType.create;

        NodeEventsErrorInfo eventsErrorInfo = NodeEventsErrorInfo.builder()
                .eventCategory(eventCategory)
                .path(RepoPathFactory.create("repoKey", "path"))
                .eventType(eventType)
                .errorMessage("errMsg")
                .build();

        when(nodeEventsErrorsDaoMock.delete(eventCategory, eventType, eventPath, null)).thenReturn(true);

        boolean isDeleted = nodeEventsErrorsStorageService.delete(eventsErrorInfo);

        Assert.assertTrue(isDeleted);

        verify(nodeEventsErrorsDaoMock).delete(eventCategory, eventType, eventPath, null);
    }

    @Test
    public void testDeleteByNodeEventsErrorInfoWithOperationKey() throws SQLException {
        String eventCategory = "EventCategory";
        String eventPath = "repoKey/path";
        EventType eventType = EventType.create;
        String operationKey = "opKey";

        NodeEventsErrorInfo eventsErrorInfo = NodeEventsErrorInfo.builder()
                .eventCategory(eventCategory)
                .path(RepoPathFactory.create("repoKey", "path"))
                .eventType(eventType)
                .operationKey(operationKey)
                .errorMessage("errMsg")
                .build();

        when(nodeEventsErrorsDaoMock.delete(eventCategory, eventType, eventPath, operationKey)).thenReturn(true);

        boolean isDeleted = nodeEventsErrorsStorageService.delete(eventsErrorInfo);

        Assert.assertTrue(isDeleted);

        verify(nodeEventsErrorsDaoMock).delete(eventCategory, eventType, eventPath, operationKey);
    }

    @Test
    public void testDeleteByEventCategoryEventTypeAndPath() throws SQLException {
        String eventCategory = "EventCategory";
        String eventPath = "path";
        EventType eventType = EventType.create;

        when(nodeEventsErrorsDaoMock.delete(eventCategory, eventType, eventPath, null)).thenReturn(true);

        boolean isDeleted = nodeEventsErrorsStorageService.delete(eventCategory, eventType, eventPath);
        Assert.assertTrue(isDeleted);

        verify(nodeEventsErrorsDaoMock).delete(eventCategory, eventType, eventPath, null);
    }

    @Test(dataProvider = "deleteByEventCategoryEventTypeAndPathBlankProvider", expectedExceptions =
            IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* param must be non-.*")
    public void testDeleteByEventCategoryEventTypeAndPathBlank(String eventCategory, EventType eventType,
            String eventPath) {
        nodeEventsErrorsStorageService.delete(eventCategory, eventType, eventPath);
    }

    @Test
    public void testDeleteByEventCategoryEventTypePathAndOperationKey() throws SQLException {
        String eventCategory = "EventCategory";
        String eventPath = "path";
        EventType eventType = EventType.create;
        String operationKey = "opKey";

        when(nodeEventsErrorsDaoMock.delete(eventCategory, eventType, eventPath, operationKey)).thenReturn(true);

        boolean isDeleted = nodeEventsErrorsStorageService.delete(eventCategory, eventType, eventPath, operationKey);
        Assert.assertTrue(isDeleted);
        verify(nodeEventsErrorsDaoMock).delete(eventCategory, eventType, eventPath, operationKey);
    }

    @Test(dataProvider = "deleteByEventCategoryEventTypePathAndOperationKeyBlankProvider", expectedExceptions =
            IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* param must be non-.*")
    public void testDeleteByEventCategoryEventTypePathAndOperationKeyBlank(String eventCategory, EventType eventType,
            String eventPath, String operationKey) {
        nodeEventsErrorsStorageService.delete(eventCategory, eventType, eventPath, operationKey);
    }

    @Test
    public void testDeleteAllByEventCategoryAndOperationKey() throws SQLException {
        String eventCategory = "EventCategory";
        String operationKey = "opKey";

        when(nodeEventsErrorsDaoMock.delete(eventCategory, operationKey)).thenReturn(1);

        int numOfDeleted = nodeEventsErrorsStorageService.deleteAll(eventCategory, operationKey);
        Assert.assertEquals(numOfDeleted, 1);

        verify(nodeEventsErrorsDaoMock).delete(eventCategory, operationKey);
    }

    @Test(dataProvider = "deleteAllByEventCategoryAndOperationKeyBlankProvider", expectedExceptions =
            IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* param must be non-.*")
    public void testDeleteAllByEventCategoryAndOperationKeyBlank(String eventCategory, String operationKey) {
        nodeEventsErrorsStorageService.deleteAll(eventCategory, operationKey);
    }

    @Test
    public void testDeleteAllByEventCategoryEventTypeAndOperationKey() throws SQLException {
        String eventCategory = "EventCategory";
        EventType eventType = EventType.create;
        String operationKey = "opKey";

        when(nodeEventsErrorsDaoMock.delete(eventCategory, operationKey, eventType)).thenReturn(1);

        int numOfDeleted = nodeEventsErrorsStorageService.deleteAll(eventCategory, eventType, operationKey);
        Assert.assertEquals(numOfDeleted, 1);

        verify(nodeEventsErrorsDaoMock).delete(eventCategory, operationKey, eventType);
    }

    @Test
    public void testDeleteByErrorId() throws SQLException {
        when(nodeEventsErrorsDaoMock.delete(1)).thenReturn(true);
        Assert.assertTrue(nodeEventsErrorsStorageService.delete(1));
        verify(nodeEventsErrorsDaoMock).delete(1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testDeleteByErrorIdFailed() {
        nodeEventsErrorsStorageService.delete(-1);
    }

    @Test
    public void testDeleteBeforeWithEventId() throws SQLException {
        // Given
        when(nodeEventsErrorsDaoMock.deleteBeforeWithEventId(anyLong())).thenReturn(1);

        // When
        int numOfDeleted = nodeEventsErrorsStorageService.deleteBeforeWithEventId(5);

        // Then
        // Verify
        Assert.assertEquals(numOfDeleted, 1);
        verify(nodeEventsErrorsDaoMock, times(1)).deleteBeforeWithEventId(5);
    }

    @Test
    public void testGetFirstEventErrorTimestampWithEventId() throws SQLException {
        // Given
        when(nodeEventsErrorsDaoMock.getFirstEventErrorTimestampWithEventId()).thenReturn(1L);

        // When
        long timestamp = nodeEventsErrorsStorageService.getFirstEventErrorTimestampWithEventId();

        // Then
        // Verify
        Assert.assertEquals(timestamp, 1L);
        verify(nodeEventsErrorsDaoMock, times(1)).getFirstEventErrorTimestampWithEventId();
    }

    @Test
    public void testUpdate() throws SQLException {
        long errorId = 1L;
        when(internalDbServiceMock.nextId()).thenReturn(errorId);
        NodeEventsErrorInfo updatedErrorInfo = createErrorInfo(2, "newErrMsg", 1234567);
        NodeEventsErrorsDBO errorDBO = createErrorDBO(errorId, updatedErrorInfo);

        when(nodeEventsErrorsDaoMock.update(errorDBO)).thenReturn(1);
        int rowsUpdated = nodeEventsErrorsStorageService.update(updatedErrorInfo);

        Assert.assertEquals(rowsUpdated, 1);
        verify(nodeEventsErrorsDaoMock).update(errorDBO);
    }

    @Test
    public void testUpdateById() throws SQLException {
        long errorId = 1L;
        NodeEventsErrorInfo updatedErrorInfo = createErrorInfo(2, "newErrMsg", 1234567);
        nodeEventsErrorsStorageService.updateById(updatedErrorInfo, errorId);
        verify(nodeEventsErrorsDaoMock, times(1)).updateById(createErrorDBO(errorId, updatedErrorInfo));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUpdateByIdFailed() {
        long errorId = 0L;
        NodeEventsErrorInfo updatedErrorInfo = createErrorInfo(2, "newErrMsg", 1234567);
        nodeEventsErrorsStorageService.updateById(updatedErrorInfo, errorId);
    }

    @Test
    public void testUpdateOptionalOperationKeyNull() throws SQLException {
        long errorId = 1L;
        when(internalDbServiceMock.nextId()).thenReturn(errorId);
        NodeEventsErrorInfo updatedErrorInfoNullOpKey =
                createErrorInfo(
                        "EventCategory",
                        EventType.update,
                        RepoPathFactory.create("repo", "dir/file.jar"),
                        null,
                        123L,
                        2, "errMsg", 123L, 123L);

        NodeEventsErrorsDBO errorDBONullOpKey = createErrorDBO(errorId, updatedErrorInfoNullOpKey);
        when(nodeEventsErrorsDaoMock.update(errorDBONullOpKey)).thenReturn(1);

        int rowsUpdated = nodeEventsErrorsStorageService.update(updatedErrorInfoNullOpKey);

        Assert.assertEquals(rowsUpdated, 1);
        verify(nodeEventsErrorsDaoMock).update(errorDBONullOpKey);
    }

    @Test
    public void testUpdateOptionalOperationKeyEmpty() throws SQLException {
        long errorId = 1L;
        when(internalDbServiceMock.nextId()).thenReturn(errorId);
        NodeEventsErrorInfo updatedErrorInfoNullOpKey =
                createErrorInfo(
                        "EventCategory",
                        EventType.update,
                        RepoPathFactory.create("repo", "dir/file.jar"),
                        StringUtils.EMPTY,
                        123L,
                        2, "errMsg", 123L, 123L);

        NodeEventsErrorsDBO errorDBONullOpKey = createErrorDBO(errorId, updatedErrorInfoNullOpKey);
        when(nodeEventsErrorsDaoMock.update(errorDBONullOpKey)).thenReturn(1);

        int rowsUpdated = nodeEventsErrorsStorageService.update(updatedErrorInfoNullOpKey);

        Assert.assertEquals(rowsUpdated, 1);
        verify(nodeEventsErrorsDaoMock).update(errorDBONullOpKey);
    }

    @Test(dataProvider = "updateIllegalArgumentProvider", expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".* param must be non-.*")
    public void testUpdateIllegalArgument(NodeEventsErrorInfo updatedErrorInfo) {
        nodeEventsErrorsStorageService.update(updatedErrorInfo);
    }

    @DataProvider(name = "dbTypeQueryProvider")
    public Object[][] dbTypeQueryProvider() {
        return new Object[][]{
                {DbType.MYSQL, "SELECT * FROM node_events_errors WHERE event_category = ? AND operation_key = ? " +
                        "LIMIT ?", new Object[]{"category", "operation-key", 10}},
                {DbType.MARIADB, "SELECT * FROM node_events_errors WHERE event_category = ? AND operation_key = ? " +
                        "LIMIT ?", new Object[]{"category", "operation-key", 10}},
                {DbType.POSTGRESQL, "SELECT * FROM node_events_errors WHERE event_category = ? AND operation_key = ? " +
                        "LIMIT ?", new Object[]{"category", "operation-key", 10}},
                {DbType.MSSQL, "SELECT TOP (?) * FROM node_events_errors WHERE event_category = ? " +
                        "AND operation_key = ?", new Object[]{10, "category", "operation-key"}},
                {DbType.ORACLE, "SELECT * FROM node_events_errors WHERE event_category = ? AND operation_key = ? " +
                        "AND ROWNUM <= ?", new Object[]{"category", "operation-key", 10}},
                {DbType.DERBY, "SELECT * FROM node_events_errors WHERE event_category = ? AND operation_key = ? " +
                        "FETCH FIRST ? ROWS ONLY", new Object[]{"category", "operation-key", 10}},
        };
    }


    @Test(dataProvider = "dbTypeQueryProvider")
    public void testSelectWithLimitQueryGeneratesExpectedQuery(DbType dbType, String expectedQuery,
            Object[] expectedParams)
            throws NoSuchFieldException, SQLException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException {
        // Arrange
        SqlDaoHelper daoHelper = Mockito.mock(SqlDaoHelper.class);
        JdbcHelper jdbcHelper = Mockito.mock(JdbcHelper.class);
        ArtifactoryDbProperties artifactoryDbProperties = Mockito.mock(ArtifactoryDbProperties.class);
        NodeEventsErrorsDAO dao = Mockito.spy(new NodeEventsErrorsDAO(jdbcHelper, artifactoryDbProperties));

        // Inject mock SqlDaoHelper into DAO
        Field helperField = NodeEventsErrorsDAO.class.getDeclaredField("daoHelper");
        helperField.setAccessible(true);
        helperField.set(dao, daoHelper);

        Mockito.when(daoHelper.findAllByQuery(Mockito.anyString(), Mockito.any(Object[].class)))
                .thenReturn(List.of());

        Method method = NodeEventsErrorsDAO.class.getDeclaredMethod(
                "selectWithLimitQuery", DbType.class, String.class, String.class, int.class);
        method.setAccessible(true);
        method.invoke(dao, dbType, "category", "operation-key", 10);

        // Assert
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(daoHelper).findAllByQuery(queryCaptor.capture(),paramsCaptor.capture());
        Assert.assertEquals(queryCaptor.getValue(), expectedQuery);
        Assert.assertEquals(paramsCaptor.getValue(), expectedParams);
    }

    private NodeEventsErrorsDBO createErrorDBO(long errorId, NodeEventsErrorInfo eventsErrorInfo) {
        return NodeEventsErrorsDBO.builder()
                .errorId(errorId)
                .eventCategory(eventsErrorInfo.getEventCategory())
                .eventType(eventsErrorInfo.getEventType().code())
                .path(eventsErrorInfo.getPath().toPath())
                .operationKey(eventsErrorInfo.getOperationKey())
                .eventTime(eventsErrorInfo.getEventTime())
                .eventId(eventsErrorInfo.getEventId())
                .errorCount(eventsErrorInfo.getErrorCount())
                .errorMessage(eventsErrorInfo.getErrorMessage())
                .firstErrorTime(eventsErrorInfo.getFirstErrorTime())
                .lastErrorTime(eventsErrorInfo.getLastErrorTime())
                .build();
    }

    private NodeEventsErrorsDBO createErrorDBO(long errorId, String eventCategory, long eventTime) {
        return createErrorDBO(errorId, eventCategory, "opKey", eventTime);
    }

    private NodeEventsErrorsDBO createErrorDBO(long errorId, String eventCategory, String operationKey,
            long eventTime) {
        return createErrorDBO(errorId, eventCategory, "path", operationKey, eventTime);
    }

    private NodeEventsErrorsDBO createErrorDBO(long errorId, String eventCategory, String eventPath,
            String operationKey,
            long eventTime) {
        return NodeEventsErrorsDBO.builder()
                .errorId(errorId)
                .eventCategory(eventCategory)
                .eventType(1)
                .path(eventPath)
                .operationKey(operationKey)
                .eventTime(eventTime)
                .errorCount(1)
                .errorMessage("errMsg")
                .firstErrorTime(123)
                .lastErrorTime(123)
                .build();
    }
}