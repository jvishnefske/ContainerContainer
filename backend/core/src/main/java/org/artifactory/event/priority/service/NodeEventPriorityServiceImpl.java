package org.artifactory.event.priority.service;

import com.google.common.collect.ImmutableList;
import org.artifactory.event.EventHintType;
import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.event.priority.service.strategy.BasePriorityChartStrategy;
import org.artifactory.event.priority.service.strategy.ExecutionPredicatePriorityChartStrategy;
import org.artifactory.event.priority.service.strategy.IdenticalPathChartStrategy;
import org.artifactory.event.priority.service.strategy.IdenticalTypeAndPathDeduplicationStrategy;
import org.artifactory.event.priority.service.strategy.PriorityChartStrategy;
import org.artifactory.event.provider.EventProvider;
import org.artifactory.event.work.NodeEventOperator;
import org.artifactory.metadata.service.indexer.InternalMetadataServiceIndexHandler;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.repo.event.NodeEvent;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_ID_PROPERTY_NAME;

/**
 * @author Uriah Levy
 */
@Service
public class NodeEventPriorityServiceImpl implements NodeEventPriorityService {
    public static final int MAX_RETRIES = 5;
    private static final Logger log = LoggerFactory.getLogger(NodeEventPriorityServiceImpl.class);
    private NodeEventCursorService eventCursorService;
    private NodeEventPriorityStorageService eventPriorityStorageService;
    private List<PriorityChartStrategy> basePriorityChartStrategies;
    private InternalMetadataServiceIndexHandler metadataServiceIndexHandler;

    @Autowired
    public NodeEventPriorityServiceImpl(NodeEventCursorService eventCursorService,
            NodeEventPriorityStorageService eventPriorityStorageService,
            InternalMetadataServiceIndexHandler metadataServiceIndexHandler) {
        this.eventCursorService = eventCursorService;
        this.eventPriorityStorageService = eventPriorityStorageService;
        this.metadataServiceIndexHandler = metadataServiceIndexHandler;
        basePriorityChartStrategies = ImmutableList
                .of(new BasePriorityChartStrategy(), new IdenticalPathChartStrategy());
    }

    @Override
    public List<PrioritizedNodeEvent> getCurrentPriorityBatch(String operatorId) {
        return eventPriorityStorageService.getCurrentPriorityBatch(operatorId);
    }

    @Override
    public void createAndSaveEventPrioritiesIfNeeded(NodeEventOperator operator,
            EventProvider<? extends NodeEvent> eventProvider) {
        String operatorId = operator.getOperatorId();
        NodeEventCursor lastOperatorCursor = eventCursorService.cursorForOperator(operator.getOperatorId());
        List<? extends NodeEvent> allEvents = getNextBatch(lastOperatorCursor, eventProvider)
                .stream()
                .filter(event -> !metadataServiceIndexHandler.shouldUseRealTimeIndex(event.getPath()))
                .filter(this::isNotApplicationPropertyCleanupEvent)
                .toList();
        NodeEventPriorityChart priorityChart = NodeEventPriorityChartHelper
                .chartFromEvents(allEvents, operatorId);
        if (!priorityChart.isEmpty()) {
            log.debug("Creating new event priority list for operator '{}'", operatorId);
            final long finalTimestamp = priorityChart.getFinalTimestamp();
            applyPriorityStrategies(priorityChart, operator);
            saveChartAndUpdateCursor(operator, priorityChart, lastOperatorCursor, finalTimestamp);
        }
    }

    private boolean isNotApplicationPropertyCleanupEvent(NodeEvent event) {
        if (event instanceof EventInfo eventInfo) {
            Set<String> hints = eventInfo.getHints();
            if (hints != null) {
                return !hints.contains(EventHintType.ApplicationPropertyCleanup.getPrefix() +
                        APPLICATION_SERVICE_ID_PROPERTY_NAME);
            }
        }
        return true;
    }

    @Override
    public boolean priorityExists(PrioritizedNodeEvent prioritizedNodeEvent) {
        return eventPriorityStorageService.priorityExists(prioritizedNodeEvent);
    }

    @Override
    public void deleteEventPriority(PrioritizedNodeEvent prioritizedNodeEvent) {
        eventPriorityStorageService.deleteEventPriority(prioritizedNodeEvent);
    }

    @Override
    public boolean priorityListIsEmpty(String operatorId) {
        return eventPriorityStorageService.priorityListIsEmpty(operatorId);
    }

    @Override
    public void saveRetryCount(PrioritizedNodeEvent prioritizedNodeEvent) {
        eventPriorityStorageService.saveRetryCount(prioritizedNodeEvent);
    }

    @Override
    public boolean isDistributed() {
        return eventPriorityStorageService.isDistributed();
    }

    private List<? extends NodeEvent> getNextBatch(NodeEventCursor lastOperatorCursor,
            EventProvider<? extends NodeEvent> eventProvider) {
        if (lastOperatorCursor != null) {
            return eventProvider.provideNextBatch(lastOperatorCursor);
        }
        return Collections.emptyList();
    }

    private void applyPriorityStrategies(NodeEventPriorityChart nodeEventPriorityChart, NodeEventOperator operator) {
        // Operator execution predicate
        ExecutionPredicatePriorityChartStrategy operatorExecutionStrategy = new ExecutionPredicatePriorityChartStrategy(
                operator.getExecutionPredicate());
        operatorExecutionStrategy.apply(nodeEventPriorityChart);
        // Opt-in squash by path and type
        if (operator.isSquashEventsByPathAndTypeCombination()) {
            new IdenticalTypeAndPathDeduplicationStrategy(operator.getSquashableEventTypes())
                    .apply(nodeEventPriorityChart);
        }
        basePriorityChartStrategies.forEach(strategy -> strategy.apply(nodeEventPriorityChart));
    }

    private void saveChartAndUpdateCursor(NodeEventOperator operator,
            NodeEventPriorityChart nodeEventPriorityChart, NodeEventCursor lastOperatorCursor, long finalTimestamp) {
        if (!nodeEventPriorityChart.isEmpty()) {
            eventPriorityStorageService.savePriorityChart(nodeEventPriorityChart);
        }
        // If the last operator cursor is older than the final timestamp of the chart, update it
        if (lastOperatorCursor != null && finalTimestamp > lastOperatorCursor.getEventMarker()) {
            eventCursorService.updateCursor(operator.getOperatorId(), finalTimestamp);
        }
    }
}
