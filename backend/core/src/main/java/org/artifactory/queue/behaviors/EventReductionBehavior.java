package org.artifactory.queue.behaviors;

import org.artifactory.queue.QueueEventImpl;

import java.util.List;

/**
 * @author avishayh
 */
public interface EventReductionBehavior {

    List<QueueEventImpl> apply(List<QueueEventImpl> events);

}
