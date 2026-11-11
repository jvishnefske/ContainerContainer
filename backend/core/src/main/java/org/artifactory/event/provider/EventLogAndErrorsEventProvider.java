package org.artifactory.event.provider;

import org.artifactory.common.ConstantValues;
import org.artifactory.event.work.NodeEventOperator;
import org.artifactory.event.work.NodeEventsErrorService;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.ClockUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.storage.db.event.error.service.ErrorInfoConverter.toEventInfos;

/**
 * @author Uriah Levy
 * An event provider that reads from the node_events and node_events_errors table
 */
public class EventLogAndErrorsEventProvider implements EventProvider<EventInfo> {

    private static final Logger log = LoggerFactory.getLogger(EventLogAndErrorsEventProvider.class);
    private static final int BATCH_SIZE = 5000;
    public static final int ERROR_BATCH_SIZE = 1000;
    private final EventsService eventsService;
    private final NodeEventsErrorService nodeEventsErrorService;
    private final NodeEventOperator eventOperator;

    public EventLogAndErrorsEventProvider(EventsService eventsService,
            NodeEventsErrorService nodeEventsErrorService, NodeEventOperator eventOperator) {
        this.eventsService = eventsService;
        this.nodeEventsErrorService = nodeEventsErrorService;
        this.eventOperator = eventOperator;
    }

    @Override
    public List<EventInfo> provideNextBatch(NodeEventCursor currentCursor) {
        long currentCursorTimestamp = currentCursor.getEventMarker();
        // The unstable margin is a short time frame during which a TX has already recorded an event with a particular
        // timestamp, but hasn't committed it yet in the event log. We make sure to avoid those TX's by never performing
        // events that are as new as the current system time.
        long nowMinusUnstableMargin = getNowMinusUnstableMargin();
        try (Stream<EventInfo> eventsStream = eventsService.getEventsSince(currentCursorTimestamp, BATCH_SIZE)) {
            List<EventInfo> eventInfos = eventsStream
                    .filter(event -> event.getTimestamp() < nowMinusUnstableMargin)
                    .collect(Collectors.toList());
            List<NodeEventsErrorInfo> errors =
                    nodeEventsErrorService
                            .getErrors(eventOperator.getEventCategory(), eventOperator.getOperatorId(),
                                    eventOperator.getBackOffStrategy(), ERROR_BATCH_SIZE);
            List<EventInfo> errorEvents = toEventInfos(errors);
            log.trace("Providing events from event log: {} and from node events errors: {}", eventInfos,
                    errorEvents);
            eventInfos.addAll(errorEvents);
            return eventInfos;
        }
    }

    @Override
    public ProviderType getType() {
        return ProviderType.EVENT_LOG;
    }

    private long getNowMinusUnstableMargin() {
        return ClockUtils.epochMillis() -
                TimeUnit.SECONDS.toMillis(ConstantValues.metadataEventUnstableMarginSecs.getLong());
    }
}
