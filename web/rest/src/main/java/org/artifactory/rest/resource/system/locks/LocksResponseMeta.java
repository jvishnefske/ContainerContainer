package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata block for {@code GET /api/system/locks} responses.
 * Helps interpret partial or degraded snapshots.
 */
public class LocksResponseMeta {

    @JsonProperty("queriedAt")
    private String queriedAt;

    @JsonProperty("queriedByNodeId")
    private String queriedByNodeId;

    @JsonProperty("fanOut")
    private FanOutMeta fanOut;

    public LocksResponseMeta() {
    }

    public LocksResponseMeta(String queriedAt, String queriedByNodeId, FanOutMeta fanOut) {
        this.queriedAt = queriedAt;
        this.queriedByNodeId = queriedByNodeId;
        this.fanOut = fanOut;
    }

    public String getQueriedAt() {
        return queriedAt;
    }

    public String getQueriedByNodeId() {
        return queriedByNodeId;
    }

    public FanOutMeta getFanOut() {
        return fanOut;
    }

    /**
     * Fan-out statistics for the cluster request.
     */
    public static class FanOutMeta {

        @JsonProperty("enabled")
        private boolean enabled;

        @JsonProperty("responded")
        private int responded;

        @JsonProperty("unreachable")
        private int unreachable;

        public FanOutMeta() {
        }

        public FanOutMeta(boolean enabled, int responded, int unreachable) {
            this.enabled = enabled;
            this.responded = responded;
            this.unreachable = unreachable;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getResponded() {
            return responded;
        }

        public int getUnreachable() {
            return unreachable;
        }
    }
}
