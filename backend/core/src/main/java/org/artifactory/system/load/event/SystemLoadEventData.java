package org.artifactory.system.load.event;

public record SystemLoadEventData(SystemLoadState systemLoadState, long creationTime)
        implements SystemLoadEvent {
}