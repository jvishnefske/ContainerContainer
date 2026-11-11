package org.artifactory.system.load.event;

/**
 * Interface representing a system load event.
 * This event is triggered when the system load state changes.
 * @author Mor Merhav
 */
public interface SystemLoadEvent {

    /**
     * @return the system load state
     */
    SystemLoadState systemLoadState();

    /**
     * @return the creation time of the event
     */
    long creationTime();
}