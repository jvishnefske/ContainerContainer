package org.artifactory.repo;

import java.util.List;

/**
 * @author Shahar Palmor
 */
public record FullSyncRtfsResponse(String message, List<String> resultPaths) {
    public FullSyncRtfsResponse(String message) {
        this(message, List.of());
    }
}
