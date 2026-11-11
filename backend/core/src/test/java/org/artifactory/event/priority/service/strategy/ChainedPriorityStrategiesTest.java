package org.artifactory.event.priority.service.strategy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.event.EventType;
import org.artifactory.event.priority.service.NodeEventPriorityChart;
import org.artifactory.event.priority.service.NodeEventPriorityChartHelper;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.repo.event.EventInfo;
import org.jfrog.common.ClockUtils;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class ChainedPriorityStrategiesTest {
    private static final List<Pair<EventType, EventType>> RESPECTIVE_MDS_SQUASHABLE_TYPES =
            Lists.newArrayList(new ImmutablePair<>(EventType.create, EventType.props),
                    new ImmutablePair<>(EventType.props, EventType.create));

    public void testFilterAndDeduplicationAndSquash() {
        final long now = ClockUtils.epochMillis();
        EventInfo createGemEvent = new EventInfo(now, EventType.create, "gem-local/foo.gem");
        EventInfo propsGemEvent = new EventInfo(now, EventType.props, "gem-local/foo.gem");
        EventInfo nonGemEvent = new EventInfo(now, EventType.create, "gem-local/foo.gemspec.rz");
        EventInfo anotherGemEvent = new EventInfo(now, EventType.delete, "gem-local/foo.gem");
        ImmutableList<EventInfo> events = ImmutableList.of(createGemEvent, propsGemEvent, nonGemEvent, anotherGemEvent);
        // chart
        NodeEventPriorityChart chart = NodeEventPriorityChartHelper
                .chartFromEvents(events, "metadata-event-operator");
        // Base chart
        new BasePriorityChartStrategy().apply(chart);
        assertTrue(chart.getEvents().stream().allMatch(event -> event.getPriority() == 1));

        MetadataEventService metadataEventService = Mockito.mock(MetadataEventService.class);
        when(metadataEventService.meetsExecutionCriteria(argThat(arg -> arg.getPath().endsWith(".gem"))))
                .thenReturn(true);
        // Operator execution predicate
        new ExecutionPredicatePriorityChartStrategy(event -> event.getPath().endsWith(".gem")).apply(chart);
        new IdenticalPathChartStrategy().apply(chart);
        new IdenticalTypeAndPathDeduplicationStrategy(RESPECTIVE_MDS_SQUASHABLE_TYPES).apply(chart);

        assertEquals(2, chart.getEvents().size());
        assertEquals(chart.getEvents().get(0).getPath(), "gem-local/foo.gem");
        assertEquals(chart.getEvents().get(0).getType(), EventType.create);
        assertEquals(chart.getEvents().get(1).getPath(), "gem-local/foo.gem");
        assertEquals(chart.getEvents().get(1).getType(), EventType.delete);
    }

    public void testFilterAndDeduplicationWithoutSquash() {
        final long now = ClockUtils.epochMillis();
        EventInfo createGemEvent = new EventInfo(now, EventType.create, "gem-local/foo.gem");
        EventInfo propsGemEvent = new EventInfo(now, EventType.props, "gem-local/foo.gem");
        EventInfo nonGemEvent = new EventInfo(now, EventType.create, "gem-local/foo.gemspec.rz");
        EventInfo anotherGemEvent = new EventInfo(now, EventType.delete, "gem-local/foo.gem");
        ImmutableList<EventInfo> events = ImmutableList.of(createGemEvent, propsGemEvent, nonGemEvent, anotherGemEvent);
        // chart
        NodeEventPriorityChart chart = NodeEventPriorityChartHelper
                .chartFromEvents(events, "metadata-event-operator");
        // Base chart
        new BasePriorityChartStrategy().apply(chart);
        assertTrue(chart.getEvents().stream().allMatch(event -> event.getPriority() == 1));

        MetadataEventService metadataEventService = Mockito.mock(MetadataEventService.class);
        when(metadataEventService.meetsExecutionCriteria(argThat(arg -> arg.getPath().endsWith(".gem"))))
                .thenReturn(true);
        // Operator execution predicate
        new ExecutionPredicatePriorityChartStrategy(event -> event.getPath().endsWith(".gem")).apply(chart);
        new IdenticalPathChartStrategy().apply(chart);
        new IdenticalTypeAndPathDeduplicationStrategy(Collections.emptyList()).apply(chart);

        assertEquals(3, chart.getEvents().size());
        assertEquals(chart.getEvents().get(0).getPath(), "gem-local/foo.gem");
        assertEquals(chart.getEvents().get(0).getType(), EventType.create);
        assertEquals(chart.getEvents().get(1).getPath(), "gem-local/foo.gem");
        assertEquals(chart.getEvents().get(1).getType(), EventType.props);
        assertEquals(chart.getEvents().get(2).getPath(), "gem-local/foo.gem");
        assertEquals(chart.getEvents().get(2).getType(), EventType.delete);
    }
}
