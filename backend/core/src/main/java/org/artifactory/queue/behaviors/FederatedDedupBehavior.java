package org.artifactory.queue.behaviors;

import org.artifactory.event.EventType;

/**
 * <pre>
 *  Examples:
 *  c- create , u- update , p- props ,  d -delete
 *  +--------+------------+------------+
 *  | Group  |  Input     |    Output  |
 *  +--------+------------+------------+
 *  |[c,p,u] | 1|create|a |            |
 *  |        | 2|update|a | 2|create|a | dedup with merge, create has higher priority than update
 *  +--------+------------+------------+
 *  |[c,p,u] | 1|update|a |            |
 *  |        | 2|props|a  | 2|update|a | regular dedup, update has higher priority than props
 *  +--------+------------+------------+
 *  |[c,p,u] | 1|create|a |            |
 *  |        | 2|props|a  |            |
 *  |        | 3|update|a | 3|create|a | create is the highest priority in this group
 *
 *  +--------+------------+------------+
 * </pre>
 *
 * @author avishayh
 */
public class FederatedDedupBehavior extends PrioritizedEventDedupBehavior {

    public FederatedDedupBehavior() {
        super(EventType.create, EventType.update, EventType.props);
    }
}
