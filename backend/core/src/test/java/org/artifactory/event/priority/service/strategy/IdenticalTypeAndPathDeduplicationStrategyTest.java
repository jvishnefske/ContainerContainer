package org.artifactory.event.priority.service.strategy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.event.EventType;
import org.artifactory.event.priority.service.NodeEventPriorityChart;
import org.artifactory.event.priority.service.NodeEventPriorityChartHelper;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.repo.event.EventInfo;
import org.jfrog.common.ClockUtils;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class IdenticalTypeAndPathDeduplicationStrategyTest {

    private static final List<Pair<EventType, EventType>> RESPECTIVE_MDS_SQUASHABLE_TYPES =
            Lists.newArrayList(new ImmutablePair<>(EventType.create, EventType.props),
                    new ImmutablePair<>(EventType.props, EventType.create));
    private AutoCloseable mocks;

    @BeforeMethod
    void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    public void testFindNextBoundaryByTypeAndPath() {
        IdenticalTypeAndPathDeduplicationStrategy strategy =
                new IdenticalTypeAndPathDeduplicationStrategy(RESPECTIVE_MDS_SQUASHABLE_TYPES);
        final long now = ClockUtils.epochMillis();
        PrioritizedNodeEvent prioritizedNodeEvent = getPrioritizedNodeEvent(now, "rpm-local/a/b/foo.txt",
                EventType.create);
        PrioritizedNodeEvent boundary = strategy.findNextBoundaryByTypeAndPath(
                prioritizedNodeEvent, ImmutableList.of(prioritizedNodeEvent));

        // First event is the boundary
        assertEquals(boundary, prioritizedNodeEvent);

        PrioritizedNodeEvent expectedBoundary =
                getPrioritizedNodeEvent(now + 10, "rpm-local/a/b/foo.txt", EventType.create);
        boundary = strategy.findNextBoundaryByTypeAndPath(
                prioritizedNodeEvent, ImmutableList.of(prioritizedNodeEvent, expectedBoundary));
        // Same path and type, new boundary
        assertEquals(boundary, expectedBoundary);

        boundary = strategy.findNextBoundaryByTypeAndPath(
                prioritizedNodeEvent, ImmutableList.of(prioritizedNodeEvent, expectedBoundary,
                        getPrioritizedNodeEvent(now + 10, "rpm-local/a/b/bar.txt", EventType.create)));
        // Different event path, boundary stays the same
        assertEquals(boundary, expectedBoundary);

        PrioritizedNodeEvent propsPrioritizedNodeEvent =
                getPrioritizedNodeEvent(now + 10, "rpm-local/a/b/foo.txt", EventType.props);
        boundary = strategy.findNextBoundaryByTypeAndPath(
                prioritizedNodeEvent, ImmutableList.of(prioritizedNodeEvent, expectedBoundary,
                        propsPrioritizedNodeEvent));
        // Different event type, boundary stays the same
        assertEquals(boundary, propsPrioritizedNodeEvent);
    }

    public void testGetSublistByBoundary() {
        IdenticalTypeAndPathDeduplicationStrategy strategy =
                new IdenticalTypeAndPathDeduplicationStrategy(RESPECTIVE_MDS_SQUASHABLE_TYPES);
        final long now = ClockUtils.epochMillis();
        PrioritizedNodeEvent prioritizedNodeEvent = getPrioritizedNodeEvent(now, "rpm-local/a/b/foo.txt",
                EventType.create);
        PrioritizedNodeEvent boundary = getPrioritizedNodeEvent(now + 10, "rpm-local/a/b/foo.txt", EventType.create);
        List<PrioritizedNodeEvent> subListByBoundary = strategy
                .getSubListByBoundary(prioritizedNodeEvent, boundary, ImmutableList.of(prioritizedNodeEvent, boundary));
        assertEquals(subListByBoundary.size(), 2);
        assertEquals(subListByBoundary.get(0), prioritizedNodeEvent);
        assertEquals(subListByBoundary.get(1), boundary);

        subListByBoundary = strategy
                .getSubListByBoundary(prioritizedNodeEvent, boundary, ImmutableList.of(prioritizedNodeEvent));
        assertEquals(subListByBoundary.size(), 1);
        assertEquals(subListByBoundary.get(0), prioritizedNodeEvent);
    }

    public void testIdenticalPathsAndTypeDeduplicated() {
        // events
        final long now = ClockUtils.epochMillis();
        ImmutableList<EventInfo> events = ImmutableList.of(
                new EventInfo(now, EventType.create, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 1, EventType.create, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 2, EventType.create, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 3, EventType.delete, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 3, EventType.delete, "rpm-local/a/b/foo.txt"),
                new EventInfo(now - 1, EventType.props, "rpm-local/a/b/foo.txt"),
                new EventInfo(now - 1, EventType.props, "rpm-local/a/b/foo.txt"));
        // chart
        NodeEventPriorityChart chart = NodeEventPriorityChartHelper
                .chartFromEvents(events, "metadata-event-operator");
        // Base chart
        new BasePriorityChartStrategy().apply(chart);
        assertTrue(chart.getEvents().stream().allMatch(event -> event.getPriority() == 1));

        new IdenticalTypeAndPathDeduplicationStrategy(RESPECTIVE_MDS_SQUASHABLE_TYPES).apply(chart);
        assertEquals(chart.getEvents().size(), 2);

        assertEquals(chart.getEvents().get(0).getType(), EventType.props);
        assertEquals(chart.getEvents().get(1).getType(), EventType.delete);
    }

    public void testOnlySequentialSquashAndOrderPreserved() {
        // events
        final long now = ClockUtils.epochMillis();
        ImmutableList<EventInfo> events = ImmutableList.of(
                new EventInfo(now, EventType.create, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 1, EventType.delete, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 2, EventType.create, "rpm-local/a/b/foo.txt"));
        // chart
        NodeEventPriorityChart chart = NodeEventPriorityChartHelper
                .chartFromEvents(events, "metadata-event-operator");
        // Base chart
        new BasePriorityChartStrategy().apply(chart);
        assertTrue(chart.getEvents().stream().allMatch(event -> event.getPriority() == 1));

        new IdenticalTypeAndPathDeduplicationStrategy(RESPECTIVE_MDS_SQUASHABLE_TYPES).apply(chart);
        assertEquals(chart.getEvents().size(), 3);

        assertEquals(chart.getEvents().get(0).getType(), EventType.create);
        assertEquals(chart.getEvents().get(1).getType(), EventType.delete);
        assertEquals(chart.getEvents().get(2).getType(), EventType.create);
    }

    public void testIdenticalPathsAndTypeDeduplicatedUnmodifiedList() {
        // events
        final long now = ClockUtils.epochMillis();
        ImmutableList<EventInfo> events = ImmutableList.of(
                new EventInfo(now, EventType.create, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 1, EventType.delete, "rpm-local/a/b/foo.txt"),
                new EventInfo(now - 1, EventType.props, "rpm-local/a/b/foo.txt"));
        // chart
        NodeEventPriorityChart chart = NodeEventPriorityChartHelper
                .chartFromEvents(events, "metadata-event-operator");
        // Base chart
        new BasePriorityChartStrategy().apply(chart);
        assertTrue(chart.getEvents().stream().allMatch(event -> event.getPriority() == 1));

        new IdenticalTypeAndPathDeduplicationStrategy(RESPECTIVE_MDS_SQUASHABLE_TYPES).apply(chart);
        assertEquals(chart.getEvents().size(), 2);

        assertEquals(chart.getEvents().get(0).getType(), EventType.props);
        assertEquals(chart.getEvents().get(1).getType(), EventType.delete);
    }

    public void testIdenticalPathsAndTypeDeduplicatedNoEvents() {
        // chart
        NodeEventPriorityChart chart = NodeEventPriorityChartHelper
                .chartFromEvents(new ArrayList<>(), "metadata-event-operator");
        // Base chart
        new BasePriorityChartStrategy().apply(chart);
        assertTrue(chart.getEvents().stream().allMatch(event -> event.getPriority() == 1));

        new IdenticalTypeAndPathDeduplicationStrategy(RESPECTIVE_MDS_SQUASHABLE_TYPES).apply(chart);
        assertEquals(chart.getEvents().size(), 0);
    }

    public void testInvalidEventsInChart() {
        final long now = ClockUtils.epochMillis();
        ImmutableList<EventInfo> events = ImmutableList.of(
                new EventInfo(now, EventType.create, null),
                new EventInfo(now + 1, null, "rpm-local/a/b/foo.txt"),
                new EventInfo(now - 1, EventType.props, "rpm-local/a/b/foo.txt"));
        // chart
        NodeEventPriorityChart chart = NodeEventPriorityChartHelper
                .chartFromEvents(events, "metadata-event-operator");
        // Base chart
        new BasePriorityChartStrategy().apply(chart);
        assertTrue(chart.getEvents().stream().allMatch(event -> event.getPriority() == 1));

        new IdenticalTypeAndPathDeduplicationStrategy(RESPECTIVE_MDS_SQUASHABLE_TYPES).apply(chart);
        // Original size preserved (strategy escaped)
        assertEquals(chart.getEvents().size(), 3);
    }

    public void testIdenticalPathsAndTypeDeduplicatedWithoutSquashOptIn() {
        // events
        final long now = ClockUtils.epochMillis();
        ImmutableList<EventInfo> events = ImmutableList.of(
                new EventInfo(now, EventType.create, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 1, EventType.create, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 2, EventType.create, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 3, EventType.delete, "rpm-local/a/b/foo.txt"),
                new EventInfo(now + 3, EventType.delete, "rpm-local/a/b/foo.txt"),
                new EventInfo(now - 1, EventType.props, "rpm-local/a/b/foo.txt"),
                new EventInfo(now - 1, EventType.props, "rpm-local/a/b/foo.txt"));
        // chart
        NodeEventPriorityChart chart = NodeEventPriorityChartHelper
                .chartFromEvents(events, "metadata-event-operator");
        // Base chart
        new BasePriorityChartStrategy().apply(chart);
        assertTrue(chart.getEvents().stream().allMatch(event -> event.getPriority() == 1));

        new IdenticalTypeAndPathDeduplicationStrategy(Collections.emptyList()).apply(chart);
        assertEquals(chart.getEvents().size(), 3);

        assertEquals(chart.getEvents().get(0).getType(), EventType.props);
        assertEquals(chart.getEvents().get(1).getType(), EventType.create);
        assertEquals(chart.getEvents().get(2).getType(), EventType.delete);
    }

    private PrioritizedNodeEvent getPrioritizedNodeEvent(long timestamp, String path, EventType type) {
        return new PrioritizedNodeEvent(new EventInfo(timestamp, type, path), "metadata-operator");
    }
}