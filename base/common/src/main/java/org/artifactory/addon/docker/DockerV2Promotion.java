package org.artifactory.addon.docker;

import lombok.Data;

/**
 * @author Hezi Cohen
 */
@Data
public class DockerV2Promotion {
    private String targetRepo;
    private String dockerRepository;
    private String targetDockerRepository;
    private String tag;
    private String targetTag;
    private EventType action;
    private boolean partialSuccess = false;

    public enum EventType {
        COPY,
        MOVE,
        DELETE
    }

    public boolean isCopy() {
        return EventType.COPY.equals(action);
    }

    public boolean isDelete() {
        return EventType.DELETE.equals(action);
    }
}
