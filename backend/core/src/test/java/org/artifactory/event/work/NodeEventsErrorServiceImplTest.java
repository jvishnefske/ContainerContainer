package org.artifactory.event.work;

import com.google.common.collect.Lists;
import org.artifactory.event.EventType;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.service.NodeEventsErrorsStorageService;
import org.artifactory.repo.event.EventInfo;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class NodeEventsErrorServiceImplTest {

    @Mock
    private NodeEventsErrorsStorageService errorsStorageServiceMock;
    @Mock
    private NodeEventsErrorsBackOffStrategy backOffStrategyMock;

    private MockitoSession mockitoSession;
    private NodeEventsErrorServiceImpl nodeEventsErrorService;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
        nodeEventsErrorService = new NodeEventsErrorServiceImpl(errorsStorageServiceMock);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testProcessNewError() {
        String eventCategory = "EventCategory";
        String operatorId = "opId";
        EventType eventType = EventType.create;
        String path = "path/";
        String errMsg = "errMsg";
        RuntimeException exception = new RuntimeException(errMsg);
        when(errorsStorageServiceMock.getError(eventCategory, eventType, path, operatorId))
                .thenReturn(Optional.empty());

        PrioritizedNodeEvent prioritizedNodeEvent =
                new PrioritizedNodeEvent(new EventInfo(1L, eventType, path), operatorId);

        nodeEventsErrorService.process(eventCategory, prioritizedNodeEvent, exception);

        ArgumentCaptor<NodeEventsErrorInfo> errorInfoArgumentCaptor =
                ArgumentCaptor.forClass(NodeEventsErrorInfo.class);

        verify(errorsStorageServiceMock).add(errorInfoArgumentCaptor.capture());

        NodeEventsErrorInfo actualErrorInfo = errorInfoArgumentCaptor.getValue();

        Assert.assertEquals(actualErrorInfo.getEventCategory(), eventCategory);
        Assert.assertEquals(actualErrorInfo.getEventType(), eventType);
        Assert.assertEquals(actualErrorInfo.getPath().toPath(), path);
        Assert.assertEquals(actualErrorInfo.getOperationKey(), operatorId);
        Assert.assertEquals(actualErrorInfo.getErrorMessage(),
                "Exception: " + errMsg + ", Root cause: java.lang.RuntimeException: " + errMsg);
    }

    @Test
    public void testUpdateExistingError() {
        String eventCategory = "EventCategory";
        String operatorId = "opId";
        EventType eventType = EventType.create;
        String repoPath = "path/";
        String errMsg = "errMsg";
        String newErrMsg = "newErrMsg";
        RuntimeException exception = new RuntimeException(newErrMsg);
        int lastErrorTime = 123;
        int errorCount = 1;

        NodeEventsErrorInfo errorInfo = NodeEventsErrorInfo.builder()
                .eventCategory(eventCategory)
                .operationKey(operatorId)
                .eventType(eventType)
                .path(RepoPathFactory.create("path"))
                .errorCount(errorCount)
                .lastErrorTime(lastErrorTime)
                .errorMessage(errMsg)
                .build();

        when(errorsStorageServiceMock.getError(eventCategory, eventType, repoPath, operatorId))
                .thenReturn(Optional.of(errorInfo));

        PrioritizedNodeEvent prioritizedNodeEvent =
                new PrioritizedNodeEvent(new EventInfo(1L, eventType, repoPath), operatorId);

        nodeEventsErrorService.process(eventCategory, prioritizedNodeEvent, exception);

        ArgumentCaptor<NodeEventsErrorInfo> errorInfoArgumentCaptor =
                ArgumentCaptor.forClass(NodeEventsErrorInfo.class);

        verify(errorsStorageServiceMock).update(errorInfoArgumentCaptor.capture());

        NodeEventsErrorInfo actualErrorInfo = errorInfoArgumentCaptor.getValue();

        Assert.assertEquals(actualErrorInfo.getEventCategory(), eventCategory);
        Assert.assertEquals(actualErrorInfo.getEventType(), eventType);
        Assert.assertEquals(actualErrorInfo.getPath().toPath(), repoPath);
        Assert.assertEquals(actualErrorInfo.getOperationKey(), operatorId);
        Assert.assertEquals(actualErrorInfo.getErrorMessage(),
                "Exception: " + newErrMsg + ", Root cause: java.lang.RuntimeException: " + newErrMsg);
        Assert.assertNotEquals(actualErrorInfo.getLastErrorTime(), lastErrorTime);
        Assert.assertEquals(actualErrorInfo.getErrorCount(), errorCount + 1);
    }

    @Test
    public void testGetErrors() {
        NodeEventsErrorInfo errorInfo1 = NodeEventsErrorInfo.builder()
                .eventCategory("eventCategory")
                .operationKey("operatorId")
                .eventType(EventType.create)
                .path(RepoPathFactory.create("path"))
                .errorCount(1)
                .lastErrorTime(123L)
                .errorMessage("errMsg1")
                .build();
        NodeEventsErrorInfo errorInfo2 = NodeEventsErrorInfo.builder()
                .eventCategory("eventCategory")
                .operationKey("operatorId")
                .eventType(EventType.delete)
                .path(RepoPathFactory.create("path"))
                .errorCount(1)
                .lastErrorTime(123L)
                .errorMessage("errMsg2")
                .build();
        List<NodeEventsErrorInfo> errorInfos = Lists.newArrayList(errorInfo1, errorInfo2);

        when(errorsStorageServiceMock.getErrors("eventCategory",
                "operatorId", 1000)).thenReturn(errorInfos);
        doReturn(true).when(backOffStrategyMock).isBackoffDelayRequired(eq(errorInfo1), anyLong());
        doReturn(false).when(backOffStrategyMock).isBackoffDelayRequired(eq(errorInfo2), anyLong());

        List<NodeEventsErrorInfo> actualErrors =
                nodeEventsErrorService.getErrors("eventCategory", "operatorId", backOffStrategyMock, 1000);

        Assert.assertNotNull(actualErrors);
        Assert.assertFalse(actualErrors.isEmpty());
        Assert.assertEquals(actualErrors.get(0), errorInfo2);
    }

    @Test
    public void testMaxRetriesReached() {
        String eventCategory = "EventCategory";
        String operatorId = "opId";
        EventType eventType = EventType.create;
        String repoPath = "path/";
        String errMsg = "errMsg";
        String newErrMsg = "newErrMsg";
        RuntimeException exception = new RuntimeException(newErrMsg);
        int lastErrorTime = 123;
        int errorCount = NodeEventsErrorServiceImpl.MAX_RETRIES;

        NodeEventsErrorInfo errorInfo = NodeEventsErrorInfo.builder()
                .eventCategory(eventCategory)
                .operationKey(operatorId)
                .eventType(eventType)
                .path(RepoPathFactory.create("path"))
                .errorCount(errorCount)
                .lastErrorTime(lastErrorTime)
                .errorMessage(errMsg)
                .build();

        when(errorsStorageServiceMock.getError(eventCategory, eventType, repoPath, operatorId))
                .thenReturn(Optional.of(errorInfo));

        PrioritizedNodeEvent prioritizedNodeEvent =
                new PrioritizedNodeEvent(new EventInfo(1L, eventType, repoPath), operatorId);

        nodeEventsErrorService.process(eventCategory, prioritizedNodeEvent, exception);

        verify(errorsStorageServiceMock, times(1)).delete(errorInfo);
    }
}