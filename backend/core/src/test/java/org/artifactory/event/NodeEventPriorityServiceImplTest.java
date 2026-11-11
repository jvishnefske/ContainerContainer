package org.artifactory.event;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.event.priority.service.NodeEventPriorityChart;
import org.artifactory.event.priority.service.NodeEventPriorityServiceImpl;
import org.artifactory.event.priority.service.NodeEventPriorityStorageService;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.event.provider.EventLogAndErrorsEventProvider;
import org.artifactory.event.provider.NodesTableEventProvider;
import org.artifactory.event.work.NodeEventOperator;
import org.artifactory.event.work.NodeEventsErrorService;
import org.artifactory.event.work.NodeEventsErrorsBackOffStrategy;
import org.artifactory.metadata.service.indexer.InternalMetadataServiceIndexHandler;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.error.factory.NodeEventsErrorInfoFactory;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.application.ApplicationServiceConfigurationUtil;
import org.jfrog.common.ClockUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 * @author Uriah Levy
 */
@Test
public class NodeEventPriorityServiceImplTest extends ArtifactoryHomeBoundTest {

    @Mock
    AqlEagerResult<AqlItem> result;
    @Mock
    private EventsService eventService;
    @Mock
    private NodeEventCursorService cursorService;
    @Mock
    private InternalDbService dbService;
    @Mock
    private NodeEventPriorityStorageService eventPriorityStorageService;
    @Mock
    private NodeEventsErrorService nodeEventsErrorService;
    @Mock
    private NodeEventOperator eventOperator;
    @Mock
    private NodeEventsErrorsBackOffStrategy backOffStrategy;
    @Mock
    private InternalMetadataServiceIndexHandler metadataServiceIndexHandler;
    private AutoCloseable mocks;

    @BeforeMethod
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        when(metadataServiceIndexHandler.shouldUseRealTimeIndex(anyString())).thenReturn(false);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    public void testCreateEventPrioritiesFromEventLogAndEventErrors() {
        EventLogAndErrorsEventProvider eventProvider =
                new EventLogAndErrorsEventProvider(eventService, nodeEventsErrorService,
                        eventOperator);
        String operatorId = "metadata-event-operator";
        NodeEventPriorityServiceImpl nodeEventPriorityService =
                spy(new NodeEventPriorityServiceImpl(cursorService, eventPriorityStorageService,
                        metadataServiceIndexHandler));
        NodeEventCursor currentCursor =
                new NodeEventCursor(operatorId, - TimeUnit.MINUTES.toMillis(10), NodeEventCursorType.METADATA_EVENTS);
        ImmutableList<EventInfo> events = ImmutableList
                .of(new EventInfo(ClockUtils.epochMillis() - TimeUnit.MINUTES.toMillis(5), EventType.props,
                        "a/b/c/foo.rpm"));

        List<NodeEventsErrorInfo> eventsErrorInfos = Lists.newArrayList(NodeEventsErrorInfoFactory
                .create("EventCategory", EventType.create, RepoPathFactory.create("npm-local", "d/e/f"), operatorId,
                        null, 1L, 1, "errMsg", 1L, 1L));
        when(nodeEventsErrorService.getErrors("EventCategory", operatorId, backOffStrategy, 1000))
                .thenReturn(eventsErrorInfos);

        when(eventOperator.getOperatorId()).thenReturn(operatorId);
        when(eventOperator.getExecutionPredicate()).thenReturn(x -> true);
        when(eventOperator.meetsExecutionCriteria(any())).thenReturn(true);
        when(eventOperator.getEventCategory()).thenReturn("EventCategory");
        when(eventOperator.getBackOffStrategy()).thenReturn(backOffStrategy);
        when(cursorService.cursorForOperator(operatorId)).thenReturn(currentCursor);
        when(eventService.getEventsSince(eq(currentCursor.getEventMarker()), anyLong())).thenReturn(events.stream());
        when(dbService.nextId()).thenReturn(1L);
        doNothing().when(eventPriorityStorageService).savePriorityChart(any());

        nodeEventPriorityService.createAndSaveEventPrioritiesIfNeeded(eventOperator, eventProvider);

        ArgumentCaptor<NodeEventPriorityChart> nodeEventPriorityChartArgumentCaptor =
                ArgumentCaptor.forClass(NodeEventPriorityChart.class);

        verify(eventPriorityStorageService).savePriorityChart(nodeEventPriorityChartArgumentCaptor.capture());

        NodeEventPriorityChart priorityChart = nodeEventPriorityChartArgumentCaptor.getValue();
        List<PrioritizedNodeEvent> prioritizedNodeEvents = priorityChart.getEvents();

        boolean foundFromNodesTable = prioritizedNodeEvents.stream()
                .anyMatch(event -> event.getPath().equals("a/b/c/foo.rpm") && event.getPriorityId() == 0 &&
                        event.getPriority() == 1);
        Assert.assertTrue(foundFromNodesTable, "Prioritized node event from node table did not reach priority chart");

        boolean foundFromErrorsTable = prioritizedNodeEvents.stream()
                .anyMatch(event -> event.getPath().equals("npm-local/d/e/f") && event.getPriorityId() == 0 &&
                        event.getPriority() == 1);
        Assert.assertTrue(foundFromErrorsTable,
                "Prioritized node event from errors table did not reach priority chart");

        verify(cursorService, times(1)).updateCursor(anyString(), anyLong());
    }

