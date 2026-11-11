package org.artifactory.queue.behaviors;

import org.artifactory.event.EventType;

/**
 * <pre>
 *  Example:
 *  c- create , u- update , p- props ,  d -delete
 *  +------------------+------------+------------+
 *  | use case         |  Input     |    Output  |
 *  +------------------+------------+------------+
 *  | eliminator d     | 1|create|a |            | //eliminated by delete
 *  | eliminates [c,u] | 2|update|a |            | //eliminated by delete
 *  | self true        | 3|delete|a |            | //self eliminating
 *  +------------------+------------+------------+
 *  | eliminator d     | 1|delete|a | 1|delete|a | //no elimination
 *  | eliminates [c,u] |            |            |
 *  | self true        |            |            |
 *  +------------------+------------+------------+
 *  </pre>
 * @author avishayh
 */
public class DeleteEliminateUpdateOrCreateBehavior extends EliminateEventBehavior {

    public DeleteEliminateUpdateOrCreateBehavior() {
        eliminator(EventType.delete);
        eliminates(EventType.create, EventType.update);
        selfElimination();
    }
}
