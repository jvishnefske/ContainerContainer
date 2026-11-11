package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Counts summary for {@code GET /api/system/locks} responses.
 * Convenient for scripts and incident dashboards.
 */
public class LocksResponseSummary {

    @JsonProperty("totalHeldLocks")
    private int totalHeldLocks;

    @JsonProperty("nodesOk")
    private int nodesOk;

    @JsonProperty("nodesUnreachable")
    private int nodesUnreachable;

    public LocksResponseSummary() {
    }

    public LocksResponseSummary(int totalHeldLocks, int nodesOk, int nodesUnreachable) {
        this.totalHeldLocks = totalHeldLocks;
        this.nodesOk = nodesOk;
        this.nodesUnreachable = nodesUnreachable;
    }

    public int getTotalHeldLocks() {
        return totalHeldLocks;
    }

    public int getNodesOk() {
        return nodesOk;
    }

    public int getNodesUnreachable() {
        return nodesUnreachable;
    }
}