    public void testCursorNotUpdatedWhenAllEventsFromErrorsTable() {
        EventLogAndErrorsEventProvider eventProvider =
                new EventLogAndErrorsEventProvider(eventService, nodeEventsErrorService,
                        eventOperator);
        String operatorId = "metadata-event-operator";
        NodeEventPriorityServiceImpl nodeEventPriorityService =
                spy(new NodeEventPriorityServiceImpl(cursorService, eventPriorityStorageService,
                        metadataServiceIndexHandler));
        NodeEventCursor currentCursor =
                new NodeEventCursor(operatorId, ClockUtils.epochMillis(), NodeEventCursorType.METADATA_EVENTS);

        List<NodeEventsErrorInfo> eventsErrorInfos = Lists.newArrayList(NodeEventsErrorInfoFactory
                .create("EventCategory", EventType.create, RepoPathFactory.create("npm-local", "d/e/f"), operatorId,
                        null, 1L, 1, "errMsg", 1L, 1L));
        when(nodeEventsErrorService.getErrors("EventCategory", operatorId, backOffStrategy, 1000))
                .thenReturn(eventsErrorInfos);

        when(eventOperator.getOperatorId()).thenReturn(operatorId);
        when(eventOperator.getExecutionPredicate()).thenReturn(x -> true);
        when(eventOperator.meetsExecutionCriteria(any())).thenReturn(true);
        when(eventOperator.getEventCategory()).thenReturn("EventCategory");
        when(eventOperator.getBackOffStrategy()).thenReturn(backOffStrategy);
        when(cursorService.cursorForOperator(operatorId)).thenReturn(currentCursor);
        when(dbService.nextId()).thenReturn(1L);
        doNothing().when(eventPriorityStorageService).savePriorityChart(any());

        nodeEventPriorityService.createAndSaveEventPrioritiesIfNeeded(eventOperator, eventProvider);

        ArgumentCaptor<NodeEventPriorityChart> nodeEventPriorityChartArgumentCaptor =
                ArgumentCaptor.forClass(NodeEventPriorityChart.class);

        verify(eventPriorityStorageService).savePriorityChart(nodeEventPriorityChartArgumentCaptor.capture());

        NodeEventPriorityChart priorityChart = nodeEventPriorityChartArgumentCaptor.getValue();
        List<PrioritizedNodeEvent> prioritizedNodeEvents = priorityChart.getEvents();


        boolean foundFromErrorsTable = prioritizedNodeEvents.stream()
                .anyMatch(event -> event.getPath().equals("npm-local/d/e/f") && event.getPriorityId() == 0 &&
                        event.getPriority() == 1);
        Assert.assertTrue(foundFromErrorsTable,
                "Prioritized node event from errors table did not reach priority chart");

        verify(cursorService, never()).updateCursor(anyString(), anyLong());
    }

    public void testUnstableMarginEventsExcluded() {
        when(metadataServiceIndexHandler.shouldUseRealTimeIndex(anyString())).thenReturn(true);
        EventLogAndErrorsEventProvider eventProvider =
                new EventLogAndErrorsEventProvider(eventService, nodeEventsErrorService,
                        eventOperator);
        String operatorId = "metadata-event-operator";
        NodeEventPriorityServiceImpl nodeEventPriorityService = spy(
                new NodeEventPriorityServiceImpl(cursorService, eventPriorityStorageService,
                        metadataServiceIndexHandler));
        NodeEventCursor currentCursor =
                new NodeEventCursor(operatorId, ClockUtils.epochMillis(), NodeEventCursorType.METADATA_EVENTS);
        ImmutableList<EventInfo> events = ImmutableList
                .of(new EventInfo(ClockUtils.epochMillis(), EventType.props, "a/b/c/foo.rpm"));

        when(eventOperator.getOperatorId()).thenReturn(operatorId);
        when(eventOperator.getExecutionPredicate()).thenReturn(x -> true);
        when(eventOperator.meetsExecutionCriteria(any())).thenReturn(true);
        when(cursorService.cursorForOperator(operatorId)).thenReturn(
                currentCursor);
        when(eventService.getEventsSince(eq(currentCursor.getEventMarker()), anyLong())).thenReturn(events.stream());
        when(dbService.nextId()).thenReturn(1L);
        doNothing().when(eventPriorityStorageService).savePriorityChart(any());

        nodeEventPriorityService.createAndSaveEventPrioritiesIfNeeded(eventOperator, eventProvider);
        // Within the unstable margin
        verify(eventPriorityStorageService, times(0)).saveRetryCount(any());
    }

