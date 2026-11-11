package org.artifactory.event.work;

import ch.qos.logback.classic.Logger;
import org.artifactory.event.EventType;
import org.artifactory.event.priority.service.NodeEventPriorityService;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.storage.db.locks.service.UnsupportedReentrantLockException;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.metadata.client.exception.MetadataClientException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.List;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.jfrog.test.log.MemoryAppender;
import static org.artifactory.event.priority.service.NodeEventPriorityServiceImpl.MAX_RETRIES;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 * @author Uriah Levy
 */
@Test
public class NodeEventSweeperTest extends ArtifactoryHomeBoundTest {

    @Mock
    private NodeEventPriorityService priorityService;
    @Mock
    private NodeEventsErrorService nodeEventsErrorService;
    @Mock
    private NodeEventOperator operator;
    @Mock
    private EventOperatorDispatcher dispatcher;
    @Mock
    private ConflictsGuard<String> conflictsGuard;
    private MockitoSession mockitoSession;
    private MemoryAppender memoryAppender;

    @BeforeMethod
    public void beforeMethod() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
    }

    @AfterMethod
    public void afterMethod() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testRun() {
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123L, "a/b/c", EventType.props,
                "metadata-event-operator", 0, ClockUtils.epochMillis(), 1, PrioritizedNodeEvent.EventStatus.PENDING);
        final List<PrioritizedNodeEvent> nodeEvents = Collections.singletonList(prioritizedNodeEvent);
        NodeEventSweeper nodeEventSweeper = spy(new NodeEventSweeper(priorityService, operator,
                dispatcher, nodeEvents, getRetryOptions(), nodeEventsErrorService, conflictsGuard));
        doNothing().when(nodeEventSweeper).tryToProcessEvent(nodeEvents.get(0));
        nodeEventSweeper.run();
        verify(dispatcher).sweeperDone();
        verify(nodeEventSweeper).tryToProcessEvent(nodeEvents.get(0));
        verify(nodeEventsErrorService, times(0))
                .process(any(String.class), any(PrioritizedNodeEvent.class), any(Exception.class));
    }

    @Test
    public void testRunWithProcessedEvent() {
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123L, "a/b/c", EventType.props,
                "metadata-event-operator", 0, ClockUtils.epochMillis(), 1, PrioritizedNodeEvent.EventStatus.PROCESSED);
        final List<PrioritizedNodeEvent> nodeEvents = Collections.singletonList(prioritizedNodeEvent);
        NodeEventSweeper nodeEventSweeper = spy(new NodeEventSweeper(priorityService, operator,
                dispatcher, nodeEvents, getRetryOptions(), nodeEventsErrorService, conflictsGuard));
        nodeEventSweeper.run();
        verify(dispatcher).sweeperDone();
        verify(nodeEventSweeper, times(0)).tryToProcessEvent(nodeEvents.get(0));
        verify(nodeEventsErrorService, times(0))
                .process(any(String.class), any(PrioritizedNodeEvent.class), any(Exception.class));
    }

    @Test
    public void testRetries() {
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123L, "a/b/c", EventType.props,
                "metadata-event-operator", 0, ClockUtils.epochMillis(), 0, PrioritizedNodeEvent.EventStatus.PENDING);
        final List<PrioritizedNodeEvent> nodeEvents = Collections.singletonList(prioritizedNodeEvent);

        when(operator.getEventCategory()).thenReturn("EventCategory");
        when(operator.isOperatorEnabled()).thenReturn(true);
        NodeEventSweeper nodeEventSweeper = spy(new NodeEventSweeper(priorityService, operator,
                dispatcher, nodeEvents, getRetryOptions(), nodeEventsErrorService, conflictsGuard));
        doAnswer(a -> {
            nodeEventSweeper.executeWithRetry(() -> nodeEventSweeper.doProcessEvent(prioritizedNodeEvent),
                    prioritizedNodeEvent);
            return null;
        }).when(dispatcher).runExclusive(any(), anyString(), any());
        MetadataClientException metadataClientException = new MetadataClientException("Metadata service error");
        doThrow(metadataClientException).when(nodeEventSweeper).doProcessEvent(prioritizedNodeEvent);
        nodeEventSweeper.run();
        verify(nodeEventSweeper, times(5)).doProcessEvent(prioritizedNodeEvent);
        verify(dispatcher, times(5)).batchError();
        verify(priorityService).saveRetryCount(any());
        verify(nodeEventsErrorService)
                .process("EventCategory", prioritizedNodeEvent, metadataClientException);
    }

    @Test
    public void testRetriesRunExclusive() {
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123L, "a/b/c", EventType.props,
                "metadata-event-operator", 0, ClockUtils.epochMillis(), 0, PrioritizedNodeEvent.EventStatus.PENDING);
        final List<PrioritizedNodeEvent> nodeEvents = Collections.singletonList(prioritizedNodeEvent);

        // Only stub what's actually used
        when(operator.isOperatorEnabled()).thenReturn(true);

        NodeEventSweeper nodeEventSweeper = spy(new NodeEventSweeper(priorityService, operator,
                dispatcher, nodeEvents, getRetryOptions(), nodeEventsErrorService, conflictsGuard));
        UnsupportedReentrantLockException unsupportedReentrantLockException = new UnsupportedReentrantLockException();

        doThrow(unsupportedReentrantLockException).when(dispatcher).runExclusive(any(), anyString(), any());

        nodeEventSweeper.run();

        verify(nodeEventSweeper, times(0)).doProcessEvent(prioritizedNodeEvent);
        verify(dispatcher, times(1)).batchError();
    }


    @Test
    public void testErrorRemovedAfterSuccess() {
        PrioritizedNodeEvent prioritizedNodeEvent = new PrioritizedNodeEvent(123L, "a/b/c", EventType.props,
                "metadata-event-operator", 0, ClockUtils.epochMillis(), 0, PrioritizedNodeEvent.EventStatus.PENDING);
        final List<PrioritizedNodeEvent> nodeEvents = Collections.singletonList(prioritizedNodeEvent);
        when(operator.getEventCategory()).thenReturn("EventCategory");
        when(operator.isOperatorEnabled()).thenReturn(true);
        NodeEventSweeper nodeEventSweeper = spy(new NodeEventSweeper(priorityService, operator,
                dispatcher, nodeEvents, getRetryOptions(), nodeEventsErrorService, conflictsGuard));
        doAnswer(a -> {
            nodeEventSweeper.executeWithRetry(() -> nodeEventSweeper.doProcessEvent(prioritizedNodeEvent),
                    prioritizedNodeEvent);
            return null;
        }).when(dispatcher).runExclusive(any(), anyString(), any());
        nodeEventSweeper.run();
        verify(nodeEventSweeper, times(1)).doProcessEvent(prioritizedNodeEvent);
        verify((nodeEventsErrorService), times(1)).removeError("EventCategory", prioritizedNodeEvent);
    }

    @Test
    public void testExecuteWithRetry_LogsWarnOnIllegalArgumentException() {
        PrioritizedNodeEvent event = new PrioritizedNodeEvent(123L, "a/b/c", EventType.props,
                "metadata-event-operator", 0, ClockUtils.epochMillis(), 0, PrioritizedNodeEvent.EventStatus.PENDING);

        NodeEventSweeper sweeper = new NodeEventSweeper(priorityService, operator, dispatcher,
                Collections.singletonList(event), getRetryOptions(), nodeEventsErrorService, conflictsGuard);

        Logger logger = (Logger) LoggerFactory.getLogger("org.artifactory.event.work");
        MemoryAppender memoryAppender = new MemoryAppender();
        memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        logger.addAppender(memoryAppender);
        memoryAppender.start();

        String exceptionMessage = "Failed executing requestPath, with response code: " +
                "STATUS and response message: message";
        sweeper.executeWithRetry(() -> { throw new IllegalArgumentException(exceptionMessage); }, event);

        // Assert the warning log message is present
        assertThat(memoryAppender.search("Unable to execute event on path a/b/c", Level.WARN)).isNotEmpty().hasSize(5);
        // Assert the exception message is present in the logs
        assertThat(memoryAppender.search("", Level.WARN)).isNotEmpty().hasSize(6);
        memoryAppender.stop();
    }

    private ExecutionUtils.RetryOptions getRetryOptions() {
        return ExecutionUtils.RetryOptions.builder()
                .backoffMaxDelay(5)
                .exponentialBackoffMultiplier(3)
                .numberOfRetries(MAX_RETRIES)
                .timeout(10)
                .build();
    }
}