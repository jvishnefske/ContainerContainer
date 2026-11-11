package org.artifactory.repo.event;

import org.artifactory.event.EventType;

/**
 * @author Uriah Levy
 */
public interface NodeEvent {
    String getPath();

    EventType getType();

    long getTimestamp();
}