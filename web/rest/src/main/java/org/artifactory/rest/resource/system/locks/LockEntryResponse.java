package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * JSON representation of a single advisory lock held by one Artifactory node.
 */
public class LockEntryResponse {

    @JsonProperty("category")
    private String category;

    @JsonProperty("key")
    private String key;

    @JsonProperty("lockedSinceEpochMs")
    private long lockedSinceEpochMs;

    @JsonProperty("lockedSinceIso")
    private String lockedSinceIso;

    @JsonProperty("threadId")
    private long threadId;

    @JsonProperty("threadName")
    private String threadName;

    public LockEntryResponse() {
    }

    public LockEntryResponse(String category, String key, long lockedSinceEpochMs, long threadId, String threadName) {
        this.category = category;
        this.key = key;
        this.lockedSinceEpochMs = lockedSinceEpochMs;
        this.lockedSinceIso = Instant.ofEpochMilli(lockedSinceEpochMs).toString();
        this.threadId = threadId;
        this.threadName = threadName;
    }

    public String getCategory() {
        return category;
    }

    public String getKey() {
        return key;
    }

    public long getLockedSinceEpochMs() {
        return lockedSinceEpochMs;
    }

    public String getLockedSinceIso() {
        return lockedSinceIso;
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }
}