    public void testApplicationPropertyCleanupEventFilteredOut() {
        when(metadataServiceIndexHandler.shouldUseRealTimeIndex(anyString())).thenReturn(true);
        EventLogAndErrorsEventProvider eventProvider =
                new EventLogAndErrorsEventProvider(eventService, nodeEventsErrorService,
                        eventOperator);
        String operatorId = "metadata-event-operator";
        NodeEventPriorityServiceImpl nodeEventPriorityService = spy(
                new NodeEventPriorityServiceImpl(cursorService, eventPriorityStorageService,
                        metadataServiceIndexHandler));
        NodeEventCursor currentCursor =
                new NodeEventCursor(operatorId, ClockUtils.epochMillis(), NodeEventCursorType.METADATA_EVENTS);
        ImmutableList<EventInfo> events = ImmutableList
                .of(new EventInfo(ClockUtils.epochMillis(), EventType.props, "a/b/c/foo.rpm"),
                        new EventInfo(EventType.props, "a/b/c/my_test.create",
                                Set.of(EventHintType.ApplicationPropertyCleanup.getPrefix() +
                                ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_ID_PROPERTY_NAME)));

        when(eventOperator.getOperatorId()).thenReturn(operatorId);
        when(eventOperator.getExecutionPredicate()).thenReturn(x -> true);
        when(eventOperator.meetsExecutionCriteria(any())).thenReturn(true);
        when(cursorService.cursorForOperator(operatorId)).thenReturn(
                currentCursor);
        when(eventService.getEventsSince(eq(currentCursor.getEventMarker()), anyLong())).thenReturn(events.stream());
        when(dbService.nextId()).thenReturn(1L);
        doNothing().when(eventPriorityStorageService).savePriorityChart(any());

        nodeEventPriorityService.createAndSaveEventPrioritiesIfNeeded(eventOperator, eventProvider);
        verify(eventPriorityStorageService, times(0)).saveRetryCount(any());
    }

    public void testCreateEventPrioritiesFromNodesTable() {
        NodeEventOperator eventOperator = mock(NodeEventOperator.class);
        final AqlService aqlService = mock(AqlService.class);
        String operatorId = "metadata-event-operator";
        NodeEventPriorityServiceImpl nodeEventPriorityService = spy(
                new NodeEventPriorityServiceImpl(cursorService, eventPriorityStorageService,
                        metadataServiceIndexHandler));
        final NodesTableEventProvider nodesTableEventProvider = new NodesTableEventProvider(aqlService, null, () -> {
        }, a -> new AqlApiItem(false));
        NodeEventCursor eventCursor =
                new NodeEventCursor(operatorId, ClockUtils.epochMillis(), NodeEventCursorType.METADATA_EVENTS);

        AqlItem aqlItem = mock(AqlItem.class);
        when(aqlItem.getCreated()).thenReturn(new Date(ClockUtils.epochMillis()));
        when(aqlItem.getPath()).thenReturn("a/b/c");
        when(aqlItem.getRepo()).thenReturn("rpm-local");
        when(aqlItem.getName()).thenReturn("foo.rpm");
        when(result.getResults()).thenReturn(ImmutableList.of(aqlItem));
        when(aqlService.executeQueryEager(any(AqlApiItem.class))).thenReturn(result);
        when(eventOperator.getOperatorId()).thenReturn(operatorId);
        when(eventOperator.getExecutionPredicate()).thenReturn(x -> true);
        when(eventOperator.meetsExecutionCriteria(any())).thenReturn(true);
        when(cursorService.cursorForOperator(operatorId)).thenReturn(
                eventCursor);
        when(dbService.nextId()).thenReturn(1L);
        doNothing().when(eventPriorityStorageService).savePriorityChart(any());

        nodeEventPriorityService.createAndSaveEventPrioritiesIfNeeded(eventOperator, nodesTableEventProvider);
        verify(eventPriorityStorageService).savePriorityChart(argThat(argument -> argument.getEvents().stream()
                .anyMatch(event -> event.getPath().equals("rpm-local/a/b/c/foo.rpm") && event.getPriorityId() == 0 &&
                        event.getPriority() == 1)));
    }

}