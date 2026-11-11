package org.artifactory.queue.behaviors;

import com.google.common.collect.Lists;
import org.artifactory.event.EventType;
import org.artifactory.queue.QueueEventImpl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extends the SimilarEventDedupBehaviour
 * instead of the strategy: "Keeps the last duplicate event"
 * uses a prioritizedEventTypes to allow merging types when deduplication found
 * when merging happens we take the higher priority event with his hints and change it to the last id of the group
 * we still save its original id (using hints field) in case rollback is needed.<br>
 * whenever a type which is not part of the priority group exist in the middle of grouped events, no dedup will happen
 * <pre>
 *  Examples:
 *  c- create , u- update , p- props ,  d -delete
 *  +--------+------------+------------+
 *  | Group  |  Input     |    Output  |
 *  +--------+------------+------------+
 *  | [c,u]  | 1|create|a |            |
 *  |        | 2|update|a | 2|create|a | dedup with merge, create has higher priority than update
 *  +--------+------------+------------+
 *  | [p,u]  | 1|update|a |            |
 *  |        | 2|props|a  | 2|props|a  | regular dedup, props has higher priority than update
 *  +--------+------------+------------+
 *  | [c,u]  | 1|create|a | 1|create|a |
 *  |        | 2|props|a  | 2|props|a  | props is not part of the group, dedup aborted
 *  |        | 3|update|a | 3|update|a |
 *  +--------+------------+------------+
 * </pre>
 *
 * @author avishayh
 */
public class PrioritizedEventDedupBehavior extends SimilarPathDedupBehavior {

    private final SimilarPathDedupBehavior dedup = new SimilarPathDedupBehavior();
    private static final Logger log = LoggerFactory.getLogger(PrioritizedEventDedupBehavior.class);

    public PrioritizedEventDedupBehavior(EventType... prioritizedEventTypes) {
        super(prioritizedEventTypes);

        if (prioritizedEventTypes.length == 0) {
            throw new IllegalArgumentException("must provide prioritizedEventTypes for this behavior");
        }
    }

    @Override
    public List<QueueEventImpl> apply(List<QueueEventImpl> events) {
        //1st take care of regular dedup with non similar events by path
        List<QueueEventImpl> nonDuplicateEvents = dedup.apply(events);
        //continue with prioritize group
        return super.apply(nonDuplicateEvents);
    }

    @Override
    protected void handleGroup(List<QueueEventImpl> group, Set<QueueEventImpl> toBeRemoved,
            Map<QueueEventImpl, QueueEventImpl> toBeReplaced) {
        //group size is at least 2
        List<QueueEventImpl> tempGroup = Lists.newArrayList();
        for (QueueEventImpl event : group) {
            log.debug("Handling queue event {}", event.getPath());
            if (similarEventTypes.contains(event.getType())) {
                log.debug("Handling queue event {} to team group with type {}", event.getPath(), event.getType());
                tempGroup.add(event);
            } else {
                //if found event with different type, we check if merge requires until the current group
                //and then we clear the group to start over
                removeAndReplace(tempGroup, toBeRemoved, toBeReplaced);
                tempGroup.clear();
            }
        }
        removeAndReplace(tempGroup, toBeRemoved, toBeReplaced);
    }

    private void removeAndReplace(List<QueueEventImpl> group, Set<QueueEventImpl> toBeRemoved,
            Map<QueueEventImpl, QueueEventImpl> toBeReplaced) {
        //if now we still have at least 2 we continue
        int size = group.size();
        if (size > 1) {
            //call the mark remove on all but last event
            markRemovedEvents(group, toBeRemoved);
            markReplacedEvents(group, toBeReplaced);
        }
    }

    private void markReplacedEvents(List<QueueEventImpl> group, Map<QueueEventImpl, QueueEventImpl> toBeReplaced) {
        QueueEventImpl lastEvent = group.get(group.size() - 1);
        QueueEventImpl firstHighestPriorityEvent = findFirstHighestPriorityEvent(group);
        if (firstHighestPriorityEvent != null && !lastEvent.getType().equals(firstHighestPriorityEvent.getType())) {
            //the last event does not have the highest priority we need to replace
            replaceEvent(toBeReplaced, lastEvent, firstHighestPriorityEvent);
        }
    }

    private void replaceEvent(Map<QueueEventImpl, QueueEventImpl> toBeReplaced, QueueEventImpl lastEvent,
            QueueEventImpl firstHighestPriorityEvent) {

        QueueEventImpl replacedEvent =
                new QueueEventImpl(lastEvent, firstHighestPriorityEvent) {
                    @Override
                    public Set<String> getHints() {
                        return firstHighestPriorityEvent.getHints();
                    }

                    @Override
                    public String getPath() {
                        return firstHighestPriorityEvent.getPath();
                    }

                    @Override
                    public EventType getType() {
                        return firstHighestPriorityEvent.getType();
                    }
                };

        toBeReplaced.put(lastEvent, replacedEvent);
    }

    @Override
    protected String getUniqueKey(QueueEventImpl event) {
        return event.getPath();
    }

    private QueueEventImpl findFirstHighestPriorityEvent(List<QueueEventImpl> group) {
        //iterating by requested priority type
        for (EventType type : similarEventTypes) {
            //check which is highest priority type exist in group
            QueueEventImpl found = group.stream()
                    .filter(event -> event.getType().equals(type))
                    .findFirst()
                    .orElse(null);
            //once found we return the 1st one
            if (found != null) {
                return found;
            }
            //if not found we continue to iterate on the next highest priority
        }
        return null;
    }
}
