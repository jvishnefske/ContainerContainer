package org.artifactory.queue.behaviors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.artifactory.event.EventType;
import org.artifactory.queue.QueueEventImpl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remove duplicate events with same path & type.<br>
 * - Keeps the last duplicate event strategy.<br>
 * - Lookahead duplication of same object (events doesn't have to be neighbours to be participant in de-duplication)<br>
 * <pre>
 * "1|create|a"
 * "2|props |a" - will be removed (similar to 4)
 * "3|create|b"
 * "4|props |a"
 * </pre>
 * In-case receiving similar event types ,deduplication will be more power full
 * <pre>
 *  e.g: when receiving [create,props] as similar
 *
 *  * "1|create|a" - will be removed (similar to 2)
 *  * "2|props |a" - will be removed (similar to 4)
 *  * "3|create|b"
 *  * "4|props |a"
 *  * </pre>
 *
 * @author avishayh
 */
public class SimilarPathDedupBehavior implements EventReductionBehavior {

    protected final List<EventType> similarEventTypes;
    private static final Logger log = LoggerFactory.getLogger(SimilarPathDedupBehavior.class);

    public SimilarPathDedupBehavior(EventType... similarEventTypes) {
        if (similarEventTypes.length == 1) {
            throw new IllegalArgumentException("there is no point in passing only 1 similar event");
        }
        //its important to keep the order for the priority
        this.similarEventTypes = Arrays.asList(similarEventTypes);
    }

    @Override
    public List<QueueEventImpl> apply(List<QueueEventImpl> events) {
        if (events == null || events.size() < 2) {
            //for events size less than 2, nothing to do here
            return events;
        }
        Set<QueueEventImpl> toBeRemoved = new HashSet<>();
        Map<QueueEventImpl, QueueEventImpl> toBeReplaced = new HashMap<>();

        //this map is used to find the duplicated events
        //creating subgroups by uniqueKey
        Multimap<String, QueueEventImpl> eventsByPath = ArrayListMultimap.create();
        events.forEach(event -> eventsByPath.put(getUniqueKey(event), event));
        //handle each group separately
        eventsByPath.asMap().values().forEach((group) -> {
            if (group.size() > 1) {
                //found more than one event in a group - handle group logic
                handleGroup((List<QueueEventImpl>) group, toBeRemoved, toBeReplaced);
            }
        });
        //now that we populated the toBeRemoved and toBeReplaced, lets do it!
        if (log.isDebugEnabled()) {
            toBeRemoved.forEach(eventInfo -> log.debug("Removing event set for {}", eventInfo.getPath()));
        }
        return events.stream()
                .filter(event -> !toBeRemoved.contains(event))
                .map(event -> toBeReplaced.getOrDefault(event, event))
                .collect(Collectors.toList());
    }

    protected void handleGroup(List<QueueEventImpl> group, Set<QueueEventImpl> remove,
            Map<QueueEventImpl, QueueEventImpl> replace) {
        markRemovedEvents(group, remove);
    }

    void markRemovedEvents(List<QueueEventImpl> group, Set<QueueEventImpl> toBeRemoved) {
        //group size is at least 2
        int size = group.size();
        //need to keep the last event (size-1)
        //iterate all by last event
        for (int i = 0; i < size - 1; i++) {
            QueueEventImpl event = group.get(i);
            log.debug("Remove event {} added", event.getPath());
            toBeRemoved.add(event);
        }
    }

    protected String getUniqueKey(QueueEventImpl event) {
        EventType type = event.getType();
        if (similarEventTypes.contains(type)) {
            //create a key without the type, so they will be in the same group
            return event.getPath();
        } else {
            //create a key combine of eventType and path,
            return type.toString() + "|" + event.getPath();
        }
    }

}
