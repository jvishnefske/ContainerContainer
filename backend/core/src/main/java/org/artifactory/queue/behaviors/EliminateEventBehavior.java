package org.artifactory.queue.behaviors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.artifactory.event.EventType;
import org.artifactory.queue.QueueEventImpl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An elimination behavior where one eventType(eliminator) deletes other types (eliminatedTypes) if found after them
 * on the same path.<br>
 * see inherited classes for examples
 * @author avishayh
 */
public class EliminateEventBehavior implements EventReductionBehavior {
    private static final Logger log = LoggerFactory.getLogger(EliminateEventBehavior.class);

    private boolean selfElimination;
    private EventType eliminator;
    private Set<EventType> eliminatedTypes;

    public EliminateEventBehavior eliminator(EventType eliminator) {
        this.eliminator = eliminator;
        return this;
    }

    public EliminateEventBehavior eliminates(EventType... eliminatedTypes) {
        if (eliminator == null) {
            throw new IllegalArgumentException("must provide eliminator before eliminatedTypes");
        }
        this.eliminatedTypes = Set.of(eliminatedTypes);
        if (this.eliminatedTypes.contains(eliminator)) {
            throw new IllegalArgumentException("eliminator cannot be of same type as eliminatedTypes");
        }
        return this;
    }

    public EliminateEventBehavior selfElimination() {
        selfElimination = true;
        return this;
    }

    @Override
    public List<QueueEventImpl> apply(List<QueueEventImpl> events) {
        if (eliminator == null || eliminatedTypes == null || eliminatedTypes.isEmpty()) {
            throw new IllegalArgumentException("must provide eliminator and eliminatedTypes");
        }
        if (events == null || events.size() < 2) {
            //for events size less than 2, nothing to do here
            return events;
        }
        Set<QueueEventImpl> toBeRemoved = new HashSet<>();

        //creating subgroups by path
        Multimap<String, QueueEventImpl> eventsByPath = ArrayListMultimap.create();
        events.forEach(event -> eventsByPath.put(event.getPath(), event));
        //handle each group separately
        eventsByPath.asMap().values().forEach(group -> {
            //iterate group logic:
            //mark events which are candidates for removal (part of eliminatedTypes)
            //if we reach an eliminator
            //  - we add the candidates to the toBeRemoved list
            //  - if selfElimination flag is on - we add the eliminator event also
            //  - we cleanup the candidates list (already marked them)
            //continue until no more events
            List<QueueEventImpl> candidatesForRemoval = new ArrayList<>();
            group.forEach(evt -> {
                boolean foundCandidate = eliminatedTypes.contains(evt.getType());
                boolean foundEliminator = evt.getType().equals(eliminator);
                //FYI: foundCandidate and foundEliminator cannot both be true
                if (foundCandidate) {
                    log.debug("Found candidate for removal: {} with type {}", evt.getPath(), evt.getType() );
                    candidatesForRemoval.add(evt);
                }
                if (foundEliminator && !candidatesForRemoval.isEmpty()) {
                    toBeRemoved.addAll(candidatesForRemoval);
                    candidatesForRemoval.clear();
                    log.debug("Found eliminator for removal: {} with type {}", evt.getPath(), evt.getType() );
                    if (selfElimination) {
                        log.debug("Found self elimination event: {}", evt.getPath());
                        toBeRemoved.add(evt);
                    }
                }
            });
        });
        if (log.isDebugEnabled()) {
            toBeRemoved.forEach(eventInfo -> log.debug("Found event to be removed: {}", eventInfo.getPath()));
        }
        //now that toBeRemoved list is populated, let's do it
        return events.stream()
                .filter(event -> !toBeRemoved.contains(event))
                .collect(Collectors.toList());
    }

}
