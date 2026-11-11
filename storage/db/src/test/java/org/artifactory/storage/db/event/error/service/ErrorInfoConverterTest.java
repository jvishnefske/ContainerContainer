package org.artifactory.storage.db.event.error.service;

import org.artifactory.event.EventType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Mor Merhav
 */
public class ErrorInfoConverterTest {

    @DataProvider
    public static Object[][] toErrorInfoProvider() {
        return new Object[][] {
                {1, "eventCategory", EventType.create.code(), "path/to/file.jar", null, 123, 10, "errMsg", 5, 6},
                {2, "eventCategory2", EventType.update.code(), "path/to/file.jar", "opKey", 123, 10, "errMsg2", 5, 6}
        };
    }

    @DataProvider
    public static Object[][] toDboDataProvider() {
        return new Object[][] {
                {"eventCategory", EventType.create, RepoPathFactory.create("path",
                        "to/file.jar"), null, 123, 10, "errMsg", 5, 6},
                {"eventCategory2", EventType.update, RepoPathFactory.create("path2",
                        "to/file.jar"), "opKey", 123, 10, "errMsg", 5, 6}
        };
    }

    @Test(dataProvider = "toErrorInfoProvider")
    public void testToErrorInfo(long errorId, String eventCategory, int eventType, String path, String operationKey,
            long eventTime, int errorCount, String errMsg, long firstErrTime, long lastErrTime) {
        NodeEventsErrorsDBO dbo = NodeEventsErrorsDBO.builder()
                .errorId(errorId)
                .eventCategory(eventCategory)
                .eventType(eventType)
                .path(path)
                .operationKey(operationKey)
                .eventTime(eventTime)
                .errorCount(errorCount)
                .errorMessage(errMsg)
                .firstErrorTime(firstErrTime)
                .lastErrorTime(lastErrTime)
                .build();

        NodeEventsErrorInfo nodeEventsErrorInfo = ErrorInfoConverter.toErrorInfo(dbo);

        Assert.assertEquals(nodeEventsErrorInfo.getEventCategory(), dbo.getEventCategory());
        Assert.assertEquals(nodeEventsErrorInfo.getEventType().code(), dbo.getEventType());
        Assert.assertEquals(nodeEventsErrorInfo.getPath().toPath(), dbo.getPath());
        Assert.assertEquals(nodeEventsErrorInfo.getOperationKey(), dbo.getOperationKey());
        Assert.assertEquals(nodeEventsErrorInfo.getEventTime(), dbo.getEventTime());
        Assert.assertEquals(nodeEventsErrorInfo.getErrorCount(), dbo.getErrorCount());
        Assert.assertEquals(nodeEventsErrorInfo.getErrorMessage(), dbo.getErrorMessage());
        Assert.assertEquals(nodeEventsErrorInfo.getFirstErrorTime(), dbo.getFirstErrorTime());
        Assert.assertEquals(nodeEventsErrorInfo.getLastErrorTime(), dbo.getLastErrorTime());
    }

    @Test(dataProvider = "toDboDataProvider")
    public void testToDBO(String eventCategory, EventType eventType, RepoPath repoPath, String opKey, long eventTime,
            int errCount, String errMsg, long firstErrTime, long lastErrTime) {
        NodeEventsErrorInfo nodeEventsErrorInfo = NodeEventsErrorInfo.builder()
                .eventCategory(eventCategory)
                .eventType(eventType)
                .path(repoPath)
                .operationKey(opKey)
                .eventTime(eventTime)
                .errorCount(errCount)
                .errorMessage(errMsg)
                .firstErrorTime(firstErrTime)
                .lastErrorTime(lastErrTime)
                .build();

        NodeEventsErrorsDBO dbo = ErrorInfoConverter.toDBO(1, nodeEventsErrorInfo);

        Assert.assertEquals(dbo.getErrorId(), 1);
        Assert.assertEquals(dbo.getEventCategory(), nodeEventsErrorInfo.getEventCategory());
        Assert.assertEquals(dbo.getEventType(), nodeEventsErrorInfo.getEventType().code());
        Assert.assertEquals(dbo.getPath(), nodeEventsErrorInfo.getPath().toPath());
        Assert.assertEquals(dbo.getOperationKey(), nodeEventsErrorInfo.getOperationKey());
        Assert.assertEquals(dbo.getEventTime(), nodeEventsErrorInfo.getEventTime());
        Assert.assertEquals(dbo.getErrorCount(), nodeEventsErrorInfo.getErrorCount());
        Assert.assertEquals(dbo.getErrorMessage(), nodeEventsErrorInfo.getErrorMessage());
        Assert.assertEquals(dbo.getFirstErrorTime(), nodeEventsErrorInfo.getFirstErrorTime());
        Assert.assertEquals(dbo.getLastErrorTime(), nodeEventsErrorInfo.getLastErrorTime());
    }
}