package org.artifactory.queue.behaviors;

import org.artifactory.event.EventType;

/**
 * <pre>
 *  Example:
 *  c- create , u- update , p- props ,  d -delete
 *  +----------------+------------+------------+
 *  | use case       |  Input     |    Output  |
 *  +----------------+------------+------------+
 *  | eliminator c   | 1|delete|a |            | //eliminated by create
 *  | eliminates [d] | 2|create|a | 2|create|a |
 *  | self false     |            |            |
 *  +----------------+------------+------------+
 *  </pre>
 * @author avishayh
 */
public class CreateEliminateDeleteBehavior extends EliminateEventBehavior {

    public CreateEliminateDeleteBehavior() {
        eliminator(EventType.create);
        eliminates(EventType.delete);
    }
}
